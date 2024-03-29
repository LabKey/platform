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
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspTemplate;
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
        replacements.add("emailPrefsUrl", String.class, "URL to set email preferences", ContentType.Plain, c -> _emailPrefsUrl == null ? null : _emailPrefsUrl.getURIString());
        replacements.add("reportAndDatasetList", String.class, "Formatted list of changed reports/datasets", ContentType.HTML, c -> _reportAndDatasetList);
    }

    public void init(Container c, Map<ViewCategory, List<NotificationInfo>> reports)
    {
        _emailPrefsUrl = PageFlowUtil.urlProvider(ReportUrls.class).urlManageNotifications(c);
        _reportAndDatasetList = buildReportAndDatasetList(c, reports);
    }

    public static class NotificationBean
    {
        private Map<ViewCategory, List<NotificationInfo>> _reports;

        public Map<ViewCategory, List<NotificationInfo>> getReports()
        {
            return _reports;
        }

        public void setReports(Map<ViewCategory, List<NotificationInfo>> reports)
        {
            _reports = reports;
        }
    }

    private String buildReportAndDatasetList(Container c, Map<ViewCategory, List<NotificationInfo>> reports)
    {
        try
        {
            NotificationBean bean = new NotificationBean();
            bean.setReports(reports);
            JspTemplate<NotificationBean> template = new JspTemplate<>("/org/labkey/query/reports/view/reportAndDatasetList.jsp", bean);

            // need to push in a view context to render the content
            ViewContext context = new ViewContext();
            context.setUser(User.getSearchUser());
            context.setContainer(c);
            context.setActionURL(new ActionURL());
            template.setViewContext(context);

            return template.render();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
