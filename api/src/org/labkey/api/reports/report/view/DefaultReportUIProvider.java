/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.reports.report.view;

import org.labkey.api.reports.ReportService;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.QuerySettings;

import java.util.Map;/*
 * User: Karl Lum
 * Date: May 16, 2008
 * Time: 4:11:33 PM
 */

public class DefaultReportUIProvider implements ReportService.UIProvider
{
    public void getReportDesignURL(ViewContext context, QuerySettings settings, Map<String, String> designers)
    {
    }
}