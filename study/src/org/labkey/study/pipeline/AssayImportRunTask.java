/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.study.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.pipeline.XarGeneratorId;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.XMLBeanTaskFactoryFactory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.pipeline.AssayImportRunTaskFactorySettings;
import org.labkey.api.study.assay.pipeline.AssayImportRunTaskId;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.pipeline.xml.AssayImportRunTaskType;
import org.labkey.pipeline.xml.TaskType;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/18/13
 */
public class AssayImportRunTask extends PipelineJob.Task<AssayImportRunTask.Factory>
{
    public static final String PROVIDER_NAME_PROPERTY = "providerName";
    public static final String PROTOCOL_NAME_PROPERTY = "protocolName";

    //private static final TaskId TASK_ID = new TaskId(StudyModule.MODULE_NAME, TaskId.Type.task, "assayimport", 0);

    public static class FactoryFactory implements XMLBeanTaskFactoryFactory
    {
        @Override
        public TaskFactory create(TaskId taskId, TaskType xobj, Path taskDir)
        {
            if (taskId.getModuleName() == null)
                throw new IllegalArgumentException("Task factory must be defined by a module");

            Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());

            if (!(xobj instanceof AssayImportRunTaskType))
                throw new IllegalArgumentException("XML instance must be a AssayImportRunTaskType");

            Factory factory = new Factory(taskId);
            factory.setDeclaringModule(module);

            AssayImportRunTaskType xtask = (AssayImportRunTaskType)xobj;
            if (xtask.isSetProviderName())
                factory._providerName = xtask.getProviderName();
            if (xtask.isSetProtocolName())
                factory._protocolName = xtask.getProtocolName();

