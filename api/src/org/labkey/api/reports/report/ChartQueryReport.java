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

package org.labkey.api.reports.report;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/*
  User: Karl Lum
  Date: Mar 28, 2007
 */

/**
 * We don't need to render this report as of 19.1 but we need to be able to register an instance of it so
 * it can be converted to a javascript report. This class can be deleted in the 21.2 release.
 */
@Deprecated
public class ChartQueryReport extends ChartReport
{
    public static final String TYPE = "ReportService.chartQueryReport";

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderReport(ViewContext context)
    {
        return null;
    }
}
