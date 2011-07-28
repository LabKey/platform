package org.labkey.study;

import org.labkey.api.util.SystemMaintenance;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.visitmanager.VisitManager;

/**
 * User: jeckels
 * Date: 7/27/11
 */
public class PurgeParticipantsMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getMaintenanceTaskName()
    {
        return "Purge unused participants";
    }

    @Override
    public void run()
    {
        for (StudyImpl study : StudyManager.getInstance().getAllStudies())
        {
            VisitManager.performParticipantPurge(study, null);
        }
    }
}
