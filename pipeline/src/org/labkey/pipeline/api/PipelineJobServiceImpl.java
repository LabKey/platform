/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.BlockingStringKeyCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.files.FileSystemWatcher;
import org.labkey.api.files.FileSystemWatchers;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.TaskPipelineSettings;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.resource.MergedDirectoryResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.Path;
import org.labkey.api.util.URIUtil;
import org.labkey.pipeline.analysis.FileAnalysisTaskPipelineImpl;
import org.labkey.pipeline.api.properties.ApplicationPropertiesImpl;
import org.labkey.pipeline.api.properties.ConfigPropertiesImpl;
import org.labkey.pipeline.api.properties.GlobusClientPropertiesImpl;
import org.labkey.pipeline.cluster.NoOpPipelineStatusWriter;
import org.labkey.pipeline.mule.JMSStatusWriter;
import org.labkey.pipeline.xstream.PathMapperImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.StandardWatchEventKinds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <code>PipelineJobServiceImpl</code>
 *
 * @author brendanx
 */
public class PipelineJobServiceImpl extends PipelineJobService
{
    public static final String MODULE_PIPELINE_DIR = "pipeline";

    private static final String MODULE_TASKS_DIR = "tasks";
    private static final String MODULE_PIPELINES_DIR = "pipelines";

    private static final String TASK_CONFIG_EXTENSION = ".task.xml";
    private static final String PIPELINE_CONFIG_EXTENSION = ".pipeline.xml";

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
            case Cluster:
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
    
    private HashMap<TaskId, TaskPipeline> _taskPipelineStore =
            new HashMap<>();
    private HashMap<TaskId, TaskFactory> _taskFactoryStore =
            new HashMap<>();

    private final Set<Module> _pipelineModules = new CopyOnWriteArraySet<>();
    private final FileSystemWatcher WATCHER = FileSystemWatchers.get("Module task and pipeline watcher");

    private String _defaultExecutionLocation = TaskFactory.WEBSERVER;
    private int _defaultAutoRetry = 0;

    private boolean _prependVersionWithDot = true;
    private ApplicationProperties _appProperties;
    private ConfigProperties _configProperties;
    private RemoteServerProperties _remoteServerProperties;
    private List<GlobusClientPropertiesImpl> _globusClientProperties = new ArrayList<>();
    private PathMapperImpl _clusterPathMapper = new PathMapperImpl(new LinkedHashMap<String, String>());

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
            current._taskPipelineStore.putAll(_taskPipelineStore);
            _taskPipelineStore = current._taskPipelineStore;
            current._taskFactoryStore.putAll(_taskFactoryStore);
            _taskFactoryStore = current._taskFactoryStore;

