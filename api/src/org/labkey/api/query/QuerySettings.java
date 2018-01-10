/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class QuerySettings
{
    public static final String URL_PARAMETER_PREFIX = "param.";

    private String _schemaName;
    private String _queryName;
    private String _viewName;
    private String _dataRegionName;
    private List<FieldKey> _fieldKeys;
    private ReportIdentifier _reportId;
    private boolean _allowChooseQuery = false;
    private boolean _allowChooseView = true;
    private boolean _allowCustomizeView = true;
    private boolean _allowHeaderLock = true;
    private boolean _showReports = true;
    private boolean _ignoreUserFilter;
    private int _maxRows = 100;
    private boolean _maxRowsSet = false; // Explicitly track setting maxRows, allows for different defaults
    private long _offset = 0;
    private String _selectionKey = null;

    @NotNull
    private String _lastFilterScope = "";

    private ShowRows _showRows = ShowRows.PAGINATED;

    PropertyValues _filterSort = null;
    private URLHelper _returnURL = null;

    private String _containerFilterName;
    private List<AnalyticsProviderItem> _analyticsProviders = new ArrayList<>();

    private SimpleFilter _baseFilter;
    private Sort _baseSort;
    private QueryDefinition _queryDef;
    private TableInfo _table;

    private final Map<String, Object> _queryParameters = new CaseInsensitiveHashMap<>();


    protected QuerySettings(String dataRegionName)
    {
        _dataRegionName = dataRegionName;

        assert MemTracker.getInstance().put(this);
    }

    /**
     * Init the QuerySettings using all the request parameters, from context.getPropertyValues().
     * @see UserSchema#getSettings(org.labkey.api.view.ViewContext, String)
     */
    public QuerySettings(ViewContext context, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(getPropertyValues(context));

        assert MemTracker.getInstance().put(this);
    }


    /**
     * Init the QuerySettings using all the request parameters, from context.getPropertyValues().
     * @see UserSchema#getSettings(org.labkey.api.view.ViewContext, String, String)
     */
    public QuerySettings(ViewContext context, String dataRegionName, String queryName)
    {
        _dataRegionName = dataRegionName;
        init(context);
        setQueryName(queryName);

        assert MemTracker.getInstance().put(this);
    }


    /**
     * @param params    all parameters from URL or POST, including dataregion.filter parameters
     * @param dataRegionName    prefix for filter params etc
     * @see UserSchema#getSettings(org.springframework.beans.PropertyValues, String) 
     */
    public QuerySettings(PropertyValues params, String dataRegionName)
    {
        _dataRegionName = dataRegionName;
        init(params);

        assert MemTracker.getInstance().put(this);
    }


    protected PropertyValues getPropertyValues(ViewContext context)
    {
        PropertyValues pvs = context.getBindPropertyValues();
        if (null == pvs)
        {
            Logger.getLogger(QuerySettings.class).warn("PropertyValues not set");
            pvs = context.getActionURL().getPropertyValues();
        }
        return pvs;
    }


    /**
     * @param url parameters for filter/sort
     */
    public void setSortFilterURL(ActionURL url)
    {
        setSortFilter(url.getPropertyValues());
    }


    public void setSortFilter(PropertyValues pvs)
    {
        _filterSort = pvs;
        String showRowsParam = _getParameter(param(QueryParam.showRows));
        if (showRowsParam != null)
        {
            try
            {
                _showRows = ShowRows.valueOf(showRowsParam.toUpperCase());
            }
            catch (IllegalArgumentException ex)
            {
                _showRows = ShowRows.PAGINATED;
            }
        }
    }

    protected String _getParameter(String param)
    {
        PropertyValue pv = _filterSort.getPropertyValue(param);
        if (pv == null)
            return null;
        Object v = pv.getValue();
        if (v == null)
            return null;
        if (v.getClass().isArray())
        {
            Object[] a = (Object[])v;
            v = a.length == 0 ? null : a[0];
        }
        return v == null ? null : String.valueOf(v);
    }

    public void init(ViewContext context)
    {
        init(getPropertyValues(context));    
    }


    /**
     * Initialize QuerySettings from the PropertyValues, binds all fields that are supported on the URL
     *. such as viewName.  Use setSortFilter() to provide sort filter parameters w/o affecting the other
     * properties.
     */
    public void init(PropertyValues pvs)
    {
        if (null == pvs)
            pvs = new MutablePropertyValues();
        setSortFilter(pvs);
        setAnalyticsProviders(pvs);

        // Let URL parameter control which query we show, even if we don't show the Query drop-down menu to let the user choose
        String param = param(QueryParam.queryName);
        String queryName = StringUtils.trimToNull(_getParameter(param));
        if (queryName != null)
        {
            setQueryName(queryName);
        }

        if (getAllowChooseView())
        {
            String viewName = StringUtils.trimToNull(_getParameter(param(QueryParam.viewName)));
            if (viewName != null)
            {
                setViewName(viewName);
            }
            if (_getParameter(param(QueryParam.ignoreFilter)) != null)
            {
                _ignoreUserFilter = true;
            }

            setReportId(ReportService.get().getReportIdentifier(_getParameter(param(QueryParam.reportId))));
        }

        // Ignore maxRows and offset parameters when not PAGINATED.
        if (_showRows == ShowRows.PAGINATED)
        {
            String offsetParam = _getParameter(param(QueryParam.offset));
            if (offsetParam != null)
            {
                try
                {
                    long offset = Long.parseLong(offsetParam);
                    if (offset > 0)
                        _offset = offset;
                }
                catch (NumberFormatException ignored) { }
            }

            String maxRowsParam = _getParameter(param(QueryParam.maxRows));
            if (maxRowsParam != null)
            {
                try
                {
                    int maxRows = Integer.parseInt(maxRowsParam);
                    if (maxRows >= 0 || maxRows == Table.ALL_ROWS)
                        setMaxRows(maxRows);
                    
                    if (_maxRows == Table.NO_ROWS)
                        _showRows = ShowRows.NONE;
                    if (_maxRows == Table.ALL_ROWS)
                        _showRows = ShowRows.ALL;
                }
                catch (NumberFormatException ignored) { }
            }
        }

        String containerFilterNameParam = _getParameter(param(QueryParam.containerFilterName));
        if (containerFilterNameParam != null)
            setContainerFilterName(containerFilterNameParam);

        String returnURL = _getParameter(ActionURL.Param.returnUrl.name());
        if (returnURL == null)
            returnURL = _getParameter("returnURL");
        if (returnURL == null)
            returnURL = _getParameter(QueryParam.srcURL.toString());
        if (returnURL != null)
        {
            try
            {
                URLHelper url = new URLHelper(returnURL);
                url.setReadOnly();
                setReturnUrl(url);
            }
            catch (URISyntaxException | IllegalArgumentException ignored) { }
        }

        String columns = StringUtils.trimToNull(_getParameter(param(QueryParam.columns)));
        if (null != columns)
        {
            String[] colArray = columns.split(",");
            _fieldKeys = new ArrayList<>();
            for (String key : colArray)
            {
                if (!(StringUtils.isEmpty(key)))
                {
                    _fieldKeys.add(FieldKey.fromString(StringUtils.trim(key)));
                }
            }
        }

        String selectionKey = StringUtils.trimToNull(_getParameter(param(QueryParam.selectionKey)));
        if (null != selectionKey)
            setSelectionKey(selectionKey);

        _parseQueryParameters(_filterSort);

        String allowHeaderLock = StringUtils.trimToNull(_getParameter(param(QueryParam.allowHeaderLock)));
        if (null != allowHeaderLock)
            setAllowHeaderLock(BooleanUtils.toBoolean(allowHeaderLock));
    }

    public @NotNull Map<String, Object> getQueryParameters()
    {
        return _queryParameters;
    }
    
    void _parseQueryParameters(PropertyValues pvs)
    {
        String paramPrefix = param(URL_PARAMETER_PREFIX).toLowerCase();
        for (PropertyValue pv : pvs.getPropertyValues())
        {
            if (!pv.getName().toLowerCase().startsWith(paramPrefix))
                continue;
            _queryParameters.put(pv.getName().substring(paramPrefix.length()),pv.getValue());
        }
    }

    void setQueryParameter(String name, Object value)
    {
        _queryParameters.put(name,value);
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setViewName(String viewName)
    {
        _viewName = StringUtils.trimToNull(viewName);
    }

    public String getViewName()
    {
        return _viewName;
    }

    public ReportIdentifier getReportId()
    {
        return _reportId;
    }

    public void setReportId(ReportIdentifier reportId)
    {
        _reportId = reportId;
    }

    public void setDataRegionName(String name)
    {
        _dataRegionName = name;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public void setSelectionKey(String selectionKey)
    {
        _selectionKey = selectionKey;
    }

    public String getSelectionKey()
    {
        if (_selectionKey != null)
            return _selectionKey;
        return DataRegionSelection.getSelectionKey(getSchemaName(), getQueryName(), getViewName(), getDataRegionName());
    }

    public void setAllowChooseQuery(boolean b)
    {
        _allowChooseQuery = b;
    }

    public boolean getAllowChooseQuery()
    {
        return _allowChooseQuery;
    }

    public void setAllowChooseView(boolean b)
    {
        _allowChooseView = b;
    }

    public boolean getAllowChooseView()
    {
        return _allowChooseView;
    }

    public void setAllowHeaderLock(boolean b)
    {
        _allowHeaderLock = b;
    }

    public boolean getAllowHeaderLock()
    {
        return _allowHeaderLock;
    }

    /**
     * Returns the "returnURL" parameter or null if none.
     * The url may not necessarily be an ActionURL, e.g. if served from a FileContent html page.
     */
    public URLHelper getReturnUrl()
    {
        return _returnURL;
    }

    public void setReturnUrl(URLHelper returnURL)
    {
        _returnURL = returnURL;
    }

    public String param(QueryParam param)
    {
        switch (param)
        {
            case schemaName:
                return param.toString();
            default:
                return param(param.toString());
        }
    }

    protected String param(String param)
    {
        if (getDataRegionName() == null)
            return param;
        return getDataRegionName() + "." + param;
    }

    public final TableInfo getTable(UserSchema schema)
    {
        if (_table == null)
        {
            _table = createTable(schema);
        }
        return _table;
    }

    protected TableInfo createTable(UserSchema schema)
    {
        String queryName = getQueryName();
        if (queryName == null)
            return null;
        TableInfo table = schema.getTable(queryName);
        if (table instanceof ContainerFilterable && getContainerFilterName() != null)
            ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(getContainerFilterName(), schema.getUser()));
        return table;
    }

    public final QueryDefinition getQueryDef(UserSchema schema)
    {
        if (_queryDef == null)
        {
            _queryDef = createQueryDef(schema);
        }
        return _queryDef;
    }

    protected QueryDefinition createQueryDef(UserSchema schema)
    {
        String queryName = getQueryName();
        if (queryName == null)
            return null;

        QueryDefinition ret = schema.getQueryDef(queryName);
        if (ret == null)
            ret = schema.getQueryDefForTable(queryName);

        if (ret != null && getContainerFilterName() != null)
            ret.setContainerFilter(ContainerFilter.getContainerFilterByName(getContainerFilterName(), schema.getUser()));

        return ret;
    }

    public CustomView getCustomView(ViewContext context, QueryDefinition queryDef)
    {
        if (queryDef == null)
        {
            return null;
        }
        return queryDef.getCustomView(context.getUser(), context.getRequest(), getViewName());
    }

    public Report getReportView(ContainerUser cu)
    {
        try {
            if (getReportId() != null)
            {
                return getReportId().getReport(cu);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean getIgnoreUserFilter()
    {
        return _ignoreUserFilter;
    }

    public void setIgnoreUserFilter(boolean b)
    {
        _ignoreUserFilter = b;
    }

    /** @return The maxRows parameter when {@link ShowRows#PAGINATED}, otherwise ALL_ROWS. */
    public int getMaxRows()
    {
        if (_showRows == ShowRows.NONE)
            return Table.NO_ROWS;
        if (_showRows != ShowRows.PAGINATED)
            return Table.ALL_ROWS;
        return _maxRowsSet ? _maxRows : 100;
    }

    /** @param maxRows the maximum number of rows to return, or Table.ALL_ROWS (unlimited) or Table.NO_ROWS (metadata only) */
    public void setMaxRows(int maxRows)
    {
        assert Table.validMaxRows(maxRows) : maxRows + " is an illegal value for maxRows; should be positive, Table.ALL_ROWS or Table.NO_ROWS";
        assert (maxRows == Table.NO_ROWS && _showRows == ShowRows.NONE) || (maxRows == Table.ALL_ROWS && _showRows == ShowRows.ALL) || _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _maxRowsSet = true;
        _maxRows = maxRows;
    }

    /** @return Boolean indicating if the maxRows param has been set, default false. */
    public boolean isMaxRowsSet()
    {
        return _maxRowsSet;
    }

    /** @return The offset parameter when {@link ShowRows#PAGINATED}, otherwise 0. */
    public long getOffset()
    {
        if (_showRows != ShowRows.PAGINATED)
            return Table.NO_OFFSET;
        return _offset;
    }

    public void setOffset(long offset)
    {
        assert (offset == Table.NO_OFFSET && _showRows != ShowRows.PAGINATED) || _showRows == ShowRows.PAGINATED : "Can't set maxRows when not paginated";
        _offset = offset;
    }

    public ShowRows getShowRows()
    {
        return _showRows;
    }

    public void setShowRows(ShowRows showRows)
    {
        _showRows = showRows;
    }

    /**
     * Base filter is applied before the custom view's filters and before any filter set by the user on the sortFilterURL.
     * The returned SimpleFilter is not null and may be mutated in place without calling the setBaseFilter() method.
     */
    public @NotNull SimpleFilter getBaseFilter()
    {
        if (_baseFilter == null)
            _baseFilter = new SimpleFilter();
        return _baseFilter;
    }

    public void setBaseFilter(SimpleFilter filter)
    {
        _baseFilter = filter;
    }

    /**
     * Base sort is applied before the custom view's sorts and before any sorts set by the user on the sortFilterURL.
     * The returned Sort is not null and may be mutated in place without calling the setBaseSort() method.
     */
    public @NotNull Sort getBaseSort()
    {
        if (_baseSort == null)
            _baseSort = new Sort();
        return _baseSort;
    }

    public void setBaseSort(Sort baseSort)
    {
        _baseSort = baseSort;
    }

    public ActionURL getSortFilterURL()
    {
        // Root context isn't available in background jobs
        ActionURL url;
        ViewContext context = HttpView.getRootContext();
        if (context != null)
            url = context.cloneActionURL();
        else
            url = new ActionURL();
        url.deleteParameters();
        url.setPropertyValues(_filterSort);
        return url;
    }

    public void addSortFilters(Map<String, Object> filters)
    {
        if (filters != null && filters.size() > 0)
        {
            // UNDONE: there should be an easier way to convert into a Filter than having to serialize them onto an ActionUrl and back out.
            // Issue 17411: Support multiple filters and aggregates on the same column.
            // If the value is a JSONArray of values, add each filter or aggregate as an additional URL parameter.
            ActionURL url = new ActionURL();
            for (String paramName : filters.keySet())
            {
                Object o = filters.get(paramName);
                Object[] values = null;
                if (o instanceof Object[])
                    values = (Object[])o;
                else if (o instanceof JSONArray)
                    values = ((JSONArray)o).toArray();

                if (values != null)
                    for (Object value : values)
                        url.addParameter(paramName, String.valueOf(value));
                else
                    url.addParameter(paramName, String.valueOf(filters.get(paramName)));
            }

            // NOTE: Creating filters may throw IllegalArgumentException or ConversionException.  See Issue 22456.
            SimpleFilter filter = getBaseFilter();
            filter.addUrlFilters(url, getDataRegionName());

            Sort sort = getBaseSort();
            sort.addURLSort(url, getDataRegionName());

            List<AnalyticsProviderItem> analyticsProviders = getAnalyticsProviders();
            analyticsProviders.addAll(AnalyticsProviderItem.fromURL(url, getDataRegionName()));

            // XXX: containerFilter
        }
    }

    public boolean isAllowCustomizeView()
    {
        return _allowCustomizeView;
    }

    public void setAllowCustomizeView(boolean allowCustomizeView)
    {
        _allowCustomizeView = allowCustomizeView;
    }

    public String getContainerFilterName()
    {
        return _containerFilterName;
    }

    public void setContainerFilterName(String name)
    {
        ContainerFilter.logSetContainerFilter(null, "QuerySettings", name);
        _containerFilterName = name;
    }

    public void addAggregates(Aggregate... aggregates)
    {
        for (Aggregate aggregate : aggregates)
            _analyticsProviders.add(new AnalyticsProviderItem(aggregate));
    }

    public void addAnalyticsProviders(AnalyticsProviderItem... analyticsProviders)
    {
        _analyticsProviders.addAll(Arrays.asList(analyticsProviders));
    }

    public List<AnalyticsProviderItem> getAnalyticsProviders()
    {
        return _analyticsProviders;
    }

    public void setAnalyticsProviders(List<AnalyticsProviderItem> analyticsProviders)
    {
        _analyticsProviders = analyticsProviders;
    }

    public void setAnalyticsProviders(PropertyValues pvs)
    {
        _analyticsProviders.addAll(AnalyticsProviderItem.fromURL(pvs, getDataRegionName()));
    }

    public List<FieldKey> getFieldKeys()
    {
        return _fieldKeys;
    }

    public void setFieldKeys(List<FieldKey> keys)
    {
        _fieldKeys = keys;
    }

    /** Optional scoping, beyond the folder itself, for .lastFilter */
    @NotNull
    public String getLastFilterScope()
    {
        return _lastFilterScope;
    }

    /** Optional scoping, beyond the folder itself, for .lastFilter */
    public void setLastFilterScope(@NotNull String lastFilterScope)
    {
        _lastFilterScope = lastFilterScope;
    }

    public boolean isShowReports()
    {
        return _showReports;
    }

    public void setShowReports(boolean showReports)
    {
        _showReports = showReports;
    }
}
