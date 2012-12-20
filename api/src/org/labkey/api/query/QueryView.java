/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.reports.report.view.RunReportView;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTrailConfig;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.visualization.TimeChartReport;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class QueryView extends WebPartView<Object>
{
    public static final String EXPERIMENTAL_GENERIC_DETAILS_URL = "generic-details-url";
    private static Logger _log = Logger.getLogger(QueryView.class);

    public static final String DATAREGIONNAME_DEFAULT = "query";
    protected DataRegion.ButtonBarPosition _buttonBarPosition = DataRegion.ButtonBarPosition.BOTH;
    private ButtonBarConfig _buttonBarConfig = null;
    private boolean _showDetailsColumn = true;
    private boolean _showUpdateColumn = true;

    private static final Map<String, ExportScriptFactory> _exportScriptFactories = new ConcurrentHashMap<String, ExportScriptFactory>();
    private String _linkTarget;

    // Overrides for any URLs that might already be set on the TableInfo
    private String _updateURL;
    private String _detailsURL;
    private String _insertURL;
    private String _importURL;
    private String _deleteURL;

    public static void register(ExportScriptFactory factory)
    {
        assert null == _exportScriptFactories.get(factory.getScriptType());

        _exportScriptFactories.put(factory.getScriptType(), factory);
    }

    public static ExportScriptFactory getExportScriptFactory(String type)
    {
        return _exportScriptFactories.get(type);
    }

    static public QueryView create(ViewContext context, UserSchema schema, QuerySettings settings, BindException errors)
    {
        return schema.createView(context, settings, errors);
    }

    static public QueryView create(QueryForm form, BindException errors)
    {
        UserSchema s = form.getSchema();
        if (s == null)
        {
            throw new NotFoundException("Could not find schema: " + form.getSchemaName());
        }
        return create(form.getViewContext(), s, form.getQuerySettings(), errors);
    }

    private QueryDefinition _queryDef;
    private CustomView _customView;
    private UserSchema _schema;
    private Errors _errors;
    private List<QueryException> _parseErrors = new ArrayList<QueryException>();
    private QuerySettings _settings;
    private boolean _showRecordSelectors = false;
    private boolean _initializeButtonBar = true;

    private boolean _shadeAlternatingRows = true;
    private boolean _showFilterDescription = true;
    private boolean _showBorders = true;
    private boolean _showSurroundingBorder = true;
    private Report _report;

    private boolean _showExportButtons = true;
    private boolean _showInsertNewButton = true;
    private boolean _showImportDataButton = true;
    private boolean _showDeleteButton = true;
    private boolean _showConfiguredButtons = true;
    private boolean _allowExportExternalQuery = true;
    private List<ContainerFilter.Type> _allowableContainerFilterTypes = Arrays.asList(ContainerFilter.Type.Current, ContainerFilter.Type.CurrentAndSubfolders);
    private boolean _useQueryViewActionExportURLs = false;
    private boolean _printView = false;
    private boolean _exportView = false;
    private boolean _showPagination = true;
    private boolean _showPaginationCount = true;
    private boolean _showReports = true;
    private ReportService.ItemFilter _itemFilter = DEFAULT_ITEM_FILTER;

    public static ReportService.ItemFilter DEFAULT_ITEM_FILTER = new ReportService.ItemFilter()
    {
        public boolean accept(String type, String label)
        {
            if (RReport.TYPE.equals(type)) return true;
            if (QueryReport.TYPE.equals(type)) return true;
            if (QuerySnapshotService.TYPE.equals(type)) return true;
            if (CrosstabReport.TYPE.equals(type)) return true;
            if (JavaScriptReport.TYPE.equals(type)) return true;
            if (GenericChartReport.TYPE.equals(type)) return true;
            return ChartQueryReport.TYPE.equals(type);
        }
    };

    private TableInfo _table;

    public QueryView(QueryForm form, Errors errors)
    {
        this(form.getSchema(), form.getQuerySettings(), errors);
    }


    /** Must call setSettings before using the view */
    public QueryView(UserSchema schema)
    {
        setSchema(schema);
    }

    @Deprecated /** Use the constructor that takes an Errors object instead */
    protected QueryView(UserSchema schema, QuerySettings settings)
    {
        this(schema, settings, null);
    }

    public QueryView(UserSchema schema, QuerySettings settings, @Nullable Errors errors)
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
        if (getShowRows() == ShowRows.NONE)
            return Table.NO_ROWS;
        if (getShowRows() != ShowRows.PAGINATED)
            return Table.ALL_ROWS;
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
        URLHelper url = getSettings().getReturnUrl();
        return url != null ? url : ViewServlet.getRequestURL();
    }

    protected boolean verboseErrors()
    {
        return true;
    }


    protected boolean ignoreUserFilter()
    {
        return (getViewContext().getRequest() != null && getViewContext().getRequest().getParameter(param(QueryParam.ignoreFilter)) != null) ||
                (getSettings() != null && getSettings().getIgnoreUserFilter());
    }


    protected void renderErrors(PrintWriter out, String message, List<? extends Throwable> errors)
    {
        out.write("<p class=\"labkey-error\">");
        out.print(PageFlowUtil.filter(message));
        if (_queryDef != null && _queryDef.canEdit(getUser()) && getContainer().equals(_queryDef.getContainer()))
            out.write("&nbsp;<a href=\"" + getSchema().urlFor(QueryAction.sourceQuery, _queryDef) + "\">Edit Query</a>");
        out.write("</p>");

        Set<String> seen = new HashSet<String>();

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

                String resolveURL =  ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.ResolveURL);
                if (null != resolveURL && seen.add(resolveURL))
                {
                    String resolveText = ExceptionUtil.getExceptionDecoration(e, ExceptionUtil.ExceptionInfo.ResolveText);
                    if (getUser().isDeveloper())
                    {
                        out.write(" ");
                        out.print(PageFlowUtil.textLink(StringUtils.defaultString(resolveText,"resolve"), resolveURL));
                    }
                }
                out.write("<br>");
            }
        }
    }


    public MenuButton createQueryPickerButton(String label)
    {
        String current = _queryDef != null ? _queryDef.getName() : null;

        URLHelper target = urlRefreshQuery();
        NavTreeMenuButton button = new NavTreeMenuButton(label);
        NavTree menu = button.getNavTree();
        menu.setId(getDataRegionName() + ".Menu." + label);

        if (_queryDef != null && _queryDef.canEdit(getUser()) && getContainer().equals(_queryDef.getContainer()))
        {
            NavTree editQueryItem;
            if (_queryDef.isSqlEditable())
                editQueryItem = new NavTree("Edit Source", getSchema().urlFor(QueryAction.sourceQuery, _queryDef));
            else
                editQueryItem = new NavTree("View Definition", getSchema().urlFor(QueryAction.schemaBrowser, _queryDef));
            editQueryItem.setId(getDataRegionName() + ":Query:EditSource");
            button.addMenuItem(editQueryItem);
            if (_queryDef.isMetadataEditable())
            {
                NavTree editMetadataItem = new NavTree("Edit Metadata", getSchema().urlFor(QueryAction.metadataQuery, _queryDef));
                editMetadataItem.setId(getDataRegionName() + ":Query:EditMetadata");
                button.addMenuItem(editMetadataItem);
            }
        }
        else
        {
            button.addMenuItem("Edit Query", false, true);
        }

        button.addSeparator();

        if (getSchema().shouldRenderTableList())
        {
            for (QueryDefinition query : getSchema().getTablesAndQueries(true))
            {
                String name = query.getName();
                NavTree item = new NavTree(name, target.clone().replaceParameter(param(QueryParam.queryName), name).getLocalURIString());
                item.setId(getDataRegionName() + ":" + label + ":" + name);
                // Intentionally don't set the description so we can avoid having to instantiate all of the TableInfos,
                // which can be expensive for some schemas
                if (name.equals(current))
                    item.setStrong(true);
                item.setImageSrc(getViewContext().getContextPath() + "/reports/grid.gif");
                button.addMenuItem(item);
            }
        }
        else
        {
            ActionURL schemaBrowserURL = PageFlowUtil.urlProvider(QueryUrls.class).urlSchemaBrowser(getContainer(), getSchema().getName());
            button.addMenuItem("Schema Browser", schemaBrowserURL);
        }

        return button;
    }

    public User getUser()
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
        StringExpression expr = _queryDef.urlExpr(action, _schema.getContainer());
        if (expr == null)
            return null;

        switch (action)
        {
            case detailsQueryRow:
            case insertQueryRow:
            case updateQueryRow:
            case deleteQueryRows:
            {
                // ICK
                URLHelper srcURL = getReturnURL();
                if (srcURL != null)
                {
                    String encodedSrcURL = PageFlowUtil.encode(srcURL.getLocalURIString());
                    expr = ((StringExpressionFactory.AbstractStringExpression)expr).addParameter(QueryParam.srcURL.name(), encodedSrcURL);
                }
            }
        }

        return expr;
    }

    protected ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = _schema.urlFor(action, _queryDef);

        if (ret == null)
        {
            return null;
        }

        // Issue 11280: Export URLs don't include the query's base sort/filter.
        // The solution is to expand the custom view's saved sort/filter before adding the base sort/filter.
        // NOTE: This is a temporary solution.
        //
        // We won't need to expand the saved custom view filters or aggregates.  Filters can be applied
        // in any order and the aggregates don't make much sense in the exported xls or tsv files.
        //
        // The correct long term solution is to (a) create proper QueryView subclasses using UserSchema.createView()
        // and (b) use POST instead of GET for the export actions (or others) to match the QueryWebPart.js config behavior.
        // Using POST is necessary since the QueryWebPart.js config expresses other options (column lists, grid rendering options, etc) that can't be expressed on URLs.
        if (_customView != null && _customView.hasFilterOrSort())
        {
            _customView.applyFilterAndSortToURL(ret, DATAREGIONNAME_DEFAULT);
        }

        // Applying the base sort/filter to the url is lossy in that anyone consuming the url can't
        // determine if the sort/filter originated from QuerySettings or from a user applied sort/filter.
        if (getSettings().getBaseFilter() != null)
            (getSettings().getBaseFilter()).applyToURL(ret, DATAREGIONNAME_DEFAULT);

        if (getSettings().getBaseSort() != null && getSettings().getBaseSort().getSortList().size()>0)
            getSettings().getBaseSort().applyToURL(ret, DATAREGIONNAME_DEFAULT, true);

        switch (action)
        {
            case deleteQuery:
            case sourceQuery:
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
            case exportRowsXLSX:
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
                addParamsByPrefix(ret, expandedURL, getDataRegionName() + ".", DATAREGIONNAME_DEFAULT + ".");
                // Copy the other parameters that aren't scoped to the data region as well. Some exports may use them.
                // For example, see issue 15451
                for (Map.Entry<String, String[]> entry : expandedURL.getParameterMap().entrySet())
                {
                    String name = entry.getKey();
                    // schemaName isn't prefixed with the data region name, and don't specify a special data region name
                    if (!name.equals("schemaName") && !name.equals("dataRegionName") && !name.startsWith(getDataRegionName() + ".") && !name.startsWith(DATAREGIONNAME_DEFAULT + "."))
                    {
                        for (String value : entry.getValue())
                        {
                            ret.addParameter(entry.getKey(), value);
                        }
                    }
                }
                ret.deleteParameter(DATAREGIONNAME_DEFAULT + ".maxRows");
                ret.replaceParameter(DATAREGIONNAME_DEFAULT + ".showRows", ShowRows.ALL.toString());
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
                ScriptReportBean bean = new ScriptReportBean();
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
        URLHelper ret = getSettings().getReturnUrl();
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
        URLHelper ret = getSettings().getReturnUrl();
        if (null == ret)
            ret = getSettings().getSortFilterURL();
        else if (getSettings().getDataRegionName() != null)
        {
            // if we are using a returnUrl for this QV, make sure we apply any sort and filter
            // parameters so that reports stay in sync with the data region.
            URLHelper url = getSettings().getSortFilterURL();
            for (String param : url.getKeysByPrefix(getSettings().getDataRegionName()))
            {
                ret.replaceParameter(param, url.getParameter(param));
            }
        }

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
            String suffix = key.substring(oldPrefix.length());
            String newKey = newPrefix + suffix;
            for (String value : source.getParameters(key))
            {
                if (suffix.equals("sort"))
                {
                    // Prepend source sort parameter before target's existing sort
                    String targetSort = target.getParameter(newKey);
                    if (targetSort != null && targetSort.length() > 0)
                        value = value + "," + targetSort;
                    target.replaceParameter(newKey, value);
                }
                else
                {
                    target.addParameter(newKey, value);
                }
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

    public boolean showImportDataButton()
    {
        return _showImportDataButton;
    }

    public void setShowImportDataButton(boolean show)
    {
        _showImportDataButton = show;
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
        return _showRecordSelectors;// || (_buttonBarPosition != DataRegion.ButtonBarPosition.NONE && showDeleteButton() && canDelete());
    }

    /**
     * Show record selectors usually doesn't need to be explicitly set.  If the ButtonBar contains
     * a button that requires selection, the record selectors will be added.
     * @param showRecordSelectors
     */
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
            bar.add(createChartButton());
        }

        if (showInsertNewButton() && canInsert())
        {
            ActionButton insertButton = createInsertButton();
            if (insertButton != null)
                bar.add(insertButton);
        }

        if (showImportDataButton() && canInsert())
        {
            ActionButton importButton = createImportButton();
            if (importButton != null)
                bar.add(importButton);
        }

        if (showDeleteButton())
        {
            if (canDelete())
            {
                ActionButton deleteButton = createDeleteButton();
                if (deleteButton != null)
                {
                    bar.add(deleteButton);
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

    public ActionButton createDeleteButton()
    {
        ActionURL urlDelete = urlFor(QueryAction.deleteQueryRows);
        if (urlDelete != null)
        {
            ActionButton btnDelete = new ActionButton(urlDelete, "Delete");
            btnDelete.setActionType(ActionButton.Action.POST);
            btnDelete.setDisplayPermission(DeletePermission.class);
            btnDelete.setRequiresSelection(true, "Are you sure you want to delete the selected row?", "Are you sure you want to delete the selected rows?");
            return btnDelete;
        }
        return null;
    }

    public ActionButton createInsertButton()
    {
        ActionURL urlInsert = urlFor(QueryAction.insertQueryRow);
        if (urlInsert != null)
        {
            ActionButton btnInsert = new ActionButton(urlInsert, "Insert New");
            btnInsert.setActionType(ActionButton.Action.LINK);
            return btnInsert;
        }
        return null;
    }

    public ActionButton createImportButton()
    {
        ActionURL urlImport = urlFor(QueryAction.importData);
        if (urlImport != null && urlImport != AbstractTableInfo.LINK_DISABLER_ACTION_URL)
        {
            ActionButton btnInsert = new ActionButton(urlImport, "Import Data");
            btnInsert.setActionType(ActionButton.Action.LINK);
            return btnInsert;
        }
        return null;
    }

    protected ActionButton createPrintButton()
    {
        ActionButton btnPrint = actionButton("Print", QueryAction.printRows);
        btnPrint.setTarget("_blank");
        return btnPrint;
    }

    /** Make all links rendered in columns target the specified browser window/tab */
    public void setLinkTarget(String linkTarget)
    {
        _linkTarget = linkTarget;
    }

    public static class ExcelExportOptionsBean
    {
        private final ActionURL _xlsURL;
        private final ActionURL _xlsxURL;
        private final ActionURL _iqyURL;

        public ExcelExportOptionsBean(ActionURL xlsURL, ActionURL xlsxURL, ActionURL iqyURL)
        {
            _xlsURL = xlsURL;
            _xlsxURL = xlsxURL;
            _iqyURL = iqyURL;
        }

        public ActionURL getXlsxURL()
        {
            return _xlsxURL;
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
        ExcelExportOptionsBean excelBean = new ExcelExportOptionsBean(urlFor(QueryAction.exportRowsExcel), urlFor(QueryAction.exportRowsXLSX), _allowExportExternalQuery ? urlFor(QueryAction.excelWebQueryDefinition) : null);
        exportButton.addSubPanel("Excel", new JspView<ExcelExportOptionsBean>("/org/labkey/api/query/excelExportOptions.jsp", excelBean));

        ActionURL tsvURL = urlFor(QueryAction.exportRowsTsv);
        if (exportAsWebPage)
        {
            tsvURL.replaceParameter("exportAsWebPage", "true");
        }
        exportButton.addSubPanel("Text", new JspView<ActionURL>("/org/labkey/api/query/textExportOptions.jsp", tsvURL));

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

            button.addSubPanel("Script", new JspView<Map<String, ActionURL>>("/org/labkey/api/query/scriptExportOptions.jsp", options));
        }
    }

    protected MenuButton createPageSizeMenuButton()
    {
        final int maxRows = getMaxRows();
        final boolean showingAll = getShowRows() == ShowRows.ALL;
        final boolean showingSelected = getShowRows() == ShowRows.SELECTED;
        final boolean showingUnselected = getShowRows() == ShowRows.UNSELECTED;

        MenuButton pageSizeMenu = new MenuButton("Page Size", getBaseMenuId() + ".Menu.PageSize")
        {
            @Override
            public void render(RenderContext ctx, Writer out) throws IOException
            {
                addSeparator();

                // We don't know if record selectors are showing until render time so we
                // need to add in the Show Selected/Unselected menu items at this time.
                DataRegion rgn = ctx.getCurrentRegion();
                if (rgn.getShowRecordSelectors(ctx) || showingSelected || showingUnselected)
                {
                    NavTree item = addMenuItem("Show Selected", "#",
                            "LABKEY.DataRegions[" + PageFlowUtil.jsString(rgn.getName()) + "].showSelected()", showingSelected);
                    item.setId("Page Size:Selected");
                    item = addMenuItem("Show Unselected", "#",
                            "LABKEY.DataRegions[" + PageFlowUtil.jsString(rgn.getName()) + "].showUnselected()", showingUnselected);
                    item.setId("Page Size:Unselected");
                }

                NavTree item = addMenuItem("Show All", "#",
                        "LABKEY.DataRegions[" + PageFlowUtil.jsString(rgn.getName()) + "].showAll()", showingAll);
                item.setId("Page Size:All");

                super.render(ctx, out);
            }

            @Override
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

        // if we are not rendering a report or not showing reports, we use the current view name to set the menu item
        // selection, an empty string denotes the default view, a customized default view will have a null name.
        if (_report == null || !_showReports)
            current = (_customView != null) ? StringUtils.defaultString(_customView.getName(), "") : "";

        URLHelper target = urlChangeView();
        NavTreeMenuButton button = new NavTreeMenuButton("Views");
        NavTree menu = button.getNavTree();
        menu.setId(getBaseMenuId() + ".Menu.Views");

        // existing views
        if (!getQueryDef().isTemporary())
        {
            addGridViews(button, target, current);

            if (_showReports)
                addReportViews(button);

            button.addSeparator();
        }
        StringBuilder baseFilterItems = new StringBuilder();

        if (!getQueryDef().isTemporary() && _report == null)
        {
            List<ReportService.DesignerInfo> reportDesigners = new ArrayList<ReportService.DesignerInfo>();
            getSettings().setSchemaName(getSchema().getSchemaName());

            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                for (ReportService.DesignerInfo designerInfo : provider.getDesignerInfo(getViewContext(), getSettings()))
                {
                    if (designerInfo.getType() != ReportService.DesignerType.VISUALIZATION)
                        reportDesigners.add(designerInfo);
                }
            }

            Collections.sort(reportDesigners, new Comparator<ReportService.DesignerInfo>()
            {
                @Override
                public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
                {
                    return o1.getLabel().compareTo(o2.getLabel());
                }
            });

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
                        submenu.setId(getBaseMenuId() + ":Views:Create");
                    }

                    NavTree item = new NavTree(designer.getLabel(), designer.getDesignerURL().getLocalURIString());
                    item.setId(getBaseMenuId() + ":Views:Create:" + designer.getLabel());
                    item.setImageSrc(designer.getIconPath());

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

        if (getSettings().isAllowCustomizeView())
            addCustomizeViewItems(button);
        if (!getQueryDef().isTemporary())
        {
            addManageViewItems(button, PageFlowUtil.map("baseFilterItems", PageFlowUtil.encode(baseFilterItems.toString()),
                    "schemaName", getSchema().getSchemaName(),
                    "queryName", getSettings().getQueryName()));
            addFilterItems(button);
        }
        return button;
    }

    public MenuButton createChartButton()
    {
        URLHelper target = urlChangeView();
        NavTreeMenuButton button = new NavTreeMenuButton("Charts");

        if (!getQueryDef().isTemporary() && _report == null)
        {
            List<ReportService.DesignerInfo> reportDesigners = new ArrayList<ReportService.DesignerInfo>();
            getSettings().setSchemaName(getSchema().getSchemaName());

            for (ReportService.UIProvider provider : ReportService.get().getUIProviders())
            {
                for (ReportService.DesignerInfo designerInfo : provider.getDesignerInfo(getViewContext(), getSettings()))
                {
                    if (designerInfo.getType() == ReportService.DesignerType.VISUALIZATION)
                        reportDesigners.add(designerInfo);
                }
            }

            Collections.sort(reportDesigners, new Comparator<ReportService.DesignerInfo>()
            {
                @Override
                public int compare(ReportService.DesignerInfo o1, ReportService.DesignerInfo o2)
                {
                    return o1.getLabel().compareTo(o2.getLabel());
                }
            });

            for (ReportService.DesignerInfo designer : reportDesigners)
            {
                NavTree item = new NavTree("Create " + designer.getLabel(), designer.getDesignerURL().getLocalURIString());
                item.setId(getBaseMenuId() + ":Charts:Create" + designer.getLabel());
                item.setImageSrc(designer.getIconPath());

                button.addMenuItem(item);
            }
        }

        if (!getQueryDef().isTemporary() && _showReports)
        {
            addChartViews(button);
        }

        return button;
    }

    public ReportService.ItemFilter getItemFilter()
    {
        QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), getSchema().getSchemaName(), getSettings().getQueryName());
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
            URLHelper url = getSettings().getReturnUrl();
            if (null == url)
                url = getSettings().getSortFilterURL();
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
            item.setId(getBaseMenuId() + ":Views:" + label);
            button.addMenuItem(item);
        }

        TableInfo t = getTable();
        if (t instanceof ContainerFilterable && t.supportsContainerFilter() && !getAllowableContainerFilterTypes().isEmpty())
        {
            button.addSeparator();
            NavTree containerFilterItem = new NavTree("Folder Filter");
            containerFilterItem.setId(getBaseMenuId() + ":Views:Folder Filter");
            button.addMenuItem(containerFilterItem);

            ContainerFilterable table = (ContainerFilterable)t;

            ContainerFilter selectedFilter = table.getContainerFilter();

            for (ContainerFilter.Type filterType : getAllowableContainerFilterTypes())
            {
                URLHelper url = getSettings().getReturnUrl();
                if (null == url)
                    url = getSettings().getSortFilterURL();
                String propName = getDataRegionName() + ".containerFilterName";
                url.replaceParameter(propName, filterType.name());
                NavTree filterItem = new NavTree(filterType.toString(), url);

                filterItem.setId(getBaseMenuId() + ":Views:Folder Filter:" + filterType.toString());

                if(selectedFilter.getType() == filterType)
                {
                    filterItem.setSelected(true);
                }
                containerFilterItem.addChild(filterItem);
            }
        }
    }

    protected String getChangeViewScript(String viewName)
    {
        return "LABKEY.DataRegions[" + PageFlowUtil.jsString(getDataRegionName()) + "].changeView({type:'view', viewName:" + PageFlowUtil.jsString(viewName) + "});";
    }

    protected String getChangeReportScript(String reportId)
    {
        return "LABKEY.DataRegions[" + PageFlowUtil.jsString(getDataRegionName()) + "].changeView({type:'report', reportId:" + PageFlowUtil.jsString(reportId) + "});";
    }

    protected void addGridViews(MenuButton menu, URLHelper target, String currentView)
    {
        // default grid view stays at the top level
        NavTree item = new NavTree("default", (String)null);
        item.setScript(getChangeViewScript(""));

        item.setId(getBaseMenuId() + ":Views:default");
        if ("".equals(currentView))
            item.setStrong(true);
        if ( _customView != null)
        {
            StringBuilder description = new StringBuilder();
            if (_customView.isSession())
            {
                item.setEmphasis(true);
                description.append("Unsaved ");
            }
            if (_customView.isShared())
                description.append("Shared ");

            if (_customView.getContainer() != null && !_customView.getContainer().equals(getContainer()))
                description.append("Inherited from '").append(PageFlowUtil.filter(_customView.getContainer().getPath())).append("'");

            if (description.length() > 0)
                item.setDescription(description.toString());
        }
        try
        {
            URLHelper iconUrl = new URLHelper("/reports/grid.gif");
            iconUrl.setContextPath(AppProps.getInstance().getParsedContextPath());
            item.setImageSrc(iconUrl.getLocalURIString());
        }
        catch (URISyntaxException e) { }
        menu.addMenuItem(item);

        // sort the grid view alphabetically, with private views over public ones
        List<CustomView> views = new ArrayList<CustomView>(getQueryDef().getCustomViews(getViewContext().getUser(), getViewContext().getRequest(), false).values());
        Collections.sort(views, new Comparator<CustomView>() {
            public int compare(CustomView o1, CustomView o2)
            {
                if (!o1.isShared() && o2.isShared()) return -1;
                if (o1.isShared() && !o2.isShared()) return 1;
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

            item = new NavTree(label, (String)null);
            item.setScript(getChangeViewScript(label));
            item.setId(getBaseMenuId() + ":Views:view-" + PageFlowUtil.filter(label));
            if (label.equals(currentView))
                item.setStrong(true);

            StringBuilder description = new StringBuilder();
            if (view.isSession())
            {
                item.setEmphasis(true);
                description.append("Unsaved ");
            }
            if (view.isShared())
                description.append("Shared ");

            if (view.getContainer() != null && !view.getContainer().equals(getContainer()))
                description.append("Inherited from '").append(PageFlowUtil.filter(view.getContainer().getPath())).append("'");

            if (description.length() > 0)
                item.setDescription(description.toString());

            try
            {
                URLHelper iconUrl;
                if (null != view.getCustomIconUrl())
                    iconUrl = new URLHelper(view.getCustomIconUrl());
                else
                    iconUrl = new URLHelper(view.isShared() ? "/reports/grid_shared.gif" : "/reports/grid.gif");
                iconUrl.setContextPath(AppProps.getInstance().getParsedContextPath());
                item.setImageSrc(iconUrl.getLocalURIString());
            }
            catch (URISyntaxException e)
            {
                _log.error("Invalid custom view icon url", e);
            }

            menu.addMenuItem(item);
        }
    }

    protected void addReportViews(MenuButton menu)
    {
        List<Report> allReports = new ArrayList<Report>();
        // Ask the schema for the report keys so that we get legacy ones for backwards compatibility too
        for (String reportKey : getSchema().getReportKeys(getSettings().getQueryName()))
        {
            allReports.addAll(ReportUtil.getReports(getContainer(), getUser(), reportKey, true));
        }
        Map<String, List<Report>> views = new TreeMap<String, List<Report>>();
        ReportService.ItemFilter viewItemFilter = getItemFilter();

        for (Report report : allReports)
        {
            // Filter out reports that don't match what this view is supposed to show. This can prevent
            // reports that were created on the same schema and table/query from a different view from showing up on a
            // view that's doing magic to add additional filters, for example.
            if (viewItemFilter.accept(report.getType(), null) && !report.getType().equals(TimeChartReport.TYPE) && !report.getType().equals(GenericChartReport.TYPE))
            {
                if (canViewReport(getUser(), getContainer(), report) && !report.getDescriptor().isHidden())
                {
                    if (!views.containsKey(report.getType()))
                        views.put(report.getType(), new ArrayList<Report>());

                    views.get(report.getType()).add(report);
                }
            }
        }

        if (views.size() > 0)
            menu.addSeparator();

        for (Map.Entry<String, List<Report>> entry : views.entrySet())
        {
            List<Report> reports = entry.getValue();

            // sort the list of reports within each type grouping
            Collections.sort(reports, new Comparator<Report>() {
                @Override
                public int compare(Report o1, Report o2)
                {
                    String n1 = StringUtils.defaultString(o1.getDescriptor().getReportName(), "");
                    String n2 = StringUtils.defaultString(o2.getDescriptor().getReportName(), "");

                    return n1.compareToIgnoreCase(n2);
                }
            });

            for (Report report : reports)
            {
                String reportId = report.getDescriptor().getReportId().toString();
                NavTree item = new NavTree(report.getDescriptor().getReportName(), (String) null);
                item.setId(getBaseMenuId() + ":Views:" + PageFlowUtil.filter(report.getDescriptor().getReportName()));
                if (report.getDescriptor().getReportId().equals(getSettings().getReportId()))
                    item.setStrong(true);
                item.setImageSrc(ReportService.get().getIconPath(report));
                item.setScript(getChangeReportScript(reportId));
                menu.addMenuItem(item);
            }
        }
    }

    protected void addChartViews(MenuButton menu)
    {
        List<Report> reports = new ArrayList<Report>();
        // Ask the schema for the report keys so that we get legacy ones for backwards compatibility too
        for (String reportKey : getSchema().getReportKeys(getSettings().getQueryName()))
        {
            reports.addAll(ReportUtil.getReports(getContainer(), getUser(), reportKey, true));
        }
        Map<String, List<Report>> views = new TreeMap<String, List<Report>>();
        ReportService.ItemFilter viewItemFilter = getItemFilter();

        for (Report report : reports)
        {
            // Filter out reports that don't match what this view is supposed to show. This can prevent
            // reports that were created on the same schema and table/query from a different view from showing up on a
            // view that's doing magic to add additional filters, for example.
            if (viewItemFilter.accept(report.getType(), null) && (report.getType().equals(TimeChartReport.TYPE) || report.getType().equals(GenericChartReport.TYPE)))
            {
                if (canViewReport(getUser(), getContainer(), report))
                {
                    if (!views.containsKey(report.getType()))
                        views.put(report.getType(), new ArrayList<Report>());

                    views.get(report.getType()).add(report);
                }
            }
        }

        if (views.size() > 0)
            menu.addSeparator();

        for (Map.Entry<String, List<Report>> entry : views.entrySet())
        {
            List<Report> charts = entry.getValue();

            Collections.sort(charts, new Comparator<Report>()
            {
                @Override
                public int compare(Report o1, Report o2)
                {
                    String n1 = StringUtils.defaultString(o1.getDescriptor().getReportName(), "");
                    String n2 = StringUtils.defaultString(o2.getDescriptor().getReportName(), "");

                    return n1.compareToIgnoreCase(n2);
                }
            });

            for (Report chart : charts)
            {
                String chartId = chart.getDescriptor().getReportId().toString();
                NavTree item = new NavTree(chart.getDescriptor().getReportName(), (String) null);
                item.setImageSrc(ReportService.get().getIconPath(chart));
                item.setScript(getChangeReportScript(chartId));

                if (chart.getDescriptor().getReportId().equals(getSettings().getReportId()))
                    item.setStrong(true);

                menu.addMenuItem(item);
            }
        }
    }

    protected boolean canViewReport(User user, Container c, Report report)
    {
        return true;
    }

    protected String textLink(String text, ActionURL url, String anchorElementId)
    {
        if (url == null)
            return null;
        return PageFlowUtil.textLink(text, url, anchorElementId).concat("&nbsp;");
    }

    protected String textLink(String text, ActionURL url)
    {
        return textLink(text, url, null);
    }

    public void addCustomizeViewItems(MenuButton button)
    {
        if (_report == null)
        {
            ActionURL urlTableInfo = getSchema().urlFor(QueryAction.tableInfo);
            urlTableInfo.addParameter(QueryParam.queryName.toString(), getQueryDef().getName());

            NavTree customizeView = new NavTree("Customize View");
            customizeView.setId(getBaseMenuId() + ":Views:Customize View");
            customizeView.setScript("LABKEY.DataRegions[" + PageFlowUtil.jsString(getDataRegionName()) + "]" +
                    ".toggleShowCustomizeView();");
            button.addMenuItem(customizeView);
        }

        if (QueryService.get().isQuerySnapshot(getContainer(), getSchema().getSchemaName(), getSettings().getQueryName()))
        {
            QuerySnapshotService.I provider = QuerySnapshotService.get(getSchema().getSchemaName());
            if (provider != null)
            {
                NavTree item = button.addMenuItem("Edit Snapshot", provider.getEditSnapshotURL(getSettings(), getViewContext()));
                item.setId(getBaseMenuId() + ":Views:Edit Snapshot");
            }
        }
    }

    public void addManageViewItems(MenuButton button, Map<String, String> params)
    {
        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getViewContext().getContainer());
        for (Map.Entry<String, String> entry : params.entrySet())
            url.addParameter(entry.getKey(), entry.getValue());

        NavTree item = button.addMenuItem("Manage Views", url);
        item.setId(getBaseMenuId() + ":Views:Manage Views");
    }

    public String getDataRegionName()
    {
        return getSettings().getDataRegionName();
    }

    private String _baseId = null;

    /** Use this html encoded dataRegionName as the base id for menus and attribute values that need to be rendered into the DOM. */
    protected String getBaseMenuId()
    {
        if (_baseId == null)
            _baseId = PageFlowUtil.filter(getDataRegionName());
        return _baseId;
    }

    protected String h(Object o)
    {
        return PageFlowUtil.filter(o);
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

    /**
     * this is the choke point for rendering reports and views, if this method is overriden you need to call
     * super in order to have report/view rendering to work properly.
     */
    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (isReportView(getViewContext()))
            renderReportView(model, request, response);
        else
            renderDataRegion(response.getWriter());
    }

    protected final void renderReportView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (_report != null)
        {
            try {
                ReportDataRegion dr = new ReportDataRegion(getSettings(), getViewContext(), _report);
                RenderContext ctx = new RenderContext(getViewContext());

                if (!isPrintView())
                {
                    // not sure why this is necessary (adding the reportId to the context)
                    ctx.put("reportId", _report.getDescriptor().getReportId());
                    ButtonBar bar = new ButtonBar();
                    populateReportButtonBar(bar);

                    dr.setButtonBar(bar);
                }
                dr.render(ctx, request, response);
            }
            catch (Exception e)
            {
                renderErrors(response.getWriter(), "Error rendering report :  " + _report.getDescriptor().getReportName(), Collections.singletonList(e));
            }
        }
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
        rgn.setSettings(getSettings());
        rgn.setShowRecordSelectors(showRecordSelectors());

        rgn.setShadeAlternatingRows(isShadeAlternatingRows());
        rgn.setShowFilterDescription(isShowFilterDescription());
        rgn.setShowBorders(isShowBorders());
        rgn.setShowSurroundingBorder(isShowSurroundingBorder());
        rgn.setShowPagination(isShowPagination());
        rgn.setShowPaginationCount(isShowPaginationCount());

        rgn.setTable(getTable());

        if (isShowConfiguredButtons())
        {
            // We first apply the button bar config from the table:
            ButtonBarConfig tableBarConfig = getTable() == null ? null : getTable().getButtonBarConfig();
            if (tableBarConfig != null)
                rgn.addButtonBarConfig(tableBarConfig);
            // Then any overriding button bar config (from javascript) is applied:
            if (_buttonBarConfig != null)
                rgn.addButtonBarConfig(_buttonBarConfig);
        }

        TableInfo table = getTable();
        if(table != null && table.getAggregateRowConfig() != null)
        {
            rgn.setAggregateRowConfig(table.getAggregateRowConfig());
        }
    }

    public void setButtonBarPosition(DataRegion.ButtonBarPosition buttonBarPosition)
    {
        _buttonBarPosition = buttonBarPosition;
    }

    public void setButtonBarConfig(ButtonBarConfig buttonBarConfig)
    {
        _buttonBarConfig = buttonBarConfig;
    }

    public ButtonBarConfig getButtonBarConfig()
    {
        return _buttonBarConfig;
    }

    private boolean isReportView(ContainerUser cu)
    {
        _report = getSettings().getReportView(cu);

        return _report != null && StringUtils.trimToNull(getSettings().getViewName()) == null;
    }

    public DataView createDataView()
    {
        DataRegion rgn = createDataRegion();

        //if explicit set of fieldkeys has been set
        //add those specifically to the region
        if (null != getSettings().getFieldKeys())
        {
            rgn.clearColumns();
            List<FieldKey> keys = getSettings().getFieldKeys();
            FieldKey starKey = FieldKey.fromParts("*");
            TableInfo table = getTable();

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

        GridView ret = new GridView(rgn, _errors);
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

        if (getSettings() != null && getSettings().getShowRows() == ShowRows.ALL)
        {
            // Don't cache if the ResultSet is likely to be very large
            ret.getRenderContext().setCache(false);
        }

        // Apply base sorts and filters from custom view and from QuerySettings.
        if (!ignoreUserFilter())
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

            List<Aggregate> aggregates = new LinkedList<Aggregate>();
            if (ret.getRenderContext().getBaseAggregates() != null)
                aggregates.addAll(ret.getRenderContext().getBaseAggregates());

            // We need to set the base sort/filter _before_ adding the customView sort/filter.
            // If the user has set a sort on their custom view, we want their sort to take precedence.
            filter.addAllClauses(getSettings().getBaseFilter());
            sort.insertSort(getSettings().getBaseSort());
            if (getSettings().getAggregates() != null)
                aggregates.addAll(getSettings().getAggregates());

            if (_customView != null && _customView.hasFilterOrSort())
            {
                ActionURL url = new ActionURL();
                _customView.applyFilterAndSortToURL(url, getDataRegionName());
                filter.addUrlFilters(url, getDataRegionName());
                sort.addURLSort(url, getDataRegionName());

                aggregates.addAll(Aggregate.fromURL(url, getDataRegionName()));
            }

            ret.getRenderContext().setBaseFilter(filter);
            ret.getRenderContext().setBaseSort(sort);
            ret.getRenderContext().setBaseAggregates(aggregates);
        }

        // XXX: Move to QuerySettings?
        if (_customView != null)
            ret.getRenderContext().setView(_customView);

        // TODO: Don't set available container filters in render context
        // 11082: Need to push list of available container filters to DataRegion.js
        ret.getRenderContext().put("allowableContainerFilterTypes", getAllowableContainerFilterTypes());
    }


    protected void renderDataRegion(PrintWriter out) throws Exception
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


    protected TSVGridWriter.ColumnHeaderType getColumnHeaderType()
    {
        // Return the sort of column names that should be used in TSV export.
        // Consider: maybe all query types should use "queryColumnName".  That has
        // dots separating foreign keys, but otherwise looks really nice.
        return TSVGridWriter.ColumnHeaderType.caption;
    }

    public TSVGridWriter getTsvWriter() throws SQLException, IOException
    {
        _initializeButtonBar = false;
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        getSettings().setShowRows(ShowRows.ALL);
        rgn.setAllowAsync(false);
        rgn.setShowPagination(false);
        RenderContext rc = view.getRenderContext();
        rc.setCache(false);
        Results rs = rgn.getResultSet(rc);
        TSVGridWriter tsv = new TSVGridWriter(rs, getExportColumns(rgn.getDisplayColumns()));
        tsv.setFilenamePrefix(getSettings().getQueryName() != null ? getSettings().getQueryName() : "query");
        tsv.setColumnHeaderType(getColumnHeaderType());
        return tsv;
    }

    public Results getResults() throws SQLException, IOException
    {
        return getResults(ShowRows.ALL);
    }

    public Results getResults(ShowRows showRows) throws SQLException, IOException
    {
        return getResults(showRows, false, false);
    }

    public Results getResults(ShowRows showRows, boolean async, boolean cache) throws SQLException, IOException
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        ShowRows prevShowRows = getSettings().getShowRows();
        try
        {
            // Set to the desired row policy
            getSettings().setShowRows(showRows);
            rgn.setAllowAsync(async);
            view.getRenderContext().setCache(cache);
            RenderContext ctx = view.getRenderContext();
            if (null == rgn.getResultSet(ctx))
                return null;
            return new ResultsImpl(ctx);
        }
        finally
        {
            // We have to reset the show-rows setting, since we don't know what's going to be done with this
            // queryview after the call to 'getResults'.  It's possible it could still be rendered to the client,
            // as happens with study datasets.
            getSettings().setShowRows(prevShowRows);
        }
    }


    public ResultSet getResultSet() throws SQLException, IOException
    {
        Results r = getResults();
        return r == null ? null : r.getResultSet();
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

    public ExcelWriter getExcelWriter(ExcelWriter.ExcelDocumentType docType) throws Exception
    {
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();

        RenderContext rc = configureForExcelExport(docType, view, rgn);

        ResultSet rs = rgn.getResultSet(rc);
        Map<FieldKey, ColumnInfo> map = rc.getFieldMap();
        ExcelWriter ew = new ExcelWriter(rs, map, getExportColumns(rgn.getDisplayColumns()), docType);
        ew.setFilenamePrefix(getSettings().getQueryName());
        return ew;
    }

    // Set up an ExcelWriter that exports no data -- used to export templates on upload pages
    protected ExcelWriter getExcelTemplateWriter(boolean respectView) throws Exception
    {
        // The template should be based on the actual columns in the table, not the user's default view,
        // which may be hiding columns or showing values joined through lookups

        //NOTE: if the the user passed a viewName param on the URL, we will use these columns
        //with the caveat that we will skip and non-user editable columns or those that do
        //map to fields in this table (ie. lookups).  we will also append any missing
        //required columns.

        //TODO: the latter might be problematic if the value of required column is set
        //in a validation script.  however, the dev could always set it to userEditable=false or nullable=true
        List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
        TableInfo t = createTable();

        if (!respectView)
        {
            for (ColumnInfo columnInfo : t.getColumns())
            {
                if (columnInfo.isUserEditable())
                {
                    fieldKeys.add(columnInfo.getFieldKey());
                }
            }
        }
        else
        {
            //get list of required columns so we can verify presence
            Set<FieldKey> requiredCols = new HashSet<FieldKey>();
            for (ColumnInfo c : t.getColumns())
            {
                if(c.inferIsShownInInsertView())
                    requiredCols.add(c.getFieldKey());
            }


            for (FieldKey key : getCustomView().getColumns())
            {
                if (key.getParent() != null)
                    continue;

                Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(t, Collections.singleton(key));
                ColumnInfo col = cols.get(key);
                if (col != null && col.isUserEditable())
                {
                    fieldKeys.add(key);
                    if(requiredCols.contains(key))
                        requiredCols.remove(key);
                }
            }
            fieldKeys.addAll(requiredCols);
        }

        // Force the view to use our special list
        getSettings().setFieldKeys(fieldKeys);

        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        rgn.setAllowAsync(false);
        rgn.setShowPagination(false);
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

    protected RenderContext configureForExcelExport(ExcelWriter.ExcelDocumentType docType, DataView view, DataRegion rgn)
    {
        getSettings().setShowRows(ShowRows.PAGINATED);
        getSettings().setMaxRows(docType.getMaxRows());
        getSettings().setOffset(Table.NO_OFFSET);
        rgn.setAllowAsync(false);
        RenderContext rc = view.getRenderContext();
        // Cache resultset only for SAS/SHARE data sources. See #12966 (which removed caching) and #13638 (which added it back for SAS)
        boolean sas = "SAS".equals(rgn.getTable().getSqlDialect().getProductName());
        rc.setCache(sas);
        return rc;
    }

    public void exportToExcel(HttpServletResponse response) throws Exception
    {
        exportToExcel(response, ExcelWriter.ExcelDocumentType.xls);
    }

    public void exportToExcel(HttpServletResponse response, ExcelWriter.ExcelDocumentType docType) throws Exception
    {
        exportToExcel(response, false, ExcelWriter.CaptionType.Label, false, docType, false, null);
    }

    public void exportToExcelTemplate(HttpServletResponse response, ExcelWriter.CaptionType captionType, boolean insertColumnsOnly) throws Exception
    {
        exportToExcelTemplate(response, captionType, insertColumnsOnly, false, null);
    }

    // Export with no data rows -- just captions
    public void exportToExcelTemplate(HttpServletResponse response, ExcelWriter.CaptionType captionType, boolean insertColumnsOnly, boolean respectView, @Nullable String prefix) throws Exception
    {
        exportToExcel(response, true, captionType, insertColumnsOnly, ExcelWriter.ExcelDocumentType.xls, respectView, prefix);
    }

    protected void exportToExcel(HttpServletResponse response, boolean templateOnly, ExcelWriter.CaptionType captionType, boolean insertColumnsOnly, ExcelWriter.ExcelDocumentType docType, boolean respectView, @Nullable String prefix) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            ExcelWriter ew = templateOnly ? getExcelTemplateWriter(respectView) : getExcelWriter(docType);
            ew.setCaptionType(captionType);
            ew.setShowInsertableColumnsOnly(insertColumnsOnly);
            if(prefix != null)
                ew.setFilenamePrefix(prefix);
            ew.write(response);

            if (!templateOnly)
                logAuditEvent("Exported to Excel");
        }
    }

    public ByteArrayAttachmentFile exportToExcelFile() throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            OutputStream stream = null;
            try
            {
                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                stream = new BufferedOutputStream(byteStream);
                ExcelWriter ew = getExcelWriter(ExcelWriter.ExcelDocumentType.xls);
                ew.setCaptionType(ExcelWriter.CaptionType.Label);
                ew.setShowInsertableColumnsOnly(false);
                ew.write(stream);
                stream.flush();
                ByteArrayAttachmentFile byteArrayAttachmentFile = new ByteArrayAttachmentFile(ew.getFilenamePrefix() + ".xls", byteStream.toByteArray(), "application/vnd.ms-excel");

                logAuditEvent("Exported to Excel file");
                return byteArrayAttachmentFile;
            }
            finally
            {
                if (stream != null)
                    stream.close();
            }
        }
        else
            return null;
    }

    public void exportToTsv(HttpServletResponse response) throws Exception
    {
        exportToTsv(response, false, TSVWriter.DELIM.TAB, TSVWriter.QUOTE.DOUBLE);
    }

    public void exportToTsv(final HttpServletResponse response, final boolean isExportAsWebPage, final TSVWriter.DELIM delim, final TSVWriter.QUOTE quote) throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();

        if (table != null)
        {
            DbScope scope = getSchema().getDbSchema().getScope();

            scope.getSqlDialect().executeWithoutJdbcCaching(scope, new Closure()
            {
                @Override
                public void execute() throws Exception
                {
                    doExport(response, isExportAsWebPage, delim, quote);
                }
            });

            logAuditEvent("Exported to TSV");
        }
    }


    private void doExport(HttpServletResponse response, boolean isExportAsWebPage, final TSVWriter.DELIM delim, final TSVWriter.QUOTE quote) throws ServletException, IOException, SQLException
    {
        TSVGridWriter tsv = getTsvWriter();
        tsv.setExportAsWebPage(isExportAsWebPage);
        tsv.setDelimiterCharacter(delim);
        tsv.setQuoteCharacter(quote);
        tsv.write(response);
    }

    public ByteArrayAttachmentFile exportToTsvFile() throws Exception
    {
        _exportView = true;
        TableInfo table = getTable();
        if (table != null)
        {
            StringBuilder tsvBuilder = new StringBuilder();
            TSVGridWriter tsvWriter = getTsvWriter();
            tsvWriter.setDelimiterCharacter(TSVWriter.DELIM.TAB);
            tsvWriter.setQuoteCharacter(TSVWriter.QUOTE.DOUBLE);
            tsvWriter.write(tsvBuilder);
            ByteArrayAttachmentFile byteArrayAttachmentFile = new ByteArrayAttachmentFile(tsvWriter.getFilenamePrefix() + ".tsv", tsvBuilder.toString().getBytes(), "text/tsv");

            logAuditEvent("Exported to TSV file");
            return byteArrayAttachmentFile;
        }
        else
            return null;
    }

    public void exportToApiResponse(ApiQueryResponse response) throws Exception
    {
        TableInfo table = getTable();
        if (table != null)
        {
            _initializeButtonBar = false;
            setShowDetailsColumn(response.isIncludeDetailsColumn());
            setShowUpdateColumn(response.isIncludeUpdateColumn());
            DataView view = createDataView();
            DataRegion rgn = view.getDataRegion();
            rgn.setShowPaginationCount(!response.isMetaDataOnly());

            //force the pk column(s) into the default list of columns
            List<ColumnInfo> pkCols = table.getPkColumns();
            for (ColumnInfo pkCol : pkCols)
            {
                if (null == rgn.getDisplayColumn(pkCol.getName()))
                    rgn.addColumn(pkCol);
            }

            rgn.setAllowAsync(false);
            response.initialize(view.getRenderContext(), rgn, table, response.isIncludeDetailsColumn() ? rgn.getDisplayColumns() : getExportColumns(rgn.getDisplayColumns()));
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

        // We're not sure if we're dealing with a version of Excel that can handle more than 65535 rows.
        // Assume that it can, and rely on the fact that Excel throws out rows if there are more than it can handle
        RenderContext ctx = configureForExcelExport(ExcelWriter.ExcelDocumentType.xlsx, view, rgn);

        ResultSet rs = rgn.getResultSet(ctx);

        // Bug 5610 & 6179. Excel web queries don't work over SSL if caching is disabled,
        // so we need to allow caching so that Excel can read from IE on Windows.
        // Set the headers to allow the client to cache, but not proxies
        response.setHeader("Pragma", "private");
        response.setHeader("Cache-Control", "private");

        new HtmlWriter().write(rs, getExportColumns(rgn.getDisplayColumns()), response, ctx, true);

        logAuditEvent("Exported to Excel Web Query data");
    }

    protected void logAuditEvent(String comment)
    {
        QueryService.get().addAuditEvent(this, comment);
    }

    public CustomView getCustomView()
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

        if (_table instanceof AbstractTableInfo)
        {
            // Setting URLs is not supported on SchemaTableInfos, which are singletons anyway and therefore
            // shouldn't be mutated by a request
            AbstractTableInfo urlTableInfo = (AbstractTableInfo)_table;
            try
            {
                if (_updateURL != null)
                {
                    urlTableInfo.setUpdateURL(DetailsURL.fromString(_updateURL));
                }
                if (_detailsURL != null)
                {
                    urlTableInfo.setDetailsURL(DetailsURL.fromString(_detailsURL));
                }
                if (_insertURL != null)
                {
                    urlTableInfo.setInsertURL(DetailsURL.fromString(_insertURL));
                }
                if (_importURL != null)
                {
                    urlTableInfo.setImportURL(DetailsURL.fromString(_importURL));
                }
                if (_deleteURL != null)
                {
                    urlTableInfo.setDeleteURL(DetailsURL.fromString(_deleteURL));
                }
            }
            catch (IllegalArgumentException e)
            {
                // Don't report bad client API URLs to the mothership
                throw new ApiUsageException(e);
            }
        }

        if (_table instanceof ContainerFilterable && _table.supportsContainerFilter())
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

    private final boolean isShowExperimentalGenericDetailsURL()
    {
        return AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_GENERIC_DETAILS_URL);
    }


    List<DisplayColumn> _queryDefDisplayColumns = null;

    public List<DisplayColumn> getDisplayColumns()
    {
        List<DisplayColumn> ret = new ArrayList<DisplayColumn>();
        TableInfo table = getTable();
        if (table == null)
            return Collections.emptyList();
        if (_showDetailsColumn && !isPrintView() && !isExportView() && (table.hasDetailsURL() || isShowExperimentalGenericDetailsURL()))
        {
            StringExpression urlDetails = urlExpr(QueryAction.detailsQueryRow);

            if (urlDetails != null && urlDetails != AbstractTableInfo.LINK_DISABLER)
            {
                // We'll decide at render time if we have enough columns in the results to make the DetailsColumn visible
                ret.add(new DetailsColumn(urlDetails, table));
            }
        }

        if (_showUpdateColumn && canUpdate() && !isPrintView() && !isExportView())
        {
            StringExpression urlUpdate = urlExpr(QueryAction.updateQueryRow);

            if (urlUpdate != null)
            {
                ret.add(0, new UpdateColumn(urlUpdate));
            }
        }

        if (null == _queryDefDisplayColumns)
            _queryDefDisplayColumns = getQueryDef().getDisplayColumns(_customView, table);
        ret.addAll(_queryDefDisplayColumns);

        if (_linkTarget != null)
        {
            for (DisplayColumn displayColumn : ret)
            {
                displayColumn.setLinkTarget(_linkTarget);
            }
        }
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

    public void setUpdateURL(String updateURL)
    {
        _updateURL = updateURL;
    }

    public void setDetailsURL(String detailsURL)
    {
        _detailsURL = detailsURL;
    }

    public void setDeleteURL(String deleteURL)
    {
        _deleteURL = deleteURL;
    }

    public void setInsertURL(String insertURL)
    {
        _insertURL = insertURL;
    }

    public void setImportURL(String importURL)
    {
        _importURL = importURL;
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

    public boolean isShowSurroundingBorder()
    {
        return _showSurroundingBorder;
    }

    public void setShowSurroundingBorder(boolean showSurroundingBorder)
    {
        _showSurroundingBorder = showSurroundingBorder;
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

    public boolean isShowReports()
    {
        return _showReports;
    }

    public void setShowReports(boolean showReports)
    {
        _showReports = showReports;
    }

    public boolean isShowConfiguredButtons()
    {
        return _showConfiguredButtons;
    }

    public void setShowConfiguredButtons(boolean showConfiguredButtons)
    {
        _showConfiguredButtons = showConfiguredButtons;
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
