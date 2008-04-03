/*
 * Copyright (c) 2008 LabKey Software Foundation
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

import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.AbstractFileAnalysisJob;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisXarGeneratorSupport;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * <code>FileAnalysisJob</code>
 */
public class FileAnalysisJob extends AbstractFileAnalysisJob
{
    private TaskId _taskPipelineId;

    public FileAnalysisJob(FileAnalysisProtocol protocol,
                           String providerName,                           
                           ViewBackgroundInfo info,
                           TaskId taskPipelineId,
                           String protocolName,
                           File fileParameters,
                           File filesInput[],
                           boolean append)
            throws SQLException, IOException
    {
        super(protocol, providerName, info, protocolName, fileParameters, filesInput, append);

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
        return (FileAnalysisTaskPipeline) super.getTaskPipeline();
    }

    public File findInputFile(String name)
    {
        return getTaskPipeline().findInputFile(this, name);
    }

    public File findOutputFile(String name)
    {
        return getTaskPipeline().findOutputFile(this, name); 
    }

    public String getXarTemplateResource()
    {
        FileAnalysisXarGeneratorSupport support = getTaskPipeline().getXarGeneratorSupport();
        if (support == null)
            return null;
        return support.getXarTemplateResource(this);
    }

    public Map<String, String> getXarTemplateReplacements() throws IOException
    {
        FileAnalysisXarGeneratorSupport support = getTaskPipeline().getXarGeneratorSupport();
        if (support == null)
            return new HashMap<String, String>();
        return support.getXarTemplateReplacements(this);
    }
}
