/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.specimen.pipeline;

import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.specimen.SpecimensPage;
import org.labkey.api.specimen.pipeline.SpecimenJobSupport;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.pipeline.StudyBatch;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;

/**
 * User: brittp
 * Date: Mar 14, 2006
 * Time: 5:04:38 PM
 */

// Pipeline job used for importing individual specimen archives (not as part of a study).
public class SpecimenBatch extends StudyBatch implements Serializable, SpecimenJobSupport
{
    private boolean _isMerge;

    // For serialization
    protected SpecimenBatch() {}

    public SpecimenBatch(ViewBackgroundInfo info, File definitionFile, PipeRoot root, boolean merge)
    {
        super(info, definitionFile, root);
        _isMerge = merge;
    }

    @Override
    protected File createLogFile()
    {
        return new File(getPipeRoot().getLogDirectory(), FileUtil.makeFileNameWithTimestamp(_definitionFile.getName(), "log"));
    }

    @Override
    public String getDescription()
    {
        String description = "Import specimens";
        if (_definitionFile != null)
            description += ": " + _definitionFile.getName();
        return description;
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getInfo().getContainer(), SpecimensPage.PAGE_ID);
    }

    @Override
    public File getSpecimenArchive()
    {
        return _definitionFile;
    }

    @Override
    public Path getSpecimenArchivePath()
    {
        return _definitionFile.toPath();
    }

    @Override
    public boolean isMerge()
    {
        return _isMerge;
    }

    @Override
    public SimpleStudyImportContext getImportContext()
    {
        return new SimpleStudyImportContext(getUser(), getContainer(), null, null, new PipelineJobLoggerGetter(this), null);
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(SpecimenBatch.class));
    }
}
