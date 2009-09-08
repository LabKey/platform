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

import org.labkey.api.pipeline.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.study.StudyImportException;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.StudyPipeline;
import org.springframework.validation.BindException;
import org.apache.log4j.Logger;

import java.io.File;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob implements StudyJobSupport
{
    private static final Logger LOG = Logger.getLogger(StudyImportJob.class);

    private final ImportContext _ctx;
    private final File _root;
    private final BindException _errors;
    private final boolean _reload;

    // Handles all four study import tasks: initial task, dataset import, specimen import, and final task
    public StudyImportJob(Container c, User user, ActionURL url, File studyXml, BindException errors)
    {
        super(null, new ViewBackgroundInfo(c, user, url));

        _root = studyXml.getParentFile();
        setLogFile(StudyPipeline.logForInputFile(new File(_root, "study_load")));
        _errors = errors;
        _ctx = new ImportContext(user, c, studyXml, url, getLogger());

        StudyImpl study = getStudy(true);
        _reload = (null != study);

        LOG.info("Pipeline job initialized for " + (_reload ? "reloading" : "importing") + " study " + (_reload ? "\"" + study.getLabel() + "\" " : "") + "to folder " + c.getPath());
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
        return "Study " + (_reload ? "reload" : "import");
    }

    public static File getStudyFile(File root, File dir, String name, String source) throws StudyImportException
    {
        File file = new File(dir, name);

        if (!file.exists())
            throw new StudyImportException(source + " refers to a file that does not exist: " + StudyImportException.getRelativePath(root, file));

        if (!file.isFile())
            throw new StudyImportException(source + " refers to " + StudyImportException.getRelativePath(root, file) + ": expected a file but found a directory");

        return file;
    }
}
