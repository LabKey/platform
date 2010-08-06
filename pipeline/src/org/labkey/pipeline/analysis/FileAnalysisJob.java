/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.pipeline.analysis;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;

/**
 * <code>FileAnalysisJob</code>
 */
public class FileAnalysisJob extends AbstractFileAnalysisJob
{
    private TaskId _taskPipelineId;

    public FileAnalysisJob(FileAnalysisProtocol protocol,
                           String providerName,
                           ViewBackgroundInfo info,
                           PipeRoot root,
                           TaskId taskPipelineId,
                           String protocolName,
                           File fileParameters,
                           File filesInput[]
    )
            throws IOException
    {
        super(protocol, providerName, info, root, protocolName, fileParameters, filesInput);

        _taskPipelineId = taskPipelineId;
    }

    public FileAnalysisJob(FileAnalysisJob job, File fileInput)
    {
        super(job, fileInput);

        _taskPipelineId = job._taskPipelineId;
    }

    public TaskId getTaskPipelineId()
    {
        return _taskPipelineId;
    }

    public AbstractFileAnalysisJob createSingleFileJob(File file)
    {
        return new FileAnalysisJob(this, file);
    }

    public FileAnalysisTaskPipeline getTaskPipeline()
    {
        TaskPipeline tp = super.getTaskPipeline();

        assert tp != null : "Task pipeline " + _taskPipelineId + " not found.";
        
        return (FileAnalysisTaskPipeline) tp; 
    }

    public File findInputFile(String name)
    {
        return getTaskPipeline().findInputFile(this, name);
    }

    public File findOutputFile(String name)
    {
        return getTaskPipeline().findOutputFile(this, name); 
    }
}
