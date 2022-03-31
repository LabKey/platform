/*
 * Copyright (c) 2013-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.experiment.ExperimentRunGraph;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
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
import java.util.stream.Collectors;

/**
 * Helper class to build experiments from pipeline jobs.  Used by XarGeneratorTask
 * User: dax
 * Date: Apr 24, 2013
*/
public class ExpGeneratorHelper
{
    static private ExpData addData(Container container, User user, Map<URI, ExpData> datas, URI originalURI, XarSource source) throws ExperimentException
    {
        ExpData data = datas.get(originalURI);
        if (data != null)
        {
            return data;
        }

        if (source != null)
            return addData(datas, originalURI, source);

        return addData(container, user, datas, originalURI);
    }

    // undone:  does this make sense for non-file based data?
    static private ExpData addData(Map<URI, ExpData> datas, URI originalURI, XarSource source) throws ExperimentException
    {
        URI uri;
        try
        {
            uri = new URI(source.getCanonicalDataFileURL(FileUtil.uriToString(originalURI)));
        }
        catch (XarFormatException | URISyntaxException e)
        {
            throw new ExperimentException(e);
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
                throw new ExperimentException(e);
            }
            datas.put(uri, data);
            datas.put(originalURI, data);
        }
        return data;
    }

    static private ExpData addData(Container container, User user, Map<URI, ExpData> datas, URI originalURI)
    {
        // todo: this code shouldn't know about the TransformDataType
        Lsid lsid = new Lsid(ExperimentService.get().generateGuidLSID(container, ExperimentService.get().getDataType("Transform")));
        ExpData data = ExperimentService.get().createData(container, FileUtil.uriToString(originalURI), lsid.toString());

        if (data != null)
        {
            datas.put(originalURI, data);
            data.save(user);
        }
        return data;
    }

    static public ExpRunImpl insertRun(PipelineJob job) throws PipelineJobException, ValidationException
    {
        return insertRun(job, null, null);
    }

    static public ExpRunImpl insertRun(PipelineJob job, @Nullable XarSource source, @Nullable XarWriter xarWriter) throws PipelineJobException, ValidationException
    {
        Container container = job.getContainer();
        User user = job.getUser();

        // Build up the sequence of steps for this pipeline definition, which gets turned into a protocol
        List<String> protocolSequences = new ArrayList<>();
        for (TaskId taskId : job.getTaskPipeline().getTaskProgression())
        {
            TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(taskId);
            protocolSequences.addAll(factory.getProtocolActionNames());
        }

        Integer jobId = PipelineService.get().getJobId(user, container, job.getJobGUID());

        // create the main (parent) protocol for the run
        ExpProtocol parentProtocol;
        Map<String, ExpProtocol> protocolCache = new HashMap<>();
        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
        {
            for (String name : protocolSequences)
            {
                addSequenceProtocol(container, user, protocolCache, name);
            }

            // Check to make sure that we have a protocol that corresponds with each action
            for (RecordedAction action : job.getActionSet().getActions())
            {
                if (protocolCache.get(action.getName()) == null)
                {
                    throw new IllegalArgumentException("Could not find a matching action declaration for " + action.getName());
                }
            }

            String protocolObjectId = job.getTaskPipeline().getProtocolIdentifier();
            Lsid parentProtocolLSID = new Lsid(ExperimentService.get().generateLSID(container, ExpProtocol.class, protocolObjectId));
            parentProtocol = ensureProtocol(container, user, protocolCache, protocolSequences, parentProtocolLSID, job.getTaskPipeline().getProtocolShortDescription(), job.getLogger());

            transaction.commit();
        }

        try
        {
            ExpRunImpl run = insertRun(container, user,
                    job.getActionSet(),
                    job.getDescription(),
                    jobId,
                    parentProtocol,
                    job.getLogger(),
                    source,
                    xarWriter);

            // Consider these actions complete. There may be additional runs created later in this job, and they
            // shouldn't duplicate the actions.
            job.clearActionSet(run);
            return run;
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException(e);
        }
    }

    static public ExpRunImpl insertRun(Container container, User user,
                                       RecordedActionSet actionSet,
                                       String runName,
                                       @Nullable Integer runJobId,
                                       ExpProtocol protocol,
                                       @NotNull Logger log,
                                       @Nullable XarSource source,
                                       @Nullable XarWriter xarWriter) throws ExperimentException, ValidationException
    {
        ExpRunImpl run;
        try
        {
            Set<RecordedAction> actions = new LinkedHashSet<>(actionSet.getActions());
            Map<URI, String> runOutputsWithRoles = new LinkedHashMap<>();
            Map<URI, String> runInputsWithRoles = new HashMap<>(actionSet.getOtherInputs());

            log.info("Checking files referenced by experiment run");

            boolean fromProvenanceRecording = protocol.getLSID().contains(ProvenanceService.PROVENANCE_PROTOCOL_LSID);

            for (RecordedAction action : actions)
            {
                for (RecordedAction.DataFile dataFile : action.getInputs())
                {
                    // For inputs, don't stomp over the role specified the first time a file was used as an input
                    if (fromProvenanceRecording && !action.isStart())
                        continue;
                    runInputsWithRoles.computeIfAbsent(dataFile.getURI(), k -> dataFile.getRole());

                    // This can be slow over network file systems so do it outside of the database
                    // transaction. The XarSource caches the results so it'll be fast once we start inserting.
                }
                for (RecordedAction.DataFile dataFile : action.getOutputs())
                {
                    if (!dataFile.isTransient())
                    {
                        // For outputs, want to use the last role that was specified, so always overwrite
                        if (fromProvenanceRecording && !action.isEnd())
                            continue;
                        runOutputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                    }

                    // This can be slow over network file systems so do it outside of the database
                    // transaction. The XarSource caches the results so it'll be fast once we start inserting.
                }
            }

            // Files count as inputs to the run if they're used by one of the actions and weren't produced by one of
            // the actions.
            for (RecordedAction action : actions)
            {
                for (RecordedAction.DataFile dataFile : action.getOutputs())
                {
                    runInputsWithRoles.remove(dataFile.getURI());
                }
            }

            try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
            {
                run = _insertRun(container, user, runName, runJobId, protocol, actionSet.getActions(), source, runOutputsWithRoles, runInputsWithRoles, fromProvenanceRecording);

                if (null != xarWriter)
                    xarWriter.writeToDisk(run);

                transaction.commit();
            }
            catch (PipelineJobException e)
            {
                throw new ExperimentException(e);
            }

            ExperimentRunGraph.clearCache(run.getContainer());
        }
        catch (XarFormatException | BatchValidationException e)
        {
            throw new ExperimentException(e);
        }
        return run;
    }

    static private ExpRunImpl _insertRun(Container container,
                                         User user,
                                         String runName,
                                         @Nullable Integer runJobId,
                                         ExpProtocol protocol,
                                         Set<RecordedAction> actions,
                                         @Nullable XarSource source,
                                         Map<URI, String> runOutputsWithRoles,
                                         Map<URI, String> runInputsWithRoles,
                                         boolean fromProvenanceRecording) throws ExperimentException, ValidationException, BatchValidationException
    {
        ExpRunImpl run = ExperimentServiceImpl.get().createExperimentRun(container, runName);
        ProvenanceService pvs = ProvenanceService.get();
        run.setProtocol(protocol);
        if (null != source)
            run.setFilePathRootPath(source.getRootPath());
        if (null != runJobId)
            run.setJobId(runJobId);

        run.save(user);

        Map<String, ExpProtocolAction> expActionMap = new HashMap<>();
        List<? extends ExpProtocolAction> expActions = protocol.getSteps();
        for (ExpProtocolAction action : expActions)
        {
            expActionMap.put(action.getChildProtocol().getName(), action);
        }

        Map<URI, ExpData> datas = new LinkedHashMap<>();

        // Set up the inputs to the whole run
        ExpProtocolApplication inputApp = run.addProtocolApplication(user, expActions.get(0), protocol.getApplicationType(), "Run inputs");
        ExpProtocolApplication outputApp = run.addProtocolApplication(user, expActions.get(expActions.size() - 1), ExpProtocol.ApplicationType.ExperimentRunOutput, "Run outputs");
        for (Map.Entry<URI, String> runInput : runInputsWithRoles.entrySet())
        {
            URI uri = runInput.getKey();
            String role = runInput.getValue();
            ExpData data = addData(container, user, datas, uri, source);
            inputApp.addDataInput(user, data, role);
        }

        // Set up the inputs and outputs for the individual actions
        for (RecordedAction action : actions)
        {
            // Look up the step by its name
            ExpProtocolAction step = expActionMap.get(action.getName());

            ExpProtocolApplication stepApp;

            if (action.isStart())
            {
                stepApp = inputApp;
            }
            else if (action.isEnd())
            {
                stepApp = outputApp;
            }
            else
            {
                stepApp = run.addProtocolApplication(user, step, ExpProtocol.ApplicationType.ProtocolApplication,
                        action.getName(), action.getStartTime(), action.getEndTime(), action.getRecordCount());
                if (action.getActivityDate() != null)
                    stepApp.setActivityDate(action.getActivityDate());
                if (action.getComments() != null)
                    stepApp.setComments(action.getComments());
            }

            if (!action.isStart() && !action.isEnd() && !action.getName().equals(action.getDescription()))
            {
                stepApp.setName(action.getDescription());
                stepApp.save(user);
            }

            // Transfer all the protocol parameters
            for (Map.Entry<RecordedAction.ParameterType, Object> param : action.getParams().entrySet())
            {
                ProtocolApplicationParameter protAppParam = new ProtocolApplicationParameter();
                protAppParam.setProtocolApplicationId(stepApp.getRowId());
                protAppParam.setRunId(run.getRowId());
                RecordedAction.ParameterType paramType = param.getKey();
                protAppParam.setName(paramType.getName());
                protAppParam.setOntologyEntryURI(paramType.getURI());

                protAppParam.setValue(paramType.getType(), param.getValue());

                ExperimentServiceImpl.get().loadParameter(user, protAppParam, ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), FieldKey.fromParts("ProtocolApplicationId"), stepApp.getRowId());
            }

            // If there are any property settings, transfer them here
            for (Map.Entry<PropertyDescriptor, Object> prop : action.getProps().entrySet())
            {
                PropertyDescriptor pd = prop.getKey();
                stepApp.setProperty(user, pd, prop.getValue());
            }

            // material inputs
            for (String lsid : action.getMaterialInputs())
            {
                ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
                material.setRun(run);
                stepApp.addMaterialInput(user, material, null, null);
            }

            // material outputs
            for (String lsid : action.getMaterialOutputs())
            {
                ExpMaterialImpl material = (ExpMaterialImpl) ExperimentService.get().getExpMaterial(lsid);
                material.setSourceApplication(stepApp);
                // set up the output to the run
                if (action.isEnd())
                {
                    material.setRun(run);
                    material.addSuccessorRunId(run.getRowId());
                    stepApp.addMaterialInput(user, material, null, null);
                }

                material.save(user);
            }

            // Set up the inputs
            for (RecordedAction.DataFile dd : action.getInputs())
            {
                if (!fromProvenanceRecording || !action.isStart())
                {
                    ExpData data = addData(container, user, datas, dd.getURI(), source);
                    stepApp.addDataInput(user, data, dd.getRole());
                }
            }

            // Set up the outputs
            for (RecordedAction.DataFile dd : action.getOutputs())
            {
                if (!fromProvenanceRecording || !action.isEnd())
                {
                    ExpData outputData = addData(container, user, datas, dd.getURI(), source);
                    if (outputData.getSourceApplication() != null)
                    {
                        datas.remove(dd.getURI());
                        datas.remove(outputData.getDataFileURI());     // TODO should look for old pattern, too
                        outputData.setDataFileURI(null);
                        outputData.save(user);

                        outputData = addData(container, user, datas, dd.getURI(), source);
                        outputData.setSourceApplication(stepApp);
                        if (dd.isGenerated())
                            ((ExpDataImpl) outputData).setGenerated(true); // CONSIDER: Add .setGenerated() to ExpData interface
                        outputData.save(user);
                    }
                    else
                    {
                        outputData.setSourceApplication(stepApp);
                        if (dd.isGenerated())
                            ((ExpDataImpl) outputData).setGenerated(true); // CONSIDER: Add .setGenerated() to ExpData interface
                        outputData.save(user);
                    }
                }
            }

            // add in any provenance mappings and prov object inputs and outputs
            if (protocol.getLSID().contains(ProvenanceService.PROVENANCE_PROTOCOL_LSID))
            {
                if (!action.getProvenanceMap().isEmpty())
                    pvs.addProvenance(container, stepApp, action.getProvenanceMap());

                // determine the right protocol app for object inputs and object outputs
                pvs.addProvenanceInputs(container, stepApp, action.getObjectInputs());
                pvs.addProvenanceOutputs(container, stepApp, action.getObjectOutputs());
            }
        }

        if (!runOutputsWithRoles.isEmpty())
        {
            // Set up the outputs for the run

            for (Map.Entry<URI, String> entry : runOutputsWithRoles.entrySet())
            {
                URI uri = entry.getKey();
                String role = entry.getValue();
                ExpData data = addData(container, user, datas, uri, source);
                outputApp.addDataInput(user, data, role);
            }
        }
        if (fromProvenanceRecording)
        {
            promoteInputs(actions, run, datas, user, container);
        }
        ExperimentServiceImpl.get().queueSyncRunEdges(run);

        return run;
    }

    /**
     * Promote the inputs that are attached to the inner steps.
     * Inputs get promoted -
     * <li>if they are not the inputs to the run</li>
     * <li>and if they are not the output of a previous step</li>
     * */
    static private void promoteInputs(Set<RecordedAction> actions, ExpRun run, Map<URI, ExpData> datas, User user, Container container)
    {
        List<RecordedAction> actionsList = new ArrayList<>(actions);
        Set<String> runMaterialInputs = run
                .getMaterialInputs()
                .keySet()
                .stream()
                .map(Identifiable::getLSID)
                .collect(Collectors.toSet());

        Set<String> runDataInputs = run
                .getDataInputs()
                .keySet()
                .stream()
                .map(Identifiable::getLSID)
                .collect(Collectors.toSet());

        String lsid = "lsid";
        String datafileurl = "datafileurl";

        // skipping the first action as the inputs to first action are attached as run inputs for provenance recording
        for (int i = 1; i < actionsList.size(); i++)
        {
            RecordedAction prevAction = actionsList.get(i-1);

            actionsList.get(i).getMaterialInputs().forEach(materialLsid -> {
                if (!runMaterialInputs.contains(materialLsid) && !prevAction.getMaterialOutputs().contains(materialLsid))
                {
                    // promote material input to run
                    addMaterialInput(run, run.getInputProtocolApplication(), materialLsid, user);
                }
            });

            actionsList.get(i).getInputs().forEach(dataFile -> {
                SimpleFilter filter = SimpleFilter.createContainerFilter(container);
                filter.addCondition(FieldKey.fromString(datafileurl), dataFile.getURI().toString(), CompareType.EQUAL);
                Map<String,Object> dataLsidMap = new TableSelector(ExperimentService.get().getTinfoData(), Set.of(lsid), filter, null).getMap();

                if (null != dataLsidMap)
                {
                    String dataLsid = dataLsidMap.get(lsid).toString();
                    if (!runDataInputs.contains(dataLsid) && !prevAction.getOutputs().contains(dataFile))
                    {
                        // promote data input to run
                        ExpData data = null;

                        try
                        {
                            data = addData(container, user, datas, dataFile.getURI(), null);
                        }
                        catch (ExperimentException e)
                        {
                            throw new RuntimeException(e);
                        }

                        run.getInputProtocolApplication().addDataInput(user, data, dataFile.getRole());
                    }
                }
            });
        }
    }

    static private void addMaterialInput(ExpRun run, ExpProtocolApplication app, String lsid, User user)
    {
        ExpMaterial material = ExperimentService.get().getExpMaterial(lsid);
        material.setRun(run);
        app.addMaterialInput(user, material, null, null);
    }

    static private Lsid createOutputProtocolLSID(Lsid parentProtocolLSID)
    {
        Lsid result = parentProtocolLSID.edit().setObjectId(parentProtocolLSID.getObjectId() + ".Output").build();
        return result;
    }

    static public ExpProtocol ensureProtocol(Container container, User user, Map<String, ExpProtocol> protocolCache,
                                              List<String> protocolSequence,
                                              Lsid lsidIn,
                                              String description,
                                              Logger log)
    {
        Lsid.LsidBuilder lsid = lsidIn.edit();
        int version = 1;
        while (true)
        {
            // Check if there's one in the database already
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(lsid.toString());
            if (existingProtocol != null)
            {
                // If so, check if it matches the one we need
                if (validateProtocol(protocolCache, protocolSequence, existingProtocol, description, log))
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
                return createProtocol(container, user, protocolCache, protocolSequence, lsid.build(), description);
            }
        }
    }

    /**
     * @return true if the stored protocol matches the one we'd generate
     */
    static private boolean validateProtocol(Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, ExpProtocol parentProtocol, String description, Logger log)
    {
        log.debug("Checking " + parentProtocol.getLSID() + " to see if it is a match");
        List<? extends ExpProtocolAction> existingSteps = parentProtocol.getSteps();
        // Two extra steps in the database, one to mark the run inputs, one to mark the run outputs
        if (existingSteps.size() != protocolSequence.size() + 2)
        {
            log.debug("Wrong number of steps in existing protocol, expected " + (protocolSequence.size() + 2) + " but was " + existingSteps.size());
            return false;
        }
        if (!description.equals(parentProtocol.getName()))
        {
            log.debug("Parent protocol names do not match, expected " + description + " but was " + parentProtocol.getName());
            return false;
        }

        int sequence = 1;
        for (ExpProtocolAction step : existingSteps)
        {
            ExpProtocol childProtocol = step.getChildProtocol();
            if (step.getActionSequence() != sequence)
            {
                log.debug("Wrong sequence number, expected " + sequence + " but was " + step.getActionSequence());
                return false;
            }
            if (sequence == 1)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRun)
                {
                    log.debug("Expected first step to be of type ExperimentRun, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (childProtocol.getRowId() != parentProtocol.getRowId())
                {
                    log.debug("Expected first step to match up with parent protocol rowId " + parentProtocol.getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            else if (sequence == protocolSequence.size() + 2)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRunOutput)
                {
                    log.debug("Expected last step to be of type ExperimentRunOutput, but was " + childProtocol.getApplicationType());
                    return false;
                }
                Lsid outputLsid = createOutputProtocolLSID(new Lsid(parentProtocol.getLSID()));
                String outputName = outputLsid.getObjectId();
                if (!childProtocol.getName().equals(outputName))
                {
                    log.debug("Expected last step to have name " + outputName + " but was " + childProtocol.getName());
                    return false;
                }
                if (!childProtocol.getLSID().equals(outputLsid.toString()))
                {
                    log.debug("Expected last step to have LSID " + outputLsid + " but was " + childProtocol.getLSID());
                    return false;
                }
            }
            else
            {
                String protocolName = protocolSequence.get(sequence - 2);
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ProtocolApplication)
                {
                    log.debug("Expected step with sequence " + sequence + " to be of type ProtocolApplication, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (!childProtocol.getName().equals(protocolName))
                {
                    log.debug("Expected step with sequence " + sequence + " to have name " + protocolName + " but was " + childProtocol.getName());
                    return false;
                }
                if (childProtocol.getRowId() != protocolCache.get(protocolName).getRowId())
                {
                    log.debug("Expected step with sequence " + sequence + " to match up with protocol rowId " + protocolCache.get(protocolName).getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            sequence++;
        }
        log.debug("Protocols match");
        return true;
    }

    static public ExpProtocol createProtocol(Container container, User user, Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        ExpProtocol parentProtocol;
        parentProtocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRun, lsid.getObjectId(), lsid.toString());
        parentProtocol.setName(description);
        parentProtocol.save(user);

        int sequence = 1;
        parentProtocol.addStep(user, parentProtocol, sequence++);
        for (String name : protocolSequence)
        {
            parentProtocol.addStep(user, protocolCache.get(name), sequence++);
        }

        Lsid outputLsid = createOutputProtocolLSID(lsid);
        ExpProtocol outputProtocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ExperimentRunOutput, outputLsid.getObjectId(), outputLsid.toString());
        outputProtocol.save(user);

        parentProtocol.addStep(user, outputProtocol, sequence++);
        return parentProtocol;
    }

    static private void addSequenceProtocol(Container container, User user, Map<String, ExpProtocol> protocols, String name)
    {
        // Check if we've already dealt with this one
        if (!protocols.containsKey(name))
        {
            // Check if it's in the database already
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(container, name);
            if (protocol == null)
            {
                protocol = ExperimentService.get().createExpProtocol(container, ExpProtocol.ApplicationType.ProtocolApplication, name);
                protocol.save(user);
            }
            protocols.put(name, protocol);
        }
    }
}
