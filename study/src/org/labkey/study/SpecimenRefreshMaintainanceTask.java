/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.study.importer.SpecimenRefreshPipelineJob;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot;
import org.labkey.study.model.StudySnapshot.SnapshotSettings;

import java.util.Collection;

/**
 * User: adam
 * Date: 9/22/12
 * Time: 12:11 PM
 */
public class SpecimenRefreshMaintainanceTask implements MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Refresh study snapshot specimen data";
    }

    @Override
    public String getName()
    {
        return "RefreshSpecimens";
    }

    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public void run(Logger log)
    {
        Collection<StudySnapshot> snapshots = StudyManager.getInstance().getRefreshStudySnapshots();

        for (StudySnapshot snapshot : snapshots)
        {
            StudyImpl sourceStudy = getStudy(snapshot.getSource());

            if (null == sourceStudy)
                continue;

            StudyImpl destinationStudy = getStudy(snapshot.getDestination());

            if (null == destinationStudy)
                continue;

            // If null, specimens definitely haven't been updated since the snapshot
            if (null == sourceStudy.getLastSpecimenLoad())
                continue;

            if (null == destinationStudy.getLastSpecimenLoad() ||
                sourceStudy.getLastSpecimenLoad().compareTo(destinationStudy.getLastSpecimenLoad()) > 0)
            {
                try
                {
                    log.info("Refreshing specimen data from \"" + sourceStudy.getContainer().getPath() + "\" (" + sourceStudy.getLabel() +
                        ") to \"" + destinationStudy.getContainer().getPath() + "\" (" + destinationStudy.getLabel() + ")");
                    SnapshotSettings settings = snapshot.getSnapshotSettings();

                    User user = UserManager.getUser(snapshot.getModifiedBy());

                    // User must exist and have admin rights in both folders... but this may not be the case.
                    // Consider: Support a built-in admin user for this purpose?

                    PipeRoot root = PipelineService.get().findPipelineRoot(sourceStudy.getContainer());
                    PipelineJob job = new SpecimenRefreshPipelineJob(sourceStudy.getContainer(), destinationStudy.getContainer(), user, null, root, settings);
                    PipelineService.get().queueJob(job);
                }
                catch(Exception e)
                {
                    // Might not want to log this... or perhaps ignore PipelineValidationException?
                    ExceptionUtil.logExceptionToMothership(null, e);
                }
            }
        }
    }

    // Return StudyImpl if containerId is not null, container exists, and container has a study
    private @Nullable StudyImpl getStudy(GUID containerId)
    {
        if (null != containerId)
        {
            Container c = ContainerManager.getForId(containerId);

            if (null != c)
                return StudyManager.getInstance().getStudy(c);
        }

        return null;
    }
}
