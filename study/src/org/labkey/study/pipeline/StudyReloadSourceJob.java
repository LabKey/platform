/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.ImportException;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyImporter;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.Serializable;
import java.sql.SQLException;

/**
 * Created by klum on 2/9/2015.
 */
public class StudyReloadSourceJob extends StudyBatch implements Serializable, StudyReloadSourceJobSupport, StudyImporter
{
    private final StudyImportContext _ctx;
    private final VirtualFile _root;
    private String _reloadSourceName;
    private BindException _errors;

    public StudyReloadSourceJob(ViewBackgroundInfo info, PipeRoot root, String reloadSourceName) throws SQLException
    {
        super(info, null, root);
        _reloadSourceName = reloadSourceName;
        _root = new FileSystemFile(root.getRootPath());
        File studyXml = new File(root.getRootPath(), "study.xml");

        _ctx = new StudyImportContext(info.getUser(), info.getContainer(), studyXml, null, new PipelineJobLoggerGetter(this), _root);
        _ctx.setSkipQueryValidation(true);
        _errors = new NullSafeBindException(new Object(), "reloadSource");

        File logFile = new File(root.getRootPath(), FileUtil.makeFileNameWithTimestamp("study_reload_source", "log"));
        setLogFile(logFile);
    }

    protected File createLogFile()
    {
        return null;
    }

    @Override
    public String getStudyReloadSource()
    {
        return _reloadSourceName;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(StudyReloadSourceJob.class));
    }

    @Override
    public StudyImpl getStudy()
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_ctx.getContainer());
        if (study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
    }

    @Override
    public StudyImpl getStudy(boolean allowNullStudy)
    {
        return getStudy();
    }

    @Override
    public StudyImportContext getImportContext()
    {
        return _ctx;
    }

    @Override
    public VirtualFile getRoot()
    {
        return _root;
    }

    @Override
    public String getOriginalFilename()
    {
        return "study.xml";
    }

    @Override
    public BindException getSpringErrors()
    {
        return _errors;
    }

    @Override
    public File getSpecimenArchive() throws ImportException, SQLException
    {
        return _ctx.getSpecimenArchive(_root);
    }

    @Override
    public boolean isMerge()
    {
        return false;
    }
}
