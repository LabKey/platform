/*
 * Copyright (c) 2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.MasterPatientIndexService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.pipeline.MasterPatientIndexUpdateTask;

public class MasterPatientIndexMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Master Patient Index Synchronization";
    }

    @Override
    public String getName()
    {
        return "masterPatientIndexMaintenance";
    }

    @Override
    public void run(Logger log)
    {
        MasterPatientIndexService svc = getConfiguredService();
        if (svc != null)
        {
            for (StudyImpl study : StudyManager.getInstance().getAllStudies())
            {
                Container container = study.getContainer();
                MasterPatientIndexService.FolderSettings settings = svc.getFolderSettings(container);
                if (settings.isEnabled())
                {
                    log.info("Starting Master Patient Index Job for folder: " + container.getPath());
                    User reloadUser = UserManager.getUser(settings.getReloadUser());
                    if (reloadUser != null)
                    {
                        try
                        {
                            ViewBackgroundInfo info = new ViewBackgroundInfo(container, reloadUser, null);
                            PipelineJob job = new MasterPatientIndexUpdateTask(info, PipelineService.get().findPipelineRoot(container), svc);

                            PipelineService.get().queueJob(job);
                        }
                        catch (Exception e)
                        {
                            log.error("Master Patient Index Maintenance Task failed for folder : " + container.getPath() + " due to : " + e.getMessage());
                        }
                    }
                    else
                        log.error("Unable to resolve the reload user: " + settings.getReloadUser());
                }
            }
        }
    }

    @Nullable
    public static MasterPatientIndexService getConfiguredService()
    {
        PropertyManager.PropertyMap map = PropertyManager.getNormalStore().getProperties(StudyController.MasterPatientProviderSettings.CATEGORY);
        String type = map.get(StudyController.MasterPatientProviderSettings.TYPE);

        if (type != null)
            return MasterPatientIndexService.getProvider(type);

        return null;
    }
}
