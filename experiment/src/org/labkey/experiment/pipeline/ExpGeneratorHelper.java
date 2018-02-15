/*
 * Copyright (c) 2013-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.experiment.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.FileUtil;
import org.labkey.experiment.ExperimentRunGraph;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class to build experiments from pipeline jobs.  Used by XarGeneratorTask
 * User: dax
 * Date: Apr 24, 2013
*/
public class ExpGeneratorHelper
{
    static private void addProtocol(PipelineJob job, Map<String, ExpProtocol> protocols, String name)
    {
        // Check if we've already dealt with this one
        if (!protocols.containsKey(name))
        {
            // Check if it's in the database already
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(job.getContainer(), name);
            if (protocol == null)
            {
                protocol = ExperimentService.get().createExpProtocol(job.getContainer(), ExpProtocol.ApplicationType.ProtocolApplication, name);
                protocol.save(job.getUser());
            }
            protocols.put(name, protocol);
        }
    }

    static private ExpData addData(PipelineJob job, Map<URI, ExpData> datas, URI originalURI, XarSource source) throws PipelineJobException
    {
        ExpData data = datas.get(originalURI);
        if (data != null)
        {
            return data;
        }

        if (source != null)
            return addData(datas, originalURI, source);

        return addData(job, datas, originalURI);
    }

    // undone:  does this make sense for non-file based data?
    static private ExpData addData(Map<URI, ExpData> datas, URI originalURI, XarSource source) throws PipelineJobException
    {
        URI uri;
        try
        {
            uri = new URI(source.getCanonicalDataFileURL(FileUtil.uriToString(originalURI)));
        }
        catch (XarFormatException | URISyntaxException e)
        {
            throw new PipelineJobException(e);
        }

        ExpData data = datas.get(uri);

        // Check if we've already dealt with this one
        if (data == null)
        {
            try
            {
                data = ExperimentService.get().createData(uri, source);
            }
            catch (XarFormatException e)
            {
                throw new PipelineJobException(e);
            }
            datas.put(uri, data);
            datas.put(originalURI, data);
        }
        return data;
    }

    static private ExpData addData(PipelineJob job, Map<URI, ExpData> datas, URI originalURI) throws PipelineJobException
    {
        Container c = job.getContainer();

        // todo: this code shouldn't know about the TransformDataType
        Lsid lsid = new Lsid(ExperimentService.get().generateGuidLSID(c, ExperimentService.get().getDataType("Transform")));
        ExpData data = ExperimentService.get().createData(c, FileUtil.uriToString(originalURI), lsid.toString());

        if (data != null)
        {
            datas.put(originalURI, data);
            data.save(job.getUser());
        }

        return data;
    }


    static public ExpRunImpl insertRun(PipelineJob job) throws PipelineJobException, ValidationException
    {
        return insertRun(job, null, null);
    }

