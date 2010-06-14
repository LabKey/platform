/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.Filter;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Aug 24, 2009
 */
public class SimpleModuleUserSchema extends UserSchema
{
    private Map<String, SchemaTableInfo> _tables;

    public SimpleModuleUserSchema(String name, String description, User user, Container container, DbSchema dbschema)
    {
        this(name, description, user, container, dbschema, null);
    }

    public SimpleModuleUserSchema(String name, String description, User user, Container container, DbSchema dbschema, @Nullable Filter<TableInfo> filter)
    {
        super(name, description, user, container, dbschema);
        _tables = new CaseInsensitiveHashMap<SchemaTableInfo>();
        if (_dbSchema != null)
        {
            for (SchemaTableInfo table : _dbSchema.getTables())
                if (null == filter || filter.accept(table))
                    _tables.put(table.getName(), table);
        }
    }

    protected TableInfo createTable(String name)
    {
        SchemaTableInfo schematable = _tables.get(name);
        if (schematable == null)
            return null;
        return createTable(name, schematable);
    }

    protected TableInfo createTable(String name, @NotNull SchemaTableInfo schematable)
    {
        SimpleModuleTable usertable = new SimpleModuleTable(this, schematable);
        return usertable;
    }

    public Set<String> getTableNames()
    {
        return Collections.unmodifiableSet(_tables.keySet());
    }

    public static class SimpleModuleTable extends FilteredTable
    {
        SimpleModuleUserSchema _userSchema;

        public SimpleModuleTable(SimpleModuleUserSchema schema, SchemaTableInfo table)
        {
            super(table, schema.getContainer());
            _userSchema = schema;
            wrapAllColumns();
        }

        protected SimpleModuleUserSchema getUserSchema()
        {
            return _userSchema;
        }

        public void wrapAllColumns()
        {
            for (ColumnInfo col : _rootTable.getColumns())
            {
                ColumnInfo wrap = addWrapColumn(col);

                // ColumnInfo doesn't copy these attributes by default
                wrap.setHidden(col.isHidden());

                final String colName = col.getName();
                if (colName.equalsIgnoreCase("owner") ||
                        colName.equalsIgnoreCase("createdby") ||
                        colName.equalsIgnoreCase("modifiedby"))
                {
                    wrap.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), _userSchema.getContainer()));
                }
                else if (col.getFk() != null)
                {
                    //FIX: 5661
                    //get the column name in the target FK table that it would have joined against
                    //the existing fks should be of type SchemaForeignKey, so try to downcast to that
                    //so that we can get the declared lookup column
                    ColumnInfo.SchemaForeignKey fk = (ColumnInfo.SchemaForeignKey)col.getFk();
                    String pkColName = fk.getLookupColumnName();
                    if (null == pkColName && col.getFkTableInfo().getPkColumnNames().size() == 1)
                        pkColName = col.getFkTableInfo().getPkColumnNames().get(0);

                    if (null != pkColName)
                    {
                        // 9338 and 9051: fixup fks for external schemas that have been renamed
                        // NOTE: This will only fixup fk schema names if they are within the current schema.
                        String lookupSchemaName = fk.getLookupSchemaName();
                        if (lookupSchemaName.equalsIgnoreCase(_userSchema.getDbSchema().getName()))
                            lookupSchemaName = _userSchema.getName();
                        ForeignKey wrapFk = new SimpleModuleForeignKey(_userSchema, wrap, lookupSchemaName, null, fk.getLookupTableName(), pkColName, fk.isJoinWithContainer());
                        wrap.setFk(wrapFk);
                    }
                }
            }
        }

        @Override
        public boolean hasPermission(User user, Class<? extends Permission> perm)
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }

        @Override
        public QueryUpdateService getUpdateService()
        {
            // UNDONE: add an 'isUserEditable' bit to the schema and table?
            TableInfo table = getRealTable();
            if (table != null && table.getTableType() == TableInfo.TABLE_TYPE_TABLE)
                return new DefaultQueryUpdateService(this, table);
            return null;
        }

        @Override
        public String getPublicSchemaName()
        {
            return _userSchema.getName();
        }
    }

    public static class SimpleModuleForeignKey extends ColumnInfo.SchemaForeignKey
    {
        UserSchema _userSchema;

        public SimpleModuleForeignKey(UserSchema userSchema, ColumnInfo foreignKey, String dbSchemaName, String ownerName, String tableName, String lookupKey, boolean joinWithContaienr)
        {
            super(foreignKey, dbSchemaName, ownerName, tableName, lookupKey, joinWithContaienr);
            _userSchema = userSchema;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            UserSchema schema = QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), getLookupSchemaName());
            // CONSIDER: should we throw an exception instead?
            if (schema == null)
                return null;

            return schema.getTable(getLookupTableName(), true);
        }
    }
}
