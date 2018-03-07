/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.ActionsHelper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.flow.api.FlowService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.UserManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.usageMetrics.UsageMetricsService;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

/**
 * The various levels of detail that can be reported back to the mothership as part of a regular ping.
 * User: jeckels
 * Date: Apr 26, 2006
 */
public enum UsageReportingLevel
{
    NONE
    {
        @Override
        protected void addExtraParams(MothershipReport report, Map<String, Object> metrics)
        {
            // no op
        }

        @Override
        protected boolean doGeneration()
        {
            return false;
        }

        @Override
        public TimerTask createTimerTask()
        {
            return null;
        }
    },
    LOW
    {
        @Override
        protected void addExtraParams(MothershipReport report, Map<String, Object> metrics)
        {
            report.addParam("userCount", UserManager.getActiveUserCount());

            report.addParam("containerCount", ContainerManager.getContainerCount());
            report.addParam("projectCount", ContainerManager.getRoot().getChildren().size());
            metrics.put("droppedExceptionCount", MothershipReport.getDroppedExceptionCount());

            // Users within the last 30 days
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DATE, -30);
            Date startDate = cal.getTime();
            report.addParam("activeUserCount", UserManager.getRecentUserCount(startDate));
            // Other counts within the last 30 days
            metrics.put("recentLoginCount", UserManager.getRecentLoginCount(startDate));
            metrics.put("recentLogoutCount", UserManager.getRecentLogOutCount(startDate));
            metrics.put("activeDayCount", UserManager.getActiveDaysCount(startDate));
            Integer averageRecentDuration = UserManager.getAverageSessionDuration(startDate);
            metrics.put("recentAvgSessionDuration", null == averageRecentDuration ? -1 : averageRecentDuration);

            FlowService fs = ServiceRegistry.get(FlowService.class);
            if (null != fs)
            {
                metrics.put("flowTempTableCount", fs.getTempTableCount());
            }

        }
    },
    MEDIUM
    {
        @Override
        protected void addExtraParams(MothershipReport report, Map<String, Object> metrics)
        {
            LOW.addExtraParams(report, metrics);

            LookAndFeelProperties laf = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
            report.addParam("logoLink", laf.getLogoHref());
            report.addParam("organizationName", laf.getCompanyName());
            report.addParam("systemDescription", laf.getDescription());
            report.addParam("systemShortName", laf.getShortName());
            report.addParam("administratorEmail", AppProps.getInstance().getAdministratorContactEmail());

            metrics.put("modules", getModulesStats());
            metrics.put("folderTypeCounts", ContainerManager.getFolderTypeNameContainerCounts(ContainerManager.getRoot()));

            report.addHostName();
        }
    };

    protected abstract void addExtraParams(MothershipReport report, Map<String, Object> metrics);

    protected boolean doGeneration()
    {
        return true;
    }

    private static Timer _timer;
    private static String _upgradeMessage;

    public static void cancelUpgradeCheck()
    {
        if (_timer != null)
        {
            _timer.cancel();
        }
        _timer = null;
        _upgradeMessage = null;
    }

    public void scheduleUpgradeCheck()
    {
        cancelUpgradeCheck();
        if (!ModuleLoader.getInstance().isDeferUsageReport())
        {
            TimerTask task = createTimerTask();
            if (task != null)
            {
                _timer = new Timer("UpgradeCheck", true);
                _timer.scheduleAtFixedRate(task, 0, DateUtils.MILLIS_PER_DAY);
            }
        }
    }

    protected TimerTask createTimerTask()
    {
        return new UsageTimerTask(this);
    }

    public static String getUpgradeMessage()
    {
        return _upgradeMessage;
    }

    public static MothershipReport generateReport(UsageReportingLevel level, MothershipReport.Target target)
    {
        if (level.doGeneration())
        {
            MothershipReport report;
            try
            {
                report = new MothershipReport(MothershipReport.Type.CheckForUpdates, target, null);
            }
            catch (MalformedURLException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
            report.addServerSessionParams();
            Map<String, Object> additionalMetrics = new LinkedHashMap<>();
            level.addExtraParams(report, additionalMetrics);
            serializeMetrics(report, additionalMetrics);
            return report;
        }
        else
            return null;
    }

    private static class UsageTimerTask extends TimerTask
    {
        final UsageReportingLevel _level;
        UsageTimerTask(UsageReportingLevel level)
        {
            _level = level;
        }

        public void run()
        {
            MothershipReport report = generateReport(_level, MothershipReport.Target.remote);
            if (report != null)
            {
                report.run();
                String message = report.getContent();
                if ("".equals(message))
                {
                    _upgradeMessage = null;
                }
                else
                {
                    _upgradeMessage = message;
                }
            }
         }
    }

    private static void serializeMetrics(MothershipReport report, Map<String, Object> metrics)
    {
        if (metrics.size() > 0)
        {
            String serializedMetrics;
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                serializedMetrics = mapper.writeValueAsString(metrics);
            }
            catch (JsonProcessingException e)
            {
                // TODO: Where to report, what to do?
                serializedMetrics = "Exception serializing json metrics. " + e.getMessage();
            }
            report.addParam("jsonMetrics", serializedMetrics);
        }
    }

    private static Map<String, Map<String, Object>> getModulesStats()
    {
        Map<String, Map<String, Object>> allModulesStats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try
        {
            ActionsHelper.getActionStatistics().forEach((module, controllersMap) -> {
                Map<String, Object> controllerHitCounts = new TreeMap<>();
                allModulesStats.put(module, controllerHitCounts);
                controllersMap.forEach((controller, actionStatsMap) -> controllerHitCounts.put(controller,
                        actionStatsMap.values().stream().mapToLong(SpringActionController.ActionStats::getCount).sum()));
            });
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            // Unlikely to hit this, but just in case, still give module list
            ModuleLoader.getInstance().getModules().forEach(module -> allModulesStats.put(module.getName(), new HashMap<>()));
            Map<String, Object> errors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            errors.put("controllerCounts", e.getMessage());
            allModulesStats.put(UsageMetricsService.ERRORS, errors);
        }

        UsageMetricsService svc = ServiceRegistry.get().getService(UsageMetricsService.class);
        if (null != svc)
        {
            svc.getModuleUsageMetrics().forEach((module, metrics) ->
            {
                // Add to the existing stats for the module, but don't assume ActionsHelper.getActionStatistics hit every module
                Map<String, Object> moduleStats = allModulesStats.computeIfAbsent(module, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                moduleStats.putAll(metrics);
            });
        }

        return allModulesStats;
    }
}
