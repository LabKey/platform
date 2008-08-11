/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.exp.pipeline.XarGeneratorFactorySettings;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.FileUtil;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.ExpRunImpl;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.DataURLRelativizer;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;

import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.*;
import java.net.URI;

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

        public FileType[] getInputTypes()
        {
            return new FileType[0];
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        public String getStatusName()
        {
            return "SAVE EXPERIMENT";
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

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
        {
            return NetworkDrive.exists(getXarFile(job));
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

    private ExpData addData(Map<URI, ExpData> datas, URI uri, XarSource source) throws PipelineJobException
    {
        ExpData data = datas.get(uri);

        // Check if we've already dealt with this one
        if (data == null)
        {
            File f = null;
            // Check if it's in the database already
            if (uri.toString().startsWith("file:"))
            {
                f = new File(uri);
            }

            data = ExperimentService.get().getExpDataByURL(uri.toString(), getJob().getContainer());
            if (data == null)
            {
                // Have to make a new one
                String name;
                if (f != null)
                {
                    name = f.getName();
                }
                else
                {
                    String[] parts = uri.toString().split("/");
                    name = parts[parts.length - 1];
                }
                try
                {
                    String path;
                    if (f != null)
                    {
                        try
                        {
                            path = FileUtil.relativizeUnix(source.getRoot(), f);
                        }
                        catch (IOException e)
                        {
                            path = f.toString();
                        }
                    }
                    else
                    {
                        path = uri.toString();
                    }

                    Lsid lsid = new Lsid(LsidUtils.resolveLsidFromTemplate(AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION, source.getXarContext(), "Data", new AutoFileLSIDReplacer(path, getJob().getContainer(), source)));
                    int version = 1;
                    do
                    {
                        data = ExperimentService.get().getExpData(lsid.toString());
                        if (data != null)
                        {
                            lsid.setVersion(Integer.toString(++version));
                        }
                    }
                    while (data != null);

                    data = ExperimentService.get().createData(getJob().getContainer(), name, lsid.toString());
                    data.setDataFileURI(uri);
                    data.save(getJob().getUser());
                }
                catch (XarFormatException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            datas.put(uri, data);
        }
        return data;
    }

    /**
     * The basic steps are:
     * 1. Start a transaction.
     * 2. Create a protocol and a run and insert them into the database, not loading the data files.
     * 3. Export the run and protocol to a temporary XAR on the file system.
     * 4. Commit the transaction/
     * 5. Import the temporary XAR (not reloading the runs it references), which causes its referenced data files to load.
     * 6. Rename the temporary XAR to its permanent name.
     *
     * This allows us to quickly tell if the task is already complete by checking for the XAR file. If it exists, we're
     * done. If the temporary file exists, we can skip directly to step 5 above. 
     */
    public List<RecordedAction> run() throws PipelineJobException
    {
        try
        {
            if (!NetworkDrive.exists(getTempXarFile()))
            {
                insertRun();
            }

            // Load the data files for this run
            ExperimentService.get().importXar(new FileXarSource(getTempXarFile(), getJob()), getJob(), false);
            
            getTempXarFile().renameTo(_factory.getXarFile(getJob()));
        }
        catch (SQLException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        catch (ExperimentException e)
        {
            throw new PipelineJobException("Failed to import data files", e);
        }
        finally
        {
            if (ExperimentService.get().isTransactionActive())
            {
                ExperimentService.get().rollbackTransaction();
            }
        }
        return Collections.emptyList();
    }

    private void insertRun() throws SQLException, PipelineJobException
    {
        ExpRunImpl run;
        try
        {
            synchronized (ExperimentService.get().getImportLock())
            {
                ExperimentService.get().beginTransaction();
                RecordedActionSet actionSet = getJob().getActionSet();
                List<RecordedAction> actions = new ArrayList<RecordedAction>(actionSet.getActions());

                Map<String, ExpProtocol> protocolCache = new HashMap<String, ExpProtocol>();
                List<String> protocolSequence = new ArrayList<String>();
                Set<URI> runOutputs = new HashSet<URI>();
                Map<URI, String> runInputsWithRoles = new HashMap<URI, String>();

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

                String protocolObjectId = getJob().getTaskPipeline().getProtocolIdentifier();
                Lsid lsid = new Lsid(ExperimentService.get().generateLSID(getJob().getContainer(), ExpProtocol.class, protocolObjectId));

                ExpProtocol parentProtocol = ensureProtocol(protocolCache, protocolSequence, lsid, getJob().getTaskPipeline().getProtocolShortDescription());

                runInputsWithRoles.putAll(actionSet.getOtherInputs());

                for (RecordedAction action : actions)
                {
                    if (protocolCache.get(action.getName()) == null)
                    {
                        throw new IllegalArgumentException("Could not find a matching action declaration for " + action.getName());
                    }

                    for (RecordedAction.DataFile dataFile : action.getInputs())
                    {
                        if (runInputsWithRoles.get(dataFile.getURI()) == null)
                        {
                            // Don't stomp over the role specified the first time a file was used as an input
                            runInputsWithRoles.put(dataFile.getURI(), dataFile.getRole());
                        }
                    }
                    for (RecordedAction.DataFile dataFile : action.getOutputs())
                    {
                        if (!dataFile.isTransient())
                        {
                            runOutputs.add(dataFile.getURI());
                        }
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

                XarSource source = new XarGeneratorSource(getJob(), _factory.getXarFile(getJob()));
                run = insertRun(actionSet.getActions(), source, runOutputs, runInputsWithRoles, parentProtocol);

                writeXarToDisk(run);

                ExperimentService.get().commitTransaction();
            }
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        // Consider these actions complete. There may be additional runs created later in this job, and they
        // shouldn't duplicate the actions.
        getJob().clearActionSet(run);
    }

    private ExpRunImpl insertRun(List<RecordedAction> actions, XarSource source, Set<URI> runOutputs, Map<URI, String> runInputsWithRoles, ExpProtocol parentProtocol)
        throws SQLException, PipelineJobException
    {
        ExpRunImpl run = ExperimentServiceImpl.get().createExperimentRun(getJob().getContainer(), getJob().getDescription());
        run.setProtocol(parentProtocol);
        run.setFilePathRoot(source.getRoot());
        run.save(getJob().getUser());

        List<ExpProtocolAction> expActions = parentProtocol.getSteps();

        Map<URI, ExpData> datas = new LinkedHashMap<URI, ExpData>();

        // Set up the inputs to the whole run
        ExpProtocolApplication inputApp = run.addProtocolApplication(getJob().getUser(), expActions.get(0), parentProtocol.getApplicationType(), "Run inputs");
        for (Map.Entry<URI, String> runInput : runInputsWithRoles.entrySet())
        {
            URI uri = runInput.getKey();
            String role = runInput.getValue();
            ExpData data = addData(datas, uri, source);
            inputApp.addDataInput(getJob().getUser(), data, role, null);
        }

        // Set up the inputs and outputs for the individual actions
        for (int i = 0; i < actions.size(); i++)
        {
            RecordedAction action = actions.get(i);
            // The first ExpProtocolAction is the input step, not a real work step, so shift by one
            ExpProtocolAction step = expActions.get(i + 1);

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
                app.addDataInput(getJob().getUser(), data, dd.getRole(), null);
            }

            // Set up the outputs
            for (RecordedAction.DataFile dd : action.getOutputs())
            {
                ExpData outputData = addData(datas, dd.getURI(), source);
                if (outputData.getSourceApplication() != null)
                {
                    outputData.setDataFileUrl(null);
                    outputData.save(getJob().getUser());

                    datas.remove(dd.getURI());
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

        // Set up the outputs for the run
        ExpProtocolApplication outputApp = run.addProtocolApplication(getJob().getUser(), expActions.get(expActions.size() - 1), ExpProtocol.ApplicationType.ExperimentRunOutput, "Run outputs");
        for (URI runInput : runOutputs)
        {
            outputApp.addDataInput(getJob().getUser(), datas.get(runInput), null, null);
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
        File f = getTempXarFile();

        FileOutputStream fOut = null;
        try
        {
            XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, DataURLRelativizer.ORIGINAL_FILE_LOCATION);
            exporter.addExperimentRun(run);

            fOut = new FileOutputStream(f);
            exporter.dumpXML(fOut);
            fOut.close();
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

    private File getTempXarFile()
    {
        return new File(_factory.getXarFile(getJob()) + ".temp");
    }
}