/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.pipeline.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * <code>PipelineJobServiceImpl</code>
 *
 * @author brendanx
 */
public class PipelineJobServiceImpl extends PipelineJobService
{
    private static Logger _log = Logger.getLogger(PipelineJobServiceImpl.class);

    static PipelineJobServiceImpl getInternal()
    {
        return (PipelineJobServiceImpl) PipelineJobService.get();
    }

    private final HashMap<TaskId, TaskPipeline> _taskPipelineStore =
            new HashMap<TaskId, TaskPipeline>();
    private final HashMap<TaskId, TaskFactory> _taskFactoryStore =
            new HashMap<TaskId, TaskFactory>();

    private TaskFactory.ExecutionLocation _defaultExecutionLocation =
                    TaskFactory.ExecutionLocation.local;

    private ApplicationProperties _appProperties;
    
    private PipelineStatusFile.StatusWriter _statusWriter;
    private PipelineStatusFile.JobStore _jobStore;

    private WorkDirFactory _workDirFactory;

    public PipelineJobServiceImpl()
    {
        // Allow Mule/Spring configuration, but keep any current defaults
        // set by the LabKey server.
        PipelineJobServiceImpl current = getInternal();
        if (current != null)
        {
            _taskPipelineStore.putAll(current._taskPipelineStore);
            _taskFactoryStore.putAll(current._taskFactoryStore);
            _defaultExecutionLocation = current._defaultExecutionLocation;
            _statusWriter = current._statusWriter;
            _workDirFactory = current._workDirFactory;
        }

        setInstance(this);
    }

    /**
     * Used to getInternal a TaskPipeline by name.  If the pipeline has been registered using
     * Mule's Spring configuration, than that version will be used.  Otherwise, the default
     * passed in version gets used.
     *
     * @param id An enum ID that uniquely identifies the pipeline
     * @return the difinitive TaskPipeline for this id
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
        TaskPipeline pipeline = getTaskPipeline(settings.getCloneId());
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
            throw new IllegalArgumentException("Base implementation " + settings.getCloneId() + " not found.");

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

    public TaskFactory.ExecutionLocation getDefaultExecutionLocation()
    {
        return _defaultExecutionLocation;
    }

    public void setDefaultExecutionLocation(TaskFactory.ExecutionLocation defaultExecutionLocation)
    {
        _defaultExecutionLocation = defaultExecutionLocation;
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

    public ApplicationProperties getAppProperties()
    {
        return _appProperties;
    }

    public void setAppProperties(ApplicationProperties appProperties)
    {
        _appProperties = appProperties;
    }

    public String getJarPath(String jarName) throws FileNotFoundException
    {
        String toolsDirPath = getAppProperties().getToolsDirectory();
        if (toolsDirPath == null || toolsDirPath.length() == 0)
        {
            throw new FileNotFoundException("Failed to locate " + jarName + ".  " +
                "Pipeline tools directory is not set.  " +
                "Use the site settings page to specify a directory.");
        }
        File jarFile = new File(new File(toolsDirPath), jarName);
        if (!jarFile.exists())
        {
            if (!jarFile.getParentFile().exists())
            {
                throw new FileNotFoundException("File not found " + jarFile + ".  " +
                    "Pipeline tools directory does not exist.  " +
                    "Use the site settings page to specify an existing directory.");
            }
            else
            {
                throw new FileNotFoundException("File not found " + jarFile + ".  " +
                    "Add this jar file to the pipeline tools directory.");                
            }
        }
        return jarFile.toString();
    }

    public String getJavaPath() throws FileNotFoundException
    {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null || javaHome.length() == 0)
        {
            throw new FileNotFoundException("Failed to locate Java.  " +
                "Please set JAVA_HOME environment variable.");
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

    public static PipelineJobServiceImpl initDefaults()
    {
        PipelineJobServiceImpl pjs = new PipelineJobServiceImpl();
        pjs.setJobStore(new PipelineJobStoreImpl());
        pjs.setWorkDirFactory(new WorkDirectoryLocal.Factory());
        PipelineJobService.setInstance(pjs);
        return pjs;
    }
}
