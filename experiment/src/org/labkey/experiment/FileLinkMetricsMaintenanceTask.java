package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FileLinkMetricsMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    public static final String NAME = "FileLinkMetricsMaintenanceTask";
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
    public void run(Logger log)
    {
        try
        {
            UsageMetricsService svc = UsageMetricsService.get();
            if (null != svc)
            {
                Map<String, Map<String, Set<String>>> results = ExperimentServiceImpl.get().doMissingFilesCheck(getTaskUser(), ContainerManager.getRoot());
                if (null != results)
                {
                    Map<String, Object> metrics = new HashMap<>();
                    Map<String, Object> missingFilesMetrics = new HashMap<>();
                    metrics.put(NAME, missingFilesMetrics);
                    missingFilesMetrics.put("Run time", new Date());
                    for (String containerId : results.keySet())
                    {
                        String containerPath = containerId;
                        Container container = ContainerManager.getForId(containerId);
                        if (container != null)
                            containerPath = container.getPath();
                        Map<String, Object> containerMetrics = new HashMap<>();
                        missingFilesMetrics.put(containerPath, containerMetrics);

                        Map<String, Set<String>> missingFiles = results.get(containerId);
                        for (String source : missingFiles.keySet())
                            containerMetrics.put(source, missingFiles.get(source).size());
                    }
                    svc.registerUsageMetrics(ExperimentServiceImpl.MODULE_NAME, () -> metrics);
                }
            }
        }
        catch (Exception e)
        {
            log.error("Unable to run missing files check task. {}", e.getMessage());
        }
    }
}
