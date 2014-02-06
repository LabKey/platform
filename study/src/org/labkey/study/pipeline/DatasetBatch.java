/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.VirtualFile;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * User: Matthew
 * Date: Jan 12, 2006
 * Time: 1:16:44 PM
 */
public class DatasetBatch extends StudyBatch implements Serializable, DatasetJobSupport
{
    private VirtualFile _datasetsDirectory;
    private String _datasetsFileName;

    public DatasetBatch(ViewBackgroundInfo info, VirtualFile datasetsDirectory, String datasetsFileName, PipeRoot root) throws SQLException
    {
        // TODO, convert StudyBatch to use virtual files instead of passing it a definitionFile
        super(info, null, root);

        _datasetsDirectory = datasetsDirectory;
        _datasetsFileName = datasetsFileName;
    }

    protected File createLogFile()
    {
        Path logFilePath = Path.parse(_datasetsDirectory.getLocation()).append(_datasetsFileName);
        return new File(logFilePath.getParent().toString(), FileUtil.makeFileNameWithTimestamp(logFilePath.getName(), "log"));
    }

    @Override
    public String getDescription()
    {
        String description = "Import datasets";
        if (_definitionFile != null)
            description += ": " + _definitionFile.getName();
        return description;
    }

    @Override
    public VirtualFile getDatasetsDirectory()
    {
        return _datasetsDirectory;
    }

    @Override
    public String getDatasetsFileName()
    {
        return _datasetsFileName;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(DatasetBatch.class));
    }
}
