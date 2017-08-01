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

package org.labkey.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AnalyticsProviderItem;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.VirtualFile;
import org.labkey.data.xml.queryCustomView.AnalyticsProviderType;
import org.labkey.data.xml.queryCustomView.AnalyticsProvidersType;
import org.labkey.data.xml.queryCustomView.ColumnsType;
import org.labkey.data.xml.queryCustomView.ContainerFilterType;
import org.labkey.data.xml.queryCustomView.CustomViewDocument;
import org.labkey.data.xml.queryCustomView.CustomViewType;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.data.xml.queryCustomView.FiltersType;
import org.labkey.data.xml.queryCustomView.OperatorType;
import org.labkey.data.xml.queryCustomView.PropertiesType;
import org.labkey.data.xml.queryCustomView.PropertyType;
import org.labkey.data.xml.queryCustomView.SortType;
import org.labkey.data.xml.queryCustomView.SortsType;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.view.CustomViewSetKey;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A custom view that's backed by a row in the database OR saved in the HTTP session for the user.
 */
public class CustomViewImpl extends CustomViewInfoImpl implements CustomView, EditableCustomView
{
    private static final Logger _log = Logger.getLogger(CustomViewImpl.class);

    private final QueryDefinition _queryDef;
    private boolean _dirty;

    public CustomViewImpl(QueryDefinition queryDef, CstmView view)
    {
        super(view);
        _queryDef = queryDef;
        _dirty = false;
    }

    /**
     * Create new CustomView for the query.
     * @param queryDef The query.
     * @param user Owner of the custom view or null for shared.
     * @param name Name of the custom view.
     */
    public CustomViewImpl(QueryDefinition queryDef, @Nullable User user, @NotNull String name)
    {
        super(new CstmView());
        _queryDef = queryDef;
        _dirty = true;
        _cstmView.setContainer(queryDef.getContainer().getId());
        _cstmView.setSchema(queryDef.getSchemaName());
        _cstmView.setQueryName(queryDef.getName());
        if (user != null)
        {
            _cstmView.setCustomViewOwner(user.getUserId());
        }
        else
        {
            _cstmView.setCustomViewOwner(null);
        }
        _cstmView.setName(name);
    }

