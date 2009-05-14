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

import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.CohortManager;
import org.labkey.study.model.Study;

import java.sql.SQLException;
import java.util.Map;
import java.io.File;

/**
 * User: adam
 * Date: May 14, 2009
 * Time: 9:39:39 AM
 */
public class StudyImportJob extends PipelineJob
{
    private final Study _study;
    private final Map<String, Integer> _p2c;

    // Note: At the moment, this just updates manual cohorts, which must be done after dataset and specimen uploads.
    // It could be extended to do other end-of-import tasks and/or wrap the dataset load, specimen load, and final
    // tasks in a single job.
    public StudyImportJob(Study study, Map<String, Integer> p2c, Container c, User user, ActionURL url, File root)
    {
        super(null, new ViewBackgroundInfo(c, user, url));
        _study = study;
        _p2c = p2c;
        setLogFile(new File(root, "study_load.log"));
    }

    public void run()
    {
        try
        {
            CohortManager.updateManualCohortAssignment(_study, getUser(), _p2c);
        }
        catch (SQLException e)
        {
            error("Exception setting manual cohorts", e);
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
