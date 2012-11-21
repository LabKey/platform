/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.labkey.api.pipeline.*;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.URIUtil;
import org.labkey.pipeline.api.properties.ApplicationPropertiesImpl;
import org.labkey.pipeline.api.properties.ConfigPropertiesImpl;
import org.labkey.pipeline.api.properties.GlobusClientPropertiesImpl;
import org.labkey.pipeline.cluster.NoOpPipelineStatusWriter;
import org.labkey.pipeline.mule.JMSStatusWriter;
import org.labkey.pipeline.xstream.PathMapperImpl;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>PipelineJobServiceImpl</code>
 *
 * @author brendanx
 */
public class PipelineJobServiceImpl extends PipelineJobService
{
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
            new HashMap<TaskId, TaskPipeline>();
    private HashMap<TaskId, TaskFactory> _taskFactoryStore =
            new HashMap<TaskId, TaskFactory>();

    private String _defaultExecutionLocation = TaskFactory.WEBSERVER;
    private int _defaultAutoRetry = 0;

    private boolean _prependVersionWithDot = true;
    private ApplicationProperties _appProperties;
    private ConfigProperties _configProperties;
    private RemoteServerProperties _remoteServerProperties;
    private List<GlobusClientPropertiesImpl> _globusClientProperties = new ArrayList<GlobusClientPropertiesImpl>();
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
        synchronized (_taskPipelineStore)
        {
            return _taskPipelineStore.get(id);
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
            _taskPipelineStore.put(pipeline.getId(), pipeline);
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

    public <T extends TaskPipeline> T[] getTaskPipelines(Class<T> inter)
    {
        ArrayList<T> pipelineList = new ArrayList<T>();
        for (TaskPipeline tp : _taskPipelineStore.values())
        {
            if (inter.isInstance(tp))
                pipelineList.add((T) tp);
        }
        return pipelineList.toArray((T[]) Array.newInstance(inter, 0));
    }

    public TaskFactory getTaskFactory(TaskId id)
    {
        synchronized (_taskFactoryStore)
        {
            return _taskFactoryStore.get(id);
        }
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
        synchronized (_taskFactoryStore)
        {
            _taskFactoryStore.put(factory.getId(), factory);
        }
    }

    public TaskFactory[] getTaskFactories()
    {
        synchronized (_taskFactoryStore)
        {
            return _taskFactoryStore.values().toArray(new TaskFactory[_taskFactoryStore.values().size()]);
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
