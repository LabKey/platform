/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudyModule;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyPipeline;
import org.springframework.validation.BindException;

import java.nio.file.Path;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob implements StudyJobSupport, StudyImporter
{
    private static final transient Logger LOG = LogManager.getLogger(StudyImportJob.class);

    private StudyImportContext _ctx;
    private VirtualFile _root;
    private final BindException _errors;          // TODO: do we need to save error messages
    private final boolean _reload;
    private final String _originalFilename;

    @JsonCreator
    protected StudyImportJob(@JsonProperty("_ctx") StudyImportContext ctx, @JsonProperty("_root") VirtualFile root,
                             @JsonProperty("_errors") NullSafeBindException errors, @JsonProperty("_reload") boolean reload,
                             @JsonProperty("_originalFilename") String originalFilename)
    {
        super();
        _ctx = ctx;
        _root = root;
        _errors = errors;
        _reload = reload;
        _originalFilename = originalFilename;
        _ctx.setLoggerGetter(new PipelineJobLoggerGetter(this));
    }

    // Handles all four study import tasks: initial task, dataset import, specimen import, and final task
    public StudyImportJob(Container c, User user, ActionURL url, Path studyXml, String originalFilename, BindException errors, PipeRoot pipeRoot, ImportOptions options)
    {
        super("StudyImport", new ViewBackgroundInfo(c, user, url), pipeRoot);
        _originalFilename = originalFilename;
        String baseLogFileName = StudyPipeline.getLogFilename(studyXml.getParent().resolve("study_load"));
        setupLocalDirectoryAndJobLog(pipeRoot, StudyModule.MODULE_NAME, baseLogFileName);
        _errors = errors;

        Path importRoot = studyXml.getParent();
        _root = new FileSystemFile(importRoot);
        _ctx = generateImportContext(user, c, studyXml, options, _root);

        StudyImpl study = getStudy(true);
        _reload = (null != study);

        LOG.info("Pipeline job initialized for " + (_reload ? "reloading" : "importing") + " study " + (_reload ? "\"" + study.getLabel() + "\" " : "") + "to folder " + c.getPath());

        for (String message : options.getMessages())
            _ctx.getLogger().info(message);
    }

    private static ImportOptions cloneImportOptions(StudyImportContext ctx)
    {
        ImportOptions newOptions = new ImportOptions(ctx.getContainer().getId(), ctx.getUser().getUserId());

        newOptions.setSkipQueryValidation(ctx.isSkipQueryValidation());
        newOptions.setCreateSharedDatasets(ctx.isCreateSharedDatasets());
        newOptions.setFailForUndefinedVisits(ctx.isFailForUndefinedVisits());
        newOptions.setIncludeSubfolders(ctx.isIncludeSubfolders());
        newOptions.setActivity(ctx.getActivity());

        return newOptions;
    }

    private StudyImportContext generateImportContext(User user, Container c, Path studyXml, ImportOptions options, VirtualFile root)
    {
        StudyImportContext context = new StudyImportContext.Builder(user, c)
                .withStudyXml(studyXml)
                .withDataTypes(options.getDataTypes())
                .withLogger(new PipelineJobLoggerGetter(this))
                .withRoot(root)
                .build();
        context.setSkipQueryValidation(options.isSkipQueryValidation());
        context.setCreateSharedDatasets(options.isCreateSharedDatasets());
        context.setFailForUndefinedVisits(options.isFailForUndefinedVisits());
        context.setIncludeSubfolders(options.isIncludeSubfolders());
        context.setActivity(options.getActivity());

        return context;
    }

    @Override
    public void updateWorkingRoot(Path newRoot)
    {
        VirtualFile vfRoot = new FileSystemFile(newRoot);
        _ctx = generateImportContext(getUser(), getContainer(), newRoot.resolve(getOriginalFilename()), cloneImportOptions(_ctx), vfRoot);
        _root = vfRoot;
    }

    @Override
    public StudyImpl getStudy()
    {
        return getStudy(false);
    }

    @Override
    public StudyImpl getStudy(boolean allowNullStudy)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(_ctx.getContainer());
        if (!allowNullStudy && study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
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
        return _originalFilename;
    }

    @Override
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

    @Override
    public ActionURL getStatusHref()
    {
        return BaseStudyController.getStudyOverviewURL(getInfo().getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Study " + (_reload ? "reload" : "import");
    }
}
