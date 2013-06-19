/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.NavTree;

import java.util.*;

abstract public class AbstractSchema implements QuerySchema
{
    protected DbSchema _dbSchema;
    protected User _user;
    protected Container _container;
    protected boolean _hidden = false;

    public AbstractSchema(DbSchema dbSchema, User user, Container container)
    {
        _dbSchema = dbSchema;
        _user = user;
        _container = container;
        assert MemTracker.put(this);
    }

    public DbSchema getDbSchema()
    {
        return _dbSchema;
    }

    public @Nullable QuerySchema getSchema(String name)
    {
        return null;
    }

    /** Returns a UserSchema with a name (ie., ignores FolderSchema.) */
    public final @Nullable UserSchema getUserSchema(String name)
    {
        QuerySchema schema = getSchema(name);
        if ((schema instanceof UserSchema) && schema.getName() != null)
            return (UserSchema)schema;

        return null;
    }

    public Set<String> getSchemaNames()
    {
        return Collections.emptySet();
    }

    public final Collection<QuerySchema> getSchemas()
    {
        Set<String> schemaNames = getSchemaNames();
        if (schemaNames.isEmpty())
            return Collections.emptyList();

        List<QuerySchema> schemas = new ArrayList<>(schemaNames.size());
        for (String schemaName : schemaNames)
        {
            QuerySchema schema = getSchema(schemaName);
            if (schema != null && !schema.isHidden())
                schemas.add(schema);
        }
        return Collections.unmodifiableList(schemas);
    }

    public final Collection<UserSchema> getUserSchemas()
    {
        Set<String> schemaNames = getSchemaNames();
        if (schemaNames.isEmpty())
            return Collections.emptyList();

        List<UserSchema> schemas = new ArrayList<>(schemaNames.size());
        for (String schemaName : schemaNames)
        {
            UserSchema schema = getUserSchema(schemaName);
            if (schema != null)
                schemas.add(schema);
        }
        return Collections.unmodifiableList(schemas);
    }

    public final Collection<TableInfo> getTables()
    {
        Set<String> tableNames = getTableNames();
        if (tableNames.isEmpty())
            return Collections.emptyList();

        List<TableInfo> tables = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames)
        {
            TableInfo table = getTable(tableName);
            if (table != null)
                tables.add(table);
        }
        return Collections.unmodifiableList(tables);
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _container;
    }

    protected void afterConstruct(TableInfo info)
    {
        if (info instanceof AbstractTableInfo)
        {
            AbstractTableInfo t = ((AbstractTableInfo)info);
            t.afterConstruct();
        }
    }

    public NavTree getSchemaBrowserLinks(User user)
    {
        return new NavTree();
    }

    @Override
    public boolean isHidden()
    {
        return _hidden;
    }
}
