/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

package org.labkey.api.visualization;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

/*
* User: brittp
* Date: Jan 28, 2011
* Time: 4:49 PM
*/
public abstract class TimeChartReport extends AbstractReport
{
    public static final String TYPE = "ReportService.TimeChartReport";

    @Override
    public String getDescriptorType()
    {
        return TimeChartReportDescriptor.TYPE;
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public ActionURL getRunReportURL(ViewContext context)
    {
        return PageFlowUtil.urlProvider(VisualizationUrls.class).getTimeChartDesignerURL(context.getContainer(), this, false);
    }

    @Override
    public ActionURL getEditReportURL(ViewContext context)
    {
        return PageFlowUtil.urlProvider(VisualizationUrls.class).getTimeChartDesignerURL(context.getContainer(), this, true);
    }

    @Override
    public String getTypeDescription()
    {
        return "Time Chart";
    }
}
