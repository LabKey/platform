/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
package org.labkey.pipeline.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.module.ModuleResourceCaches;
import org.labkey.api.module.ResourceRootProvider;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.RemoteExecutionEngine;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.XMLBeanTaskFactoryFactory;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProtocolFactory;
import org.labkey.api.pipeline.file.AbstractFileAnalysisProvider;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.NotFoundException;
import org.labkey.pipeline.analysis.FileAnalysisPipelineProvider;
import org.labkey.pipeline.api.properties.ApplicationPropertiesImpl;
import org.labkey.pipeline.api.properties.ConfigPropertiesImpl;
import org.labkey.pipeline.cluster.NoOpPipelineStatusWriter;
import org.labkey.pipeline.mule.JMSStatusWriter;
import org.labkey.pipeline.mule.test.DummyPipelineJob;
import org.labkey.pipeline.mule.test.DummyRemoteExecutionEngine;
import org.labkey.pipeline.xml.TaskType;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class PipelineJobServiceImpl implements PipelineJobService
{
    public static final String MODULE_PIPELINE_DIR = "pipeline";

    private static final Logger LOG = Logger.getLogger(PipelineJobServiceImpl.class);
    private static final String PIPELINE_TOOLS_ERROR = "Failed to locate %s. Use the site pipeline tools settings to specify where it can be found. (Currently '%s')";
    private static final String INSTALLED_PIPELINE_TOOL_ERROR = "Failed to locate %s. Check tool install location defined in pipelineConfig.xml. (Currently '%s')";
    private static final String MODULE_TASKS_DIR = "tasks";
    private static final String MODULE_PIPELINES_DIR = "pipelines";

    public static PipelineJobServiceImpl get()
    {
        return (PipelineJobServiceImpl) PipelineJobService.get();
    }

    @NotNull
    private LocationType _locationType;

    public static PipelineJobServiceImpl initDefaults(@NotNull LocationType locationType)
    {
        PipelineJobServiceImpl pjs = new PipelineJobServiceImpl(locationType, true);
        pjs.setAppProperties(new ApplicationPropertiesImpl());
        pjs.setConfigProperties(new ConfigPropertiesImpl());
        pjs.setWorkDirFactory(new WorkDirectoryLocal.Factory());
        PipelineStatusFile.JobStore jobStore;
        PipelineStatusFile.StatusWriter statusWriter;
        switch (locationType)
        {
            case WebServer:
                jobStore = new PipelineJobStoreImpl();
                statusWriter = PipelineServiceImpl.get();
                break;
            case RemoteServer:
                jobStore = new PipelineJobMarshaller();
                statusWriter = new JMSStatusWriter();
                break;
            case RemoteExecutionEngine:
                jobStore = new PipelineJobMarshaller();
                statusWriter = new NoOpPipelineStatusWriter();
                break;
            default:
                throw new IllegalStateException("Unexpected LocationType: " + locationType);
        }
        pjs.setJobStore(jobStore);
        pjs.setStatusWriter(statusWriter);
        PipelineJobService.setInstance(pjs);
        return pjs;
    }
    
    private final Map<TaskId, TaskPipeline> _taskPipelineStore;
    private final Map<TaskId, TaskFactory> _taskFactoryStore;
    private final Map<SchemaType, XMLBeanTaskFactoryFactory> _taskFactoryFactories;

    private static final ModuleResourceCache<Map<TaskId, TaskFactory>> TASK_FACTORY_CACHE = ModuleResourceCaches.create("TaskFactory cache", new TaskFactoryCacheHandler(), ResourceRootProvider.getStandard(new Path(MODULE_PIPELINE_DIR, MODULE_TASKS_DIR)));
    private static final ModuleResourceCache<Map<TaskId, TaskPipeline>> TASK_PIPELINE_CACHE = ModuleResourceCaches.create("TaskPipeline cache", new TaskPipelineCacheHandler(), ResourceRootProvider.getStandard(new Path(MODULE_PIPELINE_DIR, MODULE_PIPELINES_DIR)));

    private String _defaultExecutionLocation = TaskFactory.WEBSERVER;
    private int _defaultAutoRetry = 0;

    private boolean _prependVersionWithDot = true;
    private ApplicationProperties _appProperties;
    private ConfigProperties _configProperties;
    private RemoteServerProperties _remoteServerProperties;

    @NotNull
    private List<RemoteExecutionEngine<?>> _remoteExecutionEngines = new CopyOnWriteArrayList<>();

    private PipelineStatusFile.StatusWriter _statusWriter;
    private PipelineStatusFile.JobStore _jobStore;

    private WorkDirFactory _workDirFactory;
    private WorkDirFactory _largeWorkDirFactory;
    private PathMapper _pathMapper = new PathMapperImpl();  // Default to empty

    public PipelineJobServiceImpl()
    {
        this(null, true);
    }

    public PipelineJobServiceImpl(LocationType locationType, boolean register)
    {
        _locationType = locationType;
        // Allow Mule/Spring configuration, but keep any current defaults
        // set by the LabKey server.
        PipelineJobServiceImpl current = get();
        if (current != null)
        {
            _taskPipelineStore = current._taskPipelineStore;
            _taskFactoryStore = current._taskFactoryStore;
            _taskFactoryFactories = current._taskFactoryFactories;

            _appProperties = current._appProperties;
            _configProperties = current._configProperties;
            _remoteServerProperties = current._remoteServerProperties;
            _remoteExecutionEngines = current._remoteExecutionEngines;
            _statusWriter = current._statusWriter;
            _workDirFactory = current._workDirFactory;
            _jobStore = current._jobStore;
            _locationType = current._locationType;
        }
        else
        {
            _taskPipelineStore = new HashMap<>();
            _taskFactoryStore = new HashMap<>();
            _taskFactoryFactories = new HashMap<>();
        }

        if (register)
        {
            PipelineJobService.setInstance(this);
        }
    }

    /**
     * Used to get a TaskPipeline by name. If the pipeline has been registered using Mule's Spring configuration,
     * then that version will be used. Otherwise, the default passed-in version gets used.
     *
     * @param id An enum ID that uniquely identifies the pipeline
     * @return the definitive TaskPipeline for this id
     */
    public @Nullable TaskPipeline getTaskPipeline(TaskId id)
    {
        TaskPipeline pipeline;

        synchronized (_taskPipelineStore)
        {
            pipeline = _taskPipelineStore.get(id);
        }

        // Null pipeline could mean a task pipeline defined by a module that no longer exists, #32082. Or it could mean
        // it's defined as a module resource. Hence the null checks.
        if (null == pipeline && null != id.getModuleName())
        {
            Module module = ModuleLoader.getInstance().getModule(id.getModuleName());

            if (null != module)
            {
                pipeline = TASK_PIPELINE_CACHE.getResourceMap(module).get(id);
            }
        }

        return pipeline;
    }

    @NotNull
    @Override
    public TaskPipeline getTaskPipeline(String taskIdString)
    {
        try
        {
            TaskId taskId = new TaskId(taskIdString);
            TaskPipeline pipeline = getTaskPipeline(taskId);
            if (pipeline == null)
                throw new NotFoundException("The pipeline '" + taskId + "' was not found.");
            return pipeline;
        }
        catch (ClassNotFoundException e)
        {
            throw new NotFoundException("No pipeline found: " + e.getMessage());
        }
    }

    public void addTaskPipeline(TaskPipelineSettings settings) throws CloneNotSupportedException
    {
        TaskPipeline<TaskPipelineSettings> pipeline = getTaskPipeline(settings.getCloneId());
        if (pipeline == null)
            throw new IllegalArgumentException("Base implementation " + settings.getCloneId() + " not found.");

        addTaskPipeline(pipeline.cloneAndConfigure(settings,
                getTaskProgression(settings.getTaskProgressionSpec())));
    }

    public void addTaskPipeline(TaskPipeline pipeline)
    {
        synchronized (_taskPipelineStore)
        {
            // Remove a cached 'miss' entry if it is present
            _taskPipelineStore.put(pipeline.getId(), pipeline);
        }
    }

    protected TaskId[] getTaskProgression(Object... taskProgressionSpec)
    {
        TaskId[] taskProgression = new TaskId[taskProgressionSpec.length];
        for (int i = 0; i < taskProgressionSpec.length; i++)
        {
            // The TaskPipelineRegistrar
            taskProgression[i] = getTaskFactoryId(taskProgressionSpec[i]);
        }

        return taskProgression;
    }

    public TaskId getTaskFactoryId(Object taskFactorySpec)
    {
        // TaskPipelineRegistrar should have already converted everything
        // int TaskIds.  Just check to ensure there is really a factory
        // associated with this id.
        TaskId id = (TaskId) taskFactorySpec;
        if (getTaskFactory(id) == null)
        {
            String msg = "Failed to find factory for " + taskFactorySpec + ".";
            throw new IllegalArgumentException(msg);
        }

        return id;
    }

    @NotNull
    public Collection<TaskPipeline> getTaskPipelines(@NotNull Module module)
    {
        Collection<TaskPipeline> pipelines = new ArrayList<>();
        synchronized (_taskPipelineStore)
        {
            for (TaskPipeline pipeline : _taskPipelineStore.values())
            {
                if (module.equals(pipeline.getDeclaringModule()))
                    pipelines.add(pipeline);
            }
        }

        pipelines.addAll(TASK_PIPELINE_CACHE.getResourceMap(module).values());

        return pipelines;
    }

    @NotNull
    @Override
    public Collection<TaskPipeline> getTaskPipelines(@Nullable Container container)
    {
        return getTaskPipelines(container, null);
    }

    @NotNull
    @Override
    public <T extends TaskPipeline> Collection<T> getTaskPipelines(@Nullable Container container, @Nullable Class<T> inter)
    {
        Collection<Module> activeModules = container == null ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
        ArrayList<T> pipelineList = new ArrayList<>();

        for (Module module : activeModules)
        {
            Collection<TaskPipeline> pipelines = getTaskPipelines(module);
            for (TaskPipeline tp : pipelines)
            {
                if (tp != null && (inter == null || inter.isInstance(tp)))
                    pipelineList.add((T) tp);
            }
        }

        return Collections.unmodifiableList(pipelineList);
    }

    @Nullable
    public TaskFactory getTaskFactory(TaskId id)
    {
        synchronized (_taskFactoryStore)
        {
            TaskFactory factory = _taskFactoryStore.get(id);
            if (factory != null)
                return factory;
        }

        Module module = ModuleLoader.getInstance().getModule(id.getModuleName());

        return module == null ? null : TASK_FACTORY_CACHE.getResourceMap(module).get(id);
    }

    public void addTaskFactory(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        TaskFactory factory = getTaskFactory(settings.getCloneId());
        if (factory == null)
            throw new IllegalArgumentException("Base task factory implementation " + settings.getCloneId() + " not found in registry.");

        addTaskFactory(factory.cloneAndConfigure(settings));
    }

    @Override
    public void addTaskFactory(TaskFactory factory)
    {
        synchronized (_taskFactoryStore)
        {
            // Remove a cached 'miss' entry if present
            _taskFactoryStore.put(factory.getId(), factory);
        }
    }

    /**
     * Add a TaskFactory defined locally within a pipeline xml file.
     * NOTE: Don't use this for registering standard TaskFactories.
     */
    public void addLocalTaskFactory(TaskId pipelineId, TaskFactory factory)
    {
        if (!factory.getId().getName().startsWith(pipelineId.getName() + LOCAL_TASK_PREFIX))
            throw new IllegalArgumentException("local TaskFactory name '" + factory.getId() + "' must be prefixed with TaskPipeline name '" + pipelineId + "'.");

        addTaskFactory(factory);
    }

    /**
     * Configure and add a TaskFactory locally defined within a pipeline xml file.
     * NOTE: Don't use this for registering standard TaskFactories.
     */
    public void addLocalTaskFactory(TaskId pipelineId, TaskFactorySettings settings) throws CloneNotSupportedException
    {
        TaskFactory factory = getTaskFactory(settings.getCloneId());
        if (factory == null)
            throw new IllegalArgumentException("Base task factory implementation " + settings.getCloneId() + " not found in registry.");

        addLocalTaskFactory(pipelineId, factory.cloneAndConfigure(settings));
    }

    @NotNull
    private Collection<TaskFactory> getTaskFactories(@NotNull Module module)
    {
        Collection<TaskFactory> factories = new ArrayList<>();
        synchronized (_taskFactoryStore)
        {
            factories.addAll(_taskFactoryStore.values().stream()
                .filter(factory -> module.equals(factory.getDeclaringModule()))
                .collect(Collectors.toList()));
        }

        factories.addAll(TASK_FACTORY_CACHE.getResourceMap(module).values());

        return factories;
    }

    @NotNull
    public Collection<TaskFactory> getTaskFactories(Container container)
    {
        Collection<Module> activeModules = container == null ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
        ArrayList<TaskFactory> pipelineList = new ArrayList<>();

        for (Module module : activeModules)
        {
            Collection<TaskFactory> factories = getTaskFactories(module);
            pipelineList.addAll(factories);
        }

        return Collections.unmodifiableList(pipelineList);
    }

    @Override
    public void registerTaskFactoryFactory(SchemaType schemaType, XMLBeanTaskFactoryFactory factoryFactory)
    {
        synchronized (_taskFactoryFactories)
        {
            _taskFactoryFactories.put(schemaType, factoryFactory);
        }
    }

    @Override
    public TaskFactory createTaskFactory(TaskId taskId, TaskType xtask, Path tasksDir)
    {
        SchemaType schemaType = xtask.schemaType();
        synchronized (_taskFactoryFactories)
        {
            XMLBeanTaskFactoryFactory factoryFactory = _taskFactoryFactories.get(schemaType);
            if (factoryFactory == null)
                throw new IllegalArgumentException("TaskFactoryFactory not found for schema type: " + schemaType);

            return factoryFactory.create(taskId, xtask, tasksDir);
        }
    }

    public ParamParser createParamParser()
    {
        return new ParamParserImpl();
    }

    public String getDefaultExecutionLocation()
    {
        return _defaultExecutionLocation;
    }

    public void setDefaultExecutionLocation(String defaultExecutionLocation)
    {
        _defaultExecutionLocation = defaultExecutionLocation;
    }

    public int getDefaultAutoRetry()
    {
        return _defaultAutoRetry;
    }

    @Override
    public void removeTaskPipeline(TaskId pipelineId)
    {
        synchronized (_taskPipelineStore)
        {
            _taskPipelineStore.remove(pipelineId);
        }

        // Remove any locally defined tasks that start with the pipeline's id
        String prefix = pipelineId.getName() + LOCAL_TASK_PREFIX;
        for (TaskFactory factory : getTaskFactories((Container)null))
        {
            if (StringUtils.startsWith(factory.getId().getName(), prefix))
                removeTaskFactory(factory.getId());
        }
    }

    @Override
    public void removeTaskFactory(TaskId taskId)
    {
        synchronized (_taskFactoryStore)
        {
            _taskFactoryStore.remove(taskId);
        }
    }

    public void setDefaultAutoRetry(int defaultAutoRetry)
    {
        _defaultAutoRetry = defaultAutoRetry;
    }

    public PipelineStatusFile.StatusWriter getStatusWriter()
    {
        return _statusWriter;
    }

    public void setStatusWriter(PipelineStatusFile.StatusWriter statusWriter)
    {
        _statusWriter = statusWriter;
    }

    public PipelineStatusFile.JobStore getJobStore()
    {
        return _jobStore;
    }

    public void setJobStore(PipelineStatusFile.JobStore jobStore)
    {
        _jobStore = jobStore;
    }

    public WorkDirFactory getWorkDirFactory()
    {
        return _workDirFactory;
    }

    public void setWorkDirFactory(WorkDirFactory workDirFactory)
    {
        _workDirFactory = workDirFactory;
    }

    public WorkDirFactory getLargeWorkDirFactory()
    {
        return (_largeWorkDirFactory != null ? _largeWorkDirFactory : _workDirFactory);
    }

    public void setLargeWorkDirFactory(WorkDirFactory largeWorkDirFactory)
    {
        _largeWorkDirFactory = largeWorkDirFactory;
    }

    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public void setPathMapper(PathMapper pathMapper)
    {
        _pathMapper = pathMapper;
    }

    public ApplicationProperties getAppProperties()
    {
        return _appProperties;
    }

    public void setAppProperties(ApplicationProperties appProperties)
    {
        _appProperties = appProperties;
    }

    public ConfigProperties getConfigProperties()
    {
        return _configProperties;
    }

    public void setConfigProperties(ConfigProperties configProperties)
    {
        _configProperties = configProperties;
    }

    public RemoteServerProperties getRemoteServerProperties()
    {
        return _remoteServerProperties;
    }

    public void setRemoteServerProperties(RemoteServerProperties remoteServerProperties)
    {
        _remoteServerProperties = remoteServerProperties;
    }

    @NotNull
    public List<RemoteExecutionEngine<?>> getRemoteExecutionEngines()
    {
        return _remoteExecutionEngines;
    }

    public void registerRemoteExecutionEngine(RemoteExecutionEngine engine)
    {
        for (RemoteExecutionEngine existingEngine : _remoteExecutionEngines)
        {
            if (engine.getType().equals(existingEngine.getType()))
            {
                throw new IllegalArgumentException("Duplicate remote execution engine type: " + engine.getType());
            }
        }
        _remoteExecutionEngines.add(engine);
    }

    @SuppressWarnings("unused") // Can be called via reflection by Spring pipeline initialization
    public void setRemoteExecutionEngines(@NotNull List<RemoteExecutionEngine<?>> remoteExecutionEngines)
    {
        Set<String> locations = new CaseInsensitiveHashSet();
        for (RemoteExecutionEngine engine : remoteExecutionEngines)
        {
            if (!locations.add(engine.getConfig().getLocation()))
            {
                throw new IllegalArgumentException("Duplicate remote execution engine location: " + engine.getConfig().getLocation());
            }
        }
        _remoteExecutionEngines = remoteExecutionEngines;
    }

    private String getVersionedPath(String path, String packageName, String ver)
    {
        // Add package path prefix, if it exists.
        String packagePath = getConfigProperties().getSoftwarePackagePath(packageName);
        if (packagePath != null && packagePath.length() > 0)
        {
            path = packagePath + '/' + path;
        }

        // Handle version string replacement.
        if (ver == null)
            ver = "";
        ver = ver.trim();
        if (path.contains(VERSION_PLAIN_SUBSTITUTION))
            return path.replace(VERSION_PLAIN_SUBSTITUTION, ver);
        if (!"".equals(ver) && _prependVersionWithDot)
            ver = "." + ver;
        return path.replace(VERSION_SUBSTITUTION, ver);
    }

    /**
     * Search various directories to find the exact location of the specified tool.
     * @param installPath File system location where tool is installed. Use null to search both pipelineToolsDirectory and PATH
     * @param rel Path to tool relative to the installPath or pipelinToolsDirectory/PATH
     * @param expectExecutable Tool canExecute and may have an unspecified file extension
     * @return Full path to the specified tool
     */
    private String getPathToTool(@Nullable String installPath, String rel, boolean expectExecutable)
            throws FileNotFoundException
    {
        String path = installPath == null ? getToolsPath() : installPath;

        for (String pathFragment : path.split(File.pathSeparator))
        {
            File dir = new File(pathFragment);

            URI uri = URIUtil.resolve(dir.toURI(), rel);
            if (uri != null)
            {
                File file = new File(uri);

                // exact tool found
                if (NetworkDrive.exists(file) && (!expectExecutable || file.canExecute()))
                {
                    return file.toString();
                }

                // if a partial executable name is specified, check for possible matching executables in dir
                if (expectExecutable && dir.exists())
                {
                    final String relName;
                    final String relPackage;
                    int relSplitIndex = rel.lastIndexOf(File.separator);
                    if (relSplitIndex > 0)
                    {
                        relPackage = rel.substring(0, relSplitIndex);
                        relName = rel.substring(relSplitIndex + 1);
                    }
                    else
                    {
                        relPackage = "";
                        relName = rel;
                    }

                    File[] matchingExecutables = dir.listFiles(file1 ->
                    {
                        String fileName = file1.getName();
                        String parentName = file1.getParent();
                        String relNameExpected = relName;
                        String relPackageExpected = relPackage;

                        if (FileUtil.isCaseInsensitiveFileSystem())
                        {
                            // Convert to a lower case to do a case-insensitive comparison on Windows, etc. See issue 21269
                            fileName = fileName.toLowerCase();
                            parentName = parentName.toLowerCase();
                            relNameExpected = relNameExpected.toLowerCase();
                            relPackageExpected = relPackageExpected.toLowerCase();
                        }

                        return fileName.startsWith(relNameExpected + ".") || fileName.equals(relNameExpected) &&
                                parentName.endsWith(relPackageExpected) &&
                                file1.canExecute();
                    });

                    if (matchingExecutables != null && matchingExecutables.length > 0)
                        return file.toString();
                }
            }
        }

        if (installPath == null)
            throw new FileNotFoundException(String.format(PIPELINE_TOOLS_ERROR, rel, getAppProperties().getToolsDirectory()));
        else
            throw new FileNotFoundException(String.format(INSTALLED_PIPELINE_TOOL_ERROR, rel, installPath));
    }

    private String getToolsPath()
    {
        String toolsDir = getAppProperties().getToolsDirectory();
        CaseInsensitiveHashMap<String> ciEnvMap = new CaseInsensitiveHashMap<>((new ProcessBuilder()).environment());
        String path = ciEnvMap.get("PATH");
        return toolsDir + File.pathSeparator + path;
    }

    public String getExecutablePath(String exeRel, String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException
    {
        return getVersionedOsPath(exeRel, installPath, packageName, ver, true);
    }

    private String getVersionedOsPath(String exeRel, String installPath, String packageName, String ver, boolean expectExecutable) throws FileNotFoundException
    {
        // Make string replacements
        exeRel = getVersionedPath(exeRel, packageName, ver);

        // Can't just ask java.io.File.isAbsolute(), because if the web server is running on a different OS from
        // where the job will be running, we won't decide correctly when seeing if the task needs to run.
        // Instead, check if it will be an absolute path on either Windows (with a drive letter followed by a colon)
        // or *nix.
        if (exeRel.charAt(0) == '/' || (exeRel.length() > 2 && exeRel.charAt(1) == ':'))
        {
            return exeRel;
        }

        return getPathToTool(installPath, exeRel, expectExecutable);
    }

    @Override
    public String getToolPath(String exeRel, @Nullable String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException
    {
        return getVersionedOsPath(exeRel, installPath, packageName, ver, false);
    }

    public String getJarPath(String jarRel, String installPath, String packageName, String ver) throws FileNotFoundException
    {
        String toolsDir = installPath == null ? getAppProperties().getToolsDirectory() : installPath;
        if (toolsDir == null || toolsDir.trim().equals(""))
        {
            throw new FileNotFoundException("Failed to locate " + jarRel + ".  " +
                "Pipeline tools directory is not set.  " +
                "Use the site settings page to specify a directory.");
        }
        return getPathToTool(installPath, getVersionedPath(jarRel, packageName, ver), false);
    }

    public String getJavaPath() throws FileNotFoundException
    {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.trim().equals(""))
        {
            javaHome = System.getProperty("java.home");
            if (javaHome == null || javaHome.trim().equals(""))
            {
                throw new FileNotFoundException("Failed to locate Java.  " +
                    "Please set JAVA_HOME environment variable.\n" + System.getenv());
            }
        }
        File javaBin = new File(javaHome, "bin");
        if (!javaBin.exists())
        {
            throw new FileNotFoundException("Failed to locate Java.  " +
                "The path " + javaBin + " does not exist.  " +
                "Please fix the JAVA_HOME environment variable.");
        }
        return new File(javaBin, "java").toString();
    }

    @NotNull @Override
    public LocationType getLocationType()
    {
        return _locationType;
    }

    public boolean isPrependVersionWithDot()
    {
        return _prependVersionWithDot;
    }

    public void setPrependVersionWithDot(boolean prependVersionWithDot)
    {
        _prependVersionWithDot = prependVersionWithDot;
    }

    @NotNull
    @Override
    public AbstractFileAnalysisProtocolFactory getProtocolFactory(TaskPipeline taskPipeline)
    {
        AbstractFileAnalysisProvider provider = (AbstractFileAnalysisProvider)
                PipelineService.get().getPipelineProvider(FileAnalysisPipelineProvider.name);
        if (provider == null)
            throw new NotFoundException("No pipeline provider found for task pipeline: " + taskPipeline);

        if (!(taskPipeline instanceof FileAnalysisTaskPipeline))
            throw new NotFoundException("Task pipeline is not a FileAnalysisTaskPipeline: " + taskPipeline);

        FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline)taskPipeline;
        //noinspection unchecked
        return provider.getProtocolFactory(fatp);
    }

    public static class TestCase extends Assert
    {
        private String _tempDir;
        private File _dummyTool;
        private PipelineJobServiceImpl _impl;
        private ConfigPropertiesImpl _props;
        private ApplicationPropertiesImpl _appProps;

        @Before
        public void setUp() throws IOException
        {
            _tempDir = FileUtil.getAbsoluteCaseSensitiveFile(new File(System.getProperty("java.io.tmpdir"))).toString();
            if (_tempDir.endsWith("\\") || _tempDir.endsWith("/"))
            {
                // Strip off trailing slash
                _tempDir = _tempDir.substring(0, _tempDir.length() - 1);
            }

            _dummyTool = new File(_tempDir, "percolator_v.1.04");
            if (!_dummyTool.exists())
            {
                _dummyTool.createNewFile();
                _dummyTool.setExecutable(true);
            }

            _impl = new PipelineJobServiceImpl(null, false);
            _props = new ConfigPropertiesImpl();
            _appProps = new ApplicationPropertiesImpl();

            _appProps.setToolsDirectory(_tempDir);
            _impl.setAppProperties(_appProps);
            _impl.setConfigProperties(_props);
        }

        @Test
        public void testDummySubmit() throws PipelineValidationException, InterruptedException, PipelineProvider.HandlerException
        {
            if (!PipelineService.get().isEnterprisePipeline())
            {
                return;
            }

            int dummyEngineIndex = _impl._remoteExecutionEngines.size();
            DummyRemoteExecutionEngine dummyEngine = new DummyRemoteExecutionEngine();
            _impl._remoteExecutionEngines.add(dummyEngine);
            try
            {
                Container c = JunitUtil.getTestContainer();

                PipelineJob job = new DummyPipelineJob(c, TestContext.get().getUser());

                PipelineService.get().queueJob(job);

                int seconds = 0;
                while (seconds++ < 10 && dummyEngine.getSubmitCount() == 0)
                {
                    Thread.sleep(1000);
                }
                assertEquals("Job was never submitted", 1, dummyEngine.getSubmitCount());

                PipelineStatusFile file = PipelineStatusManager.getStatusFile(job.getContainer(), job.getLogFilePath());
                assertNotNull("No status file found!", file);

                PipelineStatusManager.cancelStatus(job.getInfo(), Collections.singleton(file.getRowId()));

                seconds = 0;
                while (seconds++ < 10 && dummyEngine.getCancelCount() == 0)
                {
                    Thread.sleep(1000);
                }
                assertEquals("Job was never cancelled", 1, dummyEngine.getCancelCount());

                // Will fail if job didn't get moved from Cancelling to Cancelled
                PipelineStatusManager.deleteStatus(job.getContainer(), job.getUser(), true, Collections.singleton(file.getRowId()));

                job.getLogFile().delete();
            }
            finally
            {
                _impl._remoteExecutionEngines.remove(dummyEngineIndex);
            }
        }

        @Test
        public void testVersionSubstitution() throws FileNotFoundException
        {
            assertEquals(_tempDir + File.separator + "percolator_v.1.04", _impl.getExecutablePath("percolator_v" + VERSION_SUBSTITUTION, null, "percolator", "1.04", null));
        }

        @Test
        public void testMissingTool()
        {
            try
            {
                _impl.getExecutablePath("percolator", null, "percolator", "1.04", null);
            }
            catch (FileNotFoundException e)
            {
                assertEquals(String.format(PIPELINE_TOOLS_ERROR, "percolator", _tempDir), e.getMessage());
            }
        }

        @Test
        public void testNonexistentPackage()
        {
            _props.setSoftwarePackages(Collections.singletonMap("percolator", "percolator_v" + VERSION_SUBSTITUTION));
            try
            {
                _impl.getExecutablePath("percolator", null, "percolator", "1.04", null);
            }
            catch (FileNotFoundException e)
            {
                assertEquals(String.format(PIPELINE_TOOLS_ERROR, "percolator_v.1.04/percolator", _tempDir), e.getMessage());
            }
        }

        @Test
        public void testToolInPackage() throws IOException
        {
            _dummyTool.delete();

            _dummyTool = new File(_tempDir, "percolator_v.1.04/percolator.exe");
            if (_dummyTool.exists())
            {
                _dummyTool.delete();
                _dummyTool.getParentFile().delete();
            }

            _dummyTool.getParentFile().mkdir();
            _dummyTool.createNewFile();
            _dummyTool.setExecutable(true);

            _props.setSoftwarePackages(Collections.singletonMap("percolator", "percolator_v" + VERSION_SUBSTITUTION));
            try
            {
                _impl.getExecutablePath("percolator", null, "percolator", "1.04", null);
            }
            catch (FileNotFoundException e)
            {
                assertEquals(String.format(PIPELINE_TOOLS_ERROR, "percolator_v.1.04/percolator", _tempDir), e.getMessage());
            }
        }

        @Test
        public void testVersionSubstitutionWithoutDot()
        {
            _impl.setPrependVersionWithDot(false);
            try
            {
                assertEquals(_tempDir + File.separator + "percolator_v1.04", _impl.getExecutablePath("percolator_v" + VERSION_SUBSTITUTION, null, "percolator", "1.04", null));
            }
            catch (FileNotFoundException e)
            {
                assertEquals(String.format(PIPELINE_TOOLS_ERROR, "percolator_v1.04", _tempDir), e.getMessage());
            }
        }

        @Test
        public void testModuleCaches()
        {
            int pipelineCount = TASK_PIPELINE_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(pipelineCount + " task pipelines defined in all modules");

            int factoryCount = TASK_FACTORY_CACHE.streamAllResourceMaps()
                .mapToInt(Map::size)
                .sum();

            LOG.info(factoryCount + " task factories defined in all modules");

            // Make sure the cache retrieves the expected number of pipelines and factories from a couple test modules,
            // if an R engine is configured and the test modules are present

            if (RReport.isEnabled())
            {
                Module pipelinetest = ModuleLoader.getInstance().getModule("pipelinetest");

                if (null != pipelinetest)
                {
                    assertEquals("Task pipelines from pipelinetest module", 8, TASK_PIPELINE_CACHE.getResourceMap(pipelinetest).size());
                    assertEquals("Task factories from pipelinetest module", 3, TASK_FACTORY_CACHE.getResourceMap(pipelinetest).size());
                }

                Module pipelinetest2 = ModuleLoader.getInstance().getModule("pipelinetest2");

                if (null != pipelinetest2)
                {
                    assertEquals("Task pipelines from pipelinetest2 module", 2, TASK_PIPELINE_CACHE.getResourceMap(pipelinetest2).size());
                    assertEquals("Task factories from pipelinetest2 module", 2, TASK_FACTORY_CACHE.getResourceMap(pipelinetest2).size());
                }
            }
        }

        @After
        public void tearDown()
        {
            if (_dummyTool.exists())
                _dummyTool.delete();
        }
    }
}
