/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.DatasetTable;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * User: bimber
 * Date: 1/8/14
 * Time: 11:06 AM
 */
abstract public class AbstractTableCustomizer implements TableCustomizer
{
    protected static final Logger _log = LogManager.getLogger(AbstractTableCustomizer.class);

    /**
     * Rely on DefaultSchema's caching of schema creation, and just track the minimum number of DefaultSchemas to
     * resolve the requested collection of target containers
     */
    private Map<Container, DefaultSchema> _defaultSchemas = new HashMap<>();

    public UserSchema getUserSchema(AbstractTableInfo ti, String name)
    {
        Container targetContainer = ti.getUserSchema().getContainer();
        return getUserSchema(ti, name, targetContainer);
    }

    public UserSchema getUserSchema(AbstractTableInfo ti, String name, Container targetContainer)
    {
        assert targetContainer != null : "No container provided";

        // Stash the DefaultSchema for the current table if we don't already have it
        _defaultSchemas.computeIfAbsent(ti.getUserSchema().getContainer(), (key) -> ti.getUserSchema().getDefaultSchema());
        DefaultSchema targetedDefaultSchema = _defaultSchemas.computeIfAbsent(targetContainer, (key) -> DefaultSchema.get(ti.getUserSchema().getUser(), targetContainer));

        return targetedDefaultSchema.getUserSchema(name);
    }

    public TableInfo getTableInfo(AbstractTableInfo ti, String schemaName, String queryName)
    {
        return getTableInfo(ti, schemaName, queryName, ti.getUserSchema().getContainer());
    }

    public TableInfo getTableInfo(AbstractTableInfo ti, String schemaName, String queryName, Container targetContainer)
    {
        assert targetContainer != null : "No container provided";
        UserSchema us = getUserSchema(ti, schemaName, targetContainer);
        if (us == null)
            return null;

        return us.getTable(queryName);
    }

    protected String getChr(TableInfo ti)
    {
        return ti.getSqlDialect().isPostgreSQL() ? "chr" : "char";
    }

    protected boolean matches(TableInfo ti, String schema, String query)
    {
        if (ti instanceof DatasetTable)
            return ti.getSchema().getName().equalsIgnoreCase(schema) && (ti.getName().equalsIgnoreCase(query) || ti.getTitle().equalsIgnoreCase(query));
        else
            return ti.getSchema().getName().equalsIgnoreCase(schema) && ti.getName().equalsIgnoreCase(query);
    }
}
