/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 4/14/13
 * Time: 9:33 AM
 */
public class QueryTabbedReportItem extends TabbedReportItem
{
    private Map<String, UserSchema> _cachedUserSchemas = new HashMap<>();
    private Map<String, TableInfo> _cachedQueries = new HashMap<>();

    private String _schemaName;
    private String _queryName;

    public QueryTabbedReportItem(DataProvider provider, String schemaName, String queryName, String label, String reportCategory)
    {
        super(provider, queryName, label, reportCategory);
        _schemaName = schemaName;
        _queryName = queryName;
    }

    public QueryTabbedReportItem(DataProvider provider, String label, String reportCategory, TableInfo ti)
    {
        this(provider, ti.getUserSchema().getSchemaName(), ti.getName(), label, reportCategory);
        if (ti.getUserSchema() != null)
        {
            _cachedUserSchemas.put(getUserSchemaKey(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), ti.getUserSchema().getName()), ti.getUserSchema());
        }

        _cachedQueries.put(getQueryKey(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser()), ti);
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    private String getUserSchemaKey(Container c, User u, String name)
    {
        return c.getId() + "||" + u.getUserId() + "||" + name;
    }

    private UserSchema getUserSchema(Container targetContainer, User u)
    {
        String key = getUserSchemaKey(targetContainer, u, getSchemaName());
        if (_cachedUserSchemas.containsKey(key))
        {
            return _cachedUserSchemas.get(key);
        }

        UserSchema ret = QueryService.get().getUserSchema(u, targetContainer, getSchemaName());
        _cachedUserSchemas.put(key, ret);

        return ret;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    private String getQueryKey(Container targetContainer, User u)
    {
        return targetContainer.getId() + "||" + u.getUserId() + "||" + getSchemaName() + "||" + getQueryName();
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        Container targetContainer = getTargetContainer(c) == null ? c : getTargetContainer(c);
        String key = getQueryKey(targetContainer, u);
        TableInfo ti;
        if (_cachedQueries.containsKey(key))
        {
            ti = _cachedQueries.get(key);
        }
        else
        {
            UserSchema us = getUserSchema(targetContainer, u);
            if (us == null)
                return null;

            QueryDefinition qd = us.getQueryDefForTable(getQueryName());
            if (qd == null)
                return null;

            List<QueryException> errors = new ArrayList<>();
            ti = qd.getTable(errors, true);
            if (errors.size() > 0)
            {
                _log.error("Unable to create tabbed report item for query: " + getSchemaName() + "." + getQueryName());
                for (QueryException e : errors)
                {
                    _log.error(e.getMessage(), e);
                }
                return null;
            }

            _cachedQueries.put(key, ti);
        }

        if (ti == null)
        {
            return null;
        }

        inferColumnsFromTable(ti);
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        String viewName = getDefaultViewName(c, getOwnerKey());
        if (viewName != null)
        {
            json.put("viewName", viewName);
        }

        return json;
    }
}
