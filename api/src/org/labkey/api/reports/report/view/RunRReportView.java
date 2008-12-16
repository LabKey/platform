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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.*;
import org.labkey.api.view.*;
import org.labkey.common.util.Pair;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 5, 2008
 */
public class RunRReportView extends RunReportView
{
    public static final String TAB_SOURCE = "Source";
    public static final String TAB_SYNTAX = "Help";

    protected Report _report;
    protected ReportIdentifier _reportId;

    public RunRReportView(Report report)
    {
        _report = report;
        if (_report != null)
        {
            _reportId = _report.getDescriptor().getReportId();
        }
    }

    protected void renderTitle(Object model, PrintWriter out) throws Exception
    {
        RReportBean bean = getReportForm();
        if (null != bean.getReportId())
        {
            ModelAndView title = new JspView<RReportBean>("/org/labkey/api/reports/report/view/reportHeader.jsp", bean);
            title.addObject("isReportInherited", isReportInherited(getReport()));

            include(title);
        }
    }

    protected Report getReport() throws Exception
    {
        return _report;
    }

    public List<NavTree> getTabList()
    {
        ActionURL url = getViewContext().cloneActionURL().replaceParameter(CACHE_PARAM, String.valueOf(_reportId));

        List<NavTree> tabs = new ArrayList<NavTree>();

        boolean saveChanges = TAB_SOURCE.equals(url.getParameter(TAB_PARAM));

        tabs.add(new RTabInfo(TAB_VIEW, TAB_VIEW, url, saveChanges));
        tabs.add(new RTabInfo(TAB_SOURCE, TAB_SOURCE, url, saveChanges));
        tabs.add(new RTabInfo(TAB_DATA, TAB_DATA, url, saveChanges));
        tabs.add(new RTabInfo(TAB_SYNTAX, TAB_SYNTAX, url, saveChanges));

        return tabs;
    }

    /**
     * Populates the form with cached report state information.
     * @throws Exception
     */
    public static void initFormFromCache(RReportBean form, String key, ViewContext context) throws Exception
    {
        HttpSession session = context.getRequest().getSession(true);
        Map<String, Object> bean = (Map<String, Object>)session.getAttribute(getReportCacheKey(key, context.getContainer()));

        BeanUtils.populate(form, bean);
    }

    protected RReportBean initReportCache(RReportBean form) throws Exception
    {
        String key = getViewContext().getActionURL().getParameter(CACHE_PARAM);
        if (!StringUtils.isEmpty(key))
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
                form.setIncludedReports(descriptor.getIncludedReports());
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

    public static final String RREPORT_CACHE_PREFIX = "RReportCache/";
    public static String getReportCacheKey(Object reportId, Container c)
    {
        StringBuffer sb = new StringBuffer();

        sb.append(RREPORT_CACHE_PREFIX);
        sb.append(c.getId());
        sb.append('/');
        sb.append(String.valueOf(reportId));

        return sb.toString();
    }

    public static void updateReportCache(RReportBean form, boolean replace) throws Exception
    {
        // saves report editing state in session
        Map<String, Object> bean = new HashMap<String, Object>();
        for (Pair<String, String> param : form.getParameters())
            bean.put(param.getKey(), param.getValue());

        // bad, need a better way to handle the bean type mismatch
        if (!form.getIncludedReports().isEmpty())
            bean.put(RReportDescriptor.Prop.includedReports.name(), form.getIncludedReports());

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

    private RReportBean getReportForm() throws Exception
    {
        RReportBean form = new RReportBean();
        form.setReportId(_reportId);
        form.reset(null, getViewContext().getRequest());

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
        RReportBean form = getReportForm();
        if (TAB_SOURCE.equals(tabId))
        {
            JspView designer = new JspView<RReportBean>("/org/labkey/api/reports/report/view/rReportDesigner.jsp", form, form.getErrors());
            if (getRenderAction() != null)
                designer.addObject("renderAction", getRenderAction().getLocalURIString());

            boolean isReadOnly = !_report.getDescriptor().canEdit(getViewContext());
            designer.addObject("readOnly", isReadOnly);

            view.addView(designer);

            if (!isReadOnly)
            {
                for (ReportService.ViewFactory vf : ReportService.get().getViewFactories())
                {
                    view.addView(vf.createView(getViewContext(), form));
                }
            }
            view.addView(new HttpView() {
                protected void renderInternal(Object model, PrintWriter out) throws Exception {
                    out.write("</form>");
                }
            });
        }
        else if (TAB_SYNTAX.equals(tabId))
        {
            view.addView(new JspView("/org/labkey/api/reports/report/view/rReportDesignerSyntaxRef.jsp"));
        }
        else if (TAB_VIEW.equals(tabId))
        {
            // for now limit pipeline view to saved reports
            if (null != form.getReportId() && form.isRunInBackground())
            {
                Report report = form.getReport();
                if (report instanceof RReport)
                {
                    view.addView(new JspView<RReport>("/org/labkey/api/reports/report/view/rReportRenderBackground.jsp", (RReport)report));

                    File logFile = new File(((RReport)report).getReportDir(), RReportJob.LOG_FILE_NAME);
                    PipelineStatusFile statusFile = PipelineService.get().getStatusFile(logFile.getAbsolutePath());
                    if (statusFile != null &&
                            !(statusFile.getStatus().equals(PipelineJob.WAITING_STATUS) ||
                              statusFile.getStatus().equals(RReportJob.PROCESSING_STATUS)))
                        view.addView(new RenderBackgroundRReportView((RReport)report));
                }
            }
            else
            {
                Report report = form.getReport();
                if (form.getIsDirty())
                    ((RReport)report).clearCache();
                view.addView(report.renderReport(getViewContext()));
            }
        }
        else if (TAB_DATA.equals(tabId))
        {
            view.addView(form.getReport().renderDataView(getViewContext()));
        }

        // add the view to manage tab and view dirty state
        JspView tabHandler = new JspView<RReportBean>("/org/labkey/api/reports/report/view/rReportTabHandler.jsp", form);
        view.addView(tabHandler);

        return view;
    }

    protected static class RTabInfo extends ReportTabInfo
    {
        boolean _saveChanges;

        public RTabInfo(String name, String id, ActionURL url, boolean saveChanges)
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
