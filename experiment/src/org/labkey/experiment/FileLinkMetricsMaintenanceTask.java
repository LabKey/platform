/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.experiment;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FileLinkMetricsMaintenanceTask implements MaintenanceTask
{
    public static final String NAME = "FileLinkMetricsMaintenanceTask";

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
        return User.getAdminServiceUser();
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
}
