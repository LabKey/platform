/*
 * Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.util.GUID;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.StudySnapshot;
import org.labkey.study.model.StudySnapshot.SnapshotSettings;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 9/22/12
 * Time: 12:11 PM
 */
public class SpecimenRefreshMaintainanceTask implements SystemMaintenance.MaintenanceTask
{
    private static final Logger LOG = Logger.getLogger(SpecimenRefreshMaintainanceTask.class);

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
    public void run()
    {
        Collection<StudySnapshot> snapshots = StudyManager.getInstance().getRefreshStudySnapshots();

        for (StudySnapshot snapshot : snapshots)
        {
            StudyImpl sourceStudy =  getStudy(snapshot.getSource());

            if (null == sourceStudy)
                continue;

            StudyImpl destinationStudy =  getStudy(snapshot.getDestination());

            if (null == destinationStudy)
                continue;

            // If null, specimens definitely haven't been updated since the snapshot
            if (null == sourceStudy.getLastSpecimenLoad())
                continue;

            if (null == destinationStudy.getLastSpecimenLoad() ||
                sourceStudy.getLastSpecimenLoad().compareTo(destinationStudy.getLastSpecimenLoad()) > 0)
            {
                LOG.info("Refreshing specimen data from \"" + sourceStudy.getContainer().getPath() + "\" (" + sourceStudy.getLabel() +
                    ") to \"" + destinationStudy.getContainer().getPath() + "\" (" + destinationStudy.getLabel() + ")");
                SnapshotSettings settings = snapshot.getSnapshotSettings();

                List<String> participants = settings.getParticipants();
                Set<Integer> visits = settings.getVisits();
                boolean useAlternateIds = settings.isUseAlternateIds();
                boolean shiftDates = settings.isShiftDates();
                boolean remoteProtected = settings.isRemoveProtected();

                // TODO: This is where we kick off the actually specimen export and import, NYI
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
