/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class QueryForeignKey implements ForeignKey
{
    TableInfo _table;
    String _schemaName;
    /** The configured container for this lookup. Null if it should use the current container */
    @Nullable
    Container _lookupContainer;
    /** The container in which the lookup should be evaluated. */
    @NotNull
    Container _effectiveContainer;
    User _user;
    String _tableName;
    String _lookupKey;
    String _displayField;
    QuerySchema _schema;
    boolean _useRawFKValue;
    LookupColumn.JoinType _joinType = LookupColumn.JoinType.leftOuter;

    public void setJoinType(LookupColumn.JoinType joinType)
    {
        _joinType = joinType;
    }

    public QueryForeignKey(@NotNull String schemaName, @NotNull Container effectiveContainer, @Nullable Container lookupContainer, User user, String tableName, @Nullable String lookupKey, @Nullable String displayField, boolean useRawFKValue)
    {
        _schemaName = schemaName;
        _effectiveContainer = effectiveContainer;
        _lookupContainer = lookupContainer;
        _user = user;
        _tableName = tableName;
        _lookupKey = lookupKey;
        _displayField = displayField;
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
        _schema = schema;
        _schemaName = schema.getSchemaName();
        _lookupContainer = lookupContainer;
        _tableName = tableName;
        _lookupKey = lookupKey;
        _displayField = displayField;
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
        _table = table;
        _lookupContainer = lookupContainer;
        _tableName = table.getName();
        _lookupKey = lookupKey;
        _displayField = displayField;
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

            LookupForeignKey.propagateContainerFilter(foreignKey, lookupTable);
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
            displayField = _displayField;
            if (displayField == null)
            {
                displayField = lookupTable.getTitleColumn();
            }
            if (displayField == null)
                return null;
        }

        return LookupColumn.create(foreignKey, lookupTable.getColumn(getLookupColumnName(lookupTable)), lookupTable.getColumn(displayField), false, _joinType);
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
        if (_schema == null && _user != null && _schemaName != null)
        {
            _schema = QueryService.get().getUserSchema(_user, _effectiveContainer , _schemaName);
        }
        return _schema;

    }

    @Override
    public String getLookupSchemaName()
    {
        return _schemaName;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
            return null;
        return LookupForeignKey.getDetailsURL(parent, table, getLookupColumnName(table));
    }

    @Override
    public String getLookupTableName()
    {
        return _tableName;
    }

    @Override
    public String getLookupColumnName()
    {
        return getLookupColumnName(null);
    }

    public String getLookupColumnName(@Nullable TableInfo tableInfo)
    {
        if (_lookupKey == null)
        {
            if (tableInfo == null)
            {
                tableInfo = getLookupTableInfo();
            }

            if (tableInfo != null)
            {
                List<String> pkColumnNames = tableInfo.getPkColumnNames();
                if (pkColumnNames.size() == 1)
                {
                    _lookupKey = pkColumnNames.get(0);
                }
            }
            return null;
        }
        return _lookupKey;
    }

    @Override
    public String getLookupDisplayName()
    {
        return _displayField;
    }

    @Override
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return new NamedObjectList();

        return lookupTable.getSelectList(getLookupColumnName());
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
