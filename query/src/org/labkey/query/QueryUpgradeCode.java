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
package org.labkey.query;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.query.persist.QueryManager;

public class QueryUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(QueryUpgradeCode.class);

    /**
     * Migrate the legacy chart views to the new json-based versions
     * Invoked from 18.30-18.31
     */
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void convertChartViews(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            try (DbScope.Transaction transaction = QueryManager.get().getDbSchema().getScope().ensureTransaction())
            {
                _log.info("Beginning conversion of legacy chart views");
                int count = 0;
                for (Container c : ContainerManager.getAllChildren(ContainerManager.getRoot()))
                {
                    for (Report report : ReportService.get().getReports(null, c))
                    {
                        if (report instanceof ChartReport)
                        {
                            ReportDescriptor descriptor = report.getDescriptor();
                            User reportOwner = UserManager.getUser(descriptor.getModifiedBy());
                            if (reportOwner == null)
                                reportOwner = User.getSearchUser();

                            ContainerUser containerUser = new DefaultContainerUser(c, reportOwner);
                            report = ReportService.get().createConvertedChartViewReportInstance(report, containerUser);
                            if (report != null)
                            {
                                ReportService.get().saveReport(containerUser, descriptor.getReportKey(), report, true);
                                count++;
                            }
                        }
                    }
                }
                transaction.commit();
                _log.info("Completed conversion of legacy chart views, number of charts converted: " + count);
            }
        }
    }
}
