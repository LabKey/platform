/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.Map;

/**
 * User: Karl Lum
 * Date: Feb 29, 2008
 */
public interface ReportUrls extends UrlProvider
{
    ActionURL urlDownloadData(Container c);
    ActionURL urlRunReport(Container c);
    ActionURL urlAjaxSaveScriptReport(Container c);
    ActionURL urlDesignChart(Container c);
    ActionURL urlCreateScriptReport(Container c);
    ActionURL urlViewScriptReport(Container c);
    ActionURL urlViewBackgroundScriptReport(Container c);
    ActionURL urlStreamFile(Container c);
    ActionURL urlReportSections(Container c);
    ActionURL urlManageViews(Container c);
    ActionURL urlPlotChart(Container c);
    ActionURL urlDeleteReport(Container c);
    ActionURL urlExportCrosstab(Container c);
    ActionURL urlShareReport(Container c, Report r);
    // Thumbnail or icon, depending on ImageType
    ActionURL urlImage(Container c, Report r, ThumbnailService.ImageType type, @Nullable Integer revision);
    ActionURL urlReportInfo(Container c);
    ActionURL urlAttachmentReport(Container c, ActionURL returnURL);
    ActionURL urlLinkReport(Container c, ActionURL returnURL);
    ActionURL urlReportDetails(Container c, Report r);
    ActionURL urlQueryReport(Container c, Report r);
    ActionURL urlManageNotifications(Container c);
    ActionURL urlModuleThumbnail(Container c);
    Pair<ActionURL, Map<String, Object>> urlAjaxExternalEditScriptReport(ViewContext viewContext, Report r);
}
