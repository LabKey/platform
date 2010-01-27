/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.data.*;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QueryView extends WebPartView<Object>
{
    public static final String DATAREGIONNAME_DEFAULT = "query";
    protected DataRegion.ButtonBarPosition _buttonBarPosition = DataRegion.ButtonBarPosition.BOTH;
    private boolean _showDetailsColumn = true;
    private boolean _showUpdateColumn = true;

    private static final Map<String, ExportScriptFactory> _exportScriptFactories = new ConcurrentHashMap<String, ExportScriptFactory>();

    public static void register(ExportScriptFactory factory)
    {
        assert null == _exportScriptFactories.get(factory.getScriptType());

        _exportScriptFactories.put(factory.getScriptType(), factory);
    }

    public static ExportScriptFactory getExportScriptFactory(String type)
    {
        return _exportScriptFactories.get(type);
    }

    static public QueryView create(ViewContext context, UserSchema schema, QuerySettings settings) throws ServletException
    {
        return schema.createView(context, settings);
    }

    static public QueryView create(QueryForm form) throws ServletException
    {
        UserSchema s = form.getSchema();
        if (s == null)
        {
            HttpView.throwNotFound("Could not find schema: " + form.getSchemaName());
            return null;
        }
        return create(form.getViewContext(), s, form.getQuerySettings());
    }

    private QueryDefinition _queryDef;
    private CustomView _customView;
    private UserSchema _schema;
    private BindException _errors;
    private List<QueryException> _parseErrors = new ArrayList<QueryException>();
    private QuerySettings _settings;
    private boolean _showRecordSelectors = false;
    private boolean _initializeButtonBar = true;

    private boolean _shadeAlternatingRows = false;
    private boolean _showFilterDescription = true;
    private boolean _showBorders = false;
    private Report _report;

    private boolean _showExportButtons = true;
    private boolean _showInsertNewButton = true;
    private boolean _showDeleteButton = true;
    private boolean _allowExportExternalQuery = true;
    private List<ContainerFilter.Type> _allowableContainerFilterTypes = Arrays.asList(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders);
    private boolean _useQueryViewActionExportURLs = false;
    private boolean _printView = false;
    private boolean _exportView = false;
    private boolean _showPagination = true;
    private boolean _showPaginationCount = true;
    private ReportService.ItemFilter _itemFilter = DEFAULT_ITEM_FILTER;

    public static ReportService.ItemFilter DEFAULT_ITEM_FILTER = new ReportService.ItemFilter()
    {
        public boolean accept(String type, String label)
        {
            if (RReport.TYPE.equals(type)) return true;
            if (QuerySnapshotService.TYPE.equals(type)) return true;
            if (CrosstabReport.TYPE.equals(type)) return true;
            if (ChartQueryReport.TYPE.equals(type)) return true;
            return false;
        }
    };

    TableInfo _table;

    public QueryView(QueryForm form, BindException errors)
    {
        this(form.getSchema(), form.getQuerySettings(), errors);
    }


    /** Must call setSettings before using the view */
    public QueryView(UserSchema schema)
    {
        setSchema(schema);
    }

    @Deprecated
    protected QueryView(UserSchema schema, QuerySettings settings)
    {
        this(schema, settings, null);
    }

    public QueryView(UserSchema schema, QuerySettings settings, BindException errors)
    {
        setSchema(schema);
        if (null != settings)
            setSettings(settings);
        _errors = errors;
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
        //_report = settings.getReportView(getViewContext());
    }


    protected int getMaxRows()
    {
        if (getShowRows() != ShowRows.PAGINATED)
            return 0;
        return getSettings().getMaxRows();
    }


    protected long getOffset()
    {
        if (getShowRows() != ShowRows.PAGINATED)
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

    /** Returns an ActionURL for the "returnURL" parameter or the current ActionURL if none. */
    public URLHelper getReturnURL()
    {
        URLHelper url = getSettings().getReturnURL();
        return url != null ? url : ViewServlet.getRequestURL();
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
        out.print("<p class=\"labkey-error\">");
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

    public static final String REPORTID_PARAM = "reportID=";

    public MenuButton createQueryPickerButton(String label)
    {
        String current = _queryDef != null ? _queryDef.getName() : null;

        URLHelper target = urlRefreshQuery();
        NavTreeMenuButton button = new NavTreeMenuButton(label);

        if (_queryDef != null && _queryDef.canEdit(getUser()) && getContainer().equals(_queryDef.getContainer()))
        {
            if (_queryDef.isTableQueryDefinition())
            {
                button.addMenuItem("Design Query", false, true);
            }
            else
            {
                button.addMenuItem("Design Query", getSchema().urlFor(QueryAction.designQuery, _queryDef));
            }
            NavTree editQueryItem = new NavTree("Edit Source", getSchema().urlFor(QueryAction.sourceQuery, _queryDef));
            editQueryItem.setId("Query:EditSource");
            button.addMenuItem(editQueryItem);
            if (_queryDef.isMetadataEditable())
            {
                NavTree editMetadataItem = new NavTree("Edit Metadata", getSchema().urlFor(QueryAction.metadataQuery, _queryDef));
                editMetadataItem.setId("Query:EditMetadata");
                button.addMenuItem(editMetadataItem);
            }
        }
        else
        {
            button.addMenuItem("Design Query", false, true);
            button.addMenuItem("Edit Query", false, true);
        }
        button.addSeparator();

        for (String query : getSchema().getTableAndQueryNames(true))
        {
            NavTree item = new NavTree(query, target.clone().replaceParameter(param(QueryParam.queryName), query).getLocalURIString());
            item.setId(label + ":" + query);
            if (query.equals(current))
                item.setHighlighted(true);
            item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
            button.addMenuItem(item);
        }
        return button;
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

    public Container getContainer()
    {
        return _schema.getContainer();
    }

    protected StringExpression urlExpr(QueryAction action)
    {
        StringExpression expr = _schema.urlExpr(action, _queryDef);

        switch (action)
        {
            case detailsQueryRow:
            case insertQueryRow:
            case updateQueryRow:
            case deleteQueryRows:
            {
                // ICK
                if (expr instanceof DetailsURL)
                {
                    ActionURL url = ((DetailsURL)expr).getActionURL();
                    if (null != url)
                    {
                        url.addParameter(QueryParam.srcURL, getReturnURL().getLocalURIString());
                        return StringExpressionFactory.createURL(url);
                    }
                }
            }
        }

        return expr;
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
                ret.addParameter(QueryParam.srcURL.toString(), getReturnURL().getLocalURIString());
                ret.addParameter(QueryParam.dataRegionName.toString(), getDataRegionName());
                ret.addParameter(QueryParam.queryName.toString(), getSettings().getQueryName());
                if (getSettings().getViewName() != null)
                    ret.addParameter(QueryParam.viewName.toString(), getSettings().getViewName());
                break;
            case detailsQueryRow:
            case insertQueryRow:
            case updateQueryRow:
            case deleteQueryRows:
                ret.addParameter(QueryParam.srcURL.toString(), getReturnURL().getLocalURIString());
                break;
            case editSnapshot:
                ret.addParameter("snapshotName", getSettings().getQueryName());
            case createSnapshot:

            case exportRowsExcel:
            case exportRowsTsv:
            case exportScript:
            case printRows:
            {
                if (_useQueryViewActionExportURLs)
                {
                    ret = getViewContext().cloneActionURL();
                    ret.addParameter("exportType", action.name());
                    ret.addParameter("exportRegion", getDataRegionName());
                    break;
                }
                ActionURL expandedURL = getViewContext().cloneActionURL();
                addParamsByPrefix(ret, expandedURL, getDataRegionName() + ".", DATAREGIONNAME_DEFAULT +  ".");
                break;
            }
            case excelWebQueryDefinition:
            {
                if (_useQueryViewActionExportURLs)
                {
                    ActionURL expandedURL = getViewContext().cloneActionURL();
                    expandedURL.addParameter("exportType", "excelWebQuery");
                    expandedURL.addParameter("exportRegion", getDataRegionName());
                    ret.addParameter("queryViewActionURL", expandedURL.getLocalURIString());
                    break;
                }
                ActionURL expandedURL = getViewContext().cloneActionURL();
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

                bean.setRedirectUrl(getReturnURL().getLocalURIString());
                return ReportUtil.getRReportDesignerURL(_viewContext, bean);
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
        ActionButton actionButton = new ActionButton(label, url);
        actionButton.setDisplayModes(DataRegion.MODE_ALL);
        return actionButton;
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

    protected URLHelper urlRefreshQuery()
    {
        URLHelper ret = getSettings().getReturnURL();
        if (null == ret)
            ret = getSettings().getSortFilterURL();
        ret.deleteParameter(param(QueryParam.queryName));
        ret.deleteParameter(param(QueryParam.viewName));
        ret.deleteParameter(param(QueryParam.reportId));
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

    protected URLHelper urlChangeView()
    {
        URLHelper ret = getSettings().getReturnURL();
        if (null == ret)
            ret = getSettings().getSortFilterURL();
        ret.deleteParameter(param(QueryParam.viewName));
        ret.deleteParameter(param(QueryParam.reportId));
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
        return table != null && table.hasPermission(getUser(), DeletePermission.class);
    }

    protected boolean canInsert()
    {
        TableInfo table = getTable();
        return table != null && table.hasPermission(getUser(), InsertPermission.class) && table.getUpdateService() != null;
    }

    protected boolean canUpdate()
    {
        TableInfo table = getTable();
        return table != null && table.hasPermission(getUser(), UpdatePermission.class) && table.getUpdateService() != null;
    }

    public boolean showInsertNewButton()
    {
        return _showInsertNewButton;
    }

    public void setShowInsertNewButton(boolean showInsertNewButton)
    {
        _showInsertNewButton = showInsertNewButton;
    }

    public boolean showDeleteButton()
    {
        return _showDeleteButton;
    }

    public void setShowDeleteButton(boolean showDeleteButton)
    {
        _showDeleteButton = showDeleteButton;
    }

    public boolean showRecordSelectors()
    {
        return _showRecordSelectors || (showDeleteButton() && canDelete());
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
        if (getSettings().getAllowChooseQuery())
        {
            bar.add(createQueryPickerButton("Query"));
        }

        if (getSettings().getAllowChooseView())
        {
            bar.add(createViewButton(_itemFilter));
        }

        if (_showExportButtons)
        {
            bar.add(createPrintButton());
        }
    }

    protected void populateButtonBar(DataView view, ButtonBar bar, boolean exportAsWebPage)
    {
        if (getSettings().getAllowChooseQuery())
        {
            bar.add(createQueryPickerButton("Query"));
        }

        if (getSettings().getAllowChooseView())
        {
            bar.add(createViewButton(_itemFilter));
        }

        if (showInsertNewButton())
        {
            if (canInsert())
            {
                ActionURL urlInsert = urlFor(QueryAction.insertQueryRow);
                if (urlInsert != null)
                {
                    ActionButton btnInsert = new ActionButton("", "Insert New");
                    btnInsert.setURL(urlInsert);
                    btnInsert.setActionType(ActionButton.Action.LINK);
                    bar.add(btnInsert);
                }
            }
        }

        if (showDeleteButton())
        {
            if (canDelete())
            {
                ActionURL urlDelete = urlFor(QueryAction.deleteQueryRows);
                if (urlDelete != null)
                {
                    ActionButton btnDelete = new ActionButton("", "Delete");
                    btnDelete.setURL(urlDelete);
                    btnDelete.setActionType(ActionButton.Action.POST);
                    btnDelete.setRequiresSelection(true, "Are you sure you want to delete the selected rows?");
                    bar.add(btnDelete);
                }
            }
        }

        if (_showExportButtons)
        {
            bar.add(createExportButton(exportAsWebPage));

            bar.add(createPrintButton());
        }

        if (view.getDataRegion().getShowPagination())
        {
            addButton(bar, createPageSizeMenuButton());
        }
    }

    protected ActionButton createPrintButton()
    {
        ActionButton btnPrint = actionButton("Print", QueryAction.printRows);
        btnPrint.setTarget("_blank");
        return btnPrint;
    }

    public static class ExcelExportOptionsBean
    {
        private final ActionURL _xlsURL;
        private final ActionURL _iqyURL;

        public ExcelExportOptionsBean(ActionURL xlsURL, ActionURL iqyURL)
        {
            _xlsURL = xlsURL;
            _iqyURL = iqyURL;
        }

        public ActionURL getIqyURL()
        {
            return _iqyURL;
        }

        public ActionURL getXlsURL()
        {
            return _xlsURL;
        }
    }

    public PanelButton createExportButton(boolean exportAsWebPage)
    {
        PanelButton exportButton = new PanelButton("Export", getDataRegionName());
        ExcelExportOptionsBean excelBean = new ExcelExportOptionsBean(urlFor(QueryAction.exportRowsExcel), _allowExportExternalQuery ? urlFor(QueryAction.excelWebQueryDefinition) : null);
        exportButton.addSubPanel("Excel", new JspView<ExcelExportOptionsBean>("/org/labkey/api/query/excelExportOptions.jsp", excelBean));
        ActionURL tsvURL = urlFor(QueryAction.exportRowsTsv);
        if (exportAsWebPage)
        {
            tsvURL.addParameter("exportAsWebPage", "true");
        }
        exportButton.addSubPanel("Text", new JspView<ActionURL>("/org/labkey/api/query/exportTextOptions.jsp", tsvURL));

        if (_allowExportExternalQuery)
        {
            addExportScriptItems(exportButton);
        }
        return exportButton;
    }

    public void addExportScriptItems(PanelButton button)
    {
        if (!_exportScriptFactories.isEmpty())
        {
            Map<String, ActionURL> options = new LinkedHashMap<String, ActionURL>();
            for (ExportScriptFactory factory : _exportScriptFactories.values())
            {
                ActionURL url = urlFor(QueryAction.exportScript);
                url.addParameter("scriptType", factory.getScriptType());
                options.put(factory.getMenuText(), url);
            }
            button.addSubPanel("Script", new JspView<Map<String, ActionURL>>("/org/labkey/api/query/excelScriptOptions.jsp", options));
        }
    }

    protected MenuButton createPageSizeMenuButton()
    {
        final int maxRows = getMaxRows();
        final boolean showingAll = getShowRows() == ShowRows.ALL;
        final boolean showingSelected = getShowRows() == ShowRows.SELECTED;
        final boolean showingUnselected = getShowRows() == ShowRows.UNSELECTED;

        MenuButton pageSizeMenu = new MenuButton("Page Size", getDataRegionName() + ".Menu.PageSize") {
            public boolean shouldRender(RenderContext ctx)
            {
                ResultSet rs = ctx.getResultSet();
                if (!(rs instanceof Table.TableResultSet))
                    return false;
                if (((Table.TableResultSet)rs).isComplete() &&
                    ctx.getCurrentRegion().getOffset() == 0 &&
                    !(showingAll || showingSelected || showingUnselected || maxRows > 0))
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
            NavTree item = pageSizeMenu.addMenuItem(String.valueOf(pageSize) + " per page", "#",
                    "LABKEY.DataRegions[" + PageFlowUtil.jsString(regionName) + "].setMaxRows(" + String.valueOf(pageSize) + ")", checked);
            item.setId("Page Size:" + pageSize);
        }
        pageSizeMenu.addSeparator();
        if (showRecordSelectors() || showingSelected || showingUnselected)
        {
            NavTree item = pageSizeMenu.addMenuItem("Show Selected", "#",
                    "LABKEY.DataRegions[" + PageFlowUtil.jsString(regionName) + "].showSelected()", showingSelected);
            item.setId("Page Size:Selected");
            item = pageSizeMenu.addMenuItem("Show Unselected", "#",
                    "LABKEY.DataRegions[" + PageFlowUtil.jsString(regionName) + "].showUnselected()", showingUnselected);
            item.setId("Page Size:Unselected");
        }
        NavTree item = pageSizeMenu.addMenuItem("Show All", "#",
                "LABKEY.DataRegions[" + PageFlowUtil.jsString(regionName) + "].showAll()", showingAll);
        item.setId("Page Size:All");

        return pageSizeMenu;
    }

    public ReportService.ItemFilter getViewItemFilter()
    {
        return _itemFilter;
    }

    public void setViewItemFilter(ReportService.ItemFilter filter)
    {
        if (filter != null)
            _itemFilter = filter;
    }

    public MenuButton createViewButton(ReportService.ItemFilter filter)
    {
        setViewItemFilter(filter);
        String current = null;

        // if we are not rendering a report we use the current view name to set the menu item selection, an empty
        // string denotes the default view, a customized default view will have a null name.
        if (_report == null)
            current = (_customView != null) ? StringUtils.defaultString(_customView.getName(), "") : "";

        URLHelper target = urlChangeView();
        NavTreeMenuButton button = new NavTreeMenuButton("Views");
        NavTree menu = button.getNavTree();
        menu.setId(getDataRegionName() + ".Menu.Views");

        // existing views
        addGridViews(button, target, current);
        addReportViews(button, target);

        button.addSeparator();
        StringBuilder baseFilterItems = new StringBuilder();

        if (_report == null)
        {
            List<ReportService.DesignerInfo> reportDesigners = new ArrayList<ReportService.DesignerInfo>();

            getSettings().setSchemaName(getSchema().getSchemaName());
            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                reportDesigners.addAll(provider.getDesignerInfo(getViewContext(), getSettings()));
            }
            NavTree submenu = null;
            String sep = "";
            ReportService.ItemFilter viewItemFilter = getItemFilter();

            for (ReportService.DesignerInfo designer : reportDesigners)
            {
                if (viewItemFilter.accept(designer.getReportType(), designer.getLabel()))
                {
                    if (submenu == null)
                    {
                        submenu = menu.addChild("Create");
                        submenu.setId("Views:Create");
                    }

                    NavTree item = new NavTree(designer.getLabel(), designer.getDesignerURL().getLocalURIString());
                    item.setId("Views:Create:" + designer.getLabel());
                    item.setImageSrc(ReportService.get().getReportIcon(getViewContext(), designer.getReportType()));

                    submenu.addChild(item);
                }
                // we want to keep track of the available report types that the base (built-in) item filter accepts
                if (_itemFilter.accept(designer.getReportType(), designer.getLabel()))
                {
                    baseFilterItems.append(sep);
                    baseFilterItems.append(designer.getReportType());
                    sep = "&";
                }
            }
        }
        addCustomizeViewItems(button);
        addManageViewItems(button, PageFlowUtil.map("baseFilterItems", PageFlowUtil.encode(baseFilterItems.toString()),
                "schemaName", getSchema().getSchemaName(),
                "queryName", getSettings().getQueryName()));
        addFilterItems(button);

        return button;
    }

    protected ReportService.ItemFilter getItemFilter()
    {
        QueryDefinition def = QueryService.get().getQueryDef(getContainer(), getSchema().getSchemaName(), getSettings().getQueryName());
        if (def == null)
            def = QueryService.get().createQueryDefForTable(getSchema(), getSettings().getQueryName());

        return new WrappedItemFilter(_itemFilter, def);
    }

    private static class WrappedItemFilter implements ReportService.ItemFilter
    {
        private ReportService.ItemFilter _filter;
        private Map<String, ViewOptions.ViewFilterItem> _filterItemMap = new HashMap<String, ViewOptions.ViewFilterItem>();


        public WrappedItemFilter(ReportService.ItemFilter filter, QueryDefinition def)
        {
            _filter = filter;

            if (def != null)
            {
                for (ViewOptions.ViewFilterItem item : def.getViewOptions().getViewFilterItems())
                    _filterItemMap.put(item.getViewType(), item);
            }
        }

        public boolean accept(String type, String label)
        {
            if (_filter.accept(type, label))
            {
                if (_filterItemMap.containsKey(type))
                    return _filterItemMap.get(type).isEnabled();
                else
                    return true;
            }

            if (_filterItemMap.containsKey(type))
                return _filterItemMap.get(type).isEnabled();

            return false;
        }
    }

    protected void addFilterItems(NavTreeMenuButton button)
    {
        if (_customView != null && _customView.hasFilterOrSort())
        {
            ActionURL url = getSettings().getSortFilterURL();
            NavTree item;
            String label = "Apply View Filter";
            if (ignoreUserFilter())
            {
                url.deleteParameter(param(QueryParam.ignoreFilter));
                item = new NavTree(label, url.toString());
            }
            else
            {
                url.replaceParameter(param(QueryParam.ignoreFilter), "1");
                item = new NavTree(label, url.toString());
                item.setSelected(true);
            }
            item.setId("Views:" + label);
            button.addMenuItem(item);
        }

        if (getTable() instanceof ContainerFilterable && !getAllowableContainerFilterTypes().isEmpty())
        {
            button.addSeparator();
            NavTree containerFilterItem = new NavTree("Folder Filter");
            containerFilterItem.setId("Views:Folder Filter");
            button.addMenuItem(containerFilterItem);

            ContainerFilterable table = (ContainerFilterable)getTable();

            ContainerFilter selectedFilter = table.getContainerFilter();

            for (ContainerFilter.Type filterType : getAllowableContainerFilterTypes())
            {
                ActionURL url = getViewContext().getActionURL().clone();
                String propName = getDataRegionName() + ".containerFilterName";
                url.replaceParameter(propName, filterType.name());
                NavTree filterItem = new NavTree(filterType.toString(), url);

                filterItem.setId("Views:Folder Filter:" + filterType.toString());

                if(selectedFilter.getType() == filterType)
                {
                    filterItem.setSelected(true);
                }
                containerFilterItem.addChild(filterItem);
            }
        }
    }

    protected void addGridViews(MenuButton menu, URLHelper target, String currentView)
    {
        // default grid view stays at the top level
        NavTree item = new NavTree("default", target.clone().replaceParameter(param(QueryParam.viewName), "").getLocalURIString());
        item.setId("Views:default");
        if ("".equals(currentView))
            item.setHighlighted(true);
        item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
        menu.addMenuItem(item);

        // sort the grid view alphabetically, with private views over public ones
        List<CustomView> views = new ArrayList<CustomView>(getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest()).values());
        Collections.sort(views, new Comparator<CustomView>() {
            public int compare(CustomView o1, CustomView o2)
            {
                if (o1.getOwner() != null && o2.getOwner() == null) return -1;
                if (o1.getOwner() == null && o2.getOwner() != null) return 1;
                if (o1.getName() == null) return -1;
                if (o2.getName() == null) return 1;

                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        for (CustomView view : views)
        {
            if (view.isHidden())
                continue;
            String label = view.getName();
            if (label == null)
                continue;

            item = new NavTree(label, target.clone().replaceParameter(param(QueryParam.viewName), label).getLocalURIString());
            item.setId("Views:" + label);
            if (label.equals(currentView))
                item.setHighlighted(true);
            if (null != view.getCustomIconUrl())
                item.setImageSrc(getViewContext().getContextPath() + "/" + view.getCustomIconUrl());
            else
                item.setImageSrc(getViewContext().getContextPath() +
                        (null != view.getOwner() ? "/reports/grid.gif" : "/reports/grid_shared.gif"));
            menu.addMenuItem(item);
        }
    }

    protected void addReportViews(MenuButton menu, URLHelper target)
    {
        String reportKey = ReportUtil.getReportKey(getSchema().getSchemaName(), getSettings().getQueryName());
        Map<String, List<Report>> views = new TreeMap<String, List<Report>>();
        ReportService.ItemFilter viewItemFilter = getItemFilter();

        for (Report report : ReportUtil.getReports(getContainer(), getUser(), reportKey, true))
        {
            // Filter out reports that don't match what this view is supposed to show. This can prevent
            // reports that were created on the same schema and table/query from a different view from showing up on a
            // view that's doing magic to add additional filters, for example.
            if (viewItemFilter.accept(report.getType(), null))
            {
                if (!views.containsKey(report.getType()))
                    views.put(report.getType(), new ArrayList<Report>());

                views.get(report.getType()).add(report);
            }
        }

        if (views.size() > 0)
            menu.addSeparator();

        for (Map.Entry<String, List<Report>> entry : views.entrySet())
        {
            for (Report report : entry.getValue())
            {
                String reportId = report.getDescriptor().getReportId().toString();
                NavTree item = new NavTree(report.getDescriptor().getReportName(), target.clone().replaceParameter(param(QueryParam.reportId), reportId).getLocalURIString());
                item.setId("Views:" + report.getDescriptor().getReportName());
                if (report.getDescriptor().getReportId().equals(getSettings().getReportId()))
                    item.setHighlighted(true);
                item.setImageSrc(ReportService.get().getReportIcon(getViewContext(), report.getType()));
                menu.addMenuItem(item);
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

    public void addCustomizeViewItems(MenuButton button)
    {
        if (_report == null && (null == _customView || _customView.isEditable()))
        {
            NavTree item = new NavTree("Customize View", urlFor(QueryAction.chooseColumns).toString());
            item.setId("Views:Customize View");

            button.addMenuItem(item);
        }

        if (QueryService.get().isQuerySnapshot(getContainer(), getSchema().getSchemaName(), getSettings().getQueryName()))
        {
            QuerySnapshotService.I provider = QuerySnapshotService.get(getSchema().getSchemaName());
            if (provider != null)
            {
                NavTree item = button.addMenuItem("Edit Snapshot", provider.getEditSnapshotURL(getSettings(), getViewContext()));
                item.setId("Views:Edit Snapshot");
            }
        }
    }

    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer());
        for (Map.Entry<String, String> entry : params.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());

        button.addMenuItem("Manage Views", url);
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
        renderDataRegion(out);
    }

    protected void renderTitle(PrintWriter out)
    {
        if (getSettings().getAllowChooseView() && _buttonBarPosition != DataRegion.ButtonBarPosition.NONE)
        {
            StringBuffer sb = new StringBuffer();
            sb.append("<br/><span><b>View :</b> ");

            if (_customView != null)
                sb.append(PageFlowUtil.filter(_customView.getName()));
            else if (_report != null)
                sb.append(PageFlowUtil.filter(_report.getDescriptor().getReportName()));
            else
                sb.append("default");
            sb.append("</span>");

            out.write(sb.toString());
        }
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
        DataRegion rgn = new DataRegion();
        configureDataRegion(rgn);
        return rgn;
    }

    protected void configureDataRegion(DataRegion rgn)
    {
        rgn.setDisplayColumns(getDisplayColumns());
        rgn.setMaxRows(getMaxRows());
        rgn.setOffset(getOffset());
        rgn.setShowRows(getShowRows());
        rgn.setShowRecordSelectors(showRecordSelectors());
        rgn.setName(getDataRegionName());
        rgn.setSelectionKey(getSelectionKey());

        rgn.setShadeAlternatingRows(isShadeAlternatingRows());
        rgn.setShowFilterDescription(isShowFilterDescription());
        rgn.setShowBorders(isShowBorders());
        rgn.setShowPagination(isShowPagination());
        rgn.setShowPaginationCount(isShowPaginationCount());
        if(null != getAggregates())
            rgn.setAggregates(getAggregates());

        rgn.setTable(getTable());
    }

    public void setButtonBarPosition(DataRegion.ButtonBarPosition buttonBarPosition)
    {
        _buttonBarPosition = buttonBarPosition;
    }

    public DataView createDataView()
    {
        DataRegion rgn = createDataRegion();
        GridView ret = new GridView(rgn, _errors)
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
        if (_initializeButtonBar)
        {
            ButtonBar bb = new ButtonBar();
            populateButtonBar(ret, bb);
            rgn.setButtonBar(bb);
        }

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

        if (_customView != null)
            ret.getRenderContext().setViewName(_customView.getName());
    }

    protected void renderDataRegion(PrintWriter out) throws Exception
    {
        _report = getSettings().getReportView();
        String viewName = getSettings().getViewName();
        if (_report != null && viewName == null)
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
        rgn.setAllowAsync(false);
        view.getRenderContext().setCache(false);
        ResultSet rs = rgn.getResultSet(view.getRenderContext());
        TSVGridWriter tsv = new TSVGridWriter(rs, getExportColumns(rgn.getDisplayColumns()));
        tsv.setColumnHeaderType(getColumnHeaderType());
        return tsv;
    }

    public Report.Results getResults() throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(0);
        rgn.setAllowAsync(false);
        view.getRenderContext().setCache(false);
        RenderContext ctx = view.getRenderContext();
        if (null == rgn.getResultSet(ctx))
            return null;
        return new Report.Results(ctx);
    }

    public ResultSet getResultset() throws SQLException, IOException
    {
        Report.Results r = getResults();
        return r == null ? null : r.rs;
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
        rgn.setAllowAsync(false);
        rgn.setOffset(Table.ALL_ROWS);
        rgn.setMaxRows(ExcelWriter.MAX_ROWS);
        RenderContext rc = view.getRenderContext();
        ResultSet rs = rgn.getResultSet(rc);
        Map<FieldKey,ColumnInfo> map = rc.getFieldMap();
        return new ExcelWriter(rs, map, getExportColumns(rgn.getDisplayColumns()));
    }

    // Set up an ExcelWriter that exports no data -- used to export templates on upload pages
    protected ExcelWriter getExcelTemplateWriter() throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setAllowAsync(false);
        List<DisplayColumn> displayColumns = getExportColumns(rgn.getDisplayColumns());
        // Need to remove special MV columns
        for (Iterator<DisplayColumn> it = displayColumns.iterator(); it.hasNext();)
        {
            DisplayColumn col = it.next();
            if (col.getColumnInfo() instanceof RawValueColumn)
                it.remove();
        }
        return new ExcelWriter(null, displayColumns);
    }

    public void exportToExcel(HttpServletResponse response) throws Exception
    {
        exportToExcel(response, false);
    }

    // Export with no data rows -- just captions
    public void exportToExcelTemplate(HttpServletResponse response) throws Exception
    {
        exportToExcel(response, true);
    }

    private void exportToExcel(HttpServletResponse response, boolean templateOnly) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            ExcelWriter ew = templateOnly ? getExcelTemplateWriter() : getExcelWriter();
            ew.write(response);
        }
    }

    public void exportToTsv(HttpServletResponse response) throws Exception
    {
        exportToTsv(response, false);
    }

    public void exportToTsv(HttpServletResponse response, boolean isExportAsWebPage) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            TSVGridWriter tsv = getTsvWriter();
            tsv.setExportAsWebPage(isExportAsWebPage);
            tsv.write(response);
        }
    }

    public void exportToApiResponse(ApiQueryResponse response) throws Exception
    {
        TableInfo table = getTable();
        if (table != null)
        {
            _initializeButtonBar = false;
            DataView view = createDataView();
            DataRegion rgn = view.getDataRegion();

            //adjust the set of display columns if the user requested a specific list
            if (null != response.getFieldKeys() && response.getFieldKeys().size() > 0)
            {
                //clear the default set of columns
                rgn.clearColumns();

                List<FieldKey> keys = response.getFieldKeys();
                FieldKey starKey = FieldKey.fromParts("*");

                //special-case: if one of the keys is *, add all columns from the
                //TableInfo and remove the * so that Query doesn't choke on it
                if (keys.contains(starKey))
                {
                    rgn.addColumns(table.getColumns());
                    keys.remove(starKey);
                }

                if (keys.size() > 0)
                {
                    Map<FieldKey,ColumnInfo> selectedCols = QueryService.get().getColumns(table, keys);
                    for (ColumnInfo col : selectedCols.values())
                        rgn.addColumn(col);
                }
            }

            //force the pk column(s) into the default list of columns
            List<ColumnInfo> pkCols = table.getPkColumns();
            if (null != pkCols)
            {
                for (ColumnInfo pkCol : pkCols)
                {
                    if (null == rgn.getDisplayColumn(pkCol.getName()))
                        rgn.addColumn(pkCol);
                }
            }

            ResultSet rs = null;

            try
            {
                rgn.setAllowAsync(false);
                RenderContext rc = view.getRenderContext();
                rs = rgn.getResultSet(rc);
                response.initialize(rs, rc.getFieldMap(), table, getExportColumns(rgn.getDisplayColumns()), rgn.getTotalRows());
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
        else
        {
            //table was null--try to get parse errors
            List<QueryException> errors = getParseErrors();
            if (null != errors && errors.size() > 0)
                throw errors.get(0);
        }

    }

    public void exportToExcelWebQuery(HttpServletResponse response) throws Exception
    {
        TableInfo table = getTable();
        if (null == table)
            return;

        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setMaxRows(0);
        view.getRenderContext().setCache(false);

        ResultSet rs = rgn.getResultSet(view.getRenderContext());

        // Bug 5610 & 6179. Excel web queries don't work over SSL if caching is disabled,
        // so we need to allow caching so that Excel can read from IE on Windows.
        // Set the headers to allow the client to cache, but not proxies
        response.setHeader("Pragma", "private");
        response.setHeader("Cache-Control", "private");

        new HtmlWriter().write(rs, getExportColumns(rgn.getDisplayColumns()), response, view.getRenderContext(), true);
    }

    protected CustomView getCustomView()
    {
        return _customView;
    }

    public void setCustomView(CustomView customView)
    {
        _customView = customView;
    }

    public void setCustomView(String viewName)
    {
        _settings.setViewName(viewName);
        _customView = _settings.getCustomView(getViewContext(), _queryDef);
    }

    protected TableInfo createTable()
    {
        return _queryDef != null ? _queryDef.getTable(_schema, _parseErrors, true) : null;
    }

    final public TableInfo getTable()
    {
        if (_table != null)
            return _table;
        _table = createTable();

        if (_table instanceof ContainerFilterable)
        {
            ContainerFilter filter = getContainerFilter();
            if (filter != null)
            {
                ContainerFilterable fTable = (ContainerFilterable)_table;
                fTable.setContainerFilter(filter);
            }
        }
        return _table;
    }

    private ContainerFilter getContainerFilter()
    {
        String filterName = _settings.getContainerFilterName();

        if (filterName == null && _customView != null)
            filterName = _customView.getContainerFilterName();

        if (filterName != null)
            return ContainerFilter.getContainerFilterByName(filterName, getUser());

        return null;
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        TableInfo table = getTable();
        if (table == null)
            return Collections.emptyList();
        if (_showDetailsColumn && !isPrintView() && !isExportView())
        {
            StringExpression urlDetails = table.getDetailsURL(Table.createFieldKeyMap(table).keySet(), getContainer());
            if (urlDetails != null)
            {
                ret.add(new DetailsColumn(urlDetails));
            }
        }

        if (_showUpdateColumn && canUpdate() && !isPrintView() && !isExportView())
        {
            StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);
            if (urlUpdate != null)
            {
                ret.add(0, new UrlColumn(urlUpdate, "edit"));
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
        return _parseErrors;
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

    public void setShowUpdateColumn(boolean showUpdateColumn)
    {
        _showUpdateColumn = showUpdateColumn;
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

    public boolean isAllowExportExternalQuery()
    {
        return _allowExportExternalQuery;
    }

    public void setAllowExportExternalQuery(boolean allowExportExternalQuery)
    {
        _allowExportExternalQuery = allowExportExternalQuery;
    }

    public boolean isShadeAlternatingRows()
    {
        return _shadeAlternatingRows;
    }

    public void setShadeAlternatingRows(boolean shadeAlternatingRows)
    {
        _shadeAlternatingRows = shadeAlternatingRows;
    }

    public boolean isShowFilterDescription()
    {
        return _showFilterDescription;
    }

    public void setShowFilterDescription(boolean showFilterDescription)
    {
        _showFilterDescription = showFilterDescription;
    }

    public boolean isShowBorders()
    {
        return _showBorders;
    }

    public void setShowBorders(boolean showBorders)
    {
        _showBorders = showBorders;
    }

    public boolean isShowPagination()
    {
        return _showPagination;
    }

    public void setShowPagination(boolean showPagination)
    {
        _showPagination = showPagination;
    }

    public boolean isShowPaginationCount()
    {
        return _showPaginationCount;
    }

    public void setShowPaginationCount(boolean showPaginationCount)
    {
        _showPaginationCount = showPaginationCount;
    }

    public List<ContainerFilter.Type> getAllowableContainerFilterTypes()
    {
        return _allowableContainerFilterTypes;
    }

    public void setAllowableContainerFilterTypes(List<ContainerFilter.Type> allowableContainerFilterTypes)
    {
        _allowableContainerFilterTypes = allowableContainerFilterTypes;
    }

    public void setAllowableContainerFilterTypes(ContainerFilter.Type... allowableContainerFilterTypes)
    {
        setAllowableContainerFilterTypes(Arrays.asList(allowableContainerFilterTypes));
    }

    public void disableContainerFilterSelection()
    {
        _allowableContainerFilterTypes = Collections.emptyList();
    }

    public List<Aggregate> getAggregates()
    {
        return getSettings().getAggregates();
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
