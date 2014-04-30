/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 12:27 PM
 */
public class QueryImportNavItem extends AbstractImportingNavItem
{
    private String _schema;
    private String _query;
    private boolean _visible = true;

    public QueryImportNavItem(DataProvider provider, String schema, String query, String category)
    {
        this(provider, schema, query, category, query);
    }

    public QueryImportNavItem(DataProvider provider, String schema, String query, String category, String label)
    {
        this(provider, null, schema, query, category, label);
    }

    public QueryImportNavItem(DataProvider provider, Container targetContainer, String schema, String query, String category, String label)
    {
        super(provider, query, label, category);
        _schema = schema;
        _query = query;
        _targetContainer = targetContainer;
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
        UserSchema us = QueryService.get().getUserSchema(u, getTargetContainer(c), _schema);
        if (us == null)
            return null;

        return us.getQueryDefForTable(getQuery());
    }

    protected TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, getTargetContainer(c), _schema);
        if (us == null)
        {
            _log.error("Unable to find schema: " + _schema + " in container: " + getTargetContainer(c).getPath());
            return null;
        }

        return us.getTable(_query);
    }

    @Override
    public boolean getDefaultVisibility(Container c, User u)
    {
        return _visible;
    }

    @Override
    public ActionURL getImportUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getImportUrl(getTargetContainer(c), u, getSchema(), getQuery());
    }

    @Override
    public ActionURL getSearchUrl(Container c, User u)
    {
        return PageFlowUtil.urlProvider(LaboratoryUrls.class).getSearchUrl(getTargetContainer(c), _schema, _query);
    }

    @Override
    public ActionURL getBrowseUrl(Container c, User u)
    {
        try
        {
            ActionURL url = QueryService.get().urlFor(u, getTargetContainer(c), QueryAction.executeQuery, _schema, _query);
            return appendDefaultView(c, url, "query");
        }
        catch (QueryParseException e)
        {
            _log.error(e.getMessage(), e);

            return null;
        }
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
        return QueryService.get().getQueryDef(u, getTargetContainer(c), getSchema(), getQuery()) != null;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", _schema);
        json.put("queryName", _query);

        return json;
    }
}
