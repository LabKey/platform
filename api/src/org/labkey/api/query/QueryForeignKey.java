/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

    /** The configured container for this lookup. Null if it should use the current container */
    @Nullable
    Container _lookupContainer;
    /** The container in which the lookup should be evaluated. */
    @NotNull
    Container _effectiveContainer;
    User _user;
    QuerySchema _schema;
    boolean _useRawFKValue;
    LookupColumn.JoinType _joinType = LookupColumn.JoinType.leftOuter;


    /* There are (were) way too many QueryForeignKey constructors, that's a sign we need a builder */
    public static class Builder implements org.labkey.api.data.Builder<ForeignKey>
    {
        QuerySchema sourceSchema;
        User user;
        ContainerFilter containerFilter;

        // target schema definition
        Container effectiveContainer;
        String lookupSchemaName;
        UserSchema targetSchema;

        // FK definition
        TableInfo table;
        Container lookupContainer;
        String lookupTableName;
        String lookupKey = null;

        // display
        String displayField = null;
        boolean useRawFKValue = false;

        public Builder(@NotNull QuerySchema schema, @Nullable ContainerFilter cf)
        {
            sourceSchema = schema;
            containerFilter = cf;
            if (schema instanceof UserSchema)
                schema((UserSchema) schema);
            effectiveContainer = schema.getContainer();
            user = sourceSchema.getUser();
        }

        public Builder schema(UserSchema lookupSchema)
        {
            lookupSchemaName = lookupSchema.getSchemaName();
            this.targetSchema = lookupSchema;
            effectiveContainer = lookupSchema.getContainer();
            return this;
        }

        public Builder schema(String schemaName)
        {
            lookupSchemaName = schemaName;
            // the caller might have defaulted the targetSchema to sourceSchema, so clear if schema is set by name
            targetSchema = null;
            return this;
        }

//        public Builder schema(String schemaName)
//        {
//            return setSchema(schemaName);
//        }

        // effectiveContainer is the container used when resolving schemaName if there is not an explicit fkFolderPath defined
        public Builder schema(String schemaName, Container effectiveContainer)
        {
            lookupSchemaName = schemaName;
            // the caller might have defaulted the targetSchema to sourceSchema, so clear if schema is set by name
            targetSchema = null;
            this.effectiveContainer = effectiveContainer;
            return this;
        }

        // effectiveContainer is the container used when resolving schemaName if there is not an explicit fkFolderPath defined
//        public Builder setSchema(String schemaName, Container effectiveContainer)
//        {
//            lookupSchemaName = schemaName;
//            this.effectiveContainer = effectiveContainer;
//            return this;
//        }

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

        public ForeignKey build()
        {
            if (null == lookupSchemaName && null == targetSchema)
                targetSchema = (UserSchema)sourceSchema;
            return new QueryForeignKey(this);
        }
    }

    public static Builder from(@NotNull QuerySchema schema, @Nullable ContainerFilter cf)
    {
        return new Builder(schema, cf);
    }

    public QueryForeignKey(Builder builder)
    {
        super(builder.sourceSchema, builder.containerFilter, builder.lookupSchemaName, builder.lookupTableName, builder.lookupKey, builder.displayField);
        _effectiveContainer = builder.effectiveContainer;
        _lookupContainer = builder.lookupContainer;
        _user = builder.user;
        _useRawFKValue = builder.useRawFKValue;
        _table = builder.table;
        _schema = builder.targetSchema;
    }

    private QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, @NotNull String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField, boolean useRawFKValue)
    {
        super(sourceSchema, cf, schemaName, tableName, lookupKey, displayField);
        _effectiveContainer = effectiveContainer;
        _lookupContainer = lookupContainer;
        _user = user;
        _useRawFKValue = useRawFKValue;
    }

    protected QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(sourceSchema, cf, schemaName, effectiveContainer, lookupContainer, user, tableName, lookupKey, displayField, false);
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    protected QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField, boolean useRawFKValue)
    {
        super(sourceSchema, cf, schema.getSchemaName(), tableName, lookupKey, displayField);
        _schema = schema;
        _lookupContainer = lookupContainer;
        _useRawFKValue = useRawFKValue;
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    public QueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(sourceSchema, cf, schema, lookupContainer, tableName, lookupKey, displayField, false);
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    @Deprecated // TODO ContainerFilter
    public QueryForeignKey(QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(null, null, schema, lookupContainer, tableName, lookupKey, displayField, false);
    }


    @Deprecated // TODO ContainerFilter
    public QueryForeignKey(TableInfo table, @Nullable Container lookupContainer, @Nullable String lookupKey, @Nullable String displayField)
    {
        super(null, null, table.getName(), lookupKey, displayField);
        _table = table;
        _lookupContainer = lookupContainer;
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

    @Override
    public TableInfo getLookupTableInfo()
    {
        if (_table == null && getSchema() != null)
        {
            _table = getSchema().getTable(_tableName, getLookupContainerFilter());
        }
        return _table;
    }

    protected QuerySchema getSchema()
    {
        if (_schema == null && _user != null && _lookupSchemaName != null)
        {
            DefaultSchema resolver = null;
            if (null==_sourceSchema || !_effectiveContainer.equals(_sourceSchema.getContainer()))
                resolver = DefaultSchema.get(_user,_effectiveContainer);
            else
                resolver = _sourceSchema.getDefaultSchema();
            _schema = resolver.getSchema(_lookupSchemaName);
        }
        return _schema;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
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
