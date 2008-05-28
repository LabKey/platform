/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.data.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.*;
import org.labkey.common.util.Pair;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class QueryView extends WebPartView<Object>
{
    public static final String DATAREGIONNAME_DEFAULT = "query";
    protected DataRegion.ButtonBarPosition _buttonBarPosition = DataRegion.ButtonBarPosition.BOTH;
    private boolean _showDetailsColumn = true;

    static public QueryView create(QueryForm form) throws ServletException
    {
        UserSchema s = form.getSchema();
        if (s == null)
        {
            HttpView.throwNotFound("Could not find schema: " + form.getSchemaName());
            return null;
        }
        return s.createView(form.getViewContext(), form.getQuerySettings());
    }

    private QueryDefinition _queryDef;
    private CustomView _customView;
    private UserSchema _schema;
    private List<QueryException> _errors = new ArrayList<QueryException>();
    private QuerySettings _settings;
    private boolean _showRecordSelectors = false;
    private boolean _showCustomizeViewLinkInButtonBar = false;

    private boolean _shadeAlternatingRows = false;
    private boolean _showColumnSeparators = false;
    private boolean _showChangeViewPicker = true;
    private Report _report;

    private boolean _showExportButtons = true;
    private boolean _allowExcelWebQuery = true;
    private boolean _useQueryViewActionExportURLs = false;
    private boolean _showChartButton = false;
    private boolean _showRReportButton = true;
    private boolean _printView = false;
    private boolean _exportView = false;
    private ReportService.ItemFilter _itemFilter = new ReportService.ItemFilter(){
        public boolean accept(String type, String label)
        {
            if (RReport.TYPE.equals(type)) return true;
            return false;
        }
    };

    TableInfo _table;

    public QueryView(QueryForm form)
    {
        this(form.getSchema(), form.getQuerySettings());
    }


    /** Must call setSettings before using the view */
    public QueryView(UserSchema schema)
    {
        setSchema(schema);
    }

    public QueryView(UserSchema schema, QuerySettings settings)
    {
        setSchema(schema);
        setSettings(settings);
    }

    public QuerySettings getSettings()
    {
        return _settings;
    }


    protected void setSettings(QuerySettings settings)
    {
         if (null != _settings || null == _schema)
            throw new IllegalStateException();
        _settings = settings;
        _queryDef = settings.getQueryDef(_schema);
        _customView = settings.getCustomView(getViewContext(), _queryDef);
        _report = settings.getReportView(getViewContext());
    }


    protected int getMaxRows()
    {
        if (getShowRows() != ShowRows.DEFAULT)
            return 0;
        return getSettings().getMaxRows();
    }


    protected long getOffset()
    {
        if (getShowRows() != ShowRows.DEFAULT)
            return 0;
        return getSettings().getOffset();
    }

    protected ShowRows getShowRows()
    {
        return getSettings().getShowRows();
    }

    protected String getSelectionKey()
    {
        return getSettings().getSelectionKey();
    }

    protected boolean verboseErrors()
    {
        return true;
    }


    protected boolean ignoreUserFilter()
    {
        return getViewContext().getRequest().getParameter(param(QueryParam.ignoreFilter)) != null || (getSettings() != null && getSettings().getIgnoreUserFilter());
    }


    protected void renderErrors(PrintWriter out, String message, List<? extends Throwable> errors)
    {
        out.print("<p style=\"color:red\">");
        out.print(PageFlowUtil.filter(message));
        out.print("</p>");
        if (verboseErrors())
        {
            for (Throwable e : errors)
            {
                if (e instanceof QueryParseException)
                {
                    out.print(PageFlowUtil.filter(e.getMessage()));
                }
                else
                {
                    out.print(PageFlowUtil.filter(e.toString()));
                }
                out.print("<br>");
            }
        }
    }

    public QueryPicker getColumnListPicker(HttpServletRequest request)
    {
        Map<String, CustomView> customViews = _queryDef.getCustomViews(getUser(), request);
        Map<String, String> options = new LinkedHashMap<String, String>();
        options.put("", "<default>");
        for (CustomView view : customViews.values())
        {
            if (view.isHidden())
                continue;
            String label = view.getName();
            if (label == null)
                continue;
            options.put(view.getName(), label);
        }
        String current = "";
        if (_customView != null)
            current = _customView.getName();
        else if (_report != null)
            current = getSettings().getViewName();//_report.getDescriptor().getReportName();

        addReportsToChangeViewPicker(options);

        return new QueryPicker("Custom View:", param(QueryParam.viewName), current, options);
    }

    public static final String REPORTID_PARAM = "reportID=";
    protected void addReportsToChangeViewPicker(Map<String, String> options)
    {
        String reportKey = ChartUtil.getReportKey(getSchema().getSchemaName(), getSettings().getQueryName());
        for (Report report : ChartUtil.getReports(getViewContext(), reportKey, true))
        {
            options.put(REPORTID_PARAM + report.getDescriptor().getReportId(), report.getDescriptor().getReportName());
        }
    }

    protected List<QueryPicker> getQueryPickers()
    {
        ArrayList<QueryPicker> ret = new ArrayList<QueryPicker>();
        QueryPicker picker = new QueryPicker("Query:", param(QueryParam.queryName), _queryDef.getName(), getSchema().getTableAndQueryNames(true));
        if (getQueryDef().getDescription() != null)
        {
            picker.setDescriptionHTML("<i>" + PageFlowUtil.filter(getQueryDef().getDescription()) + "</i>");
        }
        ret.add(picker);
        return ret;
    }
    protected List<QueryPicker> getChangeViewPickers()
    {
        ArrayList<QueryPicker> ret = new ArrayList<QueryPicker>();
        ret.add(getColumnListPicker(getViewContext().getRequest()));
        return ret;
    }

    protected void renderQueryPicker(PrintWriter out)
    {
        renderPickers(out, "queryName", urlRefreshQuery(), getQueryPickers());
    }

    public void setShowChangeViewPicker(boolean showChangeViewPicker)
    {
        _showChangeViewPicker = showChangeViewPicker;
    }

    protected void renderChangeViewPickers(PrintWriter out)
    {
        if (_showChangeViewPicker)
            renderPickers(out, "view", urlChangeView(), getChangeViewPickers());
    }

    protected User getUser()
    {
        return _schema.getUser();
    }

    public UserSchema getSchema()
    {
        return _schema;
    }

    protected void setSchema(UserSchema schema)
    {
        if (null != _settings || null != _schema)
            throw new IllegalStateException();
        _schema = schema;
    }

    protected void renderPickers(PrintWriter out, String formName, ActionURL target, List<QueryPicker> pickers)
    {
        if (target == null)
            return;

        boolean requireGoButton = false;
        StringBuilder strPickers = new StringBuilder();
        for (QueryPicker picker : pickers)
        {
            String strPicker = picker.toString();
            if (!StringUtils.isEmpty(strPicker))
            {
                if (!picker.isAutoRefresh())
                    requireGoButton = true;
                strPickers.append(strPicker);
            }
        }
        if (strPickers.length() == 0)
            return;

        out.print("<form class=\"normal\" method=\"GET\" action=\"");
        out.print(PageFlowUtil.filter(target.getPath()));
        out.print("\" name=\"");
        out.print(PageFlowUtil.filter(formName));
        out.print("\">");
        for (Pair param : target.getParameters())
        {
            out.print(hiddenField(param.getKey(), param.getValue()));
        }
        out.write(strPickers.toString());
        if (requireGoButton)
        {
            out.print("<input type=\"image\" src=\"");
            out.print(PageFlowUtil.buttonSrc("go"));
            out.print("\">");
        }
        out.print("</form>");
    }

    public Container getContainer()
    {
        return _schema.getContainer();
    }

    protected ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = _schema.urlFor(action, _queryDef);

        switch (action)
        {
            case deleteQuery:
            case designQuery:
            case sourceQuery:
                break;
            case chooseColumns:
                ret.addParameter(QueryParam.srcURL.toString(), getRootContext().getActionURL().toString());
                ret.addParameter(QueryParam.dataRegionName.toString(), getDataRegionName());
                ret.addParameter(QueryParam.queryName.toString(), getSettings().getQueryName());
                if (getSettings().getViewName() != null)
                    ret.addParameter(QueryParam.viewName.toString(), getSettings().getViewName());
                break;
            case deleteQueryRows:
                ret.addParameter(QueryParam.srcURL.toString(), getViewContext().getActionURL().toString());
                break;
            case exportRowsExcel:
            case exportRowsTsv:
            case printRows:
            {
                if (_useQueryViewActionExportURLs)
                {
                    ret = PageFlowUtil.expandLastFilter(getViewContext());
                    ret.addParameter("exportType", action.name());
                    ret.addParameter("exportRegion", getDataRegionName());
                    break;
                }
                ActionURL expandedURL = PageFlowUtil.expandLastFilter(getViewContext());
                addParamsByPrefix(ret, expandedURL, getDataRegionName() + ".", DATAREGIONNAME_DEFAULT +  ".");
                break;
            }
            case excelWebQueryDefinition:
            {
                if (_useQueryViewActionExportURLs)
                {
                    ActionURL expandedURL = PageFlowUtil.expandLastFilter(getViewContext());
                    expandedURL.addParameter("exportType", "excelWebQuery");
                    expandedURL.addParameter("exportRegion", getDataRegionName());
                    ret.addParameter("queryViewActionURL", expandedURL.getLocalURIString());
                    break;
                }
                ActionURL expandedURL = PageFlowUtil.expandLastFilter(getViewContext());
                addParamsByPrefix(ret, expandedURL, getDataRegionName() + ".", DATAREGIONNAME_DEFAULT +  ".");
                break;
            }
            case createRReport:
                RReportBean bean = new RReportBean();
                bean.setReportType(RReport.TYPE);
                bean.setSchemaName(_schema.getSchemaName());
                bean.setQueryName(getSettings().getQueryName());
                bean.setViewName(getSettings().getViewName());
                bean.setDataRegionName(getDataRegionName());

                bean.setRedirectUrl(getViewContext().getActionURL().toString());
                return ChartUtil.getRReportDesignerURL(_viewContext, bean);
        }
        return ret;
    }

    protected ActionButton actionButton(String label, QueryAction action)
    {
        return actionButton(label, action, null, null);
    }

    protected ActionButton actionButton(String label, QueryAction action, String parameterToAdd, String parameterValue)
    {
        ActionURL url = urlFor(action);
        if (url == null)
        {
            return null;
        }
        if (parameterToAdd != null)
            url.addParameter(parameterToAdd, parameterValue);
        return new ActionButton(url.getEncodedLocalURIString(), label, DataRegion.MODE_ALL, ActionButton.Action.LINK);
    }

    protected void addButton(ButtonBar bar, ActionButton button)
    {
        if (button == null)
            return;
        bar.add(button);
    }

    protected String param(QueryParam param)
    {
        return param(param.toString());
    }
    protected String param(String param)
    {
        return getDataRegionName() + "." + param;
    }

    protected ActionURL urlRefreshQuery()
    {
        ActionURL ret = getSettings().getSortFilterURL();
        ret.deleteParameter(param(QueryParam.queryName));
        ret.deleteParameter(param(QueryParam.viewName));
        ret.deleteParameter("x");
        ret.deleteParameter("y");
        for (String key : ret.getKeysByPrefix(getDataRegionName() + "."))
        {
            ret.deleteFilterParameters(key);
        }
        return ret;
    }

    protected ActionURL urlBaseView()
    {
        ActionURL ret = getSettings().getSortFilterURL();
        ret.deleteParameter("x");
        ret.deleteParameter("y");
        for (String key : ret.getKeysByPrefix(getDataRegionName() + "."))
        {
            ret.deleteFilterParameters(key);
        }
        assert null == ret.getParameter(DataRegion.LAST_FILTER_PARAM);
        return ret;
    }

    protected ActionURL urlChangeView()
    {
        ActionURL ret = getSettings().getSortFilterURL();
        ret.deleteParameter(param(QueryParam.viewName));
        ret.deleteParameter("x");
        ret.deleteParameter("y");
        ret.deleteParameter(RunReportView.CACHE_PARAM);
        ret.deleteParameter(RunReportView.TAB_PARAM);
        return ret;
    }

    protected void addParamsByPrefix(ActionURL target, ActionURL source, String oldPrefix, String newPrefix)
    {
        for (String key : source.getKeysByPrefix(oldPrefix))
        {
            String newKey = newPrefix + key.substring(oldPrefix.length());
            for (String value : source.getParameters(key))
            {
                target.addParameter(newKey, value);
            }
        }
    }

    protected boolean canDelete()
    {
        TableInfo table = getTable();
        return table != null && table.hasPermission(getUser(), ACL.PERM_DELETE);
    }

    protected boolean showRecordSelectors()
    {
        return _showRecordSelectors || canDelete();
    }

    public void setShowRecordSelectors(boolean showRecordSelectors)
    {
        _showRecordSelectors = showRecordSelectors;
    }

    protected void populateButtonBar(DataView view, ButtonBar bar)
    {
        populateButtonBar(view, bar, false);
    }

    protected void populateReportButtonBar(ButtonBar bar)
    {
        if (_showExportButtons)
        {
            bar.add(createPrintButton());
        }

        if (_showChartButton || _showRReportButton)
        {
            bar.add(createReportButton());
        }
    }

    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        if (showRecordSelectors())
        {
            if (canDelete())
            {
                ActionURL urlDelete = urlFor(QueryAction.deleteQueryRows);
                if (urlDelete != null)
                {
                    ActionButton btnDelete = new ActionButton("", "Delete");
                    btnDelete.setScript("return verifySelected(this.form, \"" + urlDelete.toString() + "\", \"post\", \"checkboxes\")");
                    btnDelete.setActionType(ActionButton.Action.GET);
                    bar.add(btnDelete);
                }
            }
        }

        if (_showCustomizeViewLinkInButtonBar)
        {
            bar.add(createCustomizeViewButton());
        }

        if (_showExportButtons)
        {
            bar.add(createExportMenuButton(exportAsWebPage));

            bar.add(createPrintButton());
        }

        if (view.getDataRegion().getShowPagination())
        {
            addButton(bar, createPageSizeMenuButton());
        }

        if (_showChartButton || _showRReportButton)
        {
            bar.add(createReportButton());
        }
    }

    protected ActionButton createPrintButton()
    {
        ActionButton btnPrint = actionButton("Print", QueryAction.printRows);
        btnPrint.setTarget("_blank");
        return btnPrint;
    }

    protected MenuButton createExportMenuButton(boolean exportAsWebPage)
    {
        MenuButton exportMenuButton = new MenuButton("Export");
        exportMenuButton.addMenuItem("Export All to Excel (.xls)", urlFor(QueryAction.exportRowsExcel).getLocalURIString());
        ActionURL tsvURL = urlFor(QueryAction.exportRowsTsv);
        if (exportAsWebPage)
        {
            tsvURL.addParameter("exportAsWebPage", "true");
        }
        exportMenuButton.addMenuItem("Export All to Text (.tsv)", tsvURL.getLocalURIString());
        if (_allowExcelWebQuery)
        {
            exportMenuButton.addMenuItem("Excel Web Query (.iqy)", urlFor(QueryAction.excelWebQueryDefinition).getLocalURIString());
        }
        return exportMenuButton;
    }

    protected MenuButton createPageSizeMenuButton()
    {
        final int maxRows = getMaxRows();
        final boolean showingAll = getShowRows() == ShowRows.ALL;
        final boolean showingSelected = getShowRows() == ShowRows.SELECTED;

        String header = maxRows + " per page";
        if (showingAll)
            header = "All Rows";
        else if (showingSelected)
            header = "Selected";

        MenuButton pageSizeMenu = new MenuButton(header) {
            public boolean shouldRender(RenderContext ctx)
            {
                ResultSet rs = ctx.getResultSet();
                if (!(rs instanceof Table.TableResultSet))
                    return false;
                if (((Table.TableResultSet)rs).isComplete() &&
                    ctx.getCurrentRegion().getOffset() == 0 &&
                    !(showingAll || showingSelected || maxRows > 0))
                    return false;
                return super.shouldRender(ctx);
            }
        };

        // insert current maxRows into sorted list of possible sizes
        List<Integer> sizes = new LinkedList<Integer>(Arrays.asList(40, 100, 250, 1000));
        if (maxRows > 0)
        {
            int index = Collections.binarySearch(sizes, maxRows);
            if (index < 0)
            {
                sizes.add(-index-1, maxRows);
            }
        }

        String regionName = getDataRegionName();
        for (Integer pageSize : sizes)
        {
            boolean checked = pageSize.intValue() == maxRows;
            pageSizeMenu.addMenuItem(String.valueOf(pageSize) + " per page", "#",
                    "setMaxRows(" + PageFlowUtil.jsString(regionName) + ", " + String.valueOf(pageSize) + ")", checked);
        }
        pageSizeMenu.addSeparator();
        pageSizeMenu.addMenuItem("Show Selected", "#",
                "setShowSelected(" + PageFlowUtil.jsString(regionName) + ")", showingSelected);
        pageSizeMenu.addMenuItem("Show All", "#",
                "setShowAll(" + PageFlowUtil.jsString(regionName) + ")", showingAll);

        return pageSizeMenu;
    }

    protected ActionButton createCustomizeViewButton()
    {
        ActionButton customizeButton = new ActionButton(urlFor(QueryAction.chooseColumns).getEncodedLocalURIString(), "Customize View");
        customizeButton.setActionType(ActionButton.Action.LINK);
        return customizeButton;
    }

    public void setViewItemFilter(ReportService.ItemFilter filter)
    {
        if (filter != null)
            _itemFilter = filter;
    }

    public ActionButton createReportButton()
    {
        NavTreeMenuButton button = new NavTreeMenuButton("Views");
        NavTree menu = button.getNavTree();
        addCreateViewItems(menu);
        if (_report != null)
            button.addMenuItem("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer()));

        return button;
    }

    public MenuButton createViewButton(ReportService.ItemFilter filter)
    {
        setViewItemFilter(filter);
        String current = "";
        if (_customView != null)
            current = _customView.getName();
        else if (_report != null)
            current = getSettings().getViewName();

        ActionURL target = urlChangeView();
        NavTreeMenuButton button = new NavTreeMenuButton("Views");
        NavTree menu = button.getNavTree();

        // default grid view stays at the top level
        addChild(menu, "default", "", target, current, getViewContext().getContextPath() + "/reports/grid.gif");

        // existing views
        addCustomViews(menu, target, current);
        addReportViews(menu, target, current);

        button.addSeparator();

        if (_report == null)
        {
            Map<String, String> menuItems = new HashMap<String, String>();
            getSettings().setSchemaName(getSchema().getSchemaName());
            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                provider.getReportDesignURL(getViewContext(), getSettings(), menuItems);
            }
            NavTree submenu = menu.addChild("Create");
            for (Map.Entry<String, String> entry : menuItems.entrySet())
            {
                if (_itemFilter.accept(entry.getKey(), entry.getValue()))
                {
                    Report report = ReportService.get().createReportInstance(entry.getKey());
                    if (report != null)
                        submenu.addChild(report.getTypeDescription(), entry.getValue());
                }
            }
        }
        addCustomizeViewItems(button);
        if (_report != null)
            button.addMenuItem("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer()));
        else
            button.addMenuItem("Manage Views", false, true);

        return button;
    }

    private void addChild(NavTree menu, String label, String paramValue, ActionURL url, String currentView, String iconPath)
    {
        NavTree item = menu.addChild(label, url.replaceParameter(param(QueryParam.viewName), paramValue).getLocalURIString());
        if (paramValue.equals(currentView))
            item.setSelected(true);
        else
            item.setImageSrc(iconPath);
    }

    protected void addCustomViews(NavTree menu, ActionURL target, String currentView)
    {
        Map<String, CustomView> customViews = getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest());
        NavTree subMenu = null;

        for (CustomView view : customViews.values())
        {
            if (view.isHidden())
                continue;
            String label = view.getName();
            if (label == null)
                continue;
            if (subMenu == null)
                subMenu = menu;
                //subMenu = menu.addChild("Custom Views");

            addChild(subMenu, label, label, target, currentView, getViewContext().getContextPath() + "/reports/grid.gif");
        }
    }

    protected void addReportViews(NavTree menu, ActionURL target, String currentView)
    {
        String reportKey = ChartUtil.getReportKey(getSchema().getSchemaName(), getSettings().getQueryName());
        Map<String, List<Report>> views = new TreeMap<String, List<Report>>();
        for (Report report : ChartUtil.getReports(getViewContext(), reportKey, true))
        {
            if (!views.containsKey(report.getType()))
                views.put(report.getType(), new ArrayList<Report>());

            views.get(report.getType()).add(report);
        }

        for (Map.Entry<String, List<Report>> entry : views.entrySet())
        {
            NavTree subMenu = null;
            for (Report report : entry.getValue())
            {
                if (subMenu == null)
                    subMenu = menu; //menu.addChild(report.getTypeDescription());

                addChild(subMenu, report.getDescriptor().getReportName(), QueryView.REPORTID_PARAM + report.getDescriptor().getReportId(),
                        target, currentView, ReportService.get().getReportIcon(getViewContext(), report.getType()));
            }
        }
    }

    protected void addCreateViewItems(NavTree menu)
    {
        if (_report == null)
        {
            Map<String, String> reportDesigners = new HashMap<String, String>();
            getSettings().setSchemaName(getSchema().getSchemaName());
            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                provider.getReportDesignURL(getViewContext(), getSettings(), reportDesigners);
            }

            List<Pair<String, String>> menuItems = new ArrayList<Pair<String, String>>();
            for (Map.Entry<String, String> entry : reportDesigners.entrySet())
            {
                if (_itemFilter.accept(entry.getKey(), entry.getValue()))
                {
                    Report report = ReportService.get().createReportInstance(entry.getKey());
                    if (report != null)
                        menuItems.add(new Pair(report.getTypeDescription(), entry.getValue()));
                        //submenu.addChild(report.getTypeDescription(), entry.getValue());
                }
            }

            NavTree submenu = (menuItems.size() > 1) ? menu.addChild("Create") : menu;
            String prefix = (menuItems.size() > 1) ? "" : "Create ";
            for (Pair<String, String> item : menuItems)
            {
                submenu.addChild(prefix + item.getKey(), item.getValue());
            }
        }
    }

    protected String textLink(String text, ActionURL url, String anchorElementId)
    {
        if (url == null)
            return null;
        StringBuilder sb = new StringBuilder();
        sb.append("[<a href=\"");
        sb.append(url.getEncodedLocalURIString());
        sb.append("\"");
        if (anchorElementId != null)
        {
            sb.append(" id=\"");
            sb.append(anchorElementId);
            sb.append("\"");
        }
        sb.append(">");
        sb.append(PageFlowUtil.filter(text));
        sb.append("</a>]&nbsp;");
        return sb.toString();
    }

    protected String textLink(String text, ActionURL url)
    {
        return textLink(text, url, null);
    }

    public void renderCustomizeLinks(PrintWriter out) throws Exception
    {
        renderCustomizeViewLink(out);
        if (_customView != null && _customView.hasFilterOrSort())
        {
            ActionURL url = getSettings().getSortFilterURL();
            if (ignoreUserFilter())
            {
                url.deleteParameter(param(QueryParam.ignoreFilter));
                out.write(textLink("Apply View Filter", url));
            }
            else
            {
                url.replaceParameter(param(QueryParam.ignoreFilter), "1");
                out.write(textLink("Ignore View Filter", url));
            }
        }
    }

    public void renderCustomizeViewLink(PrintWriter out)
    {
        if (!_showCustomizeViewLinkInButtonBar && _report == null)
        {
            out.write(textLink("Customize View", urlFor(QueryAction.chooseColumns)));
            QueryDefinition queryDef = getQueryDef();
            if (queryDef.canEdit(getUser()) && getContainer().equals(queryDef.getContainer()))
            {
                out.write(textLink("Edit Query", getSchema().urlFor(QueryAction.designQuery, queryDef)));
            }
        }
    }

    public void addCustomizeViewItems(MenuButton button) 
    {
        if (_report == null)
        {
            button.addMenuItem("Customize View", urlFor(QueryAction.chooseColumns));
            QueryDefinition queryDef = getQueryDef();
            if (queryDef.canEdit(getUser()) && getContainer().equals(queryDef.getContainer()))
            {
                button.addMenuItem("Edit Query", getSchema().urlFor(QueryAction.designQuery, queryDef));
            }
        }

        if (_customView != null && _customView.hasFilterOrSort())
        {
            ActionURL url = getSettings().getSortFilterURL();
            if (ignoreUserFilter())
            {
                url.deleteParameter(param(QueryParam.ignoreFilter));
                button.addMenuItem("Apply View Filter", url.toString(), null, false);
            }
            else
            {
                url.replaceParameter(param(QueryParam.ignoreFilter), "1");
                button.addMenuItem("Apply View Filter", url.toString(), null, true);
            }
        }
    }

    public String getDataRegionName()
    {
        return getSettings().getDataRegionName();
    }

    protected String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    protected String hiddenField(Object name, Object value)
    {
        return "<input type=\"hidden\" name=\"" + h(name) + "\" value=\"" + h(value) + "\">";
    }

    @Override
    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        if (!isPrintView())
        {
            if (getSettings().getAllowChooseView())
            {
                renderCustomizeLinks(out);
            }

            if (getSettings().getAllowChooseQuery())
            {
                renderQueryPicker(out);
            }
            if (getSettings().getAllowChooseView())
            {
                renderChangeViewPickers(out);
            }
        }

        renderDataRegion(out);
    }

    @Override
    final protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        super.renderView(model, request, response);
    }

    protected SqlDialect getSqlDialect()
    {
        return getSchema().getDbSchema().getSqlDialect();
    }

    protected DataRegion createDataRegion()
    {
        List<DisplayColumn> displayColumns = getDisplayColumns();
        DataRegion rgn = new DataRegion();
        rgn.setMaxRows(getMaxRows());
        rgn.setOffset(getOffset());
        rgn.setShowRows(getShowRows());
        rgn.setShowRecordSelectors(showRecordSelectors());
        rgn.setName(getDataRegionName());
        rgn.setSelectionKey(getSelectionKey());
        rgn.setDisplayColumns(displayColumns);

        rgn.setShadeAlternatingRows(isShadeAlternatingRows());
        rgn.setShowColumnSeparators(isShowColumnSeparators());

        rgn.setTable(getTable());
        return rgn;
    }

    public void setButtonBarPosition(DataRegion.ButtonBarPosition buttonBarPosition)
    {
        _buttonBarPosition = buttonBarPosition;
    }

    protected DataView createDataView()
    {
        DataRegion rgn = createDataRegion();
        GridView ret = new GridView(rgn)
        {
            /**
             * Since we're using user-defined sql, we can get a SQLException that
             * doesn't indicate a bug in the product. Don't log to mothership,
             * and tell the user what happened
             */
            @Override
            protected void renderException(Throwable t, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
            {
                if (t instanceof SQLException)
                {
                    renderErrors(response.getWriter(), "Query '" + getQueryDef().getName() + "' has errors", Collections.singletonList(t));
                }
                else
                {
                    super.renderException(t, request, response);
                }
            }
        };
        setupDataView(ret);
        return ret;
    }

    protected void setupDataView(DataView ret)
    {
        DataRegion rgn = ret.getDataRegion();
        ret.setFrame(WebPartView.FrameType.NONE);
        rgn.setAllowAsync(true);
        ButtonBar bb = new ButtonBar();
        populateButtonBar(ret, bb);
        rgn.setButtonBar(bb);

        if (isPrintView())
        {
            rgn.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        }
        else
        {
            rgn.setButtonBarPosition(_buttonBarPosition);
        }

        if (_customView != null && _customView.hasFilterOrSort() && !ignoreUserFilter())
        {
            SimpleFilter filter;
            if (ret.getRenderContext().getBaseFilter() instanceof SimpleFilter)
            {
                filter = (SimpleFilter) ret.getRenderContext().getBaseFilter();
            }
            else
            {
                filter = new SimpleFilter(ret.getRenderContext().getBaseFilter());
            }
            Sort sort = ret.getRenderContext().getBaseSort();
            if (sort == null)
            {
                sort = new Sort();
            }
            ActionURL url = new ActionURL();
            _customView.applyFilterAndSortToURL(url, getDataRegionName());
            filter.addUrlFilters(url, getDataRegionName());
            sort.applyURLSort(url, getDataRegionName());
            ret.getRenderContext().setBaseFilter(filter);
            ret.getRenderContext().setBaseSort(sort);
        }
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
        String viewName = getSettings().getViewName();
        if (viewName != null && viewName.startsWith(REPORTID_PARAM))
        {
            if (_report != null)
            {
                if (!isPrintView())
                {
                    RenderContext ctx = new RenderContext(getViewContext());
                    ctx.put("reportId", _report.getDescriptor().getReportId());
                    ButtonBar bar = new ButtonBar();
                    populateReportButtonBar(bar);
                    bar.render(ctx, out);
                }
                include(_report.getRunReportView(getViewContext()));
            }
        }
        else
        {
            // make sure table has been instantiated
            getTable();
            List<QueryException> errors = getParseErrors();
            if (errors.size() != 0)
            {
                renderErrors(out, "Query '" + getQueryDef().getName() + "' has errors", errors);
                return;
            }
            include(createDataView(), out);
        }
    }

    protected TSVGridWriter.ColumnHeaderType getColumnHeaderType()
    {
        // Return the sort of column names that should be used in TSV export.
        // Consider: maybe all query types should use "queryColumnName".  That has
        // dots separating foreign keys, but otherwise looks really nice.
        return TSVGridWriter.ColumnHeaderType.propertyName;
    }

    public TSVGridWriter getTsvWriter() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(0);
        view.getRenderContext().setCache(false);
        ResultSet rs = rgn.getResultSet(view.getRenderContext());
        TSVGridWriter tsv = new TSVGridWriter(rs, getExportColumns(rgn.getDisplayColumns()));
        tsv.setColumnHeaderType(getColumnHeaderType());
        return tsv;
    }

    public ResultSet getResultset() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(0);
        view.getRenderContext().setCache(false);
        return rgn.getResultSet(view.getRenderContext());
    }

    public List<DisplayColumn> getExportColumns(List<DisplayColumn> list)
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>(list);
        for (Iterator<DisplayColumn> it = ret.iterator(); it.hasNext();)
        {
            if (it.next() instanceof DetailsColumn)
            {
                it.remove();
            }
        }
        return ret;
    }

    public ExcelWriter getExcelWriter() throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(ExcelWriter.MAX_ROWS);
        ResultSet rs = rgn.getResultSet(view.getRenderContext());
        return new ExcelWriter(rs, getExportColumns(rgn.getDisplayColumns()));
    }

    // Set up an ExcelWriter that exports no data -- used to export templates on upload pages
    protected ExcelWriter getExcelTemplateWriter() throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        return new ExcelWriter(null, getExportColumns(rgn.getDisplayColumns()));
    }

    public Forward exportToExcel(HttpServletResponse response) throws Exception
    {
        return exportToExcel(response, false);
    }

    // Export with no data rows -- just captions
    public Forward exportToExcelTemplate(HttpServletResponse response) throws Exception
    {
        return exportToExcel(response, true);
    }

    private Forward exportToExcel(HttpServletResponse response, boolean templateOnly) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            ExcelWriter ew = templateOnly ? getExcelTemplateWriter() : getExcelWriter();
            ew.write(response);
        }
        return null;
    }

    public Forward exportToTsv(HttpServletResponse response) throws Exception
    {
        return exportToTsv(response, false);
    }

    public Forward exportToTsv(HttpServletResponse response, boolean isExportAsWebPage) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            TSVGridWriter tsv = getTsvWriter();
            tsv.setExportAsWebPage(isExportAsWebPage);
            tsv.write(response);
        }
        return null;
    }

    public void exportToApiResponse(ApiQueryResponse response) throws Exception
    {
        TableInfo table = getTable();
        if (table != null)
        {
            DataView view = createDataView();
            DataRegion rgn = view.getDataRegion();

            //adjust the set of display columns if the user requested a specific list
            if(null != response.getFieldKeys() && response.getFieldKeys().size() > 0)
            {
                //clear the default set of columns
                rgn.clearColumns();

                List<FieldKey> keys = response.getFieldKeys();
                FieldKey starKey = FieldKey.fromParts("*");

                //special-case: if one of the keys is *, add all columns from the
                //TableInfo and remove the * so that Query doesn't choke on it
                if(keys.contains(starKey))
                {
                    rgn.addColumns(table.getColumns());
                    keys.remove(starKey);
                }

                if(keys.size() > 0)
                {
                    Map<FieldKey,ColumnInfo> selectedCols = QueryService.get().getColumns(table, keys);
                    for(ColumnInfo col : selectedCols.values())
                        rgn.addColumn(col);
                }
            }

            //force the pk column(s) into the default list of columns
            List<ColumnInfo> pkCols = table.getPkColumns();
            if (null != pkCols)
            {
                for(ColumnInfo pkCol : pkCols)
                {
                    if (null == rgn.getDisplayColumn(pkCol.getName()))
                        rgn.addColumn(pkCol);
                }
            }

            ResultSet rs = null;

            try
            {
                rs = rgn.getResultSet(view.getRenderContext());
                response.populate(rs, table, getExportColumns(rgn.getDisplayColumns()), rgn.getTotalRows());
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }

    }

    public void exportToExcelWebQuery(HttpServletResponse response) throws Exception
    {
        TableInfo table = getTable();
        if(null == table)
            return;

        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(ExcelWriter.MAX_ROWS);
        view.getRenderContext().setCache(false);

        ResultSet rs = rgn.getResultSet(view.getRenderContext());
        new HtmlWriter().write(rs, getExportColumns(rgn.getDisplayColumns()), response, view.getRenderContext(), true);
    }

    protected CustomView getCustomView()
    {
        return _customView;
    }

    protected TableInfo createTable()
    {
        return _queryDef.getTable(null, _schema, _errors);
    }

    final public TableInfo getTable()
    {
        if (_table != null)
            return _table;
        _table = createTable();
        return _table;
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        TableInfo table = getTable();
        if (table == null)
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        if (_showDetailsColumn && !isPrintView() && !isExportView())
        {
            StringExpressionFactory.StringExpression urlDetails = table.getDetailsURL(Table.createColumnMap(table, null));
            if (urlDetails != null)
            {
                ret.add(new DetailsColumn(urlDetails));
            }
        }

        ret.addAll(getQueryDef().getDisplayColumns(_customView, table));
        return ret;
    }

    public QueryDefinition getQueryDef()
    {
        return _queryDef;
    }

    public List<QueryException> getParseErrors()
    {
        return _errors;
    }

    public NavTrailConfig getNavTrailConfig()
    {
        NavTrailConfig ret = new NavTrailConfig(getRootContext());
        ret.setTitle(getQueryDef().getName());
        ret.setExtraChildren(new NavTree(getSchema().getSchemaName() + " queries", getSchema().urlFor(QueryAction.begin)));
        return ret;
    }

    public void setShowExportButtons(boolean showExportButtons)
    {
        _showExportButtons = showExportButtons;
    }

    public void setShowDetailsColumn(boolean showDetailsColumn)
    {
        _showDetailsColumn = showDetailsColumn;
    }

    public void setShowCustomizeViewLinkInButtonBar(boolean showCustomizeViewLinkInButtonBar)
    {
        _showCustomizeViewLinkInButtonBar = showCustomizeViewLinkInButtonBar;
    }

    public boolean isShowCustomizeViewLinkInButtonBar()
    {
        return _showCustomizeViewLinkInButtonBar;
    }

    public void setShowChartButton(boolean showChartButton)
    {
        _showChartButton = showChartButton;
    }

    public void setShowRReportButton(boolean showRReportButton)
    {
        _showRReportButton = showRReportButton;
    }

    public void setPrintView(boolean b)
    {
        this._printView = b;
    }

    public boolean isPrintView()
    {
        return _printView;
    }

    public boolean isExportView()
    {
        return _exportView;
    }

    public boolean isUseQueryViewActionExportURLs()
    {
        return _useQueryViewActionExportURLs;
    }

    public void setUseQueryViewActionExportURLs(boolean useQueryViewActionExportURLs)
    {
        _useQueryViewActionExportURLs = useQueryViewActionExportURLs;
    }

    public boolean isAllowExcelWebQuery()
    {
        return _allowExcelWebQuery;
    }

    public void setAllowExcelWebQuery(boolean allowExcelWebQuery)
    {
        _allowExcelWebQuery = allowExcelWebQuery;
    }

    public boolean isShadeAlternatingRows()
    {
        return _shadeAlternatingRows;
    }

    public void setShadeAlternatingRows(boolean shadeAlternatingRows)
    {
        _shadeAlternatingRows = shadeAlternatingRows;
    }

    public boolean isShowColumnSeparators()
    {
        return _showColumnSeparators;
    }

    public void setShowColumnSeparators(boolean showColumnSeparators)
    {
        _showColumnSeparators = showColumnSeparators;
    }

    private static class NavTreeMenuButton extends MenuButton
    {
        public NavTreeMenuButton(String caption)
        {
            super(caption);
        }

        public NavTree getNavTree()
        {
            return popupMenu.getNavTree();
        }
    }
}
