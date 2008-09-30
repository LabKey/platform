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

package org.labkey.query;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;
import org.labkey.data.xml.ColumnType;
import org.labkey.query.design.*;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.view.CustomViewSetKey;

import javax.servlet.http.HttpServletRequest;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

public class CustomViewImpl implements CustomView
{
    private static final Logger _log = Logger.getLogger(CustomViewImpl.class);
    private static final String FILTER_PARAM_PREFIX = "filter";
    boolean _dirty;
    final QueryManager _mgr = QueryManager.get();
    final QueryDefinitionImpl _queryDef;
    CstmView _collist;

    public CustomViewImpl(QueryDefinitionImpl queryDef, CstmView view)
    {
        _queryDef = queryDef;
        _collist = view;
        _dirty = false;
    }

    public CustomViewImpl(QueryDefinitionImpl queryDef, User user, String name)
    {
        _queryDef = queryDef;
        _dirty = true;
        _collist = new CstmView();
        _collist.setContainer(queryDef.getContainer().getId());
        _collist.setSchema(queryDef.getSchemaName());
        _collist.setQueryName(queryDef.getName());
        if (user != null)
        {
            _collist.setCustomViewOwner(user.getUserId());
        }
        else
        {
            _collist.setCustomViewOwner(null);
        }
        _collist.setName(name);
    }

    public QueryDefinition getQueryDefinition()
    {
        return _queryDef;
    }

    public String getName()
    {
        return _collist.getName();
    }

