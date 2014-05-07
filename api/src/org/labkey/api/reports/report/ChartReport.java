/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.labkey.api.data.Results;
import org.labkey.api.reports.Report;
import org.labkey.api.view.ViewContext;

/**
 * User: Karl Lum
 * Date: Oct 3, 2006
 */
public abstract class ChartReport extends AbstractReport implements Report.ResultSetGenerator
{
    public String getDescriptorType()
    {
        return ChartReportDescriptor.TYPE;
    }

    public String getTypeDescription()
    {
        return "Chart View";
    }

    public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
    {
        return null;
    }

    public Results generateResults(ViewContext context, boolean allowAsyncQuery) throws Exception
    {
        return null;
    }
}
