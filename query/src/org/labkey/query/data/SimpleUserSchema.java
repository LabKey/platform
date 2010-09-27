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

package org.labkey.query.data;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.SimpleModule;
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
public class SimpleUserSchema extends UserSchema
{
    private Map<String, SchemaTableInfo> _tables;

    public SimpleUserSchema(String name, String description, User user, Container container, DbSchema dbschema)
    {
        this(name, description, user, container, dbschema, null);
    }

    public SimpleUserSchema(String name, String description, User user, Container container, DbSchema dbschema, @Nullable Filter<TableInfo> filter)
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
        SimpleTable usertable = new SimpleTable(this, schematable);
        return usertable;
    }

    public Set<String> getTableNames()
    {
        return Collections.unmodifiableSet(_tables.keySet());
    }

    @Override
    public String getDomainURI(String queryName)
    {
        SimpleTable table = (SimpleTable)getTable(queryName);
        if (table == null)
            return null;

        return table.getDomainURI();
    }

    public static class SimpleTable extends FilteredTable
    {

        SimpleUserSchema _userSchema;
        ColumnInfo _objectUriCol;
        Domain _domain;

        public SimpleTable(SimpleUserSchema schema, SchemaTableInfo table)
        {
            super(table, schema.getContainer());
            _userSchema = schema;
            wrapAllColumns();
        }

        protected SimpleUserSchema getUserSchema()
        {
            return _userSchema;
        }

        public void wrapAllColumns()
        {
            for (ColumnInfo col : _rootTable.getColumns())
            {
                ColumnInfo wrap = wrapColumn(col);
                addColumn(wrap);

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
                    //get the column name in the target FK table that it would have joined against.
                    ForeignKey fk = col.getFk();
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

                        boolean joinWithContainer = false;
                        if (fk instanceof ColumnInfo.SchemaForeignKey)
                            joinWithContainer = ((ColumnInfo.SchemaForeignKey)fk).isJoinWithContainer();

                        ForeignKey wrapFk = new SimpleForeignKey(_userSchema, wrap, lookupSchemaName, fk.getLookupTableName(), pkColName, joinWithContainer);
                        if (fk instanceof MultiValuedForeignKey)
                        {
                            wrapFk = new MultiValuedForeignKey(wrapFk, ((MultiValuedForeignKey)fk).getJunctionLookup());
                        }

                        wrap.setFk(wrapFk);

                        if (_objectUriCol == null && isObjectUriLookup(pkColName, fk.getLookupTableName(), fk.getLookupSchemaName()))
                        {
                            _objectUriCol = wrap;
                        }
                    }
                }
            }

            Domain domain = getDomain();
            if (domain != null)
            {
                for (DomainProperty dp : domain.getProperties())
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    ColumnInfo propColumn = new PropertyColumn(pd, _objectUriCol, getContainer().getId(), _userSchema.getUser());
                    if (getColumn(propColumn.getName()) == null)
                    {
                        addColumn(propColumn);
                        // XXX: add to list of default visible columns
                    }
                }
            }
        }
        
        public Iterable<ColumnInfo> getBuiltInColumns()
        {
            return Iterables.filter(getColumns(), new Predicate<ColumnInfo>() {
                @Override
                public boolean apply(ColumnInfo columnInfo)
                {
                    return !(columnInfo instanceof PropertyColumn);
                }
            });
        }
        
        public Iterable<PropertyColumn> getPropertyColumns()
        {
            return Iterables.filter(getColumns(), PropertyColumn.class);
        }

        private boolean isObjectUriLookup(String pkColName, String tableName, String schemaName)
        {
            return "ObjectURI".equalsIgnoreCase(pkColName) &&
                    "Object".equalsIgnoreCase(tableName) &&
                    "exp".equalsIgnoreCase(schemaName);
        }

        public ColumnInfo getObjectUriColumn()
        {
            return _objectUriCol;
        }

        @Override
        public Domain getDomain()
        {
            if (_objectUriCol == null)
                return null;

            if (_domain == null)
            {
                String domainURI = getDomainURI();
                _domain = PropertyService.get().getDomain(getContainer(), domainURI);
            }

            return _domain;
        }

        public SimpleTableDomainKind getDomainKind()
        {
            if (_objectUriCol == null)
                return null;

            return (SimpleTableDomainKind)PropertyService.get().getDomainKindByName(SimpleModule.NAMESPACE_PREFIX);
        }

        private String getDomainURI()
        {
            if (_objectUriCol == null)
                return null;

            return getDomainKind().getDomainURI(_userSchema.getName(), getName(), getContainer(), _userSchema.getUser());
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

    /**
     * The SimpleForeignKey returns a lookup TableInfo from the UserSchema
     * rather than the underlying DbSchema's SchemaTableInfo.
     */
    public static class SimpleForeignKey extends ColumnInfo.SchemaForeignKey
    {
        UserSchema _userSchema;

        public SimpleForeignKey(UserSchema userSchema, ColumnInfo foreignKey, String dbSchemaName, String tableName, String lookupKey, boolean joinWithContainer)
        {
            super(foreignKey, dbSchemaName, tableName, lookupKey, joinWithContainer);
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
