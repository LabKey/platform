/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.TableInsertDataIterator;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.NotFoundException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: kevink
 * Date: Aug 24, 2009
 */
public class SimpleUserSchema extends UserSchema
{
    // CaseInsensitiveTreeSet preserves case of the table names (from XML), unlike CaseInsensitiveHashSet
    private final Set<String> _available = new CaseInsensitiveTreeSet();
    private final Set<String> _visible = new CaseInsensitiveTreeSet();

    public SimpleUserSchema(String name, @Nullable String description, User user, Container container, DbSchema dbschema)
    {
        this(name, description, user, container, dbschema, getAvailableTableNames(dbschema), getHiddenTableNames(dbschema));
    }

    private static Collection<String> getAvailableTableNames(DbSchema dbschema)
    {
        if (null == dbschema)
            return Collections.emptySet();

        List<String> availableNames = new LinkedList<String>();

        for (String metaDataName : dbschema.getTableNames())
            availableNames.add(dbschema.getTable(metaDataName).getName());

        return availableNames;
    }

    // CONSIDER: We could consult just the XML meta data to keep from loading table meta data from the database here,
    // like we do for external schemas.  These schemas tend to have very few tables, however...
    private static Collection<String> getHiddenTableNames(DbSchema dbschema)
    {
        if (null == dbschema)
            return Collections.emptySet();

        List<String> names = new LinkedList<String>();

        for (String name : dbschema.getTableNames())
        {
            SchemaTableInfo table = dbschema.getTable(name);

            if (table.isHidden())
                names.add(table.getName());
        }

        return names;
    }

    // Hidden tables are hidden from the UI but will still be addressible by Query (for fk lookups, etc.)
    public SimpleUserSchema(String name, String description, User user, Container container, DbSchema dbschema, Collection<String> availableTables, Collection<String> hiddenTables)
    {
        super(name, description, user, container, dbschema);
        _available.addAll(availableTables);
        _visible.addAll(availableTables);
        _visible.removeAll(hiddenTables);
    }

    protected TableInfo createTable(String name)
    {
        if (!_available.contains(name))
            return null;

        SchemaTableInfo schematable = _dbSchema.getTable(name);

        if (schematable == null)
            return null;

        return createTable(name, schematable);
    }

    protected TableInfo createTable(String name, @NotNull SchemaTableInfo schematable)
    {
        return new SimpleTable(this, schematable);
    }

    public Set<String> getTableNames()
    {
        return Collections.unmodifiableSet(_available);
    }

    @Override
    public Set<String> getVisibleTableNames()
    {
        return Collections.unmodifiableSet(_visible);
    }

    @Override
    public String getDomainURI(String queryName)
    {
        TableInfo table = getTable(queryName);
        if (table == null)
            throw new NotFoundException("Table '" + queryName + "' not found in this container '" + getContainer().getPath() + "'.");

        if (table instanceof SimpleTable)
            return ((SimpleTable)table).getDomainURI();
        return null;
    }

    public static class SimpleTable extends FilteredTable implements UpdateableTableInfo
    {
        protected UserSchema _userSchema;
        protected ColumnInfo _objectUriCol;
        protected Domain _domain;

        public SimpleTable(UserSchema schema, SchemaTableInfo table)
        {
            super(table, schema.getContainer());
            _userSchema = schema;

            addColumns();

            // Generic query details page if none provided in schema xml.
            if (!hasDetailsURL())
            {
                DetailsURL detailsURL = QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow, this);
                setDetailsURL(detailsURL);
            }
        }

        protected UserSchema getUserSchema()
        {
            return _userSchema;
        }

        public void addColumns()
        {
            wrapAllColumns();
            addDomainColumns();
        }

        protected boolean acceptColumn(ColumnInfo col)
        {
            return true;
        }

        public void wrapAllColumns()
        {
            for (ColumnInfo col : _rootTable.getColumns())
            {
                if (!acceptColumn(col)) continue;

                ColumnInfo wrap = wrapColumn(col);
                // 10945: Copy label from the underlying column -- wrapColumn() doesn't copy the label.
                wrap.setLabel(col.getLabel());
                addColumn(wrap);

                // ColumnInfo doesn't copy these attributes by default
                wrap.setHidden(col.isHidden());

                final String colName = col.getName();

                // Add an FK to the Users table for special fields... but ONLY if for type integer and in the LabKey data source.  #11660
                if (JdbcType.INTEGER == col.getJdbcType() &&
                   (colName.equalsIgnoreCase("owner") || colName.equalsIgnoreCase("createdby") || colName.equalsIgnoreCase("modifiedby")) &&
                   (_userSchema.getDbSchema().getScope().isLabKeyScope()))
                {
                    wrap.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), _userSchema.getContainer()));
                    wrap.setUserEditable(false);
                    wrap.setShownInInsertView(false);
                    wrap.setShownInUpdateView(false);
                    wrap.setReadOnly(true);

