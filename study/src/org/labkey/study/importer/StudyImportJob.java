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
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.Study;

import java.io.File;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob
{
    private final Study _study;
    private final ImportContext _ctx;
    private final File _root;

    // Note: At the moment, this just updates manual cohorts, which must be done after dataset and specimen uploads.
    // It could be extended to do other end-of-import tasks and/or wrap the dataset load, specimen load, and final
    // tasks in a single job.
    public StudyImportJob(Study study, ImportContext ctx, File root)
    {
        super(null, new ViewBackgroundInfo(ctx.getContainer(), ctx.getUser(), ctx.getUrl()));
        _study = study;
        _ctx = ctx;
        _root = root;
        setLogFile(new File(root, "study_load.log"));
    }

    public void run()
    {
        try
        {
            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            new CohortImporter().process(_study, _ctx, _root);
        }
        catch (Exception e)
        {
            error("Exception setting manual cohorts", e);
        }

        try
        {
            new QueryImporter().process(_ctx, _root);
        }
        catch (Exception e)
        {
            error("Exception importing queries", e);
        }

        try
        {
            new ReportImporter().process(_ctx, _root);
        }
        catch (Exception e)
        {
            error("Exception importing reports", e);
        }
    }

    public ActionURL getStatusHref()
    {
        return new ActionURL(StudyController.OverviewAction.class, getInfo().getContainer());
    }

    public String getDescription()
    {
        return "Study Import Finalization";
    }
}
