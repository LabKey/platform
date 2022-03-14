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
package org.labkey.assay.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayDataCollector;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateMetadataDataHandler;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
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
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.writer.ZipUtil;
import org.labkey.pipeline.xml.AssayImportRunTaskType;
import org.labkey.pipeline.xml.TaskType;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * A factory for file analysis task implementations
     */
    public static class FileAnalysisFactory extends Factory
    {
        private static final String PROP_KEY = "name";
        private static final String PROP_VALUE = "value";
        private static final String RESULTS_NAME = "results";
        private static final String RUN_PROPS_NAME = "runProperties";
        private static final String BATCH_PROPS_NAME = "batchProperties";
        private static final String PLATE_METADATA_NAME = "plateMetadata";

        private static final String PROTOCOL_NAME_KEY = "name";
        private static final String PROTOCOL_ID_KEY = "id";

        public FileAnalysisFactory()
        {
            super(AssayImportRunTask.class);
        }

        @Override
        public String getStatusName()
        {
            return "ASSAY RUN IMPORT";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new AssayImportRunTask(this, job);
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        protected @NotNull ExpProtocol getProtocol(PipelineJob job, AssayProvider provider) throws PipelineJobException
        {
            ExpProtocol protocol = resolveProtocol(job);
            if (protocol != null)
                return protocol;
            return super.getProtocol(job, provider);
        }

        /**
         * Resolve the protocol from the name capture group of either : name or id
         */
        @Nullable
        private ExpProtocol resolveProtocol(PipelineJob job)
        {
            Map<String, String> params = job.getParameters();
            if (params.containsKey(PROTOCOL_ID_KEY) && params.containsKey(PROTOCOL_NAME_KEY))
            {
                job.getLogger().error("Protocol ID and name cannot be specified at the same time.");
                return null;
            }

            if (params.containsKey(PROTOCOL_ID_KEY))
            {
                return ExperimentService.get().getExpProtocol(Integer.parseInt(params.get(PROTOCOL_ID_KEY)));
            }
            else if (params.containsKey(PROTOCOL_NAME_KEY))
            {
                Optional<ExpProtocol> protocol = AssayService.get().getAssayProtocols(job.getContainer()).stream()
                        .filter(p -> p.getName().equals(params.get(PROTOCOL_NAME_KEY)))
                        .findFirst();

                if (protocol.isPresent())
                    return protocol.get();
            }
            return null;
        }

        @Override
        protected @NotNull AssayProvider getProvider(PipelineJob job) throws PipelineJobException
        {
            ExpProtocol protocol = resolveProtocol(job);
            if (protocol != null)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                    return provider;
            }

            // try to resolve using the assay provider param
            String providerName = job.getParameters().get("pipeline, assay provider");
            if (providerName != null)
            {
                AssayProvider provider = AssayService.get().getProvider(providerName);
                if (provider == null)
                    throw new PipelineJobException("Assay provider not found: " + providerName);

                return provider;
            }
            return super.getProvider(job);
        }

        /**
         * Alternatively for file analysis tasks, we can get the output files from the
         * triggered location
         */
        @Override
        List<RecordedAction.DataFile> getOutputs(PipelineJob job) throws PipelineJobException
        {
            List<RecordedAction.DataFile> outputs = new ArrayList<>();
            File dataFile = getDataFile(job);
            job.getLogger().info("Importing output data file : " + dataFile.getName());
            outputs.add(new RecordedAction.DataFile(dataFile.toURI(), "RESULTS-DATA", false, false));

            return outputs;
        }

        private File getDataFile(PipelineJob job)
        {
            FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);

            // guaranteed to have a single file upload
            assert support.getInputFiles().size() == 1;
            return support.getInputFiles().get(0);
        }

        @Override
        @Nullable
        List<Map<String, Object>> getRawData(PipelineJob job) throws PipelineJobException
        {
            File dataFile = getDataFile(job);
            try
            {
                if (ExcelLoader.isExcel(dataFile))
                {
                    job.getLogger().info("Processing excel file: " + dataFile.getName());
                    // check to see if this is a multi-sheet format
                    try (ExcelLoader loader = new ExcelLoader(dataFile, true))
                    {
                        List<String> sheets = loader.getSheetNames();
                        if (sheets.size() > 1)
                        {
                            job.getLogger().info("Processing excel multi-sheet format");

                            // if a sheet name with results exist, use that as the results data, otherwise
                            // default to the first sheet in the workbook
                            if (sheets.contains(RESULTS_NAME))
                            {
                                job.getLogger().info("Found sheet named : " + RESULTS_NAME + ", loading into results data.");
                                loader.setSheetName(RESULTS_NAME);
                            }
                            else
                                job.getLogger().info("Couldn't find sheet named : " + RESULTS_NAME + ", loading data from the first sheet.");
                            return loader.load();
                        }
                    }
                }
                else if (FileUtil.getExtension(dataFile).equals("zip"))
                {
                    ensureExplodedZip(job, dataFile);
                    File dir = getExplodedZipDir(job, dataFile);
                    File[] results = dir.listFiles((dir1, name) -> RESULTS_NAME.equalsIgnoreCase(FileUtil.getBaseName(name)));

                    if (results != null && results.length == 1)
                    {
                        File resultFile = results[0];
                        job.getLogger().info("Found results file named : " + resultFile + ", loading into results data.");
                        DataLoader loader = DataLoaderService.get().createLoader(resultFile, null, true, null, null);

                        return loader.load();
                    }
                }
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
            return null;
        }

        @Override
        @NotNull Map<String, Object> getBatchProperties(PipelineJob job) throws PipelineJobException
        {
            File dataFile = getDataFile(job);
            try
            {
                if (ExcelLoader.isExcel(dataFile))
                {
                    return loadProperties(dataFile, BATCH_PROPS_NAME, job.getLogger());
                }
                else if (FileUtil.getExtension(dataFile).equals("zip"))
                {
                    ensureExplodedZip(job, dataFile);
                    File dir = getExplodedZipDir(job, dataFile);
                    File[] results = dir.listFiles((dir1, name) -> BATCH_PROPS_NAME.equalsIgnoreCase(FileUtil.getBaseName(name)));
                    if (results != null && results.length == 1)
                    {
                        File resultFile = results[0];
                        job.getLogger().info("Found batch properties file named : " + resultFile + ", loading into results data.");
                        DataLoader loader = DataLoaderService.get().createLoader(resultFile, null, true, null, null);

                        return loadProperties(loader);
                    }
                }
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
            return Collections.emptyMap();
        }

        @Override
        @NotNull Map<String, Object> getRunProperties(PipelineJob job) throws PipelineJobException
        {
            File dataFile = getDataFile(job);
            try
            {
                if (ExcelLoader.isExcel(dataFile))
                {
                    return loadProperties(dataFile, RUN_PROPS_NAME, job.getLogger());
                }
                else if (FileUtil.getExtension(dataFile).equals("zip"))
                {
                    ensureExplodedZip(job, dataFile);
                    File dir = getExplodedZipDir(job, dataFile);
                    File[] results = dir.listFiles((dir1, name) -> RUN_PROPS_NAME.equalsIgnoreCase(FileUtil.getBaseName(name)));
                    if (results != null && results.length == 1)
                    {
                        File resultFile = results[0];
                        job.getLogger().info("Found run properties file named : " + resultFile + ", loading into results data.");
                        DataLoader loader = DataLoaderService.get().createLoader(resultFile, null, true, null, null);

                        return loadProperties(loader);
                    }
                }
            }
            catch (Exception e)
            {
                throw new PipelineJobException(e);
            }
            return Collections.emptyMap();
        }

        @Override
        @Nullable Map<String, AssayPlateMetadataService.MetadataLayer> getPlateMetadata(PipelineJob job) throws PipelineJobException
        {
            File dataFile = getDataFile(job);

            AssayPlateMetadataService svc = AssayPlateMetadataService.getService(PlateMetadataDataHandler.DATA_TYPE);
            if (svc != null)
            {
                try
                {
                    // plate metadata is only supported for zip archives because on JSON formats are currently supported
                    if (FileUtil.getExtension(dataFile).equals("zip"))
                    {
                        ensureExplodedZip(job, dataFile);
                        File dir = getExplodedZipDir(job, dataFile);
                        File[] results = dir.listFiles((dir1, name) -> PLATE_METADATA_NAME.equalsIgnoreCase(FileUtil.getBaseName(name)));
                        if (results != null && results.length == 1)
                        {
                            File metadataFile = results[0];
                            job.getLogger().info("Found plate metadata file named : " + metadataFile + ", attempting to parse JSON metadata.");
                            return svc.parsePlateMetadata(metadataFile);
                        }
                    }
                }
                catch (ExperimentException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            return null;
        }

        @Override
        void cleanUp(PipelineJob job) throws PipelineJobException
        {
            File dataFile = getDataFile(job);
            if (FileUtil.getExtension(dataFile).equals("zip"))
            {
                File dir = getExplodedZipDir(job, dataFile);
                FileUtil.deleteDir(dir, job.getLogger());
            }
        }

        private Map<String, Object> loadProperties(File dataFile, String sheetName, Logger log) throws PipelineJobException
        {
            try (ExcelLoader loader = new ExcelLoader(dataFile, true))
            {
                if (loader.getSheetNames().contains(sheetName))
                {
                    log.info("Found sheet named : " + sheetName + ", loading properties from this sheet.");

                    loader.setSheetName(sheetName);
                    return loadProperties(loader);
                }
                return Collections.emptyMap();
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }
        }

        private Map<String, Object> loadProperties(DataLoader loader) throws PipelineJobException
        {
            Map<String, Object> properties = new CaseInsensitiveHashMap<>();
            for (Map<String, Object> row : loader.load())
            {
                if (row.containsKey(PROP_KEY) && row.containsKey(PROP_VALUE))
                    properties.put(String.valueOf(row.get(PROP_KEY)), row.get(PROP_VALUE));
                else
                    throw new PipelineJobException("Batch or run properties must have column headers of : name and value " +
                            "to be parsed correctly.");
            }
            return properties;
        }

        private void ensureExplodedZip(PipelineJob job, File dataFile) throws PipelineJobException
        {
            File explodedDir = getExplodedZipDir(job, dataFile);
            if (!explodedDir.exists())
            {
                try
                {
                    ZipUtil.unzipToDirectory(dataFile, explodedDir, job.getLogger());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private File getExplodedZipDir(PipelineJob job, File dataFile)
        {
            File analysisDir = ((AbstractFileAnalysisJob)job).getAnalysisDirectory();
            return new File(analysisDir, String.format("%s-expanded", dataFile.getName()));
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

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new AssayImportRunTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public FileType getOutputType()
        {
            return _outputType;
        }

        @Override
        public String getStatusName()
        {
            return "IMPORT ASSAY RUN";
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
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
        protected AssayProvider getProvider(PipelineJob job) throws PipelineJobException
        {
            String providerName = _providerName;
            if (providerName == null)
                throw new PipelineJobException("Assay provider name or job parameter name required");

            if (providerName.startsWith("${") && providerName.endsWith("}"))
            {
                String propertyName = providerName.substring(2, providerName.length() - 1);
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
        protected ExpProtocol getProtocol(PipelineJob job, AssayProvider provider) throws PipelineJobException
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
                String propertyName = protocolName.substring(2, protocolName.length() - 1);
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

        List<RecordedAction.DataFile> getOutputs(PipelineJob job) throws PipelineJobException
        {
            return Collections.emptyList();
        }

        @Nullable
        List<Map<String, Object>> getRawData(PipelineJob job) throws PipelineJobException
        {
            return null;
        }

        @NotNull
        Map<String, Object> getBatchProperties(PipelineJob job) throws PipelineJobException
        {
            return Collections.emptyMap();
        }

        @NotNull
        Map<String, Object> getRunProperties(PipelineJob job) throws PipelineJobException
        {
            return Collections.emptyMap();
        }

        @Nullable
        Map<String, AssayPlateMetadataService.MetadataLayer> getPlateMetadata(PipelineJob job) throws PipelineJobException
        {
            return null;
        }

        void cleanUp(PipelineJob job) throws PipelineJobException
        {
        }
    }

    public AssayImportRunTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public AssayImportRunTask(FileAnalysisFactory factory, PipelineJob job)
    {
        super(factory, job);
    }

    // Get the outputs of the last action in the job sequence
    private List<RecordedAction.DataFile> getOutputs(PipelineJob job) throws PipelineJobException
    {
        List<RecordedAction.DataFile> outputs = _factory.getOutputs(job);
        if (outputs.isEmpty())
        {
            RecordedActionSet actionSet = job.getActionSet();
            List<RecordedAction> actions = new ArrayList<>(actionSet.getActions());
            if (actions.size() < 1)
                throw new PipelineJobException("No recorded actions");

            outputs = new ArrayList<>();

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
    private Map<?, String> getInputs(PipelineJob job)
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

    private Map<String, Object> getBatchProperties() throws PipelineJobException
    {
        Map<String, Object> props = new HashMap<>();
        props.putAll(getPrefixedProperties("assay batch property, "));
        props.putAll(_factory.getBatchProperties(getJob()));

        return props;
    }

    private Map<String, Object> getRunProperties() throws PipelineJobException
    {
        Map<String, Object> props = new HashMap<>();
        props.putAll(getPrefixedProperties("assay run property, "));
        props.putAll(_factory.getRunProperties(getJob()));

        return  props;
    }

    /**
     * 1. Examine the outputs of the previous steps.
     * 2. If match is found (using the assay's FileType), import into an assay.
     * 3. Result domain type's columns may be of exp.data type.... .. CONSIDER: Propagate the ptid, specimen ids, etc.
     *
     * @return
     * @throws PipelineJobException
     */
    @Override
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

            // Add raw data if specified, either raw data or uploaded file can be used but not both
            List<Map<String, Object>> rawData = _factory.getRawData(getJob());
            if (rawData != null)
                factory.setRawData(rawData);

            factory.setBatchProperties(getBatchProperties());

            factory.setRunProperties(getRunProperties());

            // add plate metadata if the provider supports it and the protocol has it enabled
            if (provider.isPlateMetadataEnabled(protocol))
            {
                Map<String, AssayPlateMetadataService.MetadataLayer> plateMetadata = _factory.getPlateMetadata(getJob());
                if (plateMetadata != null)
                {
                    factory.setRawPlateMetadata(plateMetadata);

                    // create an expdata object to track the metadata
                    ExpData plateData = DefaultAssayRunCreator.createData(container, "Plate Metadata", PlateMetadataDataHandler.DATA_TYPE, getJob().getLogger());
                    plateData.save(user);
                    factory.setOutputDatas(Map.of(plateData, ExpDataRunInput.DEFAULT_ROLE));
                }
            }

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
            _factory.cleanUp(getJob());

            tx.commit();
        }
        catch (ExperimentException | ValidationException | BatchValidationException e)
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
