/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.study.StudyFolderTabs;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyJobSupport;
import org.labkey.study.model.StudyImpl;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Mar 14, 2006
 * Time: 5:04:38 PM
 */

// Pipeline job used for importing individual specimen archives (not as part of a study).
public class SpecimenBatch extends StudyBatch implements Serializable, StudyJobSupport
{
    private boolean _isMerge;

    public static final FileType ARCHIVE_FILE_TYPE = new FileType(".specimens");

    public SpecimenBatch(ViewBackgroundInfo info, File definitionFile, PipeRoot root, boolean merge) throws SQLException
    {
        super(info, definitionFile, root);
        _isMerge = merge;
    }

    protected File createLogFile()
    {
        return StudyPipeline.logForInputFile(_definitionFile, getPipeRoot());
    }

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
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getInfo().getContainer(), StudyFolderTabs.SpecimensPage.PAGE_ID);
    }

    public File getSpecimenArchive()
    {
        return _definitionFile;
    }

    public boolean isMerge()
    {
        return _isMerge;
    }

    @Override
    public StudyImportContext getImportContext()
    {
        return new StudyImportContext(getUser(), getContainer(), null, new PipelineJobLoggerGetter(this));
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(SpecimenBatch.class));
    }

    @Override
    public StudyImpl getStudy()
    {
        return getStudy(false);
    }

    @Override
    public StudyImpl getStudy(boolean allowNullStudy)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getOriginalFilename()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public VirtualFile getRoot()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public BindException getSpringErrors()
    {
        throw new UnsupportedOperationException();
    }
}