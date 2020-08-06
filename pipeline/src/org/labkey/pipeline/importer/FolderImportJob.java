/*
 * Copyright (c) 2012-2018 LabKey Corporation
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
package org.labkey.pipeline.importer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.PipelineJobLoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;

import java.io.File;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderImportJob extends PipelineJob implements FolderJobSupport
{
    private static final Logger LOG = LogManager.getLogger(FolderImportJob.class);

    private final FolderImportContext _ctx;
    private final VirtualFile _root;
    private final String _originalFilename;

    @JsonCreator
    protected FolderImportJob(@JsonProperty("_ctx") FolderImportContext ctx, @JsonProperty("_root") VirtualFile root, @JsonProperty("_originalFilename") String originalFilename)
    {
        _ctx = ctx;
        _root = root;
        _originalFilename = originalFilename;
        _ctx.setLoggerGetter(new PipelineJobLoggerGetter(this));
    }

    public FolderImportJob(Container c, User user, ActionURL url, File folderXml, String originalFilename, PipeRoot pipeRoot, ImportOptions options)
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _root = new FileSystemFile(folderXml.getParentFile());
        _originalFilename = originalFilename;
        setLogFile(FolderImportProvider.logForInputFile(new File("folder_load"), getPipeRoot()));
        _ctx = new FolderImportContext(user, c, folderXml, options.getDataTypes(), new PipelineJobLoggerGetter(this), _root);
        _ctx.setSkipQueryValidation(options.isSkipQueryValidation());
        _ctx.setCreateSharedDatasets(options.isCreateSharedDatasets());
        _ctx.setFailForUndefinedVisits(options.isFailForUndefinedVisits());
        _ctx.setIncludeSubfolders(options.isIncludeSubfolders());
        _ctx.setActivity(options.getActivity());

        LOG.info("Pipeline job initialized for importing folder properties to folder " + c.getPath());
    }

    @Override
    public FolderImportContext getImportContext()
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
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(FolderImportJob.class));
    }

    @Override
    public URLHelper getStatusHref()
    {
        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(getInfo().getContainer());
    }

    @Override
    public String getDescription()
    {
        return "Folder import";
    }
}