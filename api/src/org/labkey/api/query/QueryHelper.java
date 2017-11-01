/*
 * Copyright (c) 2010-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 24, 2010
 * Time: 5:51:46 PM
 */
public class QueryHelper
{
    private final Container  _c;
    private final User _user;
    private final String _schemaName;
    private final String _queryName;
    private final @Nullable String _viewName;

    protected QueryHelper(Container c, User user, String[] parts)
    {
        _c = c;
        _user = user;
        _schemaName = parts[0];
        _queryName = parts[1];
        _viewName = parts.length > 2 ? parts[2] : null;
    }

    public QueryHelper(Container c, User user, String schemaName, String queryName, @Nullable String viewName)
    {
        this(c, user, new String[]{schemaName, queryName, viewName});
    }

    public QueryHelper(Container c, User user, String schemaName, String queryName)
    {
        this(c, user, new String[]{schemaName, queryName, null});
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    @Nullable
    public String getViewName()
    {
        return _viewName;
    }

    @Nullable
    public TableInfo getTableInfo()
    {
        UserSchema schema = getUserSchema();
        return schema == null ? null : schema.getTable(_queryName);
    }

    public UserSchema getUserSchema()
    {
        return QueryService.get().getUserSchema(_user, _c, _schemaName);
    }

    public SimpleFilter getViewFilter()
    {
        if (_viewName != null)
        {
            CustomView baseView = getCustomView();

            if (baseView != null)
            {
                return getViewFilter(baseView);
            }
            else
            {
                throw new IllegalStateException("Could not find view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");
            }
        }

        return new SimpleFilter();
    }

    public Sort getViewSort()
    {
        Sort sort = new Sort();
        if (_viewName == null)
            return sort;

        CustomView view = getCustomView();
        if (view == null)
            throw new IllegalStateException("Could not find view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");

        if (view.getFilterAndSort() == null)
            return sort;

        try
        {
            CustomViewInfo.FilterAndSort fs = CustomViewInfo.FilterAndSort.fromString(getCustomView().getFilterAndSort());
            List<Sort.SortField> sorts = fs.getSort();
            if (sorts != null){
                for (Sort.SortField s : sorts)
                {
                    sort.insertSortColumn(s);
                }
            }
            return sort;
        }
        catch (URISyntaxException e)
        {
            throw new IllegalStateException("Unable to parse sort for custom view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");
        }
    }

    protected CustomView getCustomView()
    {
        return QueryService.get().getCustomView(_user, _c, _user, _schemaName, _queryName, _viewName);
    }

    private SimpleFilter getViewFilter(CustomView baseView)
    {
        SimpleFilter viewFilter = new SimpleFilter();

        // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
        ActionURL url = new ActionURL();
        baseView.applyFilterAndSortToURL(url, "mockDataRegion");
        viewFilter.addUrlFilters(url, "mockDataRegion");

        return viewFilter;
    }

    private ContainerFilter getViewContainerFilter()
    {
        if (_viewName == null)
            return null;

        CustomView view = getCustomView();
        if (view == null)
            throw new IllegalStateException("Could not find view " + _viewName + " on query " + _queryName + " in schema " + _schemaName + ".");

        return ContainerFilter.getContainerFilterByName(view.getContainerFilterName(), _user);
    }

    public Results select(List<FieldKey> columns, @Nullable SimpleFilter filter)
    {
        return select(columns, filter, null);
    }

    public Results select(List<FieldKey> columns, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        QueryService qs = QueryService.get();
        TableInfo ti = getTableInfo();

        Map<FieldKey, ColumnInfo> map = qs.getColumns(ti, columns);
        Set<FieldKey> fieldKeys = new LinkedHashSet<>();

        for (ColumnInfo col : map.values())
        {
            col.getRenderer().addQueryFieldKeys(fieldKeys);
        }

        map = qs.getColumns(ti, fieldKeys);
        Set<ColumnInfo> cols = new LinkedHashSet<>(map.values());     // Use Set to remove dups causes by aliases

        //add filter.  view filter + extra filters are merged
        if(filter == null)
            filter = new SimpleFilter();
        SimpleFilter viewFilter = getViewFilter();
        if (viewFilter != null)
            filter.addAllClauses(viewFilter);

        //only apply view sort if other sort is null
        if (sort == null)
        {
            sort = getViewSort();
        }

        // TODO: add container filter
        //getViewContainerFilter();

        return new ResultsImpl(qs.select(ti, cols, filter, sort), map);
    }

    public Results select(SimpleFilter filter)
    {
        CustomView view = getCustomView();

        return select(view == null ? getTableInfo().getDefaultVisibleColumns() : view.getColumns(), filter);
    }

    public Results select()
    {
        CustomView view = getCustomView();

        return select(view == null ? getTableInfo().getDefaultVisibleColumns() : view.getColumns(), null);
    }

    public ActionURL getQueryGridURL()
    {
        return QueryService.get().urlFor(_user, _c, QueryAction.executeQuery, _schemaName, _queryName);        
    }

    @Override
    public String toString()
    {
        return _schemaName + '.' + _queryName + '.' + (null == _viewName ? "" : _viewName);
    }

    protected Container getContainer()
    {
        return _c;
    }

    protected User getUser()
    {
        return _user;
    }
}
