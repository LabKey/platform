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

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class SimpleQueryNavItem extends AbstractQueryNavItem
{
    private String _schema;
    private String _query;
    private String _label;
    private String _category;
    private DataProvider _dataProvider;
    private boolean _visible = true;

    public SimpleQueryNavItem(DataProvider provider, String schema, String query, String category)
    {
        this(provider, schema, query, category, query);
    }

    public SimpleQueryNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        _schema = schema;
        _query = query;
        _category = category;
        _dataProvider = provider;
        _label = label;
    }

    public String getName()
    {
        return _query;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getCategory()
    {
        return _category;
    }

    @Override
    public boolean isImportIntoWorkbooks(Container c, User u)
    {
        TableInfo ti = getTableInfo(c, u);
        if (ti == null)
            return false;

        return ti.supportsContainerFilter();
    }

    @Override
    public boolean isVisible(Container c, User u)
    {
        if (getTableInfo(c, u) == null)
            return false;

        return super.isVisible(c, u);
    }

    protected QueryDefinition getQueryDef(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, getSchema());
        if (us == null)
            return null;

        return us.getQueryDefForTable(getQuery());
    }

    public TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schema);
        if (us == null)
            return null;

        return us.getTable(_query);
//        QueryDefinition qd = getQueryDef(c, u);
//        if (qd == null)
//            return null;
//
//        if (!qd.isTableQueryDefinition())
//            return null;
//
//        return qd.getTable(new ArrayList<QueryException>(), false);
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return _visible;
    }

    public ActionURL getImportUrl(Container c, User u)
    {
        try
        {
            TableInfo ti = getTableInfo(c, u);
            if (ti == null)
                return null;

            return ti.getImportDataURL(c);

        }
        catch (QueryParseException e)
        {
            return null;
        }
    }

    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(c, _schema, _query);
    }

    public ActionURL getBrowseUrl(Container c, User u)
    {
        try
        {
            ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, _schema, _query);
            return appendDefaultView(c, url, "query");
        }
        catch (QueryParseException e)
        {
            return null;
        }
    }

    public DataProvider getDataProvider()
    {
        return _dataProvider;
    }

    public String getSchema()
    {
        return _schema;
    }

    public String getQuery()
    {
        return _query;
    }

    public void setVisible(boolean visible)
    {
        _visible = visible;
    }

    protected boolean isAvailable(Container c, User u)
    {
        return QueryService.get().getQueryDef(u, c, getSchema(), getQuery()) != null;
    }
}
