/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.StringExpression;

import java.util.Map;
import java.util.Set;

/**
 * {@link ForeignKey} implementation that points at a schema/query known to QueryService and resolvable
 * by name through a {@link QuerySchema}. Lazily initialized as much as possible to prevent perf hit creating schemas
 * tables that aren't actually needed.
 */
public class QueryForeignKey extends AbstractForeignKey
{
    protected TableInfo _table;

    /**
     * The configured container for this lookup. Null if it should use the current container
     * This will create a single container containerFilter() using lookupContainer (unless tableinfo was explicitly provided, of course)
     */
    @Nullable
    Container _lookupContainer;
    /** The container for the schema for the target table (unless the target schema was explicitly) provided. */
    @NotNull
    Container _effectiveContainer;
    User _user;
    QuerySchema _schema;
    boolean _useRawFKValue;
    LookupColumn.JoinType _joinType = LookupColumn.JoinType.leftOuter;
    DetailsURL _url;

    /* There are (were) way too many QueryForeignKey constructors, that's a sign we need a builder */
    public static class Builder implements org.labkey.api.data.Builder<ForeignKey>
    {
        QuerySchema sourceSchema;
        User user;
        ContainerFilter containerFilter;

        // target schema definition
        Container effectiveContainer;
        SchemaKey lookupSchemaKey;
        UserSchema targetSchema;

        // FK definition
        TableInfo table;
        Container lookupContainer;
        String lookupTableName;
        String lookupKey = null;

        // display
        String displayField = null;
        boolean useRawFKValue = false;

        DetailsURL url;

        // for deprecated constructors only
        private Builder()
        {
        }

        public Builder(@NotNull QuerySchema schema, @Nullable ContainerFilter cf)
        {
            sourceSchema = schema;
            containerFilter = cf;
            if (schema instanceof UserSchema)
                schema((UserSchema) schema);
            effectiveContainer = schema.getContainer();
            user = sourceSchema.getUser();
        }

        public Builder schema(SchemaKey lookupSchemaKey)
        {
            this.lookupSchemaKey = lookupSchemaKey;
            this.targetSchema = null;
            return this;
        }

        public Builder schema(UserSchema lookupSchema)
        {
            lookupSchemaKey = lookupSchema.getSchemaPath();
            this.targetSchema = lookupSchema;
            effectiveContainer = lookupSchema.getContainer();
            return this;
        }


        public Builder schema(String schemaName)
        {
            lookupSchemaKey = SchemaKey.fromString(schemaName);
            // the caller might have defaulted the targetSchema to sourceSchema, so clear if schema is set by name
            targetSchema = null;
            return this;
        }


        // effectiveContainer is the container used when resolving schemaName if there is not an explicit fkFolderPath defined
        public Builder schema(String schemaName, Container effectiveContainer)
        {
            lookupSchemaKey = SchemaKey.fromString(schemaName);
            // the caller might have defaulted the targetSchema to sourceSchema, so clear if schema is set by name
            targetSchema = null;
            if (null != effectiveContainer)
                this.effectiveContainer = effectiveContainer;
            return this;
        }

        // This is the container for the lookup table
        public Builder container(Container container)
        {
            this.lookupContainer = container;
            return this;
        }

        public Builder table(String tableName)
        {
            this.lookupTableName = tableName;
            return this;
        }

        public Builder table(Enum tableName)
        {
            this.lookupTableName = tableName.name();
            return this;
        }

        public Builder table(TableInfo t)
        {
            lookupTableName = null;
            table = t;
            return this;
        }

//        public Builder setTableName(String tableName)
//        {
//            this.lookupTableName = tableName;
//            return this;
//        }

        public Builder key(String name)
        {
            this.lookupKey = name;
            return this;
        }

        public Builder key(Enum name)
        {
            this.lookupKey = name.name();
            return this;
        }

        public Builder url(DetailsURL url)
        {
            this.url = url;
            return this;
        }

//        public Builder setLookupKey(String name)
//        {
//            this.lookupKey = name;
//            return this;
//        }

//        public Builder setDisplayField(String field)
//        {
//            this.displayField = field;
//            return this;
//        }

        public Builder display(String field)
        {
            this.displayField = field;
            return this;
        }

        public Builder raw(boolean b)
        {
            useRawFKValue = b;
            return this;
        }

        public Builder to(@NotNull String table, @Nullable String key, @Nullable String display)
        {
            lookupTableName = table;
            lookupKey = key;
            displayField = display;
            return this;
        }

        @Override
        public ForeignKey build()
        {
            if (null == lookupSchemaKey && null == targetSchema)
                targetSchema = (UserSchema)sourceSchema;

            /* see 41054 move the core.containers special case handling here from PdLookupForeignKey */
            boolean isLabKeyScope = null != sourceSchema && (null == sourceSchema.getDbSchema() || sourceSchema.getDbSchema().getScope().isLabKeyScope());
            if (isLabKeyScope)
            {
                if (null != lookupSchemaKey && "core".equalsIgnoreCase(lookupSchemaKey.getName()) && "containers".equalsIgnoreCase(lookupTableName) && effectiveContainer.equals(sourceSchema.getContainer()))
                {
                    if (null == containerFilter)
                        containerFilter = new ContainerFilter.AllFolders(user);
                }
            }

            return new QueryForeignKey(this);
        }
    }

