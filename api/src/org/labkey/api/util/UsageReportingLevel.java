/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.ActionsHelper;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.usageMetrics.UsageMetricsService;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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
    /** Captures only very basic user and container count information */
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
            metrics.put("mostRecentLogin", DateUtil.formatDateISO8601(UserManager.getMostRecentLogin()));
            putModulesMetrics(metrics);
        }
    },
    /**
     * Captures Site Settings info to help identify the organization running the install, and more detailed stats
     * about how many items exist or actions have been invoked.
     *
     * May capture site-wide usage information, including counts for certain data types, such as assay designs,
     * reports of a specific type, or lists. May also capture the number of times a certain feature was used in a
     * given time window, such as since the server was last restarted.
     *
     * Per policy, this should not capture the names of specific objects like container names, dataset names, etc.
     *
     * Also per policy, this should not capture metrics at a container or other similar granularity. For example,
     * metrics should not break down the number of lists defined in each folder (even if that folder was deidentified).
     */
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
            report.addParam("administratorEmail", AppProps.getInstance().getAdministratorContactEmail(true));

            putModuleControllerHits(metrics);
            putModulesMetrics(metrics);
            metrics.put("folderTypeCounts", ContainerManager.getFolderTypeNameContainerCounts(ContainerManager.getRoot()));

            report.addHostName();
        }
    },

    DIAGNOSTICS
    {
        @Override
        protected void addExtraParams(MothershipReport report, Map<String, Object> metrics)
        {
            // no op
        }
    };

    protected abstract void addExtraParams(MothershipReport report, Map<String, Object> metrics);

    protected boolean doGeneration()
    {
        return true;
    }

    private static Timer _timer;
    private static HtmlString _upgradeMessage;

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

    public static @Nullable HtmlString getUpgradeMessage()
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
        private final UsageReportingLevel _level;

        UsageTimerTask(UsageReportingLevel level)
        {
            _level = level;
        }

        @Override
        public void run()
        {
            MothershipReport report = generateReport(_level, MothershipReport.Target.remote);
            if (report != null)
            {
                report.run();
                String message = report.getContent();
                if (StringUtils.isEmpty(message))
                {
                    _upgradeMessage = null;
                }
                else
                {
                    // We assume labkey.org is sending back legal HTML
                    _upgradeMessage = HtmlString.unsafe(message);
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

    protected void putModulesMetrics(Map<String, Object> runningMetrics)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            Map<String, Map<String, Object>> allRegisteredMetrics = svc.getModuleUsageMetrics(this);
            if (null != allRegisteredMetrics)
            {
                @SuppressWarnings({"unchecked"})
                Map<String, Map<String, Object>> modulesMap = (Map<String, Map<String, Object>>) runningMetrics.computeIfAbsent("modules", k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                allRegisteredMetrics.forEach((module, metrics) ->
                {
                    Map<String, Object> moduleStats = modulesMap.computeIfAbsent(module, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                    moduleStats.putAll(metrics);
                });
            }
        }
    }

    protected void putModuleControllerHits(Map<String, Object> runningMetrics)
    {
        @SuppressWarnings({"unchecked"})
        Map<String, Map<String, Object>> allModulesStats = (Map<String, Map<String, Object>>) runningMetrics.computeIfAbsent("modules", k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
        try
        {
            ActionsHelper.getActionStatistics().forEach((module, controllersMap) -> {
                Map<String, Object> moduleStats = allModulesStats.computeIfAbsent(module, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
                Map<String, Long> controllerStats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                moduleStats.put("controllerHits", controllerStats);
                controllersMap.forEach((controller, actionStatsMap) -> controllerStats.put(controller,
                        actionStatsMap.values().stream().mapToLong(SpringActionController.ActionStats::getCount).sum()));
            });
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            // Unlikely to hit this, but just in case, still give module list
            ModuleLoader.getInstance().getModules().forEach(module -> allModulesStats.computeIfAbsent(module.getName(), k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
            // And put the error in the errors section of the metrics
            allModulesStats.computeIfAbsent(UsageMetricsService.ERRORS, k -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)).put("controllerCounts", e.getMessage());
        }
    }

    /**
     * Helper for integration tests
     */
    public static class MothershipReportTestHelper
    {
        public static MothershipReport getReport(UsageReportingLevel level)
        {
            MothershipReport report = UsageReportingLevel.generateReport(level, MothershipReport.Target.test);
            if (null == report && level.doGeneration())
                throw new ApiUsageException("No report generated for level " + level.toString());
            else
                return report;
        }

        public static Map<String, String> getReportParams(UsageReportingLevel level)
        {
            return Objects.requireNonNull(getReport(level)).getParams();
        }

        public static Map<String, Object> getMetrics(UsageReportingLevel level)
        {
            try
            {
                @SuppressWarnings({"unchecked"})
                Map<String, Object> metrics = new ObjectMapper().readValue(Objects.requireNonNull(getReportParams(level)).get("jsonMetrics"), Map.class);
                return metrics;
            }
            catch (IOException e)
            {
                throw new ApiUsageException("Exception deserializing json string that was just serialized. This should never happen.", e);
            }
        }

        public static Map<String, Object> getModuleMetrics(UsageReportingLevel level, String moduleName)
        {
            @SuppressWarnings({"unchecked"})
            Map<String, Object> moduleMetrics = (Map<String, Object>) ((Map<String, Object>) getMetrics(level).get("modules")).get(moduleName);
            return moduleMetrics;
        }
    }
}
