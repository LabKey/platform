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
        return "Task to calculate metrics for valid and missing files for File fields";
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
            Map<String, Map<String, MissingFilesCheckInfo>> results = ExperimentServiceImpl.get().doMissingFilesCheck(getTaskUser(), ContainerManager.getRoot(), false);
            Map<String, Object> missingFilesMetrics = new HashMap<>();
            missingFilesMetrics.put("Run time", new Date());
            Map<String, Object> metrics = new HashMap<>();
            metrics.put(NAME, missingFilesMetrics);
            long missingFilesCount = 0;
            Map<String, Long> validFilesCount = new HashMap<>();
            if (null != results)
            {
                for (String containerId : results.keySet())
                {
                    Map<String, MissingFilesCheckInfo> info = results.get(containerId);
                    for (String source : info.keySet())
                    {
                        missingFilesCount += info.get(source).getMissingFilesCount();

                        // e.g. 'assayresults.c9043290_test'
                        // note that files from the assay batch and run fields will have "exp" as the schema name here
                        String schemaName = source.substring(0, source.indexOf('.'));

                        long schemaValidFilesCount = validFilesCount.getOrDefault(schemaName, 0L);
                        validFilesCount.put(schemaName, schemaValidFilesCount + info.get(source).getValidFilesCount());
                    }
                }
            }
            missingFilesMetrics.put("Missing files count", missingFilesCount);
            missingFilesMetrics.put("Valid files count", validFilesCount);
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
                        return "Enable the system maintenance task to calculate metrics for valid and missing files for File fields.";
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
