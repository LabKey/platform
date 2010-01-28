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
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: kevink
 * Date: Aug 24, 2009
 */
public class SimpleModuleUserSchema extends UserSchema
{
    private Map<String, SchemaTableInfo> _tables;

    public SimpleModuleUserSchema(String name, String description, User user, Container container, DbSchema dbschema)
    {
        super(name, description, user, container, dbschema);
        _tables = new CaseInsensitiveHashMap<SchemaTableInfo>();
        if (_dbSchema != null)
        {
            for (SchemaTableInfo table : _dbSchema.getTables())
                _tables.put(table.getName(), table);
        }
    }

    protected TableInfo createTable(String name)
    {
        SchemaTableInfo schematable = getDbSchema().getTable(name);
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
                wrap.setURL(col.getURL());

                final String colName = col.getName();
                if (colName.equalsIgnoreCase("owner") ||
                        colName.equalsIgnoreCase("createdby") ||
                        colName.equalsIgnoreCase("modifiedby"))
                {
                    wrap.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), _userSchema.getContainer()));
                }

                if (col.getFk() != null)
                {
                    //FIX: 5661
                    //get the column name in the target FK table that it would have joined against
                    //the existing fks should be of type SchemaForeignKey, so try to downcast to that
                    //so that we can get the declared lookup column
                    ColumnInfo.SchemaForeignKey fk = (ColumnInfo.SchemaForeignKey)col.getFk();
                    String pkColName = fk.getLookupColumnName();
                    if (null == pkColName && col.getFkTableInfo().getPkColumnNames().size() == 1)
                        pkColName = col.getFkTableInfo().getPkColumnNames().get(0);

                    if(null != pkColName)
                    {
                        ForeignKey wrapFk = new SimpleModuleForeignKey(_userSchema, wrap, fk.getLookupSchemaName(), null, fk.getLookupTableName(), pkColName, fk.isJoinWithContainer());
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
        public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception
        {
            //ids will be comma-delimited in the case of compound PKs
            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), true);

            SimpleFilter filter = new SimpleFilter();
            List<ColumnInfo> pks = getPkColumns();
            int numPks = pks.size();

            //normalize the pks to arrays of correctly-typed objects
            List<Object[]> pkValues = new ArrayList<Object[]>();
            for (String id : ids)
            {
                String[] stringValues;
                if (numPks > 1)
                {
                    stringValues = id.split(",");
                    if (stringValues.length != numPks)
                        throw new IllegalStateException("This table has " + numPks + " primary-key columns, but " + stringValues.length + " primary-key values were provided!");
                }
                else
                    stringValues = new String[]{id};

                Object[] values = new Object[numPks];
                for (int idx = 0; idx < numPks; ++idx)
                {
                    ColumnInfo pk = pks.get(idx);
                    values[idx] = pk.getJavaClass() == String.class ? stringValues[idx] : ConvertUtils.convert(stringValues[idx], pk.getJavaClass());
                }
                pkValues.add(values);
            }

            //build the pk clause
            //OR together each AND'd set of pk values
            SimpleFilter.OrClause pkClause = new SimpleFilter.OrClause();
            for (Object[] pkset : pkValues)
            {
                SimpleFilter.AndClause pksetClause = new SimpleFilter.AndClause();
                for (int idx = 0; idx < numPks; ++idx)
                {
                    pksetClause.addClause(new CompareType.CompareClause(pks.get(idx).getColumnName(), CompareType.EQUAL, pkset[idx]));
                }
                pkClause.addClause(pksetClause);
            }

            //add the pk caluse to the overall filter
            filter.addClause(pkClause);

            //check that all rows identified by those pks exist in the current container
            ColumnInfo containerCol = getRealTable().getColumn("container");
            if (containerCol != null)
            {
                String[] containerIds = Table.executeArray(getRealTable(), containerCol, filter, null, String.class);
                Set<String> seen = new HashSet<String>();
                for (String containerId : containerIds)
                {
                    if (containerId != null)
                    {
                        if (seen.contains(containerId))
                            continue;
                        seen.add(containerId);
                        if (!form.getContainer().getId().equals(containerId))
                            HttpView.throwUnauthorized("The row is from the wrong container.");
                    }
                }

                // Filter out non-current-container rows.
                filter.addCondition(containerCol, form.getContainer());
            }

            addQueryFilters(filter);
            Table.delete(getRealTable(), filter);
            return srcURL;
        }

        protected void addQueryFilters(SimpleFilter filter)
        {
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
            UserSchema schema;

            // If this is an external schema outside the labkey database, then lookup must be within that schema.
            // See #9038.  This could be improved to support fks to other schemas within the same datasource, but
            // getUserSchema() needs to take a scope so it knows where to look for that schema.
            if (_userSchema.getDbSchema().getScope() == DbScope.getLabkeyScope())
            {
                schema = QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), getLookupSchemaName());
            }
            else
            {
                assert _userSchema.getDbSchema().getName().equals(getLookupSchemaName());
                schema = _userSchema;
            }

            return schema.getTable(getLookupTableName(), true);
        }
    }
}
