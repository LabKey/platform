/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.redcap;

import org.apache.log4j.Logger;
import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyReloadSource;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DefaultSystemMaintenanceTask;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.SystemMaintenance;

import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 5/22/2015.
 */
public class RedcapMaintenanceTask extends DefaultSystemMaintenanceTask
{
    private Logger _log = Logger.getLogger(RedcapMaintenanceTask.class);
    private static Set<String> _redcapContainerIds = new ConcurrentHashSet<>();
    private static final String REDCAP_STATIC_TASK_SETTINGS = "RedcapStaticTaskSettings";

    static
    {
        Map<String, String> map = PropertyManager.getProperties(ContainerManager.getRoot(), REDCAP_STATIC_TASK_SETTINGS);
        for (String containerId : map.keySet())
            _redcapContainerIds.add(containerId);
    }

    public void run()
    {
        long msStart = System.currentTimeMillis();
        _log.info("REDCap Import Task starting cycle...");

        for (String containerId : _redcapContainerIds)
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
                    Map<String, String> map = PropertyManager.getEncryptedStore().getProperties(container, RedcapManager.REDCAP_PROPERTIES);

                    if (map.containsKey(RedcapManager.RedcapSettings.Options.enableReload.name()) && Boolean.parseBoolean(map.get(RedcapManager.RedcapSettings.Options.enableReload.name())))
                    {
                        int dayInterval = map.containsKey(RedcapManager.RedcapSettings.Options.reloadInterval.name()) ? Integer.parseInt(map.get(RedcapManager.RedcapSettings.Options.reloadInterval.name())) : 1;
                        Date today = DateUtil.getDateOnly(new Date());
                        Date reloadDate = map.containsKey(RedcapManager.RedcapSettings.Options.reloadDate.name()) ? DateUtil.getDateOnly(new Date(DateUtil.parseDateTime(container, map.get(RedcapManager.RedcapSettings.Options.reloadDate.name())))) : today;

                        if ((today.getTime() - reloadDate.getTime()) % dayInterval == 0)
                        {
                            try
                            {
                                String user = map.get(RedcapManager.RedcapSettings.Options.reloadUser.name());
                                if (user != null)
                                {
                                    int userId = Integer.parseInt(user);
                                    User reloadUser = UserManager.getUser(userId);

                                    if (reloadUser != null)
                                    {
                                        StudyReloadSource reloadSource = StudyService.get().getStudyReloadSource(RedcapReloadSource.NAME);

                                        PipelineJob job = StudyService.get().createReloadSourceJob(container, reloadUser, reloadSource, null);
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
                                _log.error("An error occurred exporting from REDCap", e);
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

                _log.error("REDCap import failed" + message);
            }
            catch (Throwable t)
            {
                // Throwing from run() will kill the reload task, suppressing all future attempts; log to mothership and continue, so we retry later.
                ExceptionUtil.logExceptionToMothership(null, t);
            }
        }

        _log.info("REDCap Import Task finished cycle in " + String.valueOf((System.currentTimeMillis() - msStart) / 1000) + " seconds.");
    }

    public String getDescription()
    {
        return "REDCap Import Task";
    }

    @Override
    public String getName()
    {
        return "REDCapImport";
    }

    @Override
    public boolean canDisable()
    {
        return false;
    }

    @Override
    public boolean hideFromAdminPage()
    {
        return true;
    }

    public static void addContainer(String containerId)
    {
        _redcapContainerIds.add(containerId);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(ContainerManager.getRoot(), REDCAP_STATIC_TASK_SETTINGS, true);
        map.put(containerId, "true");
        map.save();
    }

    public static void removeContainer(String containerId)
    {
        _redcapContainerIds.remove(containerId);
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(ContainerManager.getRoot(), REDCAP_STATIC_TASK_SETTINGS, true);
        map.remove(containerId);
        map.save();
    }
}
