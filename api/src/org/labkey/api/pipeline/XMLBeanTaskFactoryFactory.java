package org.labkey.api.pipeline;

import org.labkey.api.util.Path;
import org.labkey.pipeline.xml.TaskType;

/**
 * Creates TaskFactory from a task xml instance.
 */
public interface XMLBeanTaskFactoryFactory
{
    TaskFactory create(TaskId taskId, TaskType xtask, Path taskDir);
}
