/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.query.reports;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.query.reports.view.ReportsWebPart;
import org.labkey.query.reports.view.ReportsWebPartConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Mar 2, 2008
 */
public class ReportsWebPartFactory extends AlwaysAvailableWebPartFactory
{
    public ReportsWebPartFactory()
    {
        super("Report", true, true);
    }
    
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        ReportsWebPart wp = new ReportsWebPart(portalCtx, webPart);
        populateProperties(wp, webPart.getPropertyMap());

        return wp;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new ReportsWebPartConfig(webPart);
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> serializedPropertyMap = new HashMap<>(propertyMap);

        // replace the reportId with the reportName and the reportKey (i.e. schemaName/queryName/viewName)
        if (serializedPropertyMap.containsKey("reportId"))
        {
            try
            {
                ReportIdentifier reportId = ReportService.get().getReportIdentifier(serializedPropertyMap.get("reportId"));
                if (reportId != null)
                {
                    Report report = reportId.getReport(ctx);
                    if (null != report)
                    {
                        serializedPropertyMap.remove("reportId");
                        serializedPropertyMap.put("reportName", report.getDescriptor().getReportName());
                        serializedPropertyMap.put("reportKey", report.getDescriptor().getReportKey());
                    }
                }
            }
            catch(Exception e)
            {
                // do nothing
            }
        }
        return serializedPropertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        Map<String, String> deserializedPropertyMap = new HashMap<>(propertyMap);

        // try to resolve the reportId from the reportName and reportKey
        if (deserializedPropertyMap.containsKey("reportName"))
        {
            try
            {
                String reportName = deserializedPropertyMap.get("reportName");
                if (deserializedPropertyMap.containsKey("reportKey"))
                {
                    String reportKey = deserializedPropertyMap.get("reportKey");
                    for (Report rpt : ReportService.get().getReports(ctx.getUser(), ctx.getContainer(), reportKey))
                    {
                        if (reportName.equals(rpt.getDescriptor().getReportName()))
                        {
                            deserializedPropertyMap.put("reportId", rpt.getDescriptor().getReportId().toString());
                        }
                    }

                    deserializedPropertyMap.remove("reportKey");
                }
                deserializedPropertyMap.remove("reportName");
            }
            catch(Exception e)
            {
                // do nothing
            }
        }

        return deserializedPropertyMap;
    }
}
