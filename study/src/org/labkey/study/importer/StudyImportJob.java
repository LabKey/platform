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
import org.labkey.api.study.ExternalStudyImporter;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.writer.StudySerializationRegistryImpl;

import java.io.File;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob
{
    private final StudyImpl _study;
    private final ImportContext _ctx;
    private final File _root;

    // Job that handles all tasks that depend on the completion of previous pipeline import jobs (e.g., datasets & specimens).
    public StudyImportJob(StudyImpl study, ImportContext ctx, File root)
    {
        super(null, new ViewBackgroundInfo(ctx.getContainer(), ctx.getUser(), ctx.getUrl()));
        _study = study;
        _ctx = ctx;
        _root = root;
        setLogFile(StudyPipeline.logForInputFile(new File(root, "study_load")));
    }

    public void run()
    {
        boolean success = false;

        try
        {
            // Dataset and Specimen upload jobs delete "unused" participants, so we need to defer setting participant
            // cohorts until the end of upload.
            setStatus("IMPORT cohort settings");
            info("Importing cohort settings");
            new CohortImporter().process(_study, _ctx, _root);
            info("Done importing cohort settings");

            // Can't assign visits to cohorts until the cohorts are created
            setStatus("IMPORT visit map cohort assignments");
            info("Importing visit map cohort assignments");
            new VisitCohortAssigner().process(_study, _ctx, _root);
            info("Done importing visit map cohort assignments");

            // Can't assign datasets to cohorts until the cohorts are created
            setStatus("IMPORT dataset cohort assignments");
            info("Importing dataset cohort assignments");
            new DatasetCohortAssigner().process(_study, _ctx, _root);
            info("Done importing dataset cohort assignments");

            for (ExternalStudyImporter importer : StudySerializationRegistryImpl.get().getRegisteredStudyImporters())
            {
                info("Importing " + importer.getDescription());
                setStatus("IMPORT " + importer.getDescription());
                importer.process(_ctx, _root);
                info("Done importing " + importer.getDescription());
            }

            success = true;
        }
        catch (Exception e)
        {
            error("Exception during study import", e);
        }
        finally
        {
            setStatus(success ? PipelineJob.COMPLETE_STATUS : PipelineJob.ERROR_STATUS);
        }
    }

    public ActionURL getStatusHref()
    {
        return new ActionURL(StudyController.OverviewAction.class, getInfo().getContainer());
    }

    public String getDescription()
    {
        return "Finalize study import";
    }
}
