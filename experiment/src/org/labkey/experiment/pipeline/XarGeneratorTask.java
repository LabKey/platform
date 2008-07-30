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
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.net.URI;

/*
* User: jeckels
* Date: Jul 25, 2008
*/
public class XarGeneratorTask extends PipelineJob.Task<XarGeneratorTask.Factory>
{
    public static class Factory extends AbstractTaskFactory
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

        public String getStatusName()
        {
            return "SAVE EXPERIMENT";
        }

        public boolean isJobComplete(PipelineJob job) throws IOException, SQLException
        {
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
            String baseName = support.getBaseName();
            File dirAnalysis = support.getAnalysisDirectory();

            return NetworkDrive.exists(_outputType.newFile(dirAnalysis, baseName));
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
                protocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), name, ExpProtocol.ApplicationType.ProtocolApplication);
                protocol.save(getJob().getUser());
            }
            protocols.put(name, protocol);
        }
        
    }

    private void addData(Map<URI, ExpData> datas, URI uri)
    {
        // Check if we've already dealt with this one
        if (!datas.containsKey(uri))
        {
            File f = null;
            // Check if it's in the database already
            if (uri.toString().startsWith("file:"))
            {
                f = new File(uri);
            }

            ExpData data = ExperimentService.get().getExpDataByURL(uri.toString(), getJob().getContainer());
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
                data = ExperimentService.get().createData(getJob().getContainer(), new DataType("Data"), name);
                data.setDataFileURI(uri);
                data.save(getJob().getUser());
            }
            datas.put(uri, data);
        }
    }

    public List<PipelineAction> run() throws PipelineJobException
    {
        try
        {
            ExperimentService.get().beginTransaction();
            List<PipelineAction> actions = getJob().getActions();

            Map<URI, ExpData> datas = new HashMap<URI, ExpData>();
            Map<String, ExpProtocol> protocolCache = new HashMap<String, ExpProtocol>();
            List<String> protocolSequence = new ArrayList<String>();
            Set<URI> allInputs = new HashSet<URI>();
            Set<URI> runOutputs = new HashSet<URI>();

            for (TaskId taskId : getJob().getTaskPipeline().getTaskProgression())
            {
                TaskFactory factory = PipelineJobService.get().getTaskFactory(taskId);
                for (String name : factory.getActionNames())
                {
                    addProtocol(protocolCache, name);
                    protocolSequence.add(name);
                }
            }

            for (PipelineAction action : actions)
            {
                if (protocolCache.get(action.getName()) == null)
                {
                    throw new IllegalArgumentException("Could not find a matching action declaration for " + action.getName());
                }

                allInputs.addAll(action.getInputs());
                Set<URI> exportedOutputs = new HashSet<URI>(action.getOutputs());
                exportedOutputs.removeAll(action.getTempOutputs());
                runOutputs.addAll(exportedOutputs);

                for (URI uri : action.getInputs())
                {
                    addData(datas, uri);
                }
                for (URI uri : action.getOutputs())
                {
                    addData(datas, uri);
                }
            }

            String pipelineName = getJob().getTaskPipeline().getId().toString();
            ExpProtocol parentProtocol = ExperimentService.get().getExpProtocol(getJob().getContainer(), pipelineName);
            List<ExpProtocolAction> protocolSteps = new ArrayList<ExpProtocolAction>();
            ExpProtocol outputProtocol = null;
            ExpProtocolAction inputStep = null;
            ExpProtocolAction outputStep = null;
            String outputName = pipelineName + ".Output";

            if (parentProtocol == null)
            {
                parentProtocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), pipelineName, ExpProtocol.ApplicationType.ExperimentRun);
                parentProtocol.save(getJob().getUser());

                int sequence = 1;
                inputStep = parentProtocol.addStep(getJob().getUser(), parentProtocol, sequence++);
                for (String name : protocolSequence)
                {
                    ExpProtocolAction action = parentProtocol.addStep(getJob().getUser(), protocolCache.get(name), sequence++);
                    protocolSteps.add(action);
                }

                outputProtocol = ExperimentService.get().createExpProtocol(getJob().getContainer(), outputName, ExpProtocol.ApplicationType.ExperimentRunOutput);
                outputProtocol.save(getJob().getUser());
                
                outputStep = parentProtocol.addStep(getJob().getUser(), outputProtocol, sequence++);
            }
            else
            {
                // TODO - make sure the protocols match up, create a new version if they don't
                List<ExpProtocolAction> existingSteps = parentProtocol.getSteps();
                // Two extra steps in the database, one to mark the run inputs, one to mark the run outputs
                if (existingSteps.size() != protocolSequence.size() + 2)
                {
                    throw new IllegalStateException("Wrong number of steps in existing protocol, expected " + (protocolSequence.size() + 2) + " but was " + existingSteps.size());
                }
                int sequence = 1;
                for (ExpProtocolAction step : existingSteps)
                {
                    ExpProtocol childProtocol = step.getChildProtocol();
                    if (step.getActionSequence() != sequence)
                    {
                        throw new IllegalStateException("Wrong sequence number, expected " + sequence + " but was " + step.getActionSequence());
                    }
                    if (sequence == 1)
                    {
                        if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRun)
                        {
                            throw new IllegalStateException("Expected first step to be of type ExperimentRun, but was " + childProtocol.getApplicationType());
                        }
                        if (!childProtocol.getName().equals(pipelineName))
                        {
                            throw new IllegalStateException("Expected first step to have name "+ pipelineName + " but was " + childProtocol.getName());
                        }
                        if (childProtocol.getRowId() != parentProtocol.getRowId())
                        {
                            throw new IllegalStateException("Expected first step to match up with parent protocol rowId " + parentProtocol.getRowId() + " but was " + childProtocol.getRowId());
                        }
                        inputStep = step;
                    }
                    else if (sequence == protocolSequence.size() + 2)
                    {
                        if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ExperimentRunOutput)
                        {
                            throw new IllegalStateException("Expected last step to be of type ExperimentRunOutput, but was " + childProtocol.getApplicationType());
                        }
                        if (!childProtocol.getName().equals(outputName))
                        {
                            throw new IllegalStateException("Expected last step to have name " + outputName + " but was " + childProtocol.getName());
                        }
                        outputStep = step;
                        outputProtocol = childProtocol;
                    }
                    else
                    {
                        String protocolName = protocolSequence.get(sequence - 2);
                        if (childProtocol.getApplicationType() != ExpProtocol.ApplicationType.ProtocolApplication)
                        {
                            throw new IllegalStateException("Expected step with sequence " + sequence + " to be of type ProtocolApplication, but was " + childProtocol.getApplicationType());
                        }
                        if (!childProtocol.getName().equals(protocolName))
                        {
                            throw new IllegalStateException("Expected step with sequence " + sequence + " to have name " + protocolName + " but was " + childProtocol.getName());
                        }
                        if (childProtocol.getRowId() != protocolCache.get(protocolName).getRowId())
                        {
                            throw new IllegalStateException("Expected step with sequence " + sequence + " to match up with protocol rowId " + protocolCache.get(protocolName).getRowId() + " but was " + childProtocol.getRowId());
                        }
                        protocolSteps.add(step);
                    }
                    sequence++;
                }
            }

            Set<URI> runInputs = new HashSet<URI>(allInputs);
            for (PipelineAction action : actions)
            {
                runInputs.removeAll(action.getOutputs());
            }

            ExpRun run = ExperimentService.get().createExperimentRun(getJob().getContainer(), getJob().getDescription());
            run.setProtocol(parentProtocol);
            run.save(getJob().getUser());

            // Set up the inputs to the whole run
            ExpProtocolApplication inputApp = run.addProtocolApplication(getJob().getUser(), inputStep, parentProtocol.getApplicationType(), null);
            for (URI runInput : runInputs)
            {
                inputApp.addDataInput(getJob().getUser(), datas.get(runInput), null, null);
            }

            // Set up the inputs and outputs for the individual actions
            for (int i = 0; i < actions.size(); i++)
            {
                PipelineAction action = actions.get(i);
                ExpProtocolAction step = protocolSteps.get(i);

                ExpProtocolApplication app = run.addProtocolApplication(getJob().getUser(), step, ExpProtocol.ApplicationType.ProtocolApplication, action.getName());
                for (Map.Entry<PipelineAction.ParameterType, Object> param : action.getParams().entrySet())
                {
                    ProtocolApplicationParameter protAppParam = new ProtocolApplicationParameter();
                    protAppParam.setProtocolApplicationId(app.getRowId());
                    protAppParam.setRunId(run.getRowId());
                    protAppParam.setName(param.getKey().getName());
                    protAppParam.setValue(param.getKey().getType(), param.getValue());

                    ExperimentServiceImpl.get().loadParameter(getJob().getUser(), protAppParam, ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter(), "ProtocolApplicationId", app.getRowId());
                }
                for (URI uri : action.getInputs())
                {
                    app.addDataInput(getJob().getUser(), datas.get(uri), null, null);
                }
                for (URI uri : action.getOutputs())
                {
                    ExpData outputData = datas.get(uri);
                    if (outputData.getSourceApplication() != null)
                    {
                        throw new PipelineJobException("Data with LSID '" + outputData.getLSID() + "' at '" + outputData.getDataFileUrl() + "' is already marked as being created by " + outputData.getSourceApplication());
                    }
                    outputData.setSourceApplication(app);
                    outputData.save(getJob().getUser());
                }
                runOutputs.removeAll(action.getTempOutputs());
            }

            // Set up the outputs for the run
            ExpProtocolApplication outputApp = run.addProtocolApplication(getJob().getUser(), outputStep, outputProtocol.getApplicationType(), null);
            for (URI runInput : runOutputs)
            {
                outputApp.addDataInput(getJob().getUser(), datas.get(runInput), null, null);
            }

            ExperimentService.get().commitTransaction();
        }
        catch (SQLException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
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
}