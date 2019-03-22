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
    TableInfo _table;

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

    public void setJoinType(LookupColumn.JoinType joinType)
    {
        _joinType = joinType;
    }

    public QueryForeignKey(@NotNull String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField, boolean useRawFKValue)
    {
        super(schemaName, tableName, lookupKey, displayField);
        _effectiveContainer = effectiveContainer;
        _lookupContainer = lookupContainer;
        _user = user;
        _useRawFKValue = useRawFKValue;
    }

    public QueryForeignKey(String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(schemaName, effectiveContainer, lookupContainer, user, tableName, lookupKey, displayField, false);
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    public QueryForeignKey(QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField, boolean useRawFKValue)
    {
        super(schema.getSchemaName(), tableName, lookupKey, displayField);
        _schema = schema;
        _lookupContainer = lookupContainer;
        _useRawFKValue = useRawFKValue;
    }

    /**
     * @param schema a schema pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    public QueryForeignKey(QuerySchema schema, @Nullable Container lookupContainer, String tableName, @Nullable String lookupKey, @Nullable String displayField)
    {
        this(schema, lookupContainer, tableName, lookupKey, displayField, false);
    }

    /**
     * @param table a TableInfo pointed at the effective container for this usage
     * @param lookupContainer null if the lookup isn't specifically configured to point at a specific container, and should be pointing at the current container
     */
    public QueryForeignKey(TableInfo table, @Nullable Container lookupContainer, @Nullable String lookupKey, @Nullable String displayField)
    {
        super(null, table.getName(), lookupKey, displayField);
        _table = table;
        _lookupContainer = lookupContainer;
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

            this.propagateContainerFilter(foreignKey, lookupTable);
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
            _table = getSchema().getTable(_tableName);
        }
        return _table;
    }

    private QuerySchema getSchema()
    {
        if (_schema == null && _user != null && _lookupSchemaName != null)
        {
            _schema = QueryService.get().getUserSchema(_user, _effectiveContainer , _lookupSchemaName);
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