            return factory;
        }
    }

    public static class Factory extends AbstractTaskFactory<AssayImportRunTaskFactorySettings, Factory>
    {
        private FileType _outputType = XarGeneratorId.FT_PIPE_XAR_XML;

        private String _providerName = "${" + PROVIDER_NAME_PROPERTY + "}";
        private String _protocolName = "${" + PROTOCOL_NAME_PROPERTY + "}";

        public Factory()
        {
            super(AssayImportRunTaskId.class);
        }

        public Factory(Class namespaceClass)
        {
            super(namespaceClass);
        }

        public Factory(TaskId taskId)
        {
            super(taskId);
        }

        @Override
        protected void configure(AssayImportRunTaskFactorySettings settings)
        {
            super.configure(settings);

            if (settings.getProviderName() != null)
                _providerName = settings.getProviderName();

            if (settings.getProtocolName() != null)
                _protocolName = settings.getProtocolName();
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new AssayImportRunTask(this, job);
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
            return "IMPORT ASSAY RUN";
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        @Override
        public void validateParameters(PipelineJob job) throws PipelineValidationException
        {
            super.validateParameters(job);

            try
            {
                AssayProvider provider = getProvider(job);
                ExpProtocol protocol = getProtocol(job, provider);
            }
            catch (PipelineJobException e)
            {
                throw new PipelineValidationException(e.getMessage());
            }
        }

        @NotNull
        private AssayProvider getProvider(PipelineJob job) throws PipelineJobException
        {
            String providerName = _providerName;
            if (providerName == null)
                throw new PipelineJobException("Assay provider name or job parameter name required");

            if (providerName.startsWith("${") && providerName.endsWith("}"))
            {
                String propertyName = providerName.substring(2, providerName.length()-3);
                String value = job.getParameters().get(propertyName);
                if (value == null)
                    throw new PipelineJobException("Assay provider name for job parameter " + providerName + " required");
                providerName = value;
            }

            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider == null)
                throw new PipelineJobException("Assay provider not found: " + providerName);

            return provider;
        }

        @NotNull
        private ExpProtocol getProtocol(PipelineJob job, AssayProvider provider) throws PipelineJobException
        {
            Container c = job.getContainer();
            List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider);

            // If only one protocol exists in the container, use it
            if (protocols.size() == 1)
                return protocols.get(0);

            // Otherwise, we require a name
            String protocolName = _protocolName;
            if (protocolName == null)
                throw new PipelineJobException("Assay protocol name or job parameter name required");

            if (protocolName.startsWith("${") && protocolName.endsWith("}"))
            {
                String propertyName = protocolName.substring(2, protocolName.length()-1);
                String value = job.getParameters().get(propertyName);
                if (value == null)
                    throw new PipelineJobException("Assay protocol name for job parameter " + protocolName + " required");
                protocolName = value;
            }

            // Find by LSID
            ExpProtocol expProtocol = ExperimentService.get().getExpProtocol(protocolName);
            if (expProtocol != null)
            {
                if (AssayService.get().getProvider(expProtocol) != provider)
                    throw new PipelineJobException("Experiment protocol LSID '" + protocolName + "' is not of assay provider type '" + provider.getName() + "'");

                return expProtocol;
            }

            // Find by name
            for (ExpProtocol protocol : protocols)
            {
                if (protocol.getName().equalsIgnoreCase(protocolName))
                    return protocol;
            }

            throw new PipelineJobException("Assay protocol not found: " + protocolName);
        }

    }

    public AssayImportRunTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    // Get the outputs of the last action in the job sequence
    private List<RecordedAction.DataFile> getOutputs(PipelineJob job) throws PipelineJobException
    {
        RecordedActionSet actionSet = job.getActionSet();
        List<RecordedAction> actions = new ArrayList<>(actionSet.getActions());
        if (actions.size() < 1)
            throw new PipelineJobException("No recorded actions");

        List<RecordedAction.DataFile> outputs = new ArrayList<>();

        RecordedAction lastAction = actions.get(actions.size()-1);
        for (RecordedAction.DataFile dataFile : lastAction.getOutputs())
        {
            if (dataFile.isTransient())
                continue;

            URI uri = dataFile.getURI();
            if (uri != null && "file".equals(uri.getScheme()))
            {
                File file = new File(uri);
                if (NetworkDrive.exists(file))
                    outputs.add(dataFile);
            }
        }

        return outputs;
    }

    private RecordedAction.DataFile findMatchedOutputFile(FileType fileType, List<RecordedAction.DataFile> outputFiles)
    {
        for (RecordedAction.DataFile outputFile : outputFiles)
        {
            URI uri = outputFile.getURI();
            File f = new File(uri);
            if (fileType.isType(f))
                return outputFile;
        }

        return null;
    }

    // Get the inputs and roles of the first action in the job sequence
    private Map<?, String> getInputs(PipelineJob job) throws PipelineJobException
    {
        RecordedActionSet actionSet = job.getActionSet();
        List<RecordedAction> actions = new ArrayList<>(actionSet.getActions());
        if (actions.size() < 1)
            return Collections.emptyMap();

        Map<File, String> inputs = new LinkedHashMap<>();

        RecordedAction firstAction = actions.get(0);
        for (RecordedAction.DataFile dataFile : firstAction.getInputs())
        {
            if (dataFile.isTransient())
                continue;

            URI uri = dataFile.getURI();
            if (uri != null && "file".equals(uri.getScheme()))
            {
                String role = dataFile.getRole();
                File file = new File(uri);
                if (NetworkDrive.exists(file))
                    inputs.put(file, role);
            }
        }

        // Include the job's analysis parameters as input so we can track which assay runs were imported with it.
        for (Map.Entry<URI, String> entry : actionSet.getOtherInputs().entrySet())
        {
            URI uri = entry.getKey();
            File file = new File(uri);
            if (NetworkDrive.exists(file))
                inputs.put(file, entry.getValue());
        }

        return inputs;
    }

    private String getName()
    {
        return getJob().getParameters().get("assay name");
    }

    private String getComments()
    {
        String comments = getJob().getParameters().get("assay comments");
        if (StringUtils.isEmpty(comments))
            comments = getJob().getParameters().get(PipelineJob.PIPELINE_PROTOCOL_DESCRIPTION_PARAM);
        return comments;
    }

    private String getTargetStudy()
    {
        return getJob().getParameters().get("assay targetStudy");
    }

    // CONSIDER: Add <runProperties> and <batchProperties> elements to the AssayImportRunTaskType in pipelineTasks.xsd instead of the prefix naming convention.
    private Map<String, Object> getPrefixedProperties(String prefix)
    {
        Map<String, String> params = getJob().getParameters();
        Map<String, Object> props = new HashMap<>();
        for (String key : params.keySet())
        {
            if (key.startsWith(prefix))
            {
                String prop = key.substring(prefix.length());
                // CONSIDER: The property value may be a ${key} which is the actual property name.
                String value = params.get(key);
                props.put(prop, value);
            }
        }
        return props;
    }

    private Map<String, Object> getBatchProperties()
    {
        return getPrefixedProperties("assay batch property, ");
    }

    private Map<String, Object> getRunProperties()
    {
        return getPrefixedProperties("assay run property, ");
    }

    /**
     * 1. Examine the outputs of the previous steps.
     * 2. If match is found (using the assay's FileType), import into an assay.
     * 3. Result domain type's columns may be of exp.data type.... .. CONSIDER: Propagate the ptid, specimen ids, etc.
     *
     * @return
     * @throws PipelineJobException
     */
    @NotNull
    public RecordedActionSet run() throws PipelineJobException
    {
        AssayProvider provider = _factory.getProvider(getJob());
        ExpProtocol protocol = _factory.getProtocol(getJob(), provider);

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            AssayDataType assayDataType = provider.getDataType();
            if (assayDataType == null)
                throw new PipelineJobException("AssayDataType required for importing run");

            FileType assayFileType = assayDataType.getFileType();
            if (assayFileType == null)
                throw new PipelineJobException("Assay FileType required for importing run");

            // Find output from the previous task
            List<RecordedAction.DataFile> outputFiles = getOutputs(getJob());
            if (outputFiles.isEmpty())
                throw new PipelineJobException("No output files found for importing run into assay '" + protocol.getName() + "'");

            // UNDONE: Support multiple input files
            // Find the first output that matches the assay's file type
            RecordedAction.DataFile matchedFile = findMatchedOutputFile(assayFileType, outputFiles);
            if (matchedFile == null)
                throw new PipelineJobException("No output files matched assay file type: " + assayFileType);

            // Issue 22587: Create ExpData for the output file from the RecordedActions if necessary so that we
            // ensure the generated bit is set on the ExpData.  Otherwise, the DefaultAssayRunCreator will create
            // the ExpData but without the generated bit.
            createData(matchedFile, assayDataType);

            User user = getJob().getUser();
            Container container = getJob().getContainer();

            AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> factory
                    = provider.createRunUploadFactory(protocol, user, container);

            factory.setName(getName());

            factory.setComments(getComments());

            // Add the job inputs as the assay run's inputs
            factory.setInputDatas(getInputs(getJob()));

            File uploadedData = new File(matchedFile.getURI());
            factory.setUploadedData(Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, uploadedData));

            factory.setBatchProperties(getBatchProperties());

            factory.setRunProperties(getRunProperties());

            factory.setTargetStudy(getTargetStudy());

            factory.setLogger(getJob().getLogger());

            AssayRunUploadContext uploadContext = factory.create();

            Integer batchId = null;

            // Import the assay run
            Pair<ExpExperiment, ExpRun> pair = provider.getRunCreator().saveExperimentRun(uploadContext, batchId);
            ExpRun run = pair.second;

            if (getJob() instanceof FileAnalysisJobSupport)
            {
                File analysisDir = getJob().getJobSupport(FileAnalysisJobSupport.class).getAnalysisDirectory();
                run.setFilePathRoot(analysisDir);
            }

            run.setJobId(PipelineService.get().getJobId(getJob().getUser(), getJob().getContainer(), getJob().getJobGUID()));
            run.save(getJob().getUser());

            // save any job-level custom properties from the run
