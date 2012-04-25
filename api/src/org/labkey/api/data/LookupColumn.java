/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RowIdForeignKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LookupColumn extends ColumnInfo
{
    /**
     * Create a lookup column to a "real" table - one that actually exists
     * in the underlying database
     */
    static public LookupColumn create(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn, boolean prefixColumnCaption)
    {
        if (lookupKey == null || lookupColumn == null)
            return null;
        LookupColumn ret = new LookupColumn(foreignKey, lookupKey, lookupColumn);
        ret.copyAttributesFrom(lookupColumn);
        ret.copyURLFrom(lookupColumn, foreignKey.getFieldKey(), null);
        if (prefixColumnCaption)
        {
            ret.setLabel(foreignKey.getLabel() + " " + lookupColumn.getLabel());
        }
        else
        {
            ret.setLabel(lookupColumn.getLabel());
        }
        ret.setShortLabel(lookupColumn.getShortLabel());
        if (ret.getFk() instanceof RowIdForeignKey)
        {
            ret.setFk(null);
        }
        return ret;
    }

    @Override
    public boolean isKeyField()
    {
        return false;   // Lookup columns are never key fields of the parent table
    }

    /** The column in the source table. In the UI, this is the column that will be joinable to the target table. */
    protected ColumnInfo _foreignKey;
    /** The column in the target table, typically its PK. */
    protected ColumnInfo _lookupKey;
    /** The display column to show */
    protected ColumnInfo _lookupColumn;
    /** Additional column pairs if this is a multi-column join */
    protected Map<ColumnInfo, ColumnInfo> _additionalJoins = new HashMap<ColumnInfo, ColumnInfo>();

    public LookupColumn(ColumnInfo foreignKey, ColumnInfo lookupKey, ColumnInfo lookupColumn)
    {
        // Bug 1166: always report that our parent table is the leftmost table, so the dataregion knows which
        // table to select from
        super(new FieldKey(foreignKey.getFieldKey(), lookupColumn.getName()), foreignKey.getParentTable());
        _foreignKey = foreignKey;
        _lookupKey = lookupKey;
        assert lookupKey.getValueSql("test") != null;
        _lookupColumn = lookupColumn;
        setSqlTypeName(lookupColumn.getSqlTypeName());
        setAlias(foreignKey.getAlias() + "$" + lookupColumn.getAlias());
    }

    public SQLFragment getValueSql(String tableAliasName)
    {
        return _lookupColumn.getValueSql(getTableAlias(tableAliasName));
    }

    public void addJoin(FieldKey foreignKeyFieldKey, ColumnInfo lookupKey)
    {
        FieldKey fieldKey = new FieldKey(_foreignKey.getFieldKey().getParent(), foreignKeyFieldKey.getName());
        Map<FieldKey, ColumnInfo> map = QueryService.get().getColumns(_foreignKey.getParentTable(), Collections.singleton(fieldKey));
        ColumnInfo translatedFK = map.get(fieldKey);
        assert translatedFK != null : "ForeignKey '" + foreignKeyFieldKey + "' not found on table '" + _foreignKey.getParentTable() + "' for lookup to '" + lookupKey.getFieldKey() + "'";

        _additionalJoins.put(translatedFK, lookupKey);
    }

    public SQLFragment getJoinCondition(String tableAliasName)
    {
        SQLFragment result = new SQLFragment("(");
        result.append(getJoinCondition(tableAliasName, _foreignKey, _lookupKey));
        for (Map.Entry<ColumnInfo, ColumnInfo> entry : _additionalJoins.entrySet())
        {
            ColumnInfo fk = entry.getKey();
            ColumnInfo pk = entry.getValue();
            result.append(" AND ");
            result.append(getJoinCondition(tableAliasName, fk, pk));
        }
        result.append(")");
        return result;
    }

    private SQLFragment getJoinCondition(String tableAliasName, ColumnInfo fk, ColumnInfo pk)
    {
        SQLFragment condition = new SQLFragment();
        boolean addCast = fk.getSqlTypeInt() != pk.getSqlTypeInt() && getSqlDialect().isPostgreSQL();
        SQLFragment fkSql = fk.getValueSql(tableAliasName);
        if (addCast)
            condition.append("CAST((").append(fkSql).append(") AS VARCHAR)");
        else
            condition.append(fkSql);
        condition.append(" = ");

        SQLFragment pkSql = pk.getValueSql(getTableAlias(tableAliasName));
        if (addCast)
            condition.append("CAST((").append(pkSql).append(") AS VARCHAR)");
        else
            condition.append(pkSql);

        return condition;
    }

    
    @SuppressWarnings({"ConstantConditions"})
    public void declareJoins(String baseAlias, Map<String, SQLFragment> map)
    {
        boolean assertEnabled = false; // needed to generate SQL for logging
        assert assertEnabled = true;

        String colTableAlias = getTableAlias(baseAlias);

        if (assertEnabled || !map.containsKey(colTableAlias))
        {
            _foreignKey.declareJoins(baseAlias, map);
            for (ColumnInfo columnInfo : _additionalJoins.keySet())
            {
                columnInfo.declareJoins(baseAlias, map);
            }
            SQLFragment strJoin = new SQLFragment("\n\tLEFT OUTER JOIN ");

            addLookupSql(strJoin, _lookupKey.getParentTable(), colTableAlias);
            strJoin.append(" ON ");
            strJoin.append(getJoinCondition(baseAlias));
            assert null == map.get(colTableAlias) || map.get(colTableAlias).getSQL().equals(strJoin.getSQL());
            map.put(colTableAlias, strJoin);
        }

        if (includeLookupJoins())
            _lookupColumn.declareJoins(colTableAlias, map);
    }


    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable, String alias)
    {
        strJoin.append(lookupTable.getFromSQL(alias));
    }


    protected boolean includeLookupJoins()
    {
        return true;
    }


    /**
     * generate a unique table name for the joined in table
     * NOTE: postgres may ignore characters past 64 resulting in spurious duplicate alias errors
     * ref 10493
     * @param baseAlias alias of table on "left hand side" of the lookup
     */
    public String getTableAlias(String baseAlias)
    {
        String alias = baseAlias + (baseAlias.endsWith("$")?"":"$") + _foreignKey.getAlias() + "$";
        alias = AliasManager.truncate(alias, 63);
        return alias;
    }

    public String getColumnName()
    {
        return _lookupColumn.getName();
    }
}
