/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.VBox;
import org.springframework.web.servlet.ModelAndView;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/*
* User: Karl Lum
* Date: Dec 29, 2008
* Time: 3:50:38 PM
*/

/**
 * Tabbed view used for executing script engine reports
 */
public class RunScriptReportView extends RunReportView
{
    public static final String TAB_SOURCE = "Source";

    protected Report _report;
    protected ReportIdentifier _reportId;

    public RunScriptReportView(Report report)
    {
        _report = report;

        if (_report != null)
        {
            _reportId = _report.getDescriptor().getReportId();
        }
    }


    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        ScriptReportBean bean = getReportForm();

        if (null != bean.getReportId())
        {
            bean.setInherited(isReportInherited(getReport()));
            ModelAndView title = new JspView<ScriptReportBean>("/org/labkey/api/reports/report/view/reportHeader.jsp", bean);

            include(title);
        }
    }


    protected Report getReport()
    {
        return _report;
    }


    public List<NavTree> getTabList()
    {
        URLHelper url = getBaseUrl().replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

        List<NavTree> tabs = new ArrayList<NavTree>();

        boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

        tabs.add(new ScriptTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
        tabs.add(new ScriptTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
        tabs.add(new ScriptTabInfo(TAB_DATA, TAB_DATA, url, saveChanges));

        return tabs;
    }


    private ScriptReportBean getReportForm() throws Exception
    {
        return populateReportForm(new ScriptReportBean());
    }


    protected <SRB extends ScriptReportBean> SRB populateReportForm(SRB form) throws Exception
    {
        form.setReportId(_reportId);
        form.setViewContext(getViewContext());

        if (getErrors() != null)
            form.setErrors(getErrors());
        else
            form.setErrors(new NullSafeBindException(form, "form"));

        ReportDesignerSessionCache.initReportCache(form, _report);

        if (null == form.getScript())
        {
            form.setScript(((ScriptReport)_report).getDefaultScript());
        }

        // set the default redirect url
        if (form.getRedirectUrl() == null)
            form.setRedirectUrl(getViewContext().cloneActionURL().
                    replaceParameter(TAB_PARAM, TAB_SOURCE).
                    deleteParameter(CACHE_PARAM).getLocalURIString());

    /*
        TODO: This redirect url code was in RunRReportView.java

        // set the default redirect url
        if (form.getRedirectUrl() == null)
            form.setRedirectUrl(getBaseUrl().
                    replaceParameter(TAB_PARAM, TAB_SOURCE).
                    deleteParameter(CACHE_PARAM).getLocalURIString());
     */

        return form;
    }

    protected ActionURL getRenderAction() throws Exception
    {
        return null;
    }


    public HttpView getTabView(String tabId) throws Exception
    {
        VBox view = new VBox();
        ScriptReportBean form = getReportForm();

        if (TAB_SOURCE.equals(tabId))
        {
            form.setRenderURL(getRenderAction());
            form.setReadOnly(!_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer()));
            JspView designer = new JspView<ScriptReportBean>("/org/labkey/api/reports/report/view/scriptReportDesigner.jsp", form, form.getErrors());

            view.addView(designer);

            view.addView(new HttpView() {
                    protected void renderInternal(Object model, PrintWriter out) throws Exception {
                        out.write("</form>");
                    }
                });
        }
        else if (TAB_VIEW.equals(tabId))
        {
            Report report = form.getReport();
            if (form.getIsDirty())
                report.clearCache();
            view.addView(report.renderReport(getViewContext()));
        }
        else if (TAB_DATA.equals(tabId))
        {
            view.addView(form.getReport().renderDataView(getViewContext()));
        }

        // add the view to manage tab and view dirty state
        JspView tabHandler = new JspView<ScriptReportBean>("/org/labkey/api/reports/report/view/rReportTabHandler.jsp", form);
        view.addView(tabHandler);

        return view;
    }


    protected static class ScriptTabInfo extends ReportTabInfo
    {
        boolean _saveChanges;

        public ScriptTabInfo(String name, String id, URLHelper url, boolean saveChanges)
        {
            super(name, id, url);

            _saveChanges = saveChanges;

            if (_saveChanges)
                setScript("switchTab('" + getValue() + "', saveChanges)");
            else
                setScript("switchTab('" + getValue() + "')");
        }
    }
}
