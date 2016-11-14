/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.study;

import org.apache.log4j.Logger;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.VisitManager;

/**
 * User: jeckels
 * Date: 7/27/11
 */
public class PurgeParticipantsMaintenanceTask implements MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Purge unused participants";
    }

    @Override
    public String getName()
    {
        return "PurgeStudyParticipants";
    }

    @Override
    public void run(Logger log)
    {
        for (StudyImpl study : StudyManager.getInstance().getAllStudies())
        {
            VisitManager.performParticipantPurge(study, null);
        }
    }
}
