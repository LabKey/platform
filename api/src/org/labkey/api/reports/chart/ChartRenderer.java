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

package org.labkey.api.reports.chart;

import org.jfree.chart.plot.Plot;
import org.labkey.api.query.QueryView;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;

import java.util.Map;

/**
 * User: Karl Lum
 * Date: Sep 27, 2006
 */
public interface ChartRenderer
{
    public String getType();
    public String getName();
    public Plot createPlot(ChartReportDescriptor descriptor, ReportQueryView view) throws Exception;
    public Map<String, String> getDisplayColumns(QueryView view, boolean isXAxis);

    /**
     * specify additional rendering info
     */
    public void setRenderInfo(ChartRenderInfo info);
    public ChartRenderInfo getRenderInfo();
}
