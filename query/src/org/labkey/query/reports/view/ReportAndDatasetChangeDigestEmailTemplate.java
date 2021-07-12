/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.query.reports.view;

import org.labkey.api.data.Container;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 6/24/2014
 *
 * Replace reportDigestNotify.jsp and digestNotifyPlain.jsp with customizable template
 */
public class ReportAndDatasetChangeDigestEmailTemplate extends EmailTemplate
{
    private static final String DEFAULT_SUBJECT = "Report/Dataset Change Notification";

    private ActionURL _folderUrl;
    private ActionURL _emailPrefsUrl;
    private String _reportAndDatasetList;

    /** Instead of in-lining a long String, we store the default body template as a ClassLoader resource */
    private static String loadBody()
    {
        try
        {
            try (InputStream is = ReportAndDatasetChangeDigestEmailTemplate.class.getResourceAsStream("/org/labkey/query/reports/view/reportAndDatasetChangeDigestNotify.txt"))
            {
                return PageFlowUtil.getStreamContentsAsString(is);
            }
        }
        catch (IOException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public ReportAndDatasetChangeDigestEmailTemplate()
    {
        super("Report/dataset change (digest)", "Daily digest of report and dataset changes", DEFAULT_SUBJECT, loadBody(), ContentType.HTML, Scope.Site);
    }

    @Override
    protected void addCustomReplacements(Replacements replacements)
    {
        replacements.add("folderUrl", String.class, "URL to folder", ContentType.Plain, c -> _folderUrl == null ? null : _folderUrl.getURIString());
        replacements.add("folderPath", String.class, "Path to folder", ContentType.Plain, c -> PageFlowUtil.filter(c.getPath()));
        replacements.add("emailPrefsUrl", String.class, "URL to set email preferences", ContentType.Plain, c -> _emailPrefsUrl == null ? null : _emailPrefsUrl.getURIString());
        replacements.add("reportAndDatasetList", String.class, "Formatted list of changed reports/datasets", ContentType.HTML, c -> _reportAndDatasetList);
    }

    public void init(Container c, Map<ViewCategory, List<NotificationInfo>> reports)
    {
        _emailPrefsUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlManageNotifications(c);
        _folderUrl = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c);
        _reportAndDatasetList = buildReportAndDatasetList(c, reports);
    }

    private String buildReportAndDatasetList(Container c, Map<ViewCategory, List<NotificationInfo>> reports)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr><th>&nbsp;&nbsp;</th><th>Name</th><th>Type</th><th>Last Modified</th><th>Status</th></tr>");
        for (Map.Entry<ViewCategory, List<NotificationInfo>> catEntry : reports.entrySet())
        {
            sb.append("<tr><th colspan='5'>");
            sb.append("Category '").append(PageFlowUtil.filter(catEntry.getKey().getLabel())).append("'");
            sb.append("</th></tr>");
            int i = 0;
            for (NotificationInfo notificationInfo : catEntry.getValue())
            {
                String rowCls = (i++ % 2 == 0) ? "labkey-row" : "labkey-alternate-row";

                // Ugh, need a bogus ViewContext with a non-null ActionURL.
                // TODO: Use the deprecated ViewContext.getMockViewContext instead?
                // TODO: Better yet, fix all the getRunReportURL implementations to take Container, @Nullable ActionURL instead of ViewContext
                ViewContext context = new ViewContext();
                context.setActionURL(new ActionURL());
                context.setContainer(c);
                ActionURL url = "Dataset".equalsIgnoreCase(notificationInfo.getType()) ?
                        PageFlowUtil.urlProvider(StudyUrls.class).getDatasetURL(c, notificationInfo.getRowId()) :
                           notificationInfo.getReport().getRunReportURL(context);
                sb.append("<tr class='").append(rowCls).append("'>");
                sb.append("<td>&nbsp;&nbsp;</td>");
                sb.append("<td>");
                if (null != url)
                    sb.append("<a href='").append(PageFlowUtil.filter(url.getURIString())).append("'>");
                sb.append(PageFlowUtil.filter(notificationInfo.getName()));
                if (null != url)
                    sb.append("</a>");
                sb.append("</td>");
                sb.append("<td>").append(PageFlowUtil.filter(notificationInfo.getType())).append("</td>\n");
                sb.append("<td>").append(PageFlowUtil.filter(DateUtil.formatDateTime(c, notificationInfo.getModified()))).append("</td>");
                sb.append("<td>").append(PageFlowUtil.filter(notificationInfo.getStatus())).append("</td>");
                sb.append("</tr>");
            }
        }

        return sb.toString();
    }
}
