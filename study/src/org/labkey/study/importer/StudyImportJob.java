/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyPipeline;
import org.springframework.validation.BindException;

import java.io.File;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob implements StudyJobSupport
{
    private final ImportContext _ctx;
    private final File _root;
    private final BindException _errors;

    // Job that handles all tasks that depend on the completion of previous pipeline import jobs (e.g., datasets & specimens).
    public StudyImportJob(ImportContext ctx, File root, BindException errors)
    {
        super(null, new ViewBackgroundInfo(ctx.getContainer(), ctx.getUser(), ctx.getUrl()));
        _ctx = ctx;
        _root = root;
        _errors = errors;
        setLogFile(StudyPipeline.logForInputFile(new File(root, "study_load")));
        ctx.setLogger(getLogger());
    }

    public StudyImpl getStudy()
    {
        return getStudy(false);
    }

    public StudyImpl getStudy(boolean allowNullStudy)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_ctx.getContainer());
        if (!allowNullStudy && study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
    }

    public ImportContext getImportContext()
    {
        return _ctx;
    }

    public File getRoot()
    {
        return _root;
    }

    @Deprecated
    public BindException getSpringErrors()
    {
        return _errors;
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(StudyImportJob.class));
    }

    public ActionURL getStatusHref()
    {
        return new ActionURL(StudyController.OverviewAction.class, getInfo().getContainer());
    }

    public String getDescription()
    {
        return "Study import";   // TODO: Reload?
    }
}
