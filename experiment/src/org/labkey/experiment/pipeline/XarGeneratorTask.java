/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.ExperimentRunGraph;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Creates an experiment run to represent the work that the task's job has done so far.
 * User: jeckels
 * Date: Jul 25, 2008
*/
public class XarGeneratorTask extends PipelineJob.Task<XarGeneratorTask.Factory>
{
    public static class Factory extends AbstractTaskFactory<XarGeneratorFactorySettings, Factory>
    {
        private FileType _outputType = XarGeneratorId.FT_PIPE_XAR_XML;

        public Factory()
        {
            super(XarGeneratorId.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new XarGeneratorTask(this, job);
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        public String getStatusName()
        {
            return "IMPORT RESULTS";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        protected File getXarFile(PipelineJob job)
        {
            FileAnalysisJobSupport jobSupport = job.getJobSupport(FileAnalysisJobSupport.class);
            return getOutputType().newFile(jobSupport.getAnalysisDirectory(), jobSupport.getBaseName());
        }

        public boolean isJobComplete(PipelineJob job)
        {
            // We can use an existing XAR file from disk if it's been generated, but we need to load it because
            // there's no simple way to tell if it's already been imported or not, or if it's been subsequently deleted
            return false;
        }

        public void configure(XarGeneratorFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getOutputExt() != null)
                _outputType = new FileType(settings.getOutputExt());
        }
    }
    
    public XarGeneratorTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    private void addProtocol(Map<String, ExpProtocol> protocols, String name)
    {
        // Check if we've already dealt with this one
        if (!protocols.containsKey(name))
        {
            // Check if it's in the database already
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(getJob().getContainer(), name);
            if (protocol == null)
            {
                protocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), ExpProtocol.ApplicationType.ProtocolApplication, name);
                protocol.save(getJob().getUser());
            }
            protocols.put(name, protocol);
        }
        
    }

