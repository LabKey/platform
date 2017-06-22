/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NullPreventingSet;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderContext implements Map<String, Object>, Serializable
{
    private static final Logger _log = Logger.getLogger(RenderContext.class);

    private boolean _useContainerFilter = true;
    private ViewContext _viewContext;
    private @Nullable Errors _errors;
    private TableViewForm _form;
    private DataRegion _currentRegion;
    private Filter _baseFilter;
    private Map<String, Object> _row;
    private Map<String, Object> _extra = new HashMap<>();
    private Sort _baseSort;
    private int _mode = DataRegion.MODE_NONE;
    private boolean _cache = true;
    protected Set<FieldKey> _ignoredColumnFilters = new LinkedHashSet<>();
    private Set<String> _selected = null;
    private ShowRows _showRows = ShowRows.PAGINATED;
    private List<String> _recordSelectorValueColumns;
    private CustomView _view;

    private List<AnalyticsProviderItem> _analyticsProviders;
    private Map<FieldKey, List<String>> _analyticsProviderNamesByFieldKey;

    private Results _rs;

    public RenderContext(ViewContext context)
    {
        this(context, null);
    }

    public RenderContext(ViewContext context, @Nullable Errors errors)
    {
        _viewContext = context;
        setErrors(errors);
        MemTracker.getInstance().put(this);
    }

    protected RenderContext()
    {
    }

    /* used by DataView */
    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    public @Nullable Errors getErrors()
    {
        return _errors;
    }

    public void setErrors(@Nullable Errors errors)
    {
        _errors = errors;
    }

    public TableViewForm getForm()
    {
        return _form;
    }

    public void setForm(TableViewForm form)
    {
        _form = form;
    }

    public DataRegion getCurrentRegion()
    {
        return _currentRegion;
    }

    public void setCurrentRegion(DataRegion currentRegion)
    {
        _currentRegion = currentRegion;
        if (_currentRegion != null)
        {
            _showRows = _currentRegion.getShowRows();
            _recordSelectorValueColumns = _currentRegion.getRecordSelectorValueColumns();

            if (_currentRegion.getSelectionKey() != null)
            {
                _selected = DataRegionSelection.getSelected(getViewContext(), _currentRegion.getSelectionKey(), true, false);
            }
        }
        else
        {
            _showRows = ShowRows.PAGINATED;
            _recordSelectorValueColumns = null;
            _selected = null;
        }
    }

    public String getSelectionKey()
    {
        return _currentRegion != null ? _currentRegion.getSelectionKey() : null;
    }

    public Filter getBaseFilter()
    {
        return _baseFilter;
    }

    public void setBaseFilter(Filter filter)
    {
        _baseFilter = filter;
    }

    public Sort getBaseSort()
    {
        return _baseSort;
    }

    public void setBaseSort(Sort sort)
    {
        _baseSort = sort;
    }

    public List<AnalyticsProviderItem> getBaseSummaryStatsProviders()
    {
        List<AnalyticsProviderItem> summaryStatsProviders = new ArrayList<>();

        if (getBaseAnalyticsProviders() != null)
        {
            for (AnalyticsProviderItem analyticsProvider : getBaseAnalyticsProviders())
            {
                if (analyticsProvider.isSummaryStatistic())
                    summaryStatsProviders.add(analyticsProvider);
            }
        }

        return !summaryStatsProviders.isEmpty() ? summaryStatsProviders : null;
    }

    public List<AnalyticsProviderItem> getBaseAnalyticsProviders()
    {
        return _analyticsProviders;
    }

    public void setBaseAnalyticsProviders(List<AnalyticsProviderItem> analyticsProviders)
    {
        _analyticsProviders = analyticsProviders;

        _analyticsProviderNamesByFieldKey = new HashMap<>();
        for (AnalyticsProviderItem analyticsProvider : analyticsProviders)
        {
            if (!_analyticsProviderNamesByFieldKey.containsKey(analyticsProvider.getFieldKey()))
                _analyticsProviderNamesByFieldKey.put(analyticsProvider.getFieldKey(), new ArrayList<>());

            _analyticsProviderNamesByFieldKey.get(analyticsProvider.getFieldKey()).add(analyticsProvider.getName());
        }
    }

    public boolean containsAnalyticsProvider(FieldKey fieldKey, String providerName)
    {
        if (_analyticsProviderNamesByFieldKey != null && !_analyticsProviderNamesByFieldKey.isEmpty())
            return _analyticsProviderNamesByFieldKey.containsKey(fieldKey) && _analyticsProviderNamesByFieldKey.get(fieldKey).contains(providerName);

        return false;
    }

    public Results getResults()
    {
        return _rs;
    }

    public void setResults(Results rs)
    {
        _rs = rs;
    }

    public static List<ColumnInfo> getSelectColumns(List<DisplayColumn> displayColumns, TableInfo tinfo)
    {
        assert null != (displayColumns = Collections.unmodifiableList(displayColumns));
        Table.checkAllColumns(tinfo, tinfo.getColumns(), "RenderContext.getSelectColumns() tinfo.getColumns()");

        Set<ColumnInfo> ret = new NullPreventingSet<>(new LinkedHashSet<ColumnInfo>());
        LinkedHashSet<FieldKey> keys = new LinkedHashSet<>();

        if (null == displayColumns || displayColumns.size() == 0)
        {
            ret.addAll(tinfo.getColumns());
        }
        else
        {
            for (DisplayColumn displayColumn : displayColumns)
            {
                assert null != displayColumn;
                if (null == displayColumn)
                    continue;
                displayColumn.addQueryColumns(ret);
            }

            // add any additional columns specified by FieldKey
            for (DisplayColumn dc : displayColumns)
                dc.addQueryFieldKeys(keys);
        }

        Table.checkAllColumns(tinfo, ret, "RenderContext.getSelectColumns() ret, after adding display columns");

        Collection<ColumnInfo> infoCollection = QueryService.get().getColumns(tinfo, keys, ret).values();
        ret.addAll(infoCollection);

        //always need to select pks
        ret.addAll(tinfo.getPkColumns());

        String versionCol = tinfo.getVersionColumnName();

        if (null != versionCol)
        {
            ColumnInfo col = tinfo.getColumn(versionCol);
            ret.add(col);
        }

        Table.checkAllColumns(tinfo, ret, "RenderContext.getSelectColumns() ret, method end");

        return new ArrayList<>(ret);
    }


    public ActionURL getSortFilterURLHelper()
    {
        return getSortFilterURLHelper(getViewContext());
    }


    public static ActionURL getSortFilterURLHelper(ViewContext context)
    {
        return context.cloneActionURL();
    }

    /**
     * valid after call to getResultSet()
     */
    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return null == _rs ? null : _rs.getFieldMap();
    }

    private List<ColumnInfo> getColumnInfos(List<DisplayColumn> displayColumns)
    {
        // collect the ColumnInfo for each DisplayColumn
        List<ColumnInfo> columnInfos = new ArrayList<>();
        if (null != displayColumns && !displayColumns.isEmpty())
        {
            displayColumns
                    .stream()
                    .filter(dc -> dc.getColumnInfo() != null)
                    .forEach(dc -> columnInfos.add(dc.getColumnInfo()));
        }
        return columnInfos;
    }

    public Results getResultSet(Map<FieldKey, ColumnInfo> fieldMap, List<DisplayColumn> displayColumns, TableInfo tinfo, QuerySettings settings, Map<String, Object> parameters, int maxRows, long offset, String name, boolean async) throws SQLException, IOException
    {
        ActionURL url;
        if (null != settings)
            url = settings.getSortFilterURL();
        else
            url = getViewContext().cloneActionURL();

        Sort sort = buildSort(tinfo, url, name);
        SimpleFilter filter = buildFilter(tinfo, getColumnInfos(displayColumns), url, name, maxRows, offset, sort);

        Collection<ColumnInfo> cols = fieldMap.values();
        if (null != QueryService.get())
            cols = QueryService.get().ensureRequiredColumns(tinfo, cols, filter, sort, _ignoredColumnFilters);

        _rs = selectForDisplay(tinfo, cols, parameters, filter, sort, maxRows, offset, async);
        return _rs;
    }

    public Map<String, List<Aggregate.Result>> getAggregates(List<DisplayColumn> displayColumns, TableInfo tinfo, QuerySettings settings, String dataRegionName, List<Aggregate> aggregatesIn, Map<String, Object> parameters, boolean async) throws IOException
    {
        if (aggregatesIn == null || aggregatesIn.isEmpty())
            return Collections.emptyMap();

        ActionURL url;
        if (null != settings)
            url = settings.getSortFilterURL();
        else
            url = getViewContext().cloneActionURL();

        Sort sort = buildSort(tinfo, url, dataRegionName);
        SimpleFilter filter = buildFilter(tinfo, getColumnInfos(displayColumns), url, dataRegionName, Table.ALL_ROWS, Table.NO_OFFSET, sort);

        Set<FieldKey> ignoredAggregateFilters = new HashSet<>();

        Collection<ColumnInfo> cols = getSelectColumns(displayColumns, tinfo);
        if (null != QueryService.get())
            cols = QueryService.get().ensureRequiredColumns(tinfo, cols, filter, sort, ignoredAggregateFilters);

        if (!ignoredAggregateFilters.equals(_ignoredColumnFilters))
        {
            // This should never happen, but if it did, the totals wouldn't match, so we won't calculate them.
            _log.error("Aggregate filter columns do not match main.  Aggregate:" + ignoredAggregateFilters + " Main:" + _ignoredColumnFilters);
            return Collections.emptyMap();
        }

        List<Aggregate> aggregates = new ArrayList<>();
        Map<FieldKey, ColumnInfo> availableFieldKeys = Table.createColumnMap(tinfo, cols);

        for (Aggregate aggregate : aggregatesIn)
        {
            FieldKey fieldKey = aggregate.getFieldKey();
            if (aggregate.isCountStar() || availableFieldKeys.containsKey(fieldKey))
            {
                aggregates.add(aggregate);
            }
            else if (fieldKey.getName().startsWith("*::") || fieldKey.getName().endsWith("::*"))
            {
                // expand aggregates containing "::" to match pivot field keys
                String[] aggNameParts = fieldKey.getName().split("::", 2);
                if (aggNameParts.length == 2)
                {
                    for (FieldKey availableFieldKey : availableFieldKeys.keySet())
                    {
                        if (availableFieldKey.getName().contains("::"))
                        {
                            String[] colNameParts = availableFieldKey.getName().split("::", 2);
                            if (colNameParts.length == 2)
                            {
                                if ("*".equals(aggNameParts[0]) && "*".equals(aggNameParts[1]))
                                {
                                    aggregates.add(new Aggregate(availableFieldKey, aggregate.getType(), aggregate.getLabel(), aggregate.isDistinct()));
                                }
                                else if ("*".equals(aggNameParts[0]) && aggNameParts[1].equalsIgnoreCase(colNameParts[1]))
                                {
                                    aggregates.add(new Aggregate(availableFieldKey, aggregate.getType(), aggregate.getLabel(), aggregate.isDistinct()));
                                }
                                else if ("*".equals(aggNameParts[1]) && aggNameParts[0].equalsIgnoreCase(colNameParts[0]))
                                {
                                    aggregates.add(new Aggregate(availableFieldKey, aggregate.getType(), aggregate.getLabel(), aggregate.isDistinct()));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!aggregates.isEmpty())
        {
            TableSelector selector = new TableSelector(tinfo, cols, filter, null).setNamedParameters(parameters);

            if (async)
                return selector.getAggregatesAsync(aggregates, getViewContext().getResponse());
            else
                return selector.getAggregates(aggregates);
        }

        return Collections.emptyMap();
    }


    public Sort buildSort(TableInfo tinfo, ActionURL url, String name)
    {
        // Create a copy of the sort so that QueryService.ensureRequiredColumns() can
        // safely remove any unresolved columns from the sort without affecting others.
        Sort sort = new Sort();
        Sort baseSort = getBaseSort();
        if (baseSort != null)
            sort.insertSort(baseSort);
        sort.addURLSort(url, name);
        return sort;
    }

    public SimpleFilter buildFilter(TableInfo tinfo, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        return buildFilter(tinfo, Collections.emptyList(), url, name, maxRows, offset, sort);
    }

    public SimpleFilter buildFilter(TableInfo tinfo, List<ColumnInfo> displayColumns, ActionURL url, String name, int maxRows, long offset, Sort sort)
    {
        SimpleFilter filter = new SimpleFilter(getBaseFilter());
        //HACK.. Need to fix up the casing of columns throughout so don't have to do
        //this...
        ColumnInfo containerCol = tinfo.getColumn("container");
        Container c = getContainer();

        if (null != c && null != containerCol && isUseContainerFilter() && tinfo.needsContainerClauseAdded())
        {
            // This CAST improves performance on Postgres for some queries by choosing a more efficient query plan
            filter.addClause(new SimpleFilter.SQLClause(containerCol.getName() + " = CAST('" + c.getId() + "' AS UniqueIdentifier)", new Object[0], containerCol.getFieldKey()));
        }

        if (_currentRegion != null && _showRows == ShowRows.SELECTED || _showRows == ShowRows.UNSELECTED)
            buildSelectedFilter(filter, tinfo, _showRows == ShowRows.UNSELECTED);
        else
            filter.addUrlFilters(url, name, displayColumns);

        return filter;
    }

    protected void buildSelectedFilter(SimpleFilter filter, TableInfo tinfo, boolean inverted)
    {
        List<String> selectorColumns = getRecordSelectorValueColumns();

        if (selectorColumns == null || selectorColumns.isEmpty())
        {
            selectorColumns = tinfo.getPkColumnNames();
        }

        Set<String> selected = getAllSelected();
        SimpleFilter.FilterClause clause;

        if (selected.isEmpty() || selectorColumns.isEmpty())
        {
            clause = new SimpleFilter.SQLClause("1 = 0", null);
        }
        else if (selectorColumns.size() == 1)
        {
            clause = new SimpleFilter.InClause(FieldKey.fromString(selectorColumns.get(0)), selected, true);
        }
        else
        {
            SimpleFilter.OrClause or = new SimpleFilter.OrClause();

            for (String row : selected)
            {
                SimpleFilter.AndClause and = new SimpleFilter.AndClause();
                String[] parts = row.split(",");
                assert parts.length == selectorColumns.size() : "Selected item and columns don't match in length: " + row;
                for (int i = 0; i < parts.length; i++)
                {
                    SimpleFilter.FilterClause eq = CompareType.EQUAL.createFilterClause(FieldKey.fromString(selectorColumns.get(i)), parts[i]);
                    eq._needsTypeConversion = true;
                    and.addClause(eq);
                }
                or.addClause(and);
            }

            clause = or;
        }

        if (inverted)
        {
            clause = new SimpleFilter.NotClause(clause);
        }

        filter.addClause(clause);
    }

    protected Results selectForDisplay(TableInfo table, Collection<ColumnInfo> columns, Map<String, Object> parameters, SimpleFilter filter, Sort sort, int maxRows, long offset, boolean async) throws SQLException, IOException
    {
        TableSelector selector = new TableSelector(table, columns, filter, sort).setNamedParameters(parameters).setMaxRows(maxRows).setOffset(offset);

        if (async)
        {
            return selector.getResultsAsync(getCache(), false, getViewContext().getResponse());
        }
        else
        {
            return selector.getResults(getCache(), false);
        }
    }


    public boolean getCache()
    {
        return _cache;
    }

    public void setCache(boolean cache)
    {
        _cache = cache;
    }

    public Map<String, Object> getRow()
    {
        return _row;
    }

    public void setRow(Map<String, Object> row)
    {
        if (row != null)
            _row = Collections.unmodifiableMap(row);
    }

    public int getMode()
    {
        return _mode;
    }

    public void setMode(int mode)
    {
        _mode = mode;
    }

    /**
     * Overrides isEmpty() to include current row
     */
    @Override
    public boolean isEmpty()
    {
        return _extra.isEmpty() && (_row == null || _row.isEmpty());
    }

    /**
     * Overrides containsKey() to include current row
     */
    @Override
    public boolean containsKey(Object key)
    {
        if (key instanceof FieldKey)
        {
            if (null != _rs)
            {
                if (_rs.hasColumn((FieldKey) key))
                    return true;
            }
            // <UNDONE>
            FieldKey f = (FieldKey) key;
            key = f.getParent() == null ? f.getName() : f.encode();
            // </UNDONE>
        }

        return _extra.containsKey(key) || (_row != null && _row.containsKey(key));
    }

    /**
     * Overrides containsValue() to include current row
     */
    @Override
    public boolean containsValue(Object value)
    {
        return _extra.containsValue(value) || (_row != null && _row.containsValue(value));
    }

    /**
     * Overrides values() to combine keys from map and current row
     */
    @Override
    public Collection<Object> values()
    {
        Collection<Object> values = _extra.values();
        if (null != _row)
        {
            values = new ArrayList<>(values);
            values.addAll(_row.values());
        }

        return values;
    }

    /**
     * Overrides entrySet to combine entries from map and current row
     */
    @Override
    public Set<Map.Entry<String, Object>> entrySet()
    {
        Set<Map.Entry<String, Object>> entrySet = _extra.entrySet();

        if (null != _row)
        {
            entrySet = new HashSet<>(entrySet);
            entrySet.addAll(_row.entrySet());
        }

        return entrySet;
    }

    /**
     * Overrides keySet to combine keys from map and current row
     */
    @Override
    public Set<String> keySet()
    {
        Set<String> keySet = _extra.keySet();

        if (null != _row)
        {
            keySet = new HashSet<>(keySet);
            keySet.addAll(_row.keySet());
        }

        return keySet;
    }

    /**
     * Overrides get to look first in the map & if not found there look in the current row
     *
     * @param key
     * @return
     */
    @Override
    public Object get(Object key)
    {
        Object val = null;

        if (key instanceof FieldKey)
        {
            if (null != _rs && _rs.hasColumn((FieldKey) key))
            {
                try
                {
                    ColumnInfo col = _rs.findColumnInfo((FieldKey) key);

                    if (null == col)
                        return null;

                    Object value = col.getValue(_row);
                    JdbcType type = col.getJdbcType();

                    // Hack for DATE and TIMESTAMP columns on SQL Server jTDS, see #27332
                    if ((type == JdbcType.DATE || type == JdbcType.TIMESTAMP) && value instanceof String)
                    {
                        try
                        {
                            value = type.convert(value);
                        }
                        catch (ConversionException x)
                        {
                            /* pass */
                        }
                    }

                    return value;
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }

            // NOTE: Ideally we should not need to convert FieldKey to String at all
            // but _row is currently a <String,Object> map (not <FieldKey,Object>)
            FieldKey f = (FieldKey) key;
            key = f.getParent() == null ? f.getName() : f.encode();

            // 13607 : Nonconforming field names in datasets cause data loss on edit
            // use FieldKey->ColumnInfo.getAlias() mapping if available
            if (null != getFieldMap())
            {
                ColumnInfo col = getFieldMap().get(f);
                if (null != col)
                    key = getFieldMap().get(f).getAlias();
            }
        }

        if (null != _row)
            val = _row.get(key);

        if (null == val)
            val = _extra.get(key);

        return val;
    }

    @Override
    public int size()
    {
        return (_row != null ? _row.size() : 0) + _extra.size();
    }

    @Override
    public Object put(String key, Object value)
    {
        if (_row != null && _row.containsKey(key))
        {
            _log.warn("Attempted to update '" + key + "' in row");
            return null;
        }

        return _extra.put(key, value);
    }

    @Override
    public Object remove(Object key)
    {
        if (_row != null && _row.containsKey(key))
        {
            _log.warn("Attempted to remove '" + key + "' from row");
            return null;
        }

        return _extra.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    // for backward compatibility in URL substitution
    public String getContainerPath()
    {
//        assert false : "don't use ${containerPath}";
        return getViewContext().getContainer().getPath();
    }

    // for backward compatibility in URL substitution
    public String getContextPath()
    {
//        assert false : "don't use ${contextPath}";
        return getViewContext().getContextPath();
    }


    /**
     * ViewContext wrappers
     */
    public Container getContainer()
    {
        return _viewContext.getContainer();
    }


    public void setContainer(Container c)
    {
        _viewContext.setContainer(c);
    }

    public HttpServletRequest getRequest()
    {
        return _viewContext.getRequest();
    }


    public void setRequest(HttpServletRequest request)
    {
        _viewContext.setRequest(request);
    }


    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public boolean isUseContainerFilter()
    {
        return _useContainerFilter;
    }

    public void setUseContainerFilter(boolean useContainerFilter)
    {
        _useContainerFilter = useContainerFilter;
    }

    public Set<FieldKey> getIgnoredFilterColumns()
    {
        if (_ignoredColumnFilters.isEmpty())
            return Collections.emptySet();

        return new LinkedHashSet<>(_ignoredColumnFilters);
    }


    /*
     * Moved getErrors() from TableViewForm
     * 4927 : DataRegion needs to support Spring errors collection
     */
    public String getErrors(String paramName)
    {
        Errors errors = getErrors();
        if (null == errors)
            return "";
        List list;
        if ("main".equals(paramName))
            list = errors.getGlobalErrors();
        else
            list = errors.getFieldErrors(paramName);
        if (list == null || list.size() == 0)
            return "";

        Set<String> uniqueErrorStrs = new CaseInsensitiveHashSet();
        StringBuilder sb = new StringBuilder();
        String br = "<font class=\"labkey-error\">";
        for (Object m : list)
        {
            String errStr = null;
            if (m instanceof LabKeyError)
            {
                errStr = ((LabKeyError) m).renderToHTML(getViewContext());
            }
            else
            {
                errStr = PageFlowUtil.filter(getViewContext().getMessage((MessageSourceResolvable) m), true);
            }

            if (!uniqueErrorStrs.contains(errStr))
            {
                sb.append(br);
                sb.append(errStr);
                br = "<br>";
            }
            uniqueErrorStrs.add(errStr);
        }
        if (sb.length() > 0)
            sb.append("</font>");
        return sb.toString();
    }

    public String getErrors(ColumnInfo column)
    {
        String errors = getErrors(getForm().getFormFieldName(column));
        if ("".equals(errors))
        {
            errors = getErrors(column.getName());
            if ("".equals(errors))
                errors = getErrors(column.getName().toLowerCase());  // error may be mapped from lowercase name because of provisioning
        }
        return errors;
    }

    // UNDONE: Use FieldKey instead
    public List<String> getRecordSelectorValueColumns()
    {
        return _recordSelectorValueColumns;
    }

    public Set<String> getAllSelected()
    {
        if (_selected == null)
        {
            return Collections.emptySet();
        }

        return _selected;
    }

    public void setAllSelected(Set<String> selected)
    {
        _selected = selected;
    }

    public void setView(CustomView view)
    {
        _view = view;
    }

    public CustomView getView()
    {
        return _view;
    }

    /**
     * Gets the value and does type conversion
     */
    public <Type> Type get(FieldKey fieldKey, Class<? extends Type> clazz)
    {
        Object value = get(fieldKey);
        if (value != null && !clazz.isInstance(value))
        {
            return (Type) ConvertUtils.convert(value.toString(), clazz);
        }
        return (Type) value;
    }
}
