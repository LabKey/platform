/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.visualization;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.UserManager;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;


public class VisualizationUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(VisualizationUpgradeCode.class);

    // Called at 13.30 -> 13.31
    public static void upgradeGenericChartSaveConfig(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        SimpleFilter filter = new SimpleFilter();
        Report[] reports = ReportService.get().getReports(filter);
        if (reports.length > 0)
        {
            for (Report report : reports)
            {
                if (report.getDescriptor().getDescriptorType().equals(GenericChartReportDescriptor.TYPE))
                {
                    GenericChartReportDescriptor descriptor = (GenericChartReportDescriptor) report.getDescriptor();
                    descriptor.updateSaveConfig();
                    Container rptContainer = ContainerManager.getForId(report.getContainerId());
                    ContainerUser rptContext = new DefaultContainerUser(rptContainer, UserManager.getUser(descriptor.getModifiedBy()));

                    try
                    {
                        ReportService.get().saveReport(rptContext, descriptor.getReportKey(), report, true);
                    }
                    catch (RuntimeSQLException e)
                    {
                        _log.error("An error occurred upgrading generic chart report properties: ", e);
                    }
                }
            }
        }
    }
}
