package org.labkey.pipeline.api;

import edu.emory.mathcs.backport.java.util.Collections;
import org.apache.log4j.Logger;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.GlobusSettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.FileType;

import java.io.IOException;
import java.util.List;

/**
 * User: kevink
 * Date: 11/8/13
 *
 * Creates TaskFactory from a task.xml config file.
 */
public class TaskFactoryResource
{
    public static TaskFactory create(TaskId taskId, Resource taskConfig)
    {
        // Dummp task factory for testing loading
        return new AbstractTaskFactory(taskId)
        {
            @Override
            public PipelineJob.Task createTask(PipelineJob job)
            {
                return null;
            }

            @Override
            public TaskFactory cloneAndConfigure(TaskFactorySettings settings) throws CloneNotSupportedException
            {
                return null;
            }

            @Override
            public List<FileType> getInputTypes()
            {
                return Collections.<FileType>emptyList();
            }

            @Override
            public List<String> getProtocolActionNames()
            {
                return Collections.<String>emptyList();
            }

            @Override
            public String getStatusName()
            {
                return "FAKE";
            }

            @Override
            public boolean isJobComplete(PipelineJob job)
            {
                return false;
            }
        };
    }
}