                    if(colName.equalsIgnoreCase("createdby"))
                        wrap.setLabel("Created By");
                    if(colName.equalsIgnoreCase("modifiedby"))
                        wrap.setLabel("Modified By");
                }
                // also add FK to container field
                else if ((col.getJdbcType() != null && col.getJdbcType().getJavaClass() == String.class)&&
                   "container".equalsIgnoreCase(colName) &&
                   (_userSchema.getDbSchema().getScope().isLabKeyScope()))
                {
                    wrap.setLabel("Folder");
                    ContainerForeignKey.initColumn(wrap, _userSchema);
                }
                else if (col.getFk() != null)
                {
                    //FIX: 5661
                    //get the column name in the target FK table that it would have joined against.
                    ForeignKey fk = col.getFk();
                    String pkColName = fk.getLookupColumnName();
                    TableInfo fkTable = col.getFkTableInfo();
                    if (null == pkColName && fkTable != null && fkTable.getPkColumnNames().size() == 1)
                        pkColName = fkTable.getPkColumnNames().get(0);

                    if (null != pkColName)
                    {
                        // 9338 and 9051: fixup fks for external schemas that have been renamed
                        // NOTE: This will only fixup fk schema names if they are within the current schema.
                        String lookupSchemaName = fk.getLookupSchemaName();
                        if (lookupSchemaName.equalsIgnoreCase(_userSchema.getDbSchema().getName()))
                            lookupSchemaName = _userSchema.getName();

                        boolean joinWithContainer = false;
                        String displayColumn = null;
                        if (fk instanceof ColumnInfo.SchemaForeignKey)
                        {
                            joinWithContainer = ((ColumnInfo.SchemaForeignKey)fk).isJoinWithContainer();
                            displayColumn = ((ColumnInfo.SchemaForeignKey)fk).getDisplayColumnName();
                        }

                        ForeignKey wrapFk = new SimpleForeignKey(_userSchema, wrap, lookupSchemaName, fk.getLookupTableName(), pkColName, joinWithContainer, displayColumn);
                        if (fk instanceof MultiValuedForeignKey)
                        {
                            wrapFk = new MultiValuedForeignKey(wrapFk, ((MultiValuedForeignKey)fk).getJunctionLookup());
                        }

                        wrap.setFk(wrapFk);

                        if (_objectUriCol == null && isObjectUriLookup(pkColName, fk.getLookupTableName(), fk.getLookupSchemaName()))
                        {
                            _objectUriCol = wrap;
                            wrap.setShownInInsertView(false);
                            wrap.setShownInUpdateView(false);
                            wrap.setShownInDetailsView(false);
                            wrap.setHidden(true);
                        }
                    }
                }
            }
        }

        protected void addDomainColumns()
        {
            Domain domain = getDomain();
            if (domain != null)
            {
                for (DomainProperty dp : domain.getProperties())
                {
                    PropertyDescriptor pd = dp.getPropertyDescriptor();
                    ColumnInfo propColumn = new PropertyColumn(pd, _objectUriCol, getContainer(), _userSchema.getUser(), true);
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

        public String getDomainURI()
        {
            if (_objectUriCol == null)
                return null;

            return SimpleTableDomainKind.getDomainURI(_userSchema.getName(), getName(), getContainer(), _userSchema.getUser());
        }

        // XXX: rename 'createObjectURI'
        protected String createPropertyURI()
        {
            if (_objectUriCol == null)
                return null;

            return SimpleTableDomainKind.createPropertyURI(_userSchema.getName(), getName(), getContainer(), _userSchema.getUser());
        }

        @Override
        public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
        {
            return _userSchema.getContainer().hasPermission(user, perm);
        }

        @Override
        public QueryUpdateService getUpdateService()
        {
            // UNDONE: add an 'isUserEditable' bit to the schema and table?
            TableInfo table = getRealTable();
            if (table != null && table.getTableType() == DatabaseTableType.TABLE)
                return new SimpleQueryUpdateService(this, table);
            return null;
        }

        @Override
        public String getPublicSchemaName()
        {
            return _userSchema.getName();
        }

    /*** UpdateableTableInfo ****/

        @Override
        public boolean insertSupported()
        {
            return true;
        }

        @Override
        public boolean updateSupported()
        {
            return true;
        }

        @Override
        public boolean deleteSupported()
        {
            return true;
        }

        @Override
        public TableInfo getSchemaTableInfo()
        {
            return getRealTable();
        }

        @Override
        public UpdateableTableInfo.ObjectUriType getObjectUriType()
        {
            return ObjectUriType.schemaColumn;
        }

        @Override
        public String getObjectURIColumnName()
        {
            return null==_objectUriCol ? null : _objectUriCol.getName();
        }

        @Override
        public String getObjectIdColumnName()
        {
            return null;
        }

        @Override
        public CaseInsensitiveHashMap<String> remapSchemaColumns()
        {
            return null;
        }

        @Override
        public CaseInsensitiveHashSet skipProperties()
        {
            return null;
        }

        @Override
        public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
        {
            return TableInsertDataIterator.create(data, this, null, context);
        }

        @Override
        public Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException
        {
            return StatementUtils.insertStatement(conn, getRealTable(), null, user, false, true);
        }

        @Override
        public Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * The SimpleForeignKey returns a lookup TableInfo from the UserSchema
     * rather than the underlying DbSchema's SchemaTableInfo.
     */
    public static class SimpleForeignKey extends ColumnInfo.SchemaForeignKey
    {
        UserSchema _userSchema;

        public SimpleForeignKey(UserSchema userSchema, ColumnInfo foreignKey, String dbSchemaName, String tableName, String lookupKey, boolean joinWithContainer, String displayColumnName)
        {
            super(foreignKey, dbSchemaName, tableName, lookupKey, joinWithContainer, displayColumnName);
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

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            TableInfo table = getLookupTableInfo();
            if (table == null)
                return null;

            return LookupForeignKey.getDetailsURL(parent, table, getLookupColumnName());
        }
    }
}
