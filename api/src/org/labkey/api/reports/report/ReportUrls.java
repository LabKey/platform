/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 29, 2008
 */
public interface ReportUrls extends UrlProvider
{
    ActionURL urlDownloadData(Container c);
    ActionURL urlRunReport(Container c);
    ActionURL urlSaveRReportState(Container c);
    ActionURL urlUpdateRReportState(Container c);
    ActionURL urlDesignChart(Container c);
    ActionURL urlCreateRReport(Container c);
    ActionURL urlCreateScriptReport(Container c);
    ActionURL urlStreamFile(Container c);
    ActionURL urlReportSections(Container c);
    ActionURL urlManageViews(Container c);
    ActionURL urlManageViewsSummary(Container c);
    ActionURL urlPlotChart(Container c);
    ActionURL urlDeleteReport(Container c);
    ActionURL urlExportCrosstab(Container c);
    ActionURL urlCustomizeView(Container c);
}
