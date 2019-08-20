/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.queryprofiler.Query;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This helper can centralize caching of TableInfos and ColumnInfos.  If code is expected to repeatedly request
 * these, it could provide a perf win.
 */
public class QueryCache
{
    private static final Logger _log = Logger.getLogger(QueryCache.class);

    private Map<String, AssayProtocolSchema> _cachedAssaySchemas = new HashMap<>();
    private Map<String, UserSchema> _cachedUserSchemas = new HashMap<>();
    private Map<String, TableInfo> _cachedQueries = new HashMap<>();
    private Map<String, ColumnInfo> _cachedColumns = new HashMap<>();

    public QueryCache()
    {

    }

    public UserSchema getUserSchema(Container targetContainer, User u, String schemaName)
    {
        String key = getUserSchemaKey(targetContainer, u, schemaName);
        if (_cachedUserSchemas.containsKey(key))
        {
            return _cachedUserSchemas.get(key);
        }

        UserSchema ret = QueryService.get().getUserSchema(u, targetContainer, schemaName);
        _cachedUserSchemas.put(key, ret);

        return ret;
    }

    public AssayProtocolSchema getAssaySchema(Container targetContainer, User u, String providerName, ExpProtocol protocol)
    {
        String key = getAssaySchemaKey(targetContainer, u, providerName, protocol.getName());
        if (_cachedAssaySchemas.containsKey(key))
        {
            return _cachedAssaySchemas.get(key);
        }

        AssayProvider ap = AssayService.get().getProvider(providerName);
        AssayProtocolSchema schema = ap.createProtocolSchema(u, targetContainer, protocol, null);
        _cachedAssaySchemas.put(key, schema);

        return schema;
    }

    public TableInfo getTableInfo(Container targetContainer, User u, String schemaPath, String queryName)
    {
        String key = getQueryKey(targetContainer, u, schemaPath, queryName);
        if (_cachedQueries.containsKey(key))
        {
            return _cachedQueries.get(key);
        }
        else
        {
            UserSchema us = getUserSchema(targetContainer, u, schemaPath);
            if (us == null)
                return null;

            QueryDefinition qd = us.getQueryDefForTable(queryName);
            if (qd == null)
                return null;

            List<QueryException> errors = new ArrayList<>();
            TableInfo ti = qd.getTable(errors, true);
            if (errors.size() > 0)
            {
                _log.error("Unable to create tabbed report item for query: " + schemaPath + "." + queryName);
                for (QueryException e : errors)
                {
                    _log.error(e.getMessage(), e);
                }
                return null;
            }

            if (ti == null)
            {
                _log.info("TableInfo was null: " + schemaPath + "/" + queryName + " in container: " + targetContainer.getPath(), new Exception());
                return null;
            }

            cache(ti);

            return ti;
        }
    }

    public void cache(UserSchema us)
    {
        _cachedUserSchemas.put(getUserSchemaKey(us.getContainer(), us.getUser(), us.getName()), us);
    }

    public void cache(TableInfo ti)
    {
        if (ti == null)
        {
            _log.warn("attempting to cache a null TableInfo", new Exception());
        }

        if (ti.getUserSchema() == null)
        {
            _log.warn("TableInfo lacks a user schema: " + ti.getPublicSchemaName() + "." + ti.getName(), new Exception());
            return;
        }

        cache(ti.getUserSchema());

        _cachedQueries.put(getQueryKey(ti.getUserSchema().getContainer(), ti.getUserSchema().getUser(), ti.getUserSchema().getSchemaPath().toString(), ti.getName()), ti);
    }

    private String getAssaySchemaKey(Container c, User u, String providerName, String protocolName)
    {
        return c.getId() + "||" + u.getUserId() + "||" + providerName + "||" + protocolName;
    }

    private String getUserSchemaKey(Container c, User u, String schemaPath)
    {
        return c.getId() + "||" + u.getUserId() + "||" + schemaPath;
    }

    private String getQueryKey(Container targetContainer, User u, String schemaPath, String queryName)
    {
        return targetContainer.getId() + "||" + u.getUserId() + "||" + schemaPath + "||" + queryName;
    }

    private String getColumnKey(TableInfo ti, FieldKey fk)
    {
        return ti.getUserSchema().getContainer().getId() + "||" + ti.getUserSchema().getUser().getUserId() + "||" + ti.getUserSchema().getName() + "||" + ti.getName() + "||" + fk.toString();
    }

    public Map<FieldKey, ColumnInfo> getColumns(TableInfo ti, Set<FieldKey> keys)
    {
        Set<FieldKey> toQuery = new HashSet<>();
        Map<FieldKey, ColumnInfo> ret = new HashMap<>();
        for (FieldKey fk : keys)
        {
            String colKey = getColumnKey(ti, fk);
            if (_cachedColumns.containsKey(colKey))
            {
                if (_cachedColumns.get(colKey) != null)
                {
                    ret.put(fk, _cachedColumns.get(colKey));
                }
            }
            else
            {
                toQuery.add(fk);
            }
        }

        if (!toQuery.isEmpty())
        {
            Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(ti, toQuery);
            for (FieldKey fk : toQuery)
            {
                _cachedColumns.put(getColumnKey(ti, fk), colMap.get(fk));
                if (colMap.get(fk) != null)
                {
                    ret.put(fk, colMap.get(fk));
                }
            }
        }

        return ret;
    }
}