    public static Builder from(@NotNull QuerySchema schema, @Nullable ContainerFilter cf)
    {
        return new Builder(schema, cf);
    }

    public QueryForeignKey(Builder builder)
    {
        super(builder.sourceSchema, builder.containerFilter, builder.lookupSchemaKey, builder.lookupTableName, builder.lookupKey, builder.displayField);
        _effectiveContainer = builder.effectiveContainer;
        _lookupContainer = builder.lookupContainer;
        _user = builder.user;
        _useRawFKValue = builder.useRawFKValue;
        _table = builder.table;
        _schema = builder.targetSchema;
        _url = builder.url;
        // TODO there is an EHR usage that fails this assert (AbstractTableCustomizer)
        // assert(null == _lookupContainer || getEffectiveContainer() == getLookupContainer());
        setShowAsPublicDependency(null==_schema || !"core".equalsIgnoreCase(_schema.getName()));
    }

    protected QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, @NotNull String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(
            from(sourceSchema,cf)
            .schema(schemaName,effectiveContainer)
            .to(tableName,lookupKey,displayField)
            .container(lookupContainer) // for metadata pass-through
        );
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    public QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(
            from(sourceSchema,cf)
            .schema((UserSchema)schema)
            .to(tableName,lookupKey,displayField)
            .container(lookupContainer) // for metadata pass-through
        );
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    @Deprecated // TODO ContainerFilter
    public QueryForeignKey(QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this( new QueryForeignKey.Builder()
            .schema((UserSchema)schema)
            .to(tableName, lookupKey, displayField)
            .container(lookupContainer) // for metadata pass-through
        );
    }

    // Caller is responsible for containerfilter on passed in TableInfo
    // Consider using LookupForeignKey instead
    public QueryForeignKey(TableInfo table, @Nullable String lookupKey, @Nullable String displayField)
    {
        this( new QueryForeignKey.Builder()
            .table(table).key(lookupKey).display(displayField)
        );
    }

    @Deprecated
    public QueryForeignKey(TableInfo table, @Nullable Container lookupContainer, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(table, lookupKey, displayField);
        assert null==lookupContainer;
    }


    public void setJoinType(LookupColumn.JoinType joinType)
    {
        _joinType = joinType;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo foreignKey, String displayField)
    {
        TableInfo lookupTable;

        try
        {
            lookupTable = getLookupTableInfo();
            if (null == lookupTable)
                return null;
        }
        catch (QueryParseException qpe)
        {
            String name = StringUtils.defaultString(displayField,"?");
            FieldKey key = new FieldKey(foreignKey.getFieldKey(), name);
            return qpe.makeErrorColumnInfo(foreignKey.getParentTable(), key);
        }

        if (displayField == null)
        {
            if (_useRawFKValue)
            {
                return foreignKey;
            }
            displayField = _displayColumnName;
            if (displayField == null)
            {
                displayField = lookupTable.getTitleColumn();
            }
            if (displayField == null)
                return null;
        }

        return LookupColumn.create(foreignKey, lookupTable.getColumn(getLookupColumnName()), lookupTable.getColumn(displayField), false, _joinType);
    }

    @Nullable
    @Override
    public Container getLookupContainer()
    {
        return _lookupContainer;
    }

    private Container getEffectiveContainer()
    {
        if (null != _table)
            return _table.getUserSchema().getContainer();
        if (null != _schema)
            return _schema.getContainer();
        return _effectiveContainer;
    }

    @Override
    protected User getLookupUser()
    {
        if (null != _user)
            return _user;
        if (null != getSchema())
            return getSchema().getUser();
        return super.getLookupUser();
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        if (_table == null && getSchema() != null)
        {
            TableInfo t = getSchema().getTable(_tableName, getLookupContainerFilter());
            if (null != t && !t.hasPermission(getLookupUser(), ReadPermission.class))
                t = null;
            _table = t;
        }
        return _table;
    }

    protected QuerySchema getSchema()
    {
        if (_schema == null && _user != null && _lookupSchemaKey != null)
        {
            DefaultSchema resolver = null;
            if (null==_sourceSchema || !_effectiveContainer.equals(_sourceSchema.getContainer()))
                resolver = DefaultSchema.get(_user,_effectiveContainer);
            else
                resolver = _sourceSchema.getDefaultSchema();
            _schema = DefaultSchema.resolve(resolver, _lookupSchemaKey);
        }
        return _schema;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;

        if (_url != null)
            return _url;

        return LookupForeignKey.getDetailsURL(parent, table, getLookupColumnName());
    }

    @Override
    public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
    {
        return this;
    }

    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        return null;
    }

    public boolean isUseRawFKValue()
    {
        return _useRawFKValue;
    }
}