    static public ExpRunImpl insertRun(PipelineJob job, @Nullable XarSource source, @Nullable XarWriter xarWriter) throws PipelineJobException, ValidationException
    {
        ExpRunImpl run;
        try
        {
            RecordedActionSet actionSet = job.getActionSet();
            Set<RecordedAction> actions = new LinkedHashSet<>(actionSet.getActions());

            Map<String, ExpProtocol> protocolCache = new HashMap<>();
            List<String> protocolSequence = new ArrayList<>();
            Map<URI, String> runOutputsWithRoles = new LinkedHashMap<>();
            Map<URI, String> runInputsWithRoles = new HashMap<>();

            runInputsWithRoles.putAll(actionSet.getOtherInputs());

            job.info("Checking files referenced by experiment run");
            for (RecordedAction action : actions)
            {
                for (RecordedAction.DataFile dataFile : action.getInputs())
                {
                    if (runInputsWithRoles.get(dataFile.getURI()) == null)
                    {
                        // For inputs, don't stomp over the role specified the first time a file was used as an input
                        runInputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                        // This can be slow over network file systems so do it outside of the database
                        // transaction. The XarSource caches the results so it'll be fast once we start inserting.

                        // consider: this shouldn't be part of the ExpGeneratorHelper code
                        if (null != source)
                            source.getCanonicalDataFileURL(FileUtil.uriToString(dataFile.getURI()));
                    }
                }
                for (RecordedAction.DataFile dataFile : action.getOutputs())
                {
                    if (!dataFile.isTransient())
                    {
                        // For outputs, want to use the last role that was specified, so always overwrite
                        runOutputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                    }

                    // This can be slow over network file systems so do it outside of the database
                    // transaction. The XarSource caches the results so it'll be fast once we start inserting.

                    // consider: this shouldn't be part of the ExpGeneratorHelper code
                    if (null != source)
                        source.getCanonicalDataFileURL(FileUtil.uriToString(dataFile.getURI()));
                }
            }
            job.debug("File check complete");

            // Files count as inputs to the run if they're used by one of the actions and weren't produced by one of
            // the actions.
            for (RecordedAction action : actions)
            {
                for (RecordedAction.DataFile dataFile : action.getOutputs())
                {
                    runInputsWithRoles.remove(dataFile.getURI());
                }
            }

            ExpProtocol parentProtocol;
            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
            {
                // Build up the sequence of steps for this pipeline definition, which gets turned into a protocol
                for (TaskId taskId : job.getTaskPipeline().getTaskProgression())
                {
                    TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(taskId);

                    for (String name : factory.getProtocolActionNames())
                    {
                        addProtocol(job, protocolCache, name);
                        protocolSequence.add(name);
                    }
                }

                // Check to make sure that we have a protocol that corresponds with each action
                for (RecordedAction action : actions)
                {
                    if (protocolCache.get(action.getName()) == null)
                    {
                        throw new IllegalArgumentException("Could not find a matching action declaration for " + action.getName());
                    }
                }

                String protocolObjectId = job.getTaskPipeline().getProtocolIdentifier();
                Lsid parentProtocolLSID = new Lsid(ExperimentService.get().generateLSID(job.getContainer(), ExpProtocol.class, protocolObjectId));
                parentProtocol = ensureProtocol(job, protocolCache, protocolSequence, parentProtocolLSID, job.getTaskPipeline().getProtocolShortDescription());

                transaction.commit();
            }

            // Break the protocol insertion and run insertion into two separate transactions
            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                run = insertRun(job, actionSet.getActions(), source, runOutputsWithRoles, runInputsWithRoles, parentProtocol);

                if (null != xarWriter)
                    xarWriter.writeToDisk(run);

                transaction.commit();
            }
            ExperimentRunGraph.clearCache(run.getContainer());
        }
        catch (XarFormatException e)
        {
            throw new PipelineJobException(e);
        }