    private ExpData addData(Map<URI, ExpData> datas, URI originalURI, XarSource source) throws PipelineJobException
    {
        ExpData data = datas.get(originalURI);
        if (data != null)
        {
            return data;
        }

        URI uri;
        try
        {
            uri = new URI(source.getCanonicalDataFileURL(originalURI.toString()));
        }
        catch (XarFormatException e)
        {
            throw new PipelineJobException(e);
        }
        catch (URISyntaxException e)
        {
            throw new PipelineJobException(e);
        }

        data = datas.get(uri);

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

    /**
     * The basic steps are:
     * 1. Start a transaction.
     * 2. Create a protocol and a run and insert them into the database, not loading the data files.
     * 3. Export the run and protocol to a temporary XAR on the file system.
     * 4. Commit the transaction.
     * 5. Import the temporary XAR (not reloading the runs it references), which causes its referenced data files to load.
     * 6. Rename the temporary XAR to its permanent name.
     *
     * This allows us to quickly tell if the task is already complete by checking for the XAR file. If it exists, we
     * can simply reimport it. If the temporary file exists, we can skip directly to step 5 above. 
     */
    public RecordedActionSet run() throws PipelineJobException
    {
        try
        {
            // Keep track of all of the runs that have been created by this task
            Set<ExpRun> importedRuns = new HashSet<ExpRun>();

            File permanentXAR = _factory.getXarFile(getJob());
            if (NetworkDrive.exists(permanentXAR))
            {
                // Be sure that it's been imported (and not already deleted from the database)
                importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(permanentXAR, getJob()), getJob(), false));
            }
            else
            {
                if (!NetworkDrive.exists(getLoadingXarFile()))
                {
                    importedRuns.add(insertRun());
                }

                // Load the data files for this run
                importedRuns.addAll(ExperimentService.get().importXar(new FileXarSource(getLoadingXarFile(), getJob()), getJob(), false));

                getLoadingXarFile().renameTo(permanentXAR);
            }

            // Check if we've been cancelled. If so, delete any newly created runs from the database
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJob().getLogFile());
            if (statusFile != null && (PipelineJob.CANCELLED_STATUS.equals(statusFile.getStatus()) || PipelineJob.CANCELLING_STATUS.equals(statusFile.getStatus())))
            {
                for (ExpRun importedRun : importedRuns)
                {
                    getJob().info("Deleting run " + importedRun.getName() + " due to cancellation request");
                    importedRun.delete(getJob().getUser());
                }
            }
        }
        catch (SQLException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to import data files", e);
        }
        return new RecordedActionSet();
    }

    private ExpRunImpl insertRun() throws SQLException, PipelineJobException
    {
        ExpRunImpl run;
        try
        {
            XarSource source = new XarGeneratorSource(getJob(), _factory.getXarFile(getJob()));
            RecordedActionSet actionSet = getJob().getActionSet();
            Set<RecordedAction> actions = new LinkedHashSet<RecordedAction>(actionSet.getActions());

            Map<String, ExpProtocol> protocolCache = new HashMap<String, ExpProtocol>();
            List<String> protocolSequence = new ArrayList<String>();
            Map<URI, String> runOutputsWithRoles = new LinkedHashMap<URI, String>();
            Map<URI, String> runInputsWithRoles = new HashMap<URI, String>();

            runInputsWithRoles.putAll(actionSet.getOtherInputs());

            getJob().info("Checking files referenced by experiment run");
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
                        source.getCanonicalDataFileURL(dataFile.getURI().toString());
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
                    source.getCanonicalDataFileURL(dataFile.getURI().toString());
                }
            }
            getJob().debug("File check complete");

            // Files count as inputs to the run if they're used by one of the actions and weren't produced by one of
            // the actions.
            for (RecordedAction action : actions)
            {
                for (RecordedAction.DataFile dataFile : action.getOutputs())
                {
                    runInputsWithRoles.remove(dataFile.getURI());
                }
            }

            synchronized (ExperimentService.get().getImportLock())
            {
                ExperimentService.get().getSchema().getScope().ensureTransaction();
                // Build up the sequence of steps for this pipeline definition, which gets turned into a protocol
                for (TaskId taskId : getJob().getTaskPipeline().getTaskProgression())
                {
                    TaskFactory<?> factory = PipelineJobService.get().getTaskFactory(taskId);

                    for (String name : factory.getProtocolActionNames())
                    {
                        addProtocol(protocolCache, name);
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

                String protocolObjectId = getJob().getTaskPipeline().getProtocolIdentifier();
                Lsid parentProtocolLSID = new Lsid(ExperimentService.get().generateLSID(getJob().getContainer(), ExpProtocol.class, protocolObjectId));
                ExpProtocol parentProtocol = ensureProtocol(protocolCache, protocolSequence, parentProtocolLSID, getJob().getTaskPipeline().getProtocolShortDescription());

                // Break the protocol insertion and run insertion into two separate transactions
                ExperimentService.get().getSchema().getScope().commitTransaction();
                ExperimentService.get().getSchema().getScope().ensureTransaction();

                run = insertRun(actionSet.getActions(), source, runOutputsWithRoles, runInputsWithRoles, parentProtocol);

                writeXarToDisk(run);

                ExperimentService.get().getSchema().getScope().commitTransaction();
            }
            ExperimentRunGraph.clearCache(run.getContainer());
        }
        catch (XarFormatException e)
        {
            throw new PipelineJobException(e);
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        // Consider these actions complete. There may be additional runs created later in this job, and they
        // shouldn't duplicate the actions.
        getJob().clearActionSet(run);
        return run;
    }

    private ExpRunImpl insertRun(Set<RecordedAction> actions, XarSource source, Map<URI, String> runOutputsWithRoles, Map<URI, String> runInputsWithRoles, ExpProtocol parentProtocol)
        throws SQLException, PipelineJobException
    {
        ExpRunImpl run = ExperimentServiceImpl.get().createExperimentRun(getJob().getContainer(), getJob().getDescription());
        run.setProtocol(parentProtocol);
        run.setFilePathRoot(source.getRoot());
        run.setJobId(PipelineService.get().getJobId(getJob().getUser(), getJob().getContainer(), getJob().getJobGUID()));
        run.save(getJob().getUser());

        Map<String, ExpProtocolAction> expActionMap = new HashMap<String, ExpProtocolAction>();
        List<ExpProtocolAction> expActions = parentProtocol.getSteps();
        for (ExpProtocolAction action : expActions)
        {
            expActionMap.put(action.getChildProtocol().getName(), action);
        }

        Map<URI, ExpData> datas = new LinkedHashMap<URI, ExpData>();

        // Set up the inputs to the whole run
        ExpProtocolApplication inputApp = run.addProtocolApplication(getJob().getUser(), expActions.get(0), parentProtocol.getApplicationType(), "Run inputs");
        for (Map.Entry<URI, String> runInput : runInputsWithRoles.entrySet())
        {
            URI uri = runInput.getKey();
            String role = runInput.getValue();
            ExpData data = addData(datas, uri, source);
            inputApp.addDataInput(getJob().getUser(), data, role);
        }

        // Set up the inputs and outputs for the individual actions
        for (RecordedAction action : actions)
        {
            // Look up the step by its name
            ExpProtocolAction step = expActionMap.get(action.getName());

            ExpProtocolApplication app = run.addProtocolApplication(getJob().getUser(), step, ExpProtocol.ApplicationType.ProtocolApplication, action.getName());
            if (!action.getName().equals(action.getDescription()))
            {
                app.setName(action.getDescription());
                app.save(getJob().getUser());
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

                ExperimentServiceImpl.get().loadParameter(getJob().getUser(), protAppParam, ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), "ProtocolApplicationId", app.getRowId());
            }

            // Set up the inputs
            for (RecordedAction.DataFile dd : action.getInputs())
            {
                ExpData data = addData(datas, dd.getURI(), source);
                app.addDataInput(getJob().getUser(), data, dd.getRole());
            }

            // Set up the outputs
            for (RecordedAction.DataFile dd : action.getOutputs())
            {
                ExpData outputData = addData(datas, dd.getURI(), source);
                if (outputData.getSourceApplication() != null)
                {
                    datas.remove(dd.getURI());
                    datas.remove(outputData.getDataFileURI());
                    outputData.setDataFileURI(null);
                    outputData.save(getJob().getUser());

                    outputData = addData(datas, dd.getURI(), source);
                    outputData.setSourceApplication(app);
                    outputData.save(getJob().getUser());
                }
                else
                {
                    outputData.setSourceApplication(app);
                    outputData.save(getJob().getUser());
                }
            }
        }

        if (!runOutputsWithRoles.isEmpty())
        {
            // Set up the outputs for the run
            ExpProtocolApplication outputApp = run.addProtocolApplication(getJob().getUser(), expActions.get(expActions.size() - 1), ExpProtocol.ApplicationType.ExperimentRunOutput, "Run outputs");
            for (Map.Entry<URI, String> entry : runOutputsWithRoles.entrySet())
            {
                URI uri = entry.getKey();
                String role = entry.getValue();
                outputApp.addDataInput(getJob().getUser(), datas.get(uri), role);
            }
        }
        return run;
    }

    private Lsid createOutputProtocolLSID(Lsid parentProtocolLSID)
    {
        Lsid result = new Lsid(parentProtocolLSID.toString());
        result.setObjectId(parentProtocolLSID.getObjectId() + ".Output");
        return result;
    }

    private ExpProtocol ensureProtocol(Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        int version = 1;
        while (true)
        {
            // Check if there's one in the database already
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(lsid.toString());
            if (existingProtocol != null)
            {
                // If so, check if it matches the one we need
                if (validateProtocol(protocolCache, protocolSequence, existingProtocol, description))
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
                return createProtocol(protocolCache, protocolSequence, lsid, description);
            }
        }
    }

    /**
     * @return true if the stored protocol matches the one we'd generate
     */
    private boolean validateProtocol(Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, ExpProtocol parentProtocol, String description)
    {
        getJob().debug("Checking " + parentProtocol.getLSID() + " to see if it is a match");
        List<ExpProtocolAction> existingSteps = parentProtocol.getSteps();
        // Two extra steps in the database, one to mark the run inputs, one to mark the run outputs
        if (existingSteps.size() != protocolSequence.size() + 2)
        {
            getJob().debug("Wrong number of steps in existing protocol, expected " + (protocolSequence.size() + 2) + " but was " + existingSteps.size());
            return false;
        }
        if (!description.equals(parentProtocol.getName()))
        {
            getJob().debug("Parent protocol names do not match, expected " + description + " but was " + parentProtocol.getName());
            return false;
        }

        int sequence = 1;
        for (ExpProtocolAction step : existingSteps)
        {
            ExpProtocol childProtocol = step.getChildProtocol();
            if (step.getActionSequence() != sequence)
            {
                getJob().debug("Wrong sequence number, expected " + sequence + " but was " + step.getActionSequence());
                return false;
            }
            if (sequence == 1)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRun)
                {
                    getJob().debug("Expected first step to be of type ExperimentRun, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (childProtocol.getRowId() != parentProtocol.getRowId())
                {
                    getJob().debug("Expected first step to match up with parent protocol rowId " + parentProtocol.getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            else if (sequence == protocolSequence.size() + 2)
            {
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRunOutput)
                {
                    getJob().debug("Expected last step to be of type ExperimentRunOutput, but was " + childProtocol.getApplicationType());
                    return false;
                }
                Lsid outputLsid = createOutputProtocolLSID(new Lsid(parentProtocol.getLSID()));
                String outputName = outputLsid.getObjectId();
                if (!childProtocol.getName().equals(outputName))
                {
                    getJob().debug("Expected last step to have name " + outputName + " but was " + childProtocol.getName());
                    return false;
                }
                if (!childProtocol.getLSID().equals(outputLsid.toString()))
                {
                    getJob().debug("Expected last step to have LSID " + outputLsid + " but was " + childProtocol.getLSID());
                    return false;
                }
            }
            else
            {
                String protocolName = protocolSequence.get(sequence - 2);
                if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ProtocolApplication)
                {
                    getJob().debug("Expected step with sequence " + sequence + " to be of type ProtocolApplication, but was " + childProtocol.getApplicationType());
                    return false;
                }
                if (!childProtocol.getName().equals(protocolName))
                {
                    getJob().debug("Expected step with sequence " + sequence + " to have name " + protocolName + " but was " + childProtocol.getName());
                    return false;
                }
                if (childProtocol.getRowId() != protocolCache.get(protocolName).getRowId())
                {
                    getJob().debug("Expected step with sequence " + sequence + " to match up with protocol rowId " + protocolCache.get(protocolName).getRowId() + " but was " + childProtocol.getRowId());
                    return false;
                }
            }
            sequence++;
        }
        getJob().debug("Protocols match");
        return true;
    }

    private ExpProtocol createProtocol(Map<String, ExpProtocol> protocolCache, List<String> protocolSequence, Lsid lsid, String description)
    {
        ExpProtocol parentProtocol;
        parentProtocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), ExpProtocol.ApplicationType.ExperimentRun, lsid.getObjectId(), lsid.toString());
        parentProtocol.setName(description);
        parentProtocol.save(getJob().getUser());

        int sequence = 1;
        parentProtocol.addStep(getJob().getUser(), parentProtocol, sequence++);
        for (String name : protocolSequence)
        {
            parentProtocol.addStep(getJob().getUser(), protocolCache.get(name), sequence++);
        }

        Lsid outputLsid = createOutputProtocolLSID(lsid);
        ExpProtocol outputProtocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), ExpProtocol.ApplicationType.ExperimentRunOutput, outputLsid.getObjectId(), outputLsid.toString());
        outputProtocol.save(getJob().getUser());

        parentProtocol.addStep(getJob().getUser(), outputProtocol, sequence++);
        return parentProtocol;
    }

    private void writeXarToDisk(ExpRunImpl run) throws PipelineJobException
    {
        File f = getLoadingXarFile();
        File tempFile = new File(f.getPath() + ".temp");

        FileOutputStream fOut = null;
        try
        {
            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, DataURLRelativizer.RUN_RELATIVE_LOCATION.createURLRewriter());
            exporter.addExperimentRun(run);

            fOut = new FileOutputStream(tempFile);
            exporter.dumpXML(fOut);
            fOut.close();
            FileUtils.moveFile(tempFile, f);
            fOut = null;
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to write XAR to disk", e);
        }
        catch (IOException e)
        {
            throw new PipelineJobException("Failed to write XAR to disk", e);
        }
        finally
        {
            if (fOut != null)
            {
                try { fOut.close(); } catch (IOException e) {}
            }
        }
    }

    private File getLoadingXarFile()
    {
        return new File(_factory.getXarFile(getJob()) + ".loading");
    }
}