    public void setContainer(Container container)
    {
        edit().setContainer(container.getId());
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    public void setName(String name)
    {
        edit().setName(name);
    }

    @Override
    public void setQueryName(String queryName)
    {
        edit().setQueryName(queryName);
    }

    static String encodeProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> list)
    {
        StringBuilder ret = new StringBuilder();
        String strAnd = "";
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : list)
        {
            ret.append(strAnd);
            ret.append(PageFlowUtil.encode(entry.getKey().toString()));
            if (!entry.getValue().isEmpty())
            {
                ret.append("=");
                ret.append(PageFlowUtil.encode(PageFlowUtil.toQueryString(entry.getValue().entrySet())));
            }
            strAnd = "&";
        }
        return ret.toString();
    }

    public void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> map)
    {
        edit().setColumns(encodeProperties(map));
    }


    public void setColumns(List<FieldKey> columns)
    {
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> list = new ArrayList<>(columns.size());
        for (FieldKey column : columns)
            list.add(Pair.of(column, Collections.emptyMap()));

        edit().setColumns(encodeProperties(list));
    }

    public boolean hasColumnList()
    {
        return StringUtils.trimToNull(_cstmView.getColumns()) != null;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        if (!hasFilterOrSort())
            return;
        try
        {
            URLHelper src = new URLHelper(_cstmView.getFilter());
            for (String key : src.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
            {
                String newKey = dataRegionName + key.substring(FILTER_PARAM_PREFIX.length());
                for (String value : src.getParameterValues(key))
                {
                    url.addParameter(newKey, value);
                }
            }
        }
        catch (URISyntaxException use)
        {
            // do nothing
        }

    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        try
        {
            URLHelper dest = new URLHelper("");
            String[] keys = url.getKeysByPrefix(dataRegionName + ".");
            if (keys.length == 0)
            {
                edit().setFilter(null);
            }
            else
            {
                for (String key : keys)
                {
                    String newKey = FILTER_PARAM_PREFIX + key.substring(dataRegionName.length());
                    for (String value : url.getParameterValues(key))
                    {
                        dest.addParameter(newKey, value);
                    }
                }
                edit().setFilter(dest.toString());
            }
        }
        catch (URISyntaxException use)
        {
            throw UnexpectedException.wrap(use);
        }
    }

    public void setFilterAndSort(String filter)
    {
        edit().setFilter(filter);
    }

    public boolean saveInSession()
    {
        return getOwner() != null && (getOwner().isGuest() || isSession());
    }

    public void save(User user, HttpServletRequest request)
    {
        if (!_dirty)
            return;
        try
        {
            if (saveInSession())
            {
                _cstmView = CustomViewSetKey.saveCustomViewInSession(request, getQueryDefinition(), _cstmView);
            }
            else if (isNew())
            {
                _cstmView = _mgr.insert(user, _cstmView);
            }
            else
            {
                _cstmView = _mgr.update(user, _cstmView);
            }
            _mgr.fireViewChanged(this);
            _dirty = false;
        }
        catch (RuntimeSQLException e)
        {
            throw new QueryException("Error", e);
        }
    }

    public void delete(User user, HttpServletRequest request) throws QueryException
    {
        try
        {
            if (saveInSession())
            {
                CustomViewSetKey.deleteCustomViewFromSession(request, getQueryDefinition(), getName());
            }
            else
            {
                if (isNew())
                    return;

                _mgr.delete(_cstmView);
                _mgr.fireViewDeleted(this);
                _cstmView = null;
            }
        }
        catch (RuntimeSQLException e)
        {
            throw new QueryException("Error", e);
        }
    }

    public boolean serialize(VirtualFile dir) throws IOException
    {
        // Don't serialize session views
        if (isSession())
            return false;

        CustomViewDocument customViewDoc = CustomViewDocument.Factory.newInstance();
        CustomViewType customViewXml = customViewDoc.addNewCustomView();

        customViewXml.setName(getName());
        customViewXml.setSchema(getQueryDefinition().getSchemaName());
        customViewXml.setQuery(getQueryDefinition().getName());

        if (isHidden())
            customViewXml.setHidden(isHidden());
        if(canInherit())
            customViewXml.setCanInherit(canInherit());

        // At the moment, CustomViewImpl always returns null.  If that changes, this will export it, though  it needs
        //  to be enabled on the import side.
        if (null != getCustomIconUrl())
            customViewXml.setCustomIconUrl(getCustomIconUrl());

        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> columns = getColumnProperties();

        if (!columns.isEmpty())
        {
            ColumnsType columnsXml = customViewXml.addNewColumns();

            for (Map.Entry<FieldKey, Map<ColumnProperty, String>> column : columns)
            {
                org.labkey.data.xml.queryCustomView.ColumnType columnXml = columnsXml.addNewColumn();

                columnXml.setName(column.getKey().toString());

                Map<ColumnProperty, String> props = column.getValue();

                if (!props.isEmpty())
                {
                    PropertiesType propsXml = columnXml.addNewProperties();

                    for (Map.Entry<ColumnProperty, String> propEntry : props.entrySet())
                    {
                        PropertyType propXml = propsXml.addNewProperty();
                        propXml.setName(propEntry.getKey().getXmlPropertyEnum());
                        propXml.setValue(propEntry.getValue());
                    }
                }
            }
        }

        try
        {
            FilterAndSort fas = FilterAndSort.fromString(_cstmView.getFilter());

            if (!fas.getFilter().isEmpty())
            {
                FiltersType filtersXml = customViewXml.addNewFilters();

                for (FilterInfo filter : fas.getFilter())
                {
                    FilterType filterXml = filtersXml.addNewFilter();

                    filterXml.setColumn(filter.getField().toString());

                    if (null != filter.getOp())
                    {
                        OperatorType.Enum opType = OperatorType.Enum.forString(filter.getOp().getPreferredUrlKey());

                        if (null != opType)
                        {
                            filterXml.setOperator(opType);
                            filterXml.setValue(filter.getValue());                            
                        }
                    }
                }
            }

            if (!fas.getSort().isEmpty())
            {
                SortsType sortsXml = customViewXml.addNewSorts();

                for (Sort.SortField sort : fas.getSort())
                {
                    SortType sortXml = sortsXml.addNewSort();

                    sortXml.setColumn(sort.getFieldKey().toString());
                    sortXml.setDescending(sort.getSortDirection() == Sort.SortDirection.DESC);
                }
            }

            if (!fas.getAnalyticsProviders().isEmpty())
            {
                AnalyticsProvidersType analyticsXml = customViewXml.addNewAnalyticsProviders();

                for (AnalyticsProviderItem analyticsItem : fas.getAnalyticsProviders())
                {
                    AnalyticsProviderType analyticXml = analyticsXml.addNewAnalyticsProvider();
                    analyticXml.setColumn(analyticsItem.getFieldKey().toString());
                    analyticXml.setType(analyticsItem.getName());
                }
            }

            if (fas.getContainerFilterNames() != null && fas.getContainerFilterNames().size() > 0)
            {
                String containerFilter = fas.getContainerFilterNames().get(0);
                ContainerFilterType.Enum containerFilterType = ContainerFilterType.Enum.forString(containerFilter);
                if (containerFilterType != null)
                    customViewXml.setContainerFilter(containerFilterType);
            }
        }
        catch (URISyntaxException e)
        {
            _log.error("Bad filter/sort URL in custom view: " + _cstmView.getFilter());
        }

        String filename = (null != getName() ? getName() : "default") + ".db_" + getCstmView().getCustomViewId() + CustomViewXmlReader.XML_FILE_EXTENSION;

        dir.saveXmlBean(filename, customViewDoc);
        return true;
    }

    public boolean isNew()
    {
        return _cstmView.getCustomViewId() == 0;
    }

    @Override
    public void setCanInherit(boolean f)
    {
        edit().setFlags(_mgr.setCanInherit(_cstmView.getFlags(), f));
    }

    @Override
    public boolean canEdit(Container c, org.springframework.validation.Errors errors)
    {
        if (!isEditable())
        {
            if (errors != null)
                errors.reject(null, "The view '" + (getName() == null ? "<default>" : getName()) + "' is read-only and cannot be edited.");
            return false;
        }
        return true;
    }

    public void setIsHidden(boolean b)
    {
        edit().setFlags(_mgr.setIsHidden(_cstmView.getFlags(), b));
    }

    public void update(org.json.JSONObject jsonView, boolean saveFilterAndSort)
    {
        CustomViewUtil.update(this, jsonView, saveFilterAndSort);
    }

/*
    public ViewDocument getDesignDocument(UserSchema schema)
    {
        ViewDocument ret = ViewDocument.Factory.newInstance();
        DgQuery view = ret.addNewView();
        DgQuery.Select select = view.addNewSelect();
        DgQuery.From from = view.addNewFrom();
        TableInfo tinfo = getQueryDefinition().getTable(schema, null, true);
        if (tinfo == null)
        {
            return null;
        }
        DgTable dgTable = from.addNewTable();
        TableXML.initTable(dgTable.addNewMetadata(), tinfo, null);

        DgQuery.Where where = view.addNewWhere();
        DgQuery.OrderBy orderBy = view.addNewOrderBy();
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> columns = getColumnProperties();
        if (columns.size() == 0)
        {
            columns = new ArrayList<Map.Entry<FieldKey, Map<ColumnProperty, String>>>();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.<ColumnProperty, String>emptyMap()));
            }
        }
        Set<FieldKey> allKeys = new HashSet<FieldKey>();
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : columns)
        {
            DgColumn column = select.addNewColumn();
            DgValue value = column.addNewValue();
            allKeys.add(entry.getKey());
            value.addNewField().setStringValue(entry.getKey().toString());
            if (!entry.getValue().isEmpty())
            {
                ColumnType metadata = column.addNewMetadata();
                String columnTitle = entry.getValue().get(ColumnProperty.columnTitle);
                if (columnTitle != null)
                {
                    metadata.setColumnTitle(columnTitle);
                }
            }
        }

        String strFilter = _cstmView.getFilter();
        if (strFilter != null)
        {
            try
            {
                URLHelper filterSort = new URLHelper(strFilter);
                for (String key : filterSort.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
                {
                    String param = key.substring(FILTER_PARAM_PREFIX.length() + 1);
                    String[] parts = StringUtils.splitPreserveAllTokens(param, '~');
                    if (parts.length != 2)
                        continue;
                    for (String value : filterSort.getParameters(key))
                    {
                        DgCompare compare = where.addNewCompare();
                        allKeys.add(FieldKey.fromString(parts[0]));
                        compare.setField(parts[0]);
                        compare.setOp(parts[1]);
                        compare.setLiteral(value);
                    }
                }
                Sort sort = new Sort(filterSort, FILTER_PARAM_PREFIX);
                for (Sort.SortField sf : sort.getSortList())
                {
                    DgOrderByString ob = orderBy.addNewField();
                    ob.setDir(sf.getSortDirection().toString());
                    ob.setStringValue(sf.getColumnName());
                    allKeys.add(FieldKey.fromString(sf.getColumnName()));
                }
                String[] containerFilterNames = filterSort.getParameters(FILTER_PARAM_PREFIX + "." + CONTAINER_FILTER_NAME);
                if (containerFilterNames.length > 0)
                    view.setContainerFilterName(containerFilterNames[containerFilterNames.length - 1]);
            }
            catch (URISyntaxException use)
            {
                _log.error("Error", use);
            }
        }
        DgTable tableOutput = from.addNewTable();
        tableOutput.setAlias("output");
        TableXML.initTable(tableOutput.addNewMetadata(), tinfo, null, getColumnInfos(tinfo, allKeys).values());
        return ret;
    }
*/

    static public Map<FieldKey, ColumnInfo> getColumnInfos(TableInfo table, Collection<FieldKey> fields)
    {
        Map<FieldKey, ColumnInfo> ret = QueryService.get().getColumns(table, fields);

        if (ret.size() != fields.size())
        {
            for (FieldKey field : fields)
            {
                if (ret.containsKey(field))
                {
                    continue;
                }
                ColumnInfo column = new ColumnInfo(field);
                column.setLabel(field.toDisplayString() + " (not found)");
                ret.put(field, column);
            }
        }

        return ret;
    }

    protected CstmView edit()
    {
        if (_dirty)
        {
            return _cstmView;
        }
        _cstmView = _cstmView.clone();
        _dirty = true;
        return _cstmView;
    }

    @Override
    public Collection<String> getDependents(User user)
    {
        return QueryManager.get().getQueryDependents(user, getContainer(), null, getSchemaPath(), Collections.singleton(getName()));
    }

    @Override
    public CustomViewImpl getEditableViewInfo(User owner, boolean session)
    {
        return this;
    }
}
