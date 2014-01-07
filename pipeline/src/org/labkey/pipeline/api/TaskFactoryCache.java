package org.labkey.pipeline.api;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceCache;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.pipeline.xml.TaskDocument;
import org.labkey.pipeline.xml.TaskType;

import java.io.IOException;

/**
 * User: kevink
 * Date: 1/6/14
 */
/* package */ class TaskFactoryCache extends ModuleResourceCache<TaskFactory>
{
    private static final Logger LOG = Logger.getLogger(TaskFactoryCache.class);
    private static final String TASK_CONFIG_EXTENSION = ".task.xml";
    private static final String MODULE_TASKS_DIR = "tasks";

    private static final TaskFactoryCache _instance = new TaskFactoryCache();

    public static TaskFactoryCache get()
    {
        return _instance;
    }

    private TaskFactoryCache()
    {
        super(new Path(PipelineJobServiceImpl.MODULE_PIPELINE_DIR, MODULE_TASKS_DIR), "TaskFactory cache");
    }


    @Nullable
    @Override
    protected FileSystemDirectoryListener createChainedDirectoryListener(Module module)
    {
        return null;
    }

    @Override
    protected boolean isResourceFile(String filename)
    {
        return filename.endsWith(TASK_CONFIG_EXTENSION) && filename.length() > TASK_CONFIG_EXTENSION.length();
    }

    @Override
    protected String getResourceName(Module module, String filename)
    {
        String taskName = filename.substring(0, filename.length() - TASK_CONFIG_EXTENSION.length());
        TaskId taskId = createId(module, taskName);
        return taskId.toString();
    }

    protected TaskId createId(Module module, String resourceName)
    {
        return new TaskId(module.getName(), TaskId.Type.task, resourceName, 0);
    }

    protected TaskId parseId(Module module, String resourceName)
    {
        try
        {
            TaskId taskId = TaskId.valueOf(resourceName);
            assert taskId.getModuleName().equals(module.getName());
            assert taskId.getType().equals(TaskId.Type.task);
            return taskId;
        }
        catch (ClassNotFoundException e)
        {
            // shouldn't happen since we're not managing tasks with class names
            return null;
        }
    }

    @Override
    protected String createCacheKey(Module module, String resourceName)
    {
        TaskId taskId = parseId(module, resourceName);
        return taskId.toString();
    }

    @Override
    protected CacheLoader<String, TaskFactory> getResourceLoader()
    {
        return new CacheLoader<String, TaskFactory>()
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
                    LOG.warn(e);
                    return null;
                }

                // Look for a module task config file
                if (taskId.getName() != null && taskId.getModuleName() != null)
                {
                    Module module = ModuleLoader.getInstance().getModule(taskId.getModuleName());
                    String configFileName = taskId.getName() + TASK_CONFIG_EXTENSION;

                    Path tasksDirPath = new Path(PipelineJobServiceImpl.MODULE_PIPELINE_DIR, MODULE_TASKS_DIR);

                    // Look for a "pipeline/tasks/<name>.task.xml" file
                    Path taskConfigPath = tasksDirPath.append(configFileName);
                    Resource taskConfig = module.getModuleResource(taskConfigPath);
                    if (taskConfig != null && taskConfig.isFile())
                        return load(taskId, taskConfig);
                }

                return null;
            }

            private TaskFactory load(TaskId taskId, Resource taskConfig)
            {
                try
                {
                    return create(taskId, taskConfig);
                }
                catch (IllegalArgumentException|IllegalStateException e)
                {
                    LOG.warn("Error registering '" + taskId + "' task: " + e.getMessage());
                    return null;
                }
            }

            private TaskFactory create(TaskId taskId, Resource taskConfig)
            {
                if (taskId.getName() == null)
                    throw new IllegalArgumentException("Task factory must by named");

                if (taskId.getType() != TaskId.Type.task)
                    throw new IllegalArgumentException("Task factory must by of type 'task'");

                TaskDocument doc;
                try
                {
                    XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
                    doc = TaskDocument.Factory.parse(taskConfig.getInputStream(), options);
                    XmlBeansUtil.validateXmlDocument(doc, "Task factory config '" + taskConfig.getPath() + "'");
                }
                catch (XmlValidationException e)
                {
                    LOG.error(e);
                    return null;
                }
                catch (XmlException |IOException e)
                {
                    LOG.error("Error loading task factory '" + taskConfig.getPath() + "':\n" + e.getMessage());
                    return null;
                }

                TaskType xtask = doc.getTask();
                if (xtask == null)
                    throw new IllegalArgumentException("task element required");

                if (!taskId.getName().equals(xtask.getName()))
                    throw new IllegalArgumentException(String.format("Task factory config must have the name '%s'", taskId.getName()));

                Path taskDir = taskConfig.getPath().getParent();
                return create(taskId, xtask, taskDir);
            }

            private TaskFactory create(TaskId taskId, TaskType xtask, Path taskDir)
            {
                return PipelineJobServiceImpl.get().createTaskFactory(taskId, xtask, taskDir);
            }

        };

    }

}
