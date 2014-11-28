/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import org.apache.commons.lang3.time.DateUtils;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

/**
 * User: jeckels
 * Date: Apr 26, 2006
 */
public enum UsageReportingLevel
{
    NONE
    {
        public TimerTask createTimerTask()
        {
            return null;
        }
    },
    LOW
    {
        public TimerTask createTimerTask()
        {
            return new UsageTimerTask()
            {
                protected void addExtraParams(MothershipReport report)
                {
                    addLowUsageReportingParams(report);
                }
            };
        }
    },
    MEDIUM
    {
        public TimerTask createTimerTask()
        {
            return new UsageTimerTask()
            {
                protected void addExtraParams(MothershipReport report)
                {
                    addMediumUsageReportingParams(report);
                }
            };

        }
    };

    private static void addMediumUsageReportingParams(MothershipReport report)
    {
        addLowUsageReportingParams(report);

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(ContainerManager.getRoot());

        report.addParam("logoLink", laf.getLogoHref());
        report.addParam("organizationName", laf.getCompanyName());
        report.addParam("systemDescription", laf.getDescription());
        report.addParam("systemShortName", laf.getShortName());

        report.addParam("administratorEmail", AppProps.getInstance().getAdministratorContactEmail());
    }

    private static void addLowUsageReportingParams(MothershipReport report)
    {
        report.addParam("userCount", UserManager.getActiveUserCount());
        // Users within the last 30 days
        Calendar cal = new GregorianCalendar();
        cal.add(Calendar.DATE, -30);
        report.addParam("activeUserCount", UserManager.getRecentUserCount(cal.getTime()));
        report.addParam("containerCount", ContainerManager.getContainerCount());
        report.addParam("projectCount", ContainerManager.getRoot().getChildren().size());
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

    protected abstract TimerTask createTimerTask();

    public static String getUpgradeMessage()
    {
        return _upgradeMessage;
    }

    private abstract static class UsageTimerTask extends TimerTask
    {
        public void run()
        {
            try
            {
                MothershipReport report = new MothershipReport(MothershipReport.Type.CheckForUpdates, false);
                report.addServerSessionParams();
                addExtraParams(report);
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
            catch (MalformedURLException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        }

        protected abstract void addExtraParams(MothershipReport report);
    }
}
