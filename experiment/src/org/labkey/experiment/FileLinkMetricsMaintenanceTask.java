package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FileLinkMetricsMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    public static final String NAME = "FileLinkMetricsMaintenanceTask";
    public static final String STARTUP_SCOPE = "FileLinkMetrics";

    @Override
    public String getDescription()
    {
        return "Task to calculate metrics for missing files for File fields.";
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    private User getTaskUser()
    {
        User taskUser = new User("FileLinkMetricsMaintenanceUser", -1);
        taskUser.setPrincipalType(PrincipalType.SERVICE);
        taskUser.setDisplayName("FileLinkMetricsMaintenanceUser");
        return new LimitedUser(taskUser, ProjectAdminRole.class);
    }

    @Override
    public boolean isEnabledByDefault()
    {
        return false;
    }


    @Override
    public void run(Logger log)
    {
        try
        {
            Map<String, Map<String, Set<String>>> results = ExperimentServiceImpl.get().doMissingFilesCheck(getTaskUser(), ContainerManager.getRoot());
            Map<String, Object> missingFilesMetrics = new HashMap<>();
            missingFilesMetrics.put("Run time", new Date());
            Map<String, Object> metrics = new HashMap<>();
            metrics.put(NAME, missingFilesMetrics);
            long totalCount = 0;
            if (null != results)
            {
                for (String containerId : results.keySet())
                {
                    Map<String, Set<String>> missingFiles = results.get(containerId);
                    for (String source : missingFiles.keySet())
                        totalCount += missingFiles.get(source).size();
                }
            }
            missingFilesMetrics.put("Missing files count", totalCount);
            FileLinkMetricsProvider.getInstance().updateMetrics(metrics);
        }
        catch (Exception e)
        {
            log.error("Unable to run missing files check task. {}", e);
        }
    }

    private enum StartupProperties implements StartupProperty
    {
        EnableFileLinkMetricsTask
                {
                    @Override
                    public String getDescription()
                    {
                        return "Enable the system maintenance task to calculate metrics for missing files for File fields.";
                    }
                }
    }

    public static void populateStartupProperties()
    {
        // Looking for startup.properties value of FileLinkMetrics.EnableFileLinkMetricsTask = true
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(STARTUP_SCOPE, StartupProperties.class)
        {
            @Override
            public void handle(Map<StartupProperties, StartupPropertyEntry> map)
            {
                StartupPropertyEntry entry = map.get(StartupProperties.EnableFileLinkMetricsTask);
                if (null != entry && Boolean.valueOf(entry.getValue()))
                    SystemMaintenance.enableTask(FileLinkMetricsMaintenanceTask.NAME);
            }
        });

    }
}