            _appProperties = current._appProperties;
            _configProperties = current._configProperties;
            _remoteServerProperties = current._remoteServerProperties;
            _globusClientProperties = current._globusClientProperties;
            _statusWriter = current._statusWriter;
            _workDirFactory = current._workDirFactory;
            _jobStore = current._jobStore;
            _locationType = current._locationType;
            _clusterPathMapper = current._clusterPathMapper;
        }

        if (register)
        {
            setInstance(this);
        }
    }

    // At startup, we record all modules with "pipeline/tasks" or "pipeline/pipelines" directories and register a file listener to monitor for changes.
    // Loading the list of configurations in each module and the descriptors themselves happens lazily.
    public void registerModule(Module module)
    {
        Path tasksDirPath = new Path(MODULE_PIPELINE_DIR, MODULE_TASKS_DIR);
        Resource tasksDir = module.getModuleResolver().lookup(tasksDirPath);

        Path pipelinesDirPath = new Path(MODULE_PIPELINE_DIR, MODULE_PIPELINES_DIR);
        Resource pipelinesDir = module.getModuleResolver().lookup(pipelinesDirPath);

        // UNDONE: Register listeners for 'pipeline/tasks/<name>' and 'pipeline/pipelines/<name>' as well
        if ((null != tasksDir && tasksDir.isCollection()) || (null != pipelinesDir && pipelinesDir.isCollection()))
        {
            _pipelineModules.add(module);

            // TODO: Integrate this better with Resource
            if (tasksDir != null)
            {
                ((MergedDirectoryResource)tasksDir).registerListener(WATCHER,
                        new PipelineResourceDirectoryListener(module, TaskId.Type.task, TASK_FACTORY_CACHE),
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            }

            // TODO: Integrate this better with Resource
            if (pipelinesDir != null)
            {
                ((MergedDirectoryResource)pipelinesDir).registerListener(WATCHER,
                        new PipelineResourceDirectoryListener(module, TaskId.Type.pipeline, TASK_PIPELINE_CACHE),
                        StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            }
        }
    }

    /**
     * Listens for file changes in the module's 'pipeline/tasks' and 'pipeline/pipelines' directories.
     */
    private class PipelineResourceDirectoryListener implements FileSystemDirectoryListener
    {
        final Module _module;
        final TaskId.Type _type;
        final Cache<String, ?> _cache;

        PipelineResourceDirectoryListener(Module module, TaskId.Type type, Cache<String, ?> cache)
        {
            _module = module;
            _type = type;
            _cache = cache;
        }

        @Override
        public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeConfigNames(_module);
        }

        @Override
        public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            removeConfigNames(_module);
            TaskId taskId = createTaskId(_module, _type, entry);
            removeTaskId(taskId);
        }

        @Override
        public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
        {
            TaskId taskId = createTaskId(_module, _type, entry);
            removeTaskId(taskId);
        }

        @Override
        public void overflow()
        {
            // I guess we should just clear the entire cache
            _cache.clear();
        }

        private void removeConfigNames(Module module)
        {
            _cache.remove(module.getName());
        }

        private void removeTaskId(TaskId taskId)
        {
            if (taskId != null)
                _cache.remove(taskId.toString());
        }
    }

    /**
     * Used to getInternal a TaskPipeline by name.  If the pipeline has been registered using
     * Mule's Spring configuration, than that version will be used.  Otherwise, the default
     * passed in version gets used.
     *
     * @param id An enum ID that uniquely identifies the pipeline
     * @return the definitive TaskPipeline for this id
     */
    public TaskPipeline getTaskPipeline(TaskId id)
    {
        //noinspection unchecked
        return (TaskPipeline)TASK_PIPELINE_CACHE.get(id.toString(), null, TASK_PIPELINE_LOADER);
    }

    @NotNull
    private Collection<TaskId> getTaskPipelineIds(@NotNull Module module)
    {
        //noinspection unchecked
        return (Collection<TaskId>)TASK_PIPELINE_CACHE.get(module.getName(), null, TASK_PIPELINE_IDS_LOADER);
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
        TASK_PIPELINE_CACHE.remove(pipeline.getId().toString());
        synchronized (_taskPipelineStore)
        {
            // Remove a cached 'miss' entry if it is present
            _taskPipelineStore.put(pipeline.getId(), pipeline);
            Module module = pipeline.getDeclaringModule();
            assert module != null; // TODO: is this true?
            _pipelineModules.add(module);
        }
    }

    protected TaskId[] getTaskProgression(Object... taskProgressioSpec)
    {
        TaskId[] taskProgression = new TaskId[taskProgressioSpec.length];
        for (int i = 0; i < taskProgressioSpec.length; i++)
        {
            // The TaskPipelineRegistrar
            taskProgression[i] = getTaskFactoryId(taskProgressioSpec[i]);
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

        for (Module module : _pipelineModules)
        {
            if (activeModules.contains(module))
            {
                Collection<TaskId> taskIds = getTaskPipelineIds(module);
                for (TaskId taskId : taskIds)
                {
                    TaskPipeline tp = getTaskPipeline(taskId);
                    if (tp != null && (inter == null || inter.isInstance(tp)))
                        pipelineList.add((T) tp);
                }
            }
        }

        return Collections.unmodifiableList(pipelineList);
    }

    @Nullable
    public TaskFactory getTaskFactory(TaskId id)
    {
        //noinspection unchecked
        return (TaskFactory)TASK_FACTORY_CACHE.get(id.toString(), null, TASK_FACTORY_LOADER);
    }

    @NotNull
    private Collection<TaskId> getTaskFactoryIds(@NotNull Module module)
    {
        //noinspection unchecked
        return (Collection<TaskId>)TASK_FACTORY_CACHE.get(module.getName(), null, TASK_FACTORY_IDS_LOADER);
    }

    public void addTaskFactory(TaskFactorySettings settings) throws CloneNotSupportedException
    {
        TaskFactory factory = getTaskFactory(settings.getCloneId());
        if (factory == null)
            throw new IllegalArgumentException("Base task factory implementation " + settings.getCloneId() + " not found in registry.");

        addTaskFactory(factory.cloneAndConfigure(settings));
    }

    public void addTaskFactory(TaskFactory factory)
    {
        TASK_FACTORY_CACHE.remove(factory.getId().toString());
        synchronized (_taskFactoryStore)
        {
            // Remove a cached 'miss' entry if present
            _taskFactoryStore.put(factory.getId(), factory);
            Module module = factory.getDeclaringModule();
            assert module != null; // TODO: is this true?
            _pipelineModules.add(module);
        }
    }

    @NotNull
    public Collection<TaskFactory> getTaskFactories(Container container)
    {
        Collection<Module> activeModules = container == null ? ModuleLoader.getInstance().getModules() : container.getActiveModules();
        ArrayList<TaskFactory> pipelineList = new ArrayList<>();

        for (Module module : _pipelineModules)
        {
            if (activeModules.contains(module))
            {
                Collection<TaskId> taskIds = getTaskFactoryIds(module);
                for (TaskId taskId : taskIds)
                {
                    TaskFactory tf = getTaskFactory(taskId);
                    if (tf != null)
                        pipelineList.add(tf);
                }
            }
        }

        return Collections.unmodifiableList(pipelineList);
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
        TASK_PIPELINE_CACHE.remove(pipelineId.toString());
        synchronized (_taskPipelineStore)
        {
            _taskPipelineStore.remove(pipelineId);
        }
    }

    @Override
    public void removeTaskFactory(TaskId taskId)
    {
        TASK_FACTORY_CACHE.remove(taskId.toString());
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

    public GlobusClientPropertiesImpl getGlobusClientProperties()
    {
        return _globusClientProperties.get(0);
    }
    
    @NotNull
    public List<GlobusClientPropertiesImpl> getGlobusClientPropertiesList()
    {
        return _globusClientProperties;
    }

    public void setGlobusClientProperties(GlobusClientPropertiesImpl globusClientProperties)
    {
        setGlobusClientPropertiesList(Collections.singletonList(globusClientProperties));
    }

    public void setGlobusClientPropertiesList(List<GlobusClientPropertiesImpl> globusClientProperties)
    {
        for (GlobusClientPropertiesImpl newProperties : globusClientProperties)
        {
            validateUniqueLocation(newProperties);
            _globusClientProperties.add(newProperties);
        }
        for (GlobusClientProperties globusClientProperty : globusClientProperties)
        {
            PathMapper mapper = globusClientProperty.getPathMapper();
            if (mapper != null)
            {
                for (Map.Entry<String, String> entry : mapper.getPathMap().entrySet())
                {
                    _clusterPathMapper.getPathMap().put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void validateUniqueLocation(GlobusClientPropertiesImpl newProperties)
    {
        String location = newProperties.getLocation();
        if (location == null)
        {
            throw new IllegalArgumentException("No location property specified for Globus server " + newProperties.getGlobusEndpoint());
        }
        for (GlobusClientPropertiesImpl existingProperties : _globusClientProperties)
        {
            if (existingProperties.getLocation().equalsIgnoreCase(location))
            {
                throw new IllegalArgumentException("Duplicate location property value '" + newProperties.getLocation() + "' specified for Globus server in pipelineConfig.xml. The default location value is 'cluster', and each Globus server must have a unique location property value.");
            }
        }
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
        if (!"".equals(ver) && _prependVersionWithDot)
            ver = "." + ver;

        return path.replace(VERSION_SUBSTITUTION, ver);
    }

    private String getToolsDirPath(String toolsDir, String rel, boolean checkFile)
            throws FileNotFoundException
    {
        File dir = new File(toolsDir);

        // Resolve strips the final part of the path, so add a dummy file name
        // to avoid losing part of the tools directory.
        URI uri = URIUtil.resolve(dir.toURI(), rel);
        if (uri == null)
        {
            if (!dir.isDirectory())
            {
                throw new FileNotFoundException("Failed to locate " + rel + ".  " +
                        "Pipeline tools directory " + dir + " does not exist.  " +
                        "Use the site settings page to specify an existing directory.");
            }
            else
            {
                throw new FileNotFoundException("Failed to locate " + rel + ".  " +
                        " Relative path is invalid.");

            }
        }
        File file = new File(uri);
        if (!file.exists())
        {
            if (!dir.exists())
            {
                throw new FileNotFoundException("File not found " + file + ".  " +
                    "Pipeline tools directory does not exist.  " +
                    "Use the site settings page to specify an existing directory.");
            }
            else if (!dir.equals(file.getParentFile()) && !file.getParentFile().exists())
            {
                throw new FileNotFoundException("File not found " + file + ".  " +
                    "Parent directory does not exist.");                
            }
            else if (checkFile)
            {
                throw new FileNotFoundException("File not found " + file + ".  " +
                    "Add this file to the pipeline tools directory.");
            }
        }
        return file.toString();
    }

    public String getExecutablePath(String exeRel, String installPath, String packageName, String ver, Logger jobLogger) throws FileNotFoundException
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

        String toolsDir = installPath == null ? getAppProperties().getToolsDirectory() : installPath;
        if (toolsDir == null || toolsDir.trim().equals(""))
        {
            // If the tools directory is not set, then rely on the path.
            jobLogger.warn("Pipeline tools directory is not set, relying on system path to find executables");
            return exeRel;
        }
        // CONSIDER(brendanx): CruiseControl fails without this, as may other situations
        //                     where the tools directory is set automatically to a bogus
        //                     path, but the required executables are on the path.
        else if (!NetworkDrive.exists(new File(toolsDir)))
        {
            jobLogger.warn("Pipeline tools directory '" + toolsDir + "' does not exist, tool execution may fail");
            return exeRel;
        }

        // Don't check for file existence with executable paths, since they may be
        // lacking an extension (exe, bat, cmd) on Windows platforms.
        return getToolsDirPath(toolsDir, exeRel, false);
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
        return getToolsDirPath(toolsDir, getVersionedPath(jarRel, packageName, ver), true);
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

    public void setClusterPathMapper(PathMapperImpl clusterPathMapper)
    {
        _clusterPathMapper = clusterPathMapper;
    }

    public PathMapperImpl getClusterPathMapper()
    {
        return _clusterPathMapper;
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

    private boolean isTaskConfigFile(String filename)
    {
        return filename.endsWith(TASK_CONFIG_EXTENSION) && filename.length() > TASK_CONFIG_EXTENSION.length();
    }

    private boolean isPipelineConfigFile(String filename)
    {
        return filename.endsWith(PIPELINE_CONFIG_EXTENSION) && filename.length() > PIPELINE_CONFIG_EXTENSION.length();
    }

    // Parses a filename '<name>.task.xml' or '<name>.pipeline.xml' into a TaskId
    // TODO: Check that the Path is in the module and under the 'tasks' or 'pipelines' directory.
    private TaskId createTaskId(Module module, TaskId.Type type, java.nio.file.Path path)
    {
        String filename = path.getFileName().toString();
        String taskName = getTaskNameFromFileName(filename);
        if (taskName == null)
            return null;

        // TODO: Version information
        TaskId taskId = new TaskId(module.getName(), type, taskName, 0);
        return taskId;
    }

    // Checks for "<name>.task.xml" or "<name>.pipeline.xml" and removes the extension.
    private String getTaskNameFromFileName(String filename)
    {
        String taskName = null;
        if (isTaskConfigFile(filename))
        {
            taskName = filename.substring(0, filename.length() - TASK_CONFIG_EXTENSION.length());
        }
        else if (isPipelineConfigFile(filename))
        {
            taskName = filename.substring(0, filename.length() - PIPELINE_CONFIG_EXTENSION.length());
        }

        return taskName;
    }

    private final CacheLoader TASK_FACTORY_IDS_LOADER = new CacheLoader<String, Collection<TaskId>>()
    {
        @Override
        public Collection<TaskId> load(String moduleName, @Nullable Object argument)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);

            Collection<TaskId> ids = new LinkedHashSet<>();

            // First, get ids registered via Spring xml configs filtered by module
            synchronized (PipelineJobServiceImpl.this._taskFactoryStore)
            {
                for (TaskFactory factory : PipelineJobServiceImpl.this._taskFactoryStore.values())
                {
                    if (factory.getDeclaringModule() != null && module == factory.getDeclaringModule())
                        ids.add(factory.getId());
                }
            }

            // Next, look for module task configs
            Path tasksDirPath = new Path(MODULE_PIPELINE_DIR, MODULE_TASKS_DIR);
            Resource tasksDir = module.getModuleResolver().lookup(tasksDirPath);
            if (tasksDir != null && tasksDir.isCollection())
            {
                // Create a list of all files in this directory that conform to the configuration file format (ends in .task.xml or is a directory that contains a .task.xml)
                for (Resource r : tasksDir.list())
                {
                    String fileName = r.getName();
                    if (r.isFile())
                    {
                        if (isTaskConfigFile(fileName))
                        {
                            // TODO: version information
                            String taskName = getTaskNameFromFileName(fileName);
                            TaskId taskId = new TaskId(module.getName(), TaskId.Type.task, taskName, 0);
                            ids.add(taskId);
                        }
                    }
                    else if (r.isCollection())
                    {
                        Resource child = r.find(fileName + TASK_CONFIG_EXTENSION);
                        if (child != null)
                        {
                            // TODO: version information
                            String taskName = getTaskNameFromFileName(fileName);
                            TaskId taskId = new TaskId(module.getName(), TaskId.Type.task, taskName, 0);
                            ids.add(taskId);
                        }
                    }
                }
            }

            return Collections.unmodifiableCollection(ids);
        }
    };

    private final CacheLoader TASK_FACTORY_LOADER = new CacheLoader<String, TaskFactory>()
    {
        @Override
        public TaskFactory load(String key, @Nullable Object argument)
        {
            TaskId taskId;
            try
            {
                taskId = TaskId.valueOf(key);
            }
            catch (ClassNotFoundException e)
            {
                Logger.getLogger(PipelineJobServiceImpl.class).warn(e);
                return null;
            }

            // First, look for tasks registered via Spring xml configs
            synchronized (PipelineJobServiceImpl.this._taskFactoryStore)
            {
                TaskFactory factory = PipelineJobServiceImpl.this._taskFactoryStore.get(taskId);
                if (factory != null)
                    return factory;
            }

            // Next, look for a module task config file
            if (taskId.getName() != null && taskId.getModuleName() != null)
            {
                Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());
                String configFileName = taskId.getName() + TASK_CONFIG_EXTENSION;

                Path tasksDirPath = new Path(MODULE_PIPELINE_DIR, MODULE_TASKS_DIR);

                // Look for a "pipeline/tasks/<name>.task.xml" file
                Path taskConfigPath = tasksDirPath.append(configFileName);
                Resource taskConfig = module.getModuleResource(taskConfigPath);
                if (taskConfig != null && taskConfig.isFile())
                    return load(taskId, taskConfig);

                // Look for a "pipeline/tasks/<name>/<name>.task.xml" file
                taskConfigPath = tasksDirPath.append(taskId.getName()).append(configFileName);
                taskConfig = ModuleLoader.getInstance().getResource(taskConfigPath);
                if (taskConfig != null && taskConfig.isFile())
                    return load(taskId, taskConfig);
            }

            return null;
        }

        private TaskFactory load(TaskId taskId, Resource taskConfig)
        {
            try
            {
                return SimpleTaskFactory.create(taskId, taskConfig);
            }
            catch (IllegalArgumentException|IllegalStateException e)
            {
                Logger.getLogger(PipelineJobServiceImpl.class).warn("Error registering '" + taskId + "' task: " + e.getMessage());
                return null;
            }
        }

    };

    private final CacheLoader TASK_PIPELINE_IDS_LOADER = new CacheLoader<String, Collection<TaskId>>()
    {
        @Override
        public Collection<TaskId> load(String moduleName, @Nullable Object argument)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);

            Collection<TaskId> ids = new LinkedHashSet<>();

            // First, get ids registered via Spring xml configs filtered by module
            synchronized (PipelineJobServiceImpl.this._taskPipelineStore)
            {
                for (TaskPipeline factory : PipelineJobServiceImpl.this._taskPipelineStore.values())
                {
                    if (factory.getDeclaringModule() != null && module == factory.getDeclaringModule())
                        ids.add(factory.getId());
                }
            }

            // Next, look for module pipeline configs
            Path pipelinesDirPath = new Path(MODULE_PIPELINE_DIR, MODULE_PIPELINES_DIR);
            Resource pipelinesDir = module.getModuleResolver().lookup(pipelinesDirPath);
            if (pipelinesDir != null && pipelinesDir.isCollection())
            {
                // Create a list of all files in this directory that conform to the configuration file format (ends in .pipeline.xml)
                for (Resource r : pipelinesDir.list())
                {
                    if (r.isFile())
                    {
                        String fileName = r.getName();
                        if (isPipelineConfigFile(fileName))
                        {
                            // TODO: version information
                            String pipelineName = getTaskNameFromFileName(fileName);
                            TaskId taskId = new TaskId(module.getName(), TaskId.Type.pipeline, pipelineName, 0);
                            ids.add(taskId);
                        }
                    }
                }
            }

            return Collections.unmodifiableCollection(ids);
        }
    };

    private final CacheLoader TASK_PIPELINE_LOADER = new CacheLoader<String, TaskPipeline>()
    {
        @Override
        public TaskPipeline load(String key, @Nullable Object argument)
        {
            TaskId taskId;
            try
            {
                taskId = TaskId.valueOf(key);
            }
            catch (ClassNotFoundException e)
            {
                Logger.getLogger(PipelineJobServiceImpl.class).warn(e);
                return null;
            }

            // First, look for pipeline registered via Spring xml configs
            synchronized (PipelineJobServiceImpl.this._taskPipelineStore)
            {
                TaskPipeline pipeline = PipelineJobServiceImpl.this._taskPipelineStore.get(taskId);
                if (pipeline != null)
                    return pipeline;
            }

            // Next, look for a module pipeline config file
            if (taskId.getNamespaceClass() == null && taskId.getName() != null && taskId.getModuleName() != null)
            {
                Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());
                String configFileName = taskId.getName() + PIPELINE_CONFIG_EXTENSION;

                // Look for a "pipeline/pipelines/<name>.pipeline.xml" file
                Path pipelineConfigPath = new Path(MODULE_PIPELINE_DIR, MODULE_PIPELINES_DIR, configFileName);
                Resource pipelineConfig = module.getModuleResource(pipelineConfigPath);
                if (pipelineConfig != null && pipelineConfig.isFile())
                    return load(taskId, pipelineConfig);
            }

            return null;
        }

        private TaskPipeline load(TaskId taskId, Resource pipelineConfig)
        {
            try
            {
                return FileAnalysisTaskPipelineImpl.create(taskId, pipelineConfig);
            }
            catch (IllegalArgumentException|IllegalStateException e)
            {
                Logger.getLogger(PipelineJobServiceImpl.class).warn("Error registering '" + taskId + "' pipeline: " + e.getMessage());
                return null;
            }
        }
    };

    private final BlockingStringKeyCache<Object> TASK_FACTORY_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, "TaskFactory Cache", null);
    private final BlockingStringKeyCache<Object> TASK_PIPELINE_CACHE = CacheManager.getBlockingStringKeyCache(1000, CacheManager.DAY, "TaskPipeline Cache", null);

    public static class TestCase extends Assert
    {
        @Test
        public void testVersionSubstitution() throws FileNotFoundException
        {
            PipelineJobServiceImpl impl = new PipelineJobServiceImpl(null, false);
            ConfigPropertiesImpl props = new ConfigPropertiesImpl();
            ApplicationPropertiesImpl appProps = new ApplicationPropertiesImpl();
            String homeDir = FileUtil.getAbsoluteCaseSensitiveFile(new File(System.getProperty("java.io.tmpdir"))).toString();
            if (homeDir.endsWith("\\") || homeDir.endsWith("/"))
            {
                // Strip off trailing slash
                homeDir = homeDir.substring(0, homeDir.length() - 1);
            }
            appProps.setToolsDirectory(homeDir);
            impl.setAppProperties(appProps);
            impl.setConfigProperties(props);

            assertEquals(homeDir + File.separator + "percolator_v.1.04", impl.getExecutablePath("percolator_v" + VERSION_SUBSTITUTION, null, "percolator", "1.04", null));
            assertEquals(homeDir + File.separator + "percolator", impl.getExecutablePath("percolator", null, "percolator", "1.04", null));

            props.setSoftwarePackages(Collections.singletonMap("percolator", "percolator_v" + VERSION_SUBSTITUTION));
            try
            {
                impl.getExecutablePath("percolator", null, "percolator", "1.04", null);
            }
            catch (FileNotFoundException e)
            {
                assertTrue("Message contains expected path", e.getMessage().contains(homeDir + File.separator + "percolator_v.1.04" + File.separator + "percolator"));
                assertTrue("Message contains correct error", e.getMessage().contains("Parent directory does not exist."));
            }

            impl.setPrependVersionWithDot(false);
            props.setSoftwarePackages(Collections.<String, String>emptyMap());
            assertEquals(homeDir + File.separator + "percolator_v1.04", impl.getExecutablePath("percolator_v" + VERSION_SUBSTITUTION, null, "percolator", "1.04", null));
        }
    }
}
