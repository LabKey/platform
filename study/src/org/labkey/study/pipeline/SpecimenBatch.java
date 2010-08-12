/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Mar 14, 2006
 * Time: 5:04:38 PM
 */

// Pipeline job used for importing individual specimen archives (not as part of a study).
public class SpecimenBatch extends StudyBatch implements Serializable, SpecimenJobSupport
{
    public SpecimenBatch(ViewBackgroundInfo info, File definitionFile, PipeRoot root) throws SQLException
    {
        super(info, definitionFile, root);
    }

    public String getDescription()
    {
        String description = "Import specimens";
        if (_definitionFile != null)
            description += ": " + _definitionFile.getName();
        return description;
    }

    public File getSpecimenArchive()
    {
        return _definitionFile;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(SpecimenBatch.class));
    }
}