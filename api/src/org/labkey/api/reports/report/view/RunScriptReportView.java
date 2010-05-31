/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.util.Pair;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            ModelAndView title = new JspView<ScriptReportBean>("/org/labkey/api/reports/report/view/reportHeader.jsp", bean);
            title.addObject("isReportInherited", isReportInherited(getReport()));

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

    /**
     * Populates the form with cached report state information.
     * @throws Exception
     */
    public static void initFormFromCache(ScriptReportBean form, String key, ViewContext context) throws Exception
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, Object> bean = (Map<String, Object>)session.getAttribute(getReportCacheKey(key, context.getContainer()));

        BeanUtils.populate(form, bean);
    }

    public static boolean isCacheValid(String key, ViewContext context) throws Exception
    {
        HttpSession session = context.getRequest().getSession(true);

        return session.getAttribute(getReportCacheKey(key, context.getContainer())) != null;
    }

    protected ScriptReportBean initReportCache(ScriptReportBean form) throws Exception
    {
        String key = getViewContext().getActionURL().getParameter(CACHE_PARAM);
        if (!StringUtils.isEmpty(key) && isCacheValid(key, getViewContext()))
        {
            initFormFromCache(form, key, getViewContext());
        }
        else if (_report != null)
        {
            ReportDescriptor reportDescriptor = _report.getDescriptor();
            if (reportDescriptor instanceof RReportDescriptor)
            {
                RReportDescriptor descriptor = (RReportDescriptor)reportDescriptor;

                form.setQueryName(descriptor.getProperty(ReportDescriptor.Prop.queryName));
                form.setSchemaName(descriptor.getProperty(ReportDescriptor.Prop.schemaName));
                form.setViewName(descriptor.getProperty(ReportDescriptor.Prop.viewName));
                form.setDataRegionName(descriptor.getProperty(ReportDescriptor.Prop.dataRegionName));
                form.setReportName(descriptor.getProperty(ReportDescriptor.Prop.reportName));
                form.setReportType(descriptor.getProperty(ReportDescriptor.Prop.reportType));
                form.setScript(descriptor.getProperty(RReportDescriptor.Prop.script));
                form.setRunInBackground(BooleanUtils.toBoolean(descriptor.getProperty(RReportDescriptor.Prop.runInBackground)));
                form.setFilterParam(descriptor.getProperty(ReportDescriptor.Prop.filterParam));
                form.setShareReport((descriptor.getOwner() == null));
                form.setInheritable((descriptor.getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0);
                form.setCached(BooleanUtils.toBoolean(descriptor.getProperty(ReportDescriptor.Prop.cached)));
                form.setScriptExtension(descriptor.getProperty(RReportDescriptor.Prop.scriptExtension));

                //if (descriptor.getProperty("redirectUrl") != null)
                //    form.setRedirectUrl(descriptor.getProperty("redirectUrl"));
                form.setRedirectUrl(getViewContext().getActionURL().getParameter("redirectUrl"));

                // finally save in session cache
                updateReportCache(form, true);
            }
        }
        return form;
    }

    public static final String SCRIPT_REPORT_CACHE_PREFIX = "ScriptReportCache/";
    public static String getReportCacheKey(Object reportId, Container c)
    {
        StringBuffer sb = new StringBuffer();

        sb.append(SCRIPT_REPORT_CACHE_PREFIX);
        sb.append(c.getId());
        sb.append('/');
        sb.append(String.valueOf(reportId));

        return sb.toString();
    }

    public static void updateReportCache(ScriptReportBean form, boolean replace) throws Exception
    {
        // saves report editing state in session
        Map<String, Object> bean = new HashMap<String, Object>();
        for (Pair<String, String> param : form.getParameters())
            bean.put(param.getKey(), param.getValue());

        HttpSession session = form.getRequest().getSession(true);
        if (replace)
            session.setAttribute(getReportCacheKey(form.getReportId(), form.getContainer()), bean);
        else
        {
            String key = getReportCacheKey(form.getReportId(), form.getContainer());
            Object o = session.getAttribute(key);
            if (o instanceof Map)
            {
                ((Map)o).put(ReportDescriptor.Prop.viewName.name(), null);
                ((Map)o).putAll(bean);
            }
            else
                session.setAttribute(key, bean);
        }
    }

    private ScriptReportBean getReportForm() throws Exception
    {
        ScriptReportBean form = new ScriptReportBean();
        form.setReportId(_reportId);
        form.setViewContext(getViewContext());

        if (getErrors() != null)
            form.setErrors(getErrors());
        else
            form.setErrors(new BindException(form, "form"));

        initReportCache(form);

        // set the default redirect url
        if (form.getRedirectUrl() == null)
            form.setRedirectUrl(getViewContext().cloneActionURL().
                    //deleteParameter(TAB_PARAM).
                    replaceParameter(TAB_PARAM, TAB_SOURCE).
                    deleteParameter(CACHE_PARAM).getLocalURIString());

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
            JspView designer = new JspView<ScriptReportBean>("/org/labkey/api/reports/report/view/scriptReportDesigner.jsp", form, form.getErrors());
            if (getRenderAction() != null)
                designer.addObject("renderAction", getRenderAction().getLocalURIString());

            boolean isReadOnly = !_report.getDescriptor().canEdit(getViewContext().getUser(), getViewContext().getContainer());
            designer.addObject("readOnly", isReadOnly);

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