//            PropertiesJobSupport jobSupport = getJob().getJobSupport(PropertiesJobSupport.class);
//            for (Map.Entry<PropertyDescriptor, Object> prop : jobSupport.getProps().entrySet())
//            {
//                run.setProperty(getJob().getUser(), prop.getKey(), prop.getValue());
//            }

            // Check if we've been cancelled. If so, delete any newly created runs from the database
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(getJob().getLogFile());
            if (statusFile != null && (PipelineJob.TaskStatus.cancelled.matches(statusFile.getStatus()) || PipelineJob.TaskStatus.cancelling.matches(statusFile.getStatus())))
            {
                getJob().info("Deleting run " + run.getName() + " due to cancellation request");
                run.delete(getJob().getUser());
            }

            // Consider these actions complete.  Saves the exp run's URL into the job status.
            getJob().clearActionSet(run);

            tx.commit();
        }
        catch (ExperimentException | ValidationException e)
        {
            throw new PipelineJobException("Failed to save experiment run in the database", e);
        }
        return new RecordedActionSet();
    }

    private ExpData createData(RecordedAction.DataFile dataFile, AssayDataType assayDataType)
    {
        Container c = getJob().getContainer();
        URI uri = dataFile.getURI();
        File file = new File(uri);

        ExpData data = ExperimentService.get().getExpDataByURL(file, c);
        if (data == null)
        {
            data = ExperimentService.get().createData(c, assayDataType, file.getName(), dataFile.isGenerated());
            data.setLSID(ExperimentService.get().generateGuidLSID(c, assayDataType));
            data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(file).toURI());
            data.save(getJob().getUser());
        }

        return data;
    }

}