        // Consider these actions complete. There may be additional runs created later in this job, and they
        // shouldn't duplicate the actions.
        job.clearActionSet(run);
        return run;
    }

    static private ExpRunImpl insertRun(PipelineJob job, Set<RecordedAction> actions, XarSource source, Map<URI, String> runOutputsWithRoles, Map<URI, String> runInputsWithRoles, ExpProtocol parentProtocol)
        throws PipelineJobException, ValidationException
    {
        ExpRunImpl run = ExperimentServiceImpl.get().createExperimentRun(job.getContainer(), job.getDescription());
        run.setProtocol(parentProtocol);

        if (null != source)
            run.setFilePathRoot(source.getRoot());

        run.setJobId(PipelineService.get().getJobId(job.getUser(), job.getContainer(), job.getJobGUID()));
        run.save(job.getUser());

        Map<String, ExpProtocolAction> expActionMap = new HashMap<>();
        List<? extends ExpProtocolAction> expActions = parentProtocol.getSteps();
        for (ExpProtocolAction action : expActions)
        {
            expActionMap.put(action.getChildProtocol().getName(), action);
        }

        Map<URI, ExpData> datas = new LinkedHashMap<>();

        // Set up the inputs to the whole run
        ExpProtocolApplication inputApp = run.addProtocolApplication(job.getUser(), expActions.get(0), parentProtocol.getApplicationType(), "Run inputs");
        for (Map.Entry<URI, String> runInput : runInputsWithRoles.entrySet())
        {
            URI uri = runInput.getKey();
            String role = runInput.getValue();
            ExpData data = addData(job, datas, uri, source);
            inputApp.addDataInput(job.getUser(), data, role);
        }

        // Set up the inputs and outputs for the individual actions
        for (RecordedAction action : actions)
        {
            // Look up the step by its name
            ExpProtocolAction step = expActionMap.get(action.getName());

            ExpProtocolApplication app = run.addProtocolApplication(job.getUser(), step, ExpProtocol.ApplicationType.ProtocolApplication,
                    action.getName(), action.getStartTime(), action.getEndTime(), action.getRecordCount());

            if (!action.getName().equals(action.getDescription()))
            {
                app.setName(action.getDescription());
                app.save(job.getUser());
            }

            // Transfer all the protocol parameters
            for (Map.Entry<RecordedAction.ParameterType, Object> param : action.getParams().entrySet())
            {
                ProtocolApplicationParameter protAppParam = new ProtocolApplicationParameter();
                protAppParam.setProtocolApplicationId(app.getRowId());
                protAppParam.setRunId(run.getRowId());
                RecordedAction.ParameterType paramType = param.getKey();
                protAppParam.setName(paramType.getName());
                protAppParam.setOntologyEntryURI(paramType.getURI());

                protAppParam.setValue(paramType.getType(), param.getValue());

                ExperimentServiceImpl.get().loadParameter(job.getUser(), protAppParam, ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), FieldKey.fromParts("ProtocolApplicationId"), app.getRowId());
            }

            // If there are any property settings, transfer them here
            for (Map.Entry<PropertyDescriptor, Object> prop : action.getProps().entrySet())
            {
                PropertyDescriptor pd = prop.getKey();
                app.setProperty(job.getUser(), pd, prop.getValue());
            }

            // Set up the inputs
            for (RecordedAction.DataFile dd : action.getInputs())
            {
                ExpData data = addData(job, datas, dd.getURI(), source);
                app.addDataInput(job.getUser(), data, dd.getRole());
            }

            // Set up the outputs
            for (RecordedAction.DataFile dd : action.getOutputs())
            {
                ExpData outputData = addData(job, datas, dd.getURI(), source);
                if (outputData.getSourceApplication() != null)
                {
                    datas.remove(dd.getURI());
                    datas.remove(outputData.getDataFileURI());
                    outputData.setDataFileURI(null);
                    outputData.save(job.getUser());

                    outputData = addData(job, datas, dd.getURI(), source);
                    outputData.setSourceApplication(app);
                    if (dd.isGenerated())
                        ((ExpDataImpl)outputData).setGenerated(true); // CONSIDER: Add .setGenerated() to ExpData interface
                    outputData.save(job.getUser());
                }
                else
                {
                    outputData.setSourceApplication(app);
                    if (dd.isGenerated())
                        ((ExpDataImpl)outputData).setGenerated(true); // CONSIDER: Add .setGenerated() to ExpData interface
                    outputData.save(job.getUser());
                }
            }
        }

        if (!runOutputsWithRoles.isEmpty())
        {
            // Set up the outputs for the run
            ExpProtocolApplication outputApp = run.addProtocolApplication(job.getUser(), expActions.get(expActions.size() - 1), ExpProtocol.ApplicationType.ExperimentRunOutput, "Run outputs");
            for (Map.Entry<URI, String> entry : runOutputsWithRoles.entrySet())
            {
                URI uri = entry.getKey();
                String role = entry.getValue();
                outputApp.addDataInput(job.getUser(), datas.get(uri), role);
            }
        }

        ExperimentServiceImpl.get().syncRunEdges(run);

        return run;
    }

    static private Lsid createOutputProtocolLSID(Lsid parentProtocolLSID)
    {
        Lsid result = new Lsid.LsidBuilder(parentProtocolLSID).setObjectId(parentProtocolLSID.getObjectId() + ".Output").build();
        return result;
    }

    static private ExpProtocol ensureProtocol(PipelineJob job, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsidIn, String description)
    {
        Lsid.LsidBuilder lsid = new Lsid.LsidBuilder(lsidIn);
        int version = 1;
        while (true)
        {
            // Check if there's one in the database already
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(lsid.toString());
            if (existingProtocol != null)
            {
                // If so, check if it matches the one we need
                if (validateProtocol(job, protocolCache, protocolSequence, existingProtocol, description))
                {
                    // It matches, so use it
                    return existingProtocol;
                }
                else
                {
                    // Increment the version to try again
                    lsid.setVersion(Integer.toString(++version));
                }
            }
            else
            {
                // Don't have a matching one, so create a brand new one with the next version number
                return createProtocol(job, protocolCache, protocolSequence, lsid.build(), description);
            }
        }
    }

    /**
     * @return true if the stored protocol matches the one we'd generate
     */
    static private boolean validateProtocol(PipelineJob job, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, ExpProtocol parentProtocol, String description)
    {
        job.debug("Checking " + parentProtocol.getLSID() + " to see if it is a match");
        List<? extends ExpProtocolAction> existingSteps = parentProtocol.getSteps();
        // Two extra steps in the database, one to mark the run inputs, one to mark the run outputs
        if (existingSteps.size() != protocolSequence.size() + 2)
        {
            job.debug("Wrong number of steps in existing protocol, expected " + (protocolSequence.size() + 2) + " but was " + existingSteps.size());
            return false;
        }
        if (!description.equals(parentProtocol.getName()))
        {
            job.debug("Parent protocol names do not match, expected " + description + " but was " + parentProtocol.getName());
            return false;
        }

        int sequence = 1;
        for (ExpProtocolAction step : existingSteps)
        {
            ExpProtocol childProtocol = step.getChildProtocol();
            if (step.getActionSequence() != sequence)
            {
                job.debug("Wrong sequence number, expected " + sequence + " but was " + step.getActionSequence());
                return false;
            }
            if (sequence == 1)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRun)
                {
                    job.debug("Expected first step to be of type ExperimentRun, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (childProtocol.getRowId() != parentProtocol.getRowId())
                {
                    job.debug("Expected first step to match up with parent protocol rowId " + parentProtocol.getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            else if (sequence == protocolSequence.size() + 2)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRunOutput)
                {
                    job.debug("Expected last step to be of type ExperimentRunOutput, but was " + childProtocol.getApplicationType());
                    return false;
                }
                Lsid outputLsid = createOutputProtocolLSID(new Lsid(parentProtocol.getLSID()));
                String outputName = outputLsid.getObjectId();
                if (!childProtocol.getName().equals(outputName))
                {
                    job.debug("Expected last step to have name " + outputName + " but was " + childProtocol.getName());
                    return false;
                }
                if (!childProtocol.getLSID().equals(outputLsid.toString()))
                {
                    job.debug("Expected last step to have LSID " + outputLsid + " but was " + childProtocol.getLSID());
                    return false;
                }
            }
            else
            {
                String protocolName = protocolSequence.get(sequence - 2);
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ProtocolApplication)
                {
                    job.debug("Expected step with sequence " + sequence + " to be of type ProtocolApplication, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (!childProtocol.getName().equals(protocolName))
                {
                    job.debug("Expected step with sequence " + sequence + " to have name " + protocolName + " but was " + childProtocol.getName());
                    return false;
                }
                if (childProtocol.getRowId() != protocolCache.get(protocolName).getRowId())
                {
                    job.debug("Expected step with sequence " + sequence + " to match up with protocol rowId " + protocolCache.get(protocolName).getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            sequence++;
        }
        job.debug("Protocols match");
        return true;
    }

    static private ExpProtocol createProtocol(PipelineJob job, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        ExpProtocol parentProtocol;
        parentProtocol = ExperimentService.get().createExpProtocol(job.getContainer(), ExpProtocol.ApplicationType.ExperimentRun, lsid.getObjectId(), lsid.toString());
        parentProtocol.setName(description);
        parentProtocol.save(job.getUser());

        int sequence = 1;
        parentProtocol.addStep(job.getUser(), parentProtocol, sequence++);
        for (String name : protocolSequence)
        {
            parentProtocol.addStep(job.getUser(), protocolCache.get(name), sequence++);
        }

        Lsid outputLsid = createOutputProtocolLSID(lsid);
        ExpProtocol outputProtocol = ExperimentService.get().createExpProtocol(job.getContainer(), ExpProtocol.ApplicationType.ExperimentRunOutput, outputLsid.getObjectId(), outputLsid.toString());
        outputProtocol.save(job.getUser());

        parentProtocol.addStep(job.getUser(), outputProtocol, sequence++);
        return parentProtocol;
    }
}
