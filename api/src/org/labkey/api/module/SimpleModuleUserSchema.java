/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.query.*;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.*;

/**
 * User: kevink
 * Date: Aug 24, 2009
 */
public class SimpleModuleUserSchema extends UserSchema
{
    private LinkedHashSet<String> _tableNames;

    public SimpleModuleUserSchema(String name, User user, Container container, DbSchema dbschema)
    {
        super(name, null, user, container, dbschema);

        _tableNames = new LinkedHashSet<String>();
        for (SchemaTableInfo table : dbschema.getTables())
            _tableNames.add(table.getName());
    }

    protected TableInfo createTable(String name)
    {
        SchemaTableInfo schematable = getDbSchema().getTable(name);
        if (schematable == null)
            return null;
        SimpleModuleTable usertable = new SimpleModuleTable(this, schematable);
        return usertable;
    }

    public Set<String> getTableNames()
    {
        return Collections.unmodifiableSet(_tableNames);
    }

    class SimpleModuleTable extends FilteredTable
    {
        SimpleModuleUserSchema _userSchema;

        public SimpleModuleTable(SimpleModuleUserSchema schema, TableInfo table)
        {
            super(table);
            _userSchema = schema;
            wrapAllColumns();
        }

        public void wrapAllColumns()
        {
            for (ColumnInfo col : _rootTable.getColumns())
            {
                ColumnInfo wrap = addWrapColumn(col);

                // ColumnInfo doesn't copy these attributes by default
                wrap.setHidden(col.isHidden());
                wrap.setURL(col.getURL());

                final String colName = col.getName();
                if (colName.equalsIgnoreCase("owner") ||
                        colName.equalsIgnoreCase("createdby") ||
                        colName.equalsIgnoreCase("modifiedby"))
                {
                    //UserIdForeignKey.initColumn(wrap);
                    wrap.setDisplayColumnFactory(new DisplayColumnFactory() {
                        public DisplayColumn createRenderer(ColumnInfo colInfo)
                        {
                            return new UserIdRenderer(colInfo);
                        }
                    });
                }

                if (col.getFk() != null)
                {
                    ColumnInfo.SchemaForeignKey fk = (ColumnInfo.SchemaForeignKey)col.getFk();
                    ForeignKey wrapFk = new SimpleModuleForeignKey(_userSchema, wrap, fk.getLookupSchemaName(), null, fk.getLookupTableName(), fk.getLookupColumnName(), fk.isJoinWithContainer());
                    wrap.setFk(wrapFk);
                }
            }
        }

        @Override
        public boolean hasPermission(User user, int perm)
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }

        @Override
        public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
        {
            // UNDONE
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public QueryUpdateService getUpdateService()
        {
            // UNDONE: add an 'isUserEditable' bit to the schema and table?
            TableInfo table = getRealTable();
            if (table != null && table.getTableType() == TableInfo.TABLE_TYPE_TABLE)
                return new DefaultQueryUpdateService(table);
            return null;
        }
    }

    class SimpleModuleForeignKey extends ColumnInfo.SchemaForeignKey
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
            return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), getLookupSchemaName()).getTable(getLookupTableName(), true);
        }

    }

}
