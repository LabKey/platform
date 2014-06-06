/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.freezerpro;

import org.apache.log4j.Logger;
import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.ViewBackgroundInfo;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Set;

public class FreezerProUploadTask implements SystemMaintenance.MaintenanceTask
{
    private static Set<String> _freezerProContainerIds = new ConcurrentHashSet<>();
    private static final String FREEZER_PRO_STATIC_TASK_PROPERTIES = "FreezerProStaticTaskSettings";
    static
    {
        Map<String, String> map = PropertyManager.getProperties(ContainerManager.getRoot(), FREEZER_PRO_STATIC_TASK_PROPERTIES);
        for (String containerId : map.keySet())
            _freezerProContainerIds.add(containerId);
    }

    private static final String FREEZER_PRO_PROPERTIES = "FreezerProConfigurationSettings";    // duplicate from FreezerProController

    public void run()
    {
        long msStart = System.currentTimeMillis();
        _log.info("FreezerPro Upload Task starting cycle...");

        for (String containerId : _freezerProContainerIds)
        {
            try
            {
                Container container = ContainerManager.getForId(containerId);

                if (null == container)
                {
                    // Container must have been deleted
                    throw new ImportException("Container " + containerId + " does not exist");
                }

                Study study = StudyService.get().getStudy(container);
                if (null == study)
                {
                    // Study must have been deleted
                    throw new ImportException("Study does not exist in folder " + container.getPath());
                }

                if (Encryption.isMasterEncryptionPassPhraseSpecified())
                {
                    Map<String, String> map = PropertyManager.getEncryptedStore().getProperties(container, FREEZER_PRO_PROPERTIES);

                    if (map.containsKey(FreezerProConfig.Options.enableReload.name()) && Boolean.parseBoolean(map.get(FreezerProConfig.Options.enableReload.name())))
                    {
                        int dayInterval = map.containsKey(FreezerProConfig.Options.reloadInterval.name()) ? Integer.parseInt(map.get(FreezerProConfig.Options.reloadInterval.name())) : 1;
                        Date today = DateUtil.getDateOnly(new Date());
                        Date reloadDate = map.containsKey(FreezerProConfig.Options.reloadDate.name()) ? DateUtil.getDateOnly(new Date(DateUtil.parseDateTime(container, map.get(FreezerProConfig.Options.reloadDate.name())))) : today;

                        if ((today.getTime() - reloadDate.getTime()) % dayInterval == 0)
                        {
                            try
                            {
                                String user = map.get(FreezerProConfig.Options.reloadUser.name());
                                if (user != null)
                                {
                                    int userId = Integer.parseInt(user);
                                    User reloadUser = UserManager.getUser(userId);

                                    if (reloadUser != null)
                                    {
                                        _log.info("Running FreezerPro upload for " + container.getName());
                                        SpecimenTransform transform = SpecimenService.get().getSpecimenTransform(FreezerProTransform.NAME);

                                        PipelineJob job = SpecimenService.get().createSpecimenReloadJob(container, reloadUser, transform, null);
                                        PipelineService.get().queueJob(job);
                                    }
                                    else
                                        _log.error("The specified reload user is invalid");
                                }
                                else
                                    _log.error("No reload user has been configured");
                            }
                            catch (Exception e)
                            {
                                _log.error("An error occurred exporting from FreezerPro", e);
                            }
                        }
                    }
                }
                else
                {
                    throw new ImportException("Unable to save or retrieve configuration information. MasterEncryptionKey has not been specified in labkey.xml.");
                }
            }
            catch (ImportException e)
            {
                Container c = ContainerManager.getForId(containerId);
                String message = null != c ? " in folder " + c.getPath() : "";

                _log.error("FreezerPro upload failed" + message);
            }
            catch (Throwable t)
            {
                // Throwing from run() will kill the reload task, suppressing all future attempts; log to mothership and continue, so we retry later.
                ExceptionUtil.logExceptionToMothership(null, t);
            }
        }

        _log.info("FreezerPro Upload Task finished cycle in " + String.valueOf((System.currentTimeMillis() - msStart) / 1000) + " seconds.");
    }

    public String getDescription()
    {
        return "FreezerPro Upload Task";
    }

    @Override
    public String getName()
    {
        return "FreezerProUpload";
    }

    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public boolean hideFromAdminPage() { return true; }

    public static void addFreezerProContainer(String containerId)
    {
        _freezerProContainerIds.add(containerId);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FREEZER_PRO_STATIC_TASK_PROPERTIES, true);
        map.put(containerId, "true");
        PropertyManager.saveProperties(map);
    }

    public static void removeFreezerProContainer(String containerId)
    {
        _freezerProContainerIds.remove(containerId);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FREEZER_PRO_STATIC_TASK_PROPERTIES, true);
        map.remove(containerId);
        PropertyManager.saveProperties(map);
    }

    private Logger _log = Logger.getLogger(FreezerProUploadTask.class);
}
