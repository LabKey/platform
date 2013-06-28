/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class QueryCountNavItem extends SingleNavItem
{
    private static final Logger _log = Logger.getLogger(QueryCountNavItem.class);

    private String _schema;
    private String _query;
    private SimpleFilter _filter = null;

    public QueryCountNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        super(provider, label, null, (DetailsURL)null, category);
        _schema = schema;
        _query = query;
    }

    public String getName()
    {
        return _query;
    }

    public String getRendererName()
    {
        return "singleItemRenderer";
    }

    private TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schema);
        return us.getTable(_query);
    }

    private Long getRowCount(Container c, User u)
    {
        TableInfo ti = getTableInfo(c, u);
        if (ti == null)
            return new Long(0);

        SimpleFilter filter = new SimpleFilter();

        if (ti.getColumn("container") != null)
            filter.addClause(ContainerFilter.CURRENT.createFilterClause(ti.getSchema(), FieldKey.fromString("container"), c));

        if (_filter != null)
        {
            for (SimpleFilter.FilterClause clause : _filter.getClauses())
                filter.addCondition(clause);
        }

        TableSelector ts = new TableSelector(ti, ti.getPkColumns(), filter, null);
        return ts.getRowCount();
    }

    protected ActionURL getActionURL(Container c, User u)
    {
        ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
        if (_filter != null)
            _filter.applyToURL(url, "query");

        return url;
    }

    public JSONObject toJSON(Container c, User u)
    {
        try
        {
            Long total = getRowCount(c, u);
            _itemText = total.toString();
        }
        catch (Exception e)
        {
            _log.error("Error calculating rowcount for table " + _schema + "." + _query, e);
            _itemText = "0";
        }

        return super.toJSON(c, u);
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }
}