    public User getOwner()
    {
        Integer userId = _collist.getCustomViewOwner();
        if (userId == null)
            return null;
        return UserManager.getUser(userId);
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_collist.getContainerId());
    }

    public List<FieldKey> getColumns()
    {
        List<FieldKey> ret = new ArrayList();
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : getColumnProperties())
        {
            ret.add(entry.getKey());
        }
        return ret;
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

    static List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> decodeProperties(String value)
    {
        if (value == null)
        {
            return Collections.EMPTY_LIST;
        }
        String[] values = StringUtils.split(value, "&");
        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> ret = new ArrayList();
        for (String entry : values)
        {
            int ichEquals = entry.indexOf("=");
            Map<ColumnProperty, String> properties;
            FieldKey field;
            if (ichEquals < 0)
            {
                field = FieldKey.fromString(PageFlowUtil.decode(entry));
                properties = Collections.EMPTY_MAP;
            }
            else
            {
                properties = new EnumMap(ColumnProperty.class);
                field = FieldKey.fromString(PageFlowUtil.decode(entry.substring(0, ichEquals)));
                for (Map.Entry<String, String> e : PageFlowUtil.fromQueryString(PageFlowUtil.decode(entry.substring(ichEquals + 1))))
                {
                    properties.put(ColumnProperty.valueOf(e.getKey()), e.getValue());
                }

            }
            ret.add(Pair.of(field, properties));
        }
        return Collections.unmodifiableList(ret);
    }

    public List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> getColumnProperties()
    {
        return decodeProperties(_collist.getColumns());
    }

    public Map<FieldKey, Map<ColumnProperty, String>> getColumnPropertiesMap()
    {
        Map<FieldKey, Map<ColumnProperty, String>> ret = new HashMap();
        for (Map.Entry<FieldKey, Map<ColumnProperty, String>> entry : getColumnProperties())
        {
            ret.put(entry.getKey(), entry.getValue());
        }
        return ret;
    }



    public void setColumnProperties(List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> map)
    {
        edit().setColumns(encodeProperties(map));
    }


    public void setColumns(List<FieldKey> columns)
    {
        List<Map.Entry<FieldKey, Map<String, String>>> properties = new ArrayList();
        for (FieldKey field : columns)
        {
            properties.add(Pair.of(field, Collections.<String, String>emptyMap()));
        }
        edit().setColumns(StringUtils.join(columns.iterator(), "&"));
    }

    public boolean hasColumnList()
    {
        return StringUtils.trimToNull(_collist.getColumns()) != null;
    }

    public boolean hasFilterOrSort()
    {
        return StringUtils.trimToNull(_collist.getFilter()) != null;
    }

    public void applyFilterAndSortToURL(ActionURL url, String dataRegionName)
    {
        if (!hasFilterOrSort())
            return;
        try
        {
            URLHelper src = new URLHelper(_collist.getFilter());
            for (String key : src.getKeysByPrefix(FILTER_PARAM_PREFIX + "."))
            {
                String newKey = dataRegionName + key.substring(FILTER_PARAM_PREFIX.length());
                for (String value : src.getParameters(key))
                {
                    url.addParameter(newKey, value);
                }
            }
        }
        catch (URISyntaxException use)
        {
            return;
        }

    }

    public void setFilterAndSortFromURL(ActionURL url, String dataRegionName)
    {
        try
        {
            URLHelper dest = new URLHelper("/?");
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
                    for (String value : url.getParameters(key))
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

    public String getFilter()
    {
        return _collist.getFilter();
    }

    public void setFilter(String filter)
    {
        edit().setFilter(filter);
    }

    public boolean saveInSession()
    {
        return getOwner() != null && getOwner().isGuest();
    }

    public void save(User user, HttpServletRequest request)
    {
        if (!_dirty)
            return;
        try
        {
            if (saveInSession())
            {
                _collist = CustomViewSetKey.saveCustomViewInSession(request, getQueryDefinition(), _collist);
            }
            else if (isNew())
            {
                _collist = _mgr.insert(user, _collist);
            }
            else
            {
                _collist = _mgr.update(user, _collist);
            }
            _dirty = false;
        }
        catch (SQLException e)
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

                _mgr.delete(user, _collist);
                _collist = null;
            }
        }
        catch (SQLException e)
        {
            throw new QueryException("Error", e);
        }
    }

    static public boolean isUnselectable(ColumnInfo column)
    {
        return column.isUnselectable();
    }

    public boolean isNew()
    {
        return _collist.getCustomViewId() == 0;
    }

    public boolean canInherit()
    {
        return _mgr.canInherit(_collist.getFlags());
    }

    public void setCanInherit(boolean f)
    {
        edit().setFlags(_mgr.setCanInherit(_collist.getFlags(), f));
    }

    public boolean isHidden()
    {
        return _mgr.isHidden(_collist.getFlags());
    }

    public void setIsHidden(boolean b)
    {
        edit().setFlags(_mgr.setIsHidden(_collist.getFlags(), b));
    }

    public ViewDocument getDesignDocument(QuerySchema schema)
    {
        ViewDocument ret = ViewDocument.Factory.newInstance();
        DgQuery view = ret.addNewView();
        DgQuery.Select select = view.addNewSelect();
        DgQuery.From from = view.addNewFrom();
        TableInfo tinfo = getQueryDefinition().getTable(null, schema, null);
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
            columns = new ArrayList();
            for (FieldKey key : tinfo.getDefaultVisibleColumns())
            {
                columns.add(Pair.of(key, Collections.<ColumnProperty, String>emptyMap()));
            }
        }
        Set<FieldKey> allKeys = new HashSet();
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

        String strFilter = _collist.getFilter();
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
                ColumnInfo column = new ColumnInfo(field.toString());
                column.setCaption(field.getDisplayString() + " (not found)");
                ret.put(field, column);
            }

        }
        return ret;
    }

    public void update(ViewDocument doc, boolean saveFilterAndSort)
    {
        DgQuery view = doc.getView();
        DgQuery.Select select = view.getSelect();

        List<Map.Entry<FieldKey, Map<ColumnProperty, String>>> fields = new ArrayList();
        for (DgColumn column : select.getColumnArray())
        {
            FieldKey key = FieldKey.fromString(column.getValue().getField().getStringValue());
            Map<ColumnProperty, String> map = Collections.emptyMap();
            if (column.getMetadata() != null)
            {
                ColumnType metadata = column.getMetadata();
                map = new EnumMap(ColumnProperty.class);
                if (metadata.getColumnTitle() != null)
                {
                    map.put(ColumnProperty.columnTitle, metadata.getColumnTitle());
                }
            }
            fields.add(Pair.of(key, map));
        }
        setColumnProperties(fields);
        if (!saveFilterAndSort)
            return;

        DgQuery.Where where = view.getWhere();
        DgQuery.OrderBy orderBy = view.getOrderBy();
        ActionURL url = new ActionURL();
        for (DgCompare compare : where.getCompareArray())
        {
            String op = compare.getOp();
            if (op == null)
                op = "";

            String value = compare.getLiteral();
            if (value == null)
            {
                value = "";
            }

            url.addParameter(FILTER_PARAM_PREFIX + "." + compare.getField() + "~" + op, value);
        }
        StringBuilder sort = new StringBuilder();
        String strComma = "";
        for (DgOrderByString obs : orderBy.getFieldArray())
        {
            sort.append(strComma);
            strComma = ",";
            if ("DESC".equals(obs.getDir()))
            {
                sort.append("-");
            }
            sort.append(obs.getStringValue());
        }
        if (sort.length() != 0)
        {
            url.addParameter(FILTER_PARAM_PREFIX + ".sort", sort.toString());
        }
        setFilterAndSortFromURL(url, FILTER_PARAM_PREFIX);
    }

    protected CstmView edit()
    {
        if (_dirty)
        {
            return _collist;
        }
        _collist = _collist.clone();
        _dirty = true;
        return _collist;
    }

    public CstmView getCstmView()
    {
        return _collist;
    }
}
