/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;

import java.io.PrintWriter;
import java.net.URISyntaxException;

/**
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public abstract class RunReportView extends TabStripView
{
    public static final String CACHE_PARAM = "cacheKey";
    public static final String MSG_PARAM = "msg";

    public static final String TAB_VIEW = "Chart";
    public static final String TAB_DATA = "Data";

    protected BindException _errors;

    protected void renderInternal(TabStripView model, PrintWriter out) throws Exception
    {
        final Report report = getReport();

        if (report == null)
        {
            include(new HtmlView("Unable to find the specified report"));
            return;
        }

        renderTitle(model, out);

        if (getViewContext().getActionURL().getParameter(MSG_PARAM) != null)
            getErrors().reject(MSG_PARAM, getViewContext().getActionURL().getParameter(MSG_PARAM));

        super.renderInternal(model, out);
    }


    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        final Report report = getReport();

        if (report.getDescriptor().getReportName() != null)
        {
            final StringBuffer sb = new StringBuffer();

            sb.append("<table><tr>");
            sb.append("<td style=\"border:none;font-weight:bold\">View :</td><td style=\"border:none\">");
            sb.append(report.getDescriptor().getReportName());
            if (report.getDescriptor().getReportDescription() != null)
            {
                sb.append("&nbsp;(");
                sb.append(report.getDescriptor().getReportDescription());
                sb.append(")");
            }
            sb.append("</td></tr>");
            String viewName = report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName);
            if (viewName != null)
            {
                sb.append("<tr><td style=\"border:none;font-weight:bold\">Created from Grid View :</td><td style=\"border:none\">");
                sb.append(viewName);
                sb.append("</td></tr>");
            }
            if (isReportInherited(report))
            {
                sb.append("<tr><td style=\"border:none;font-weight:bold\">Inherited from project :</td><td style=\"border:none\">");
                sb.append(report.getDescriptor().getContainerPath());
                sb.append("</td></tr>");
            }
            sb.append("</table>");
            include(new HttpView() {

                protected void renderInternal(Object model, PrintWriter out) throws Exception {
                    out.write("<table width=\"100%\"><tr class=\"labkey-wp-header\"><td align=\"left\">" + sb + "</td></tr><tr><td></td>&nbsp;</tr></table>");
                }
            });
        }
    }

    protected boolean isReportInherited(Report report)
    {
        return ReportUtil.isReportInherited(getViewContext().getContainer(), report);
    }

    public BindException getErrors()
    {
        if (_errors == null)
            _errors = new NullSafeBindException(this, "form");

        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    protected abstract Report getReport();

    protected URLHelper getBaseUrl()
    {
        ActionURL url = getViewContext().getActionURL();
        String returnURL = url.getParameter(ActionURL.Param.returnUrl);

        try
        {
            if (!StringUtils.isBlank(returnURL))
                return new URLHelper(returnURL);
            else
                return new URLHelper(url.toString());
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }
}
