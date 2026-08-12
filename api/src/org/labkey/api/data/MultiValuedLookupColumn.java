/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.labkey.api.data.JdbcType.BINARY;

/**
 * Special kind of lookup column that can join and display multiple values, through a junction table, instead of a
 * single, standard foreign-key type relationship.
 *
 * User: adam
 * Date: Sep 14, 2010
*/
public class MultiValuedLookupColumn extends LookupColumn
{
    private final ColumnInfo _display;
    private final ForeignKey _rightFk;
    private final ColumnInfo _junctionKey;
    /** The column _rightFk hangs off: the junction's target key at the first hop, the previous hop's display column beyond it. */
    private final ColumnInfo _rightFkParent;
    /** The hop this column was reached through, or null at the first hop. Each hop aggregates in its own subquery. */
    private final MultiValuedLookupColumn _previousHop;

    public MultiValuedLookupColumn(ColumnInfo parentPkColumn, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk, ColumnInfo display)
    {
        super(parentPkColumn, childKey, display);
        _display = display;
        _rightFk = fk;
        _junctionKey = junctionKey;
        _rightFkParent = junctionKey;
        _previousHop = null;

        // Issue 47311: Rewrite the lookup FieldKey to remove the intermediate table/query
        setFieldKey(parentPkColumn.getFieldKey().append(display.getFieldKey().getName()));

        copyAttributesFrom(display);
        copyURLFrom(display, parentPkColumn.getFieldKey(), null);
        // NOTE: Changing the type to a VARCHAR causes MultiValueRenderContext.get() type conversion to be skipped and we don't want that.
        //setJdbcType(JdbcType.VARCHAR);
    }

    /**
     * Continue a lookup past a multi-valued column, aggregating a column of the table {@code rightFk} points at.
     * The extra join lives inside this column's own aggregate subquery, so the values stay one-per-junction-row.
     */
    protected MultiValuedLookupColumn(MultiValuedLookupColumn previousHop, ForeignKey rightFk, ColumnInfo display)
    {
        super(previousHop._foreignKey, previousHop._lookupKey, display);
        _display = display;
        _rightFk = rightFk;
        _junctionKey = previousHop._junctionKey;
        _rightFkParent = previousHop._display;
        _previousHop = previousHop;

        // Keep the whole traversed path; appending to the declaring column would collide with the first hop's columns.
        setFieldKey(previousHop.getFieldKey().append(display.getFieldKey().getName()));

        copyAttributesFrom(display);
        copyURLFrom(display, previousHop.getFieldKey(), null);
    }

    @Override
    public ForeignKey getFk()
    {
        // The value here is an aggregate of many rows, so a plain lookup can't join from it. MultiValuedPassthroughForeignKey
        // pushes the next join inside the subquery instead. super.getFk() is the target column's own FK, copied by
        // copyAttributesFrom() in the constructor.
        ForeignKey fk = super.getFk();
        return null == fk ? null : new MultiValuedPassthroughForeignKey(this, fk);
    }

    ColumnInfo getDisplayColumn()
    {
        return _display;
    }

    int getHopCount()
    {
        return null == _previousHop ? 1 : _previousHop.getHopCount() + 1;
    }

    // Each hop aggregates in its own subquery, so it needs its own alias. Sibling columns reached through the same hop
    // (Organism/Name and Organism/Genus) produce the same alias and the same SQL, so declareJoins() still dedupes them.
    @Override
    public String getTableAlias(String baseAlias)
    {
        if (null == _previousHop)
            return super.getTableAlias(baseAlias);
        return getTableAlias(_previousHop.getTableAlias(baseAlias), _rightFkParent.getAlias().getId(), getSqlDialect());
    }

    // A sort would order by the delimited aggregate rather than the individual values. The first hop stays as it was;
    // only the columns this change makes reachable opt out.
    @Override
    public boolean isSortable()
    {
        return null == _previousHop && super.isSortable();
    }

    @Override
    public DisplayColumn getRenderer()
    {
        // NOTE: Calling `new MultiValuedDisplayColumn(super.getRenderer())` will re-wrap the MVDC which results in arrays of arrays of values
        return getDisplayColumnFactory().createRenderer(this);
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return colInfo -> new MultiValuedDisplayColumn(MultiValuedLookupColumn.super.getDisplayColumnFactory().createRenderer(colInfo));
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return new SQLFragment(getTableAlias(tableAliasName)).append(".").appendIdentifier(_display.getAlias());
    }

    // Without this the aggregate groups the entire junction table and is only then matched to the outer row, so paging
    // the outer query saves nothing. Correlating it turns the scan into a per-row lookup.
    @Override
    protected boolean isLateralJoin()
    {
        return getSqlDialect().isPostgreSQL();
    }

    @Override
    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable, String alias, String baseAlias)
    {
        strJoin.append(getLookupSql(lookupTable, alias, baseAlias));
    }

    /** @param baseAlias outer table alias for a lateral join to correlate to, or null to aggregate uncorrelated */
    protected SQLFragment getLookupSql(TableInfo lookupTable, String alias, @Nullable String baseAlias)
    {
        SqlDialect dialect = lookupTable.getSqlDialect();
        boolean groupConcat = dialect.supportsGroupConcat();

        // In group_concat case, we always join to child.  In select_concat case, we need to re-join to junction on each
        // column select, so we need a unique alias for the inner join.
        String fromAlias   = AliasManager.makeLegalName("_mvlc_" + alias, dialect);
        String nestedAlias = "_mvlc_select_";

        SQLFragment strJoin = new SQLFragment();
        strJoin.appendComment("<MultiValuedForeignKey target=" + lookupTable.getName() + ">", dialect );
        strJoin.append("\n\t(\n\t\t");
        strJoin.append("SELECT ");
        strJoin.append(_lookupKey.getValueSql(fromAlias));

        String joinAlias = groupConcat ? fromAlias : nestedAlias;

        Map<String, SQLFragment> joins = new LinkedHashMap<>();
        _lookupColumn.declareJoins(joinAlias, joins);

        // declareJoins() recurses leftward first, so the map's first entry is the leftmost join in a chain, not the target table.
        String baseJoinTarget = _lookupColumn instanceof LookupColumn lookupCol
                ? lookupCol.getTableAlias(joinAlias)
                : joins.keySet().iterator().next();
        assert joins.containsKey(baseJoinTarget) : "Join target '" + baseJoinTarget + "' was not declared; found " + joins.keySet();

        // Every aggregate below must see the same row order, or MultiValuedRenderContext can't line the parallel columns
        // up. Ties are only possible between junction rows sharing a target, whose aggregated values are identical, so
        // the junction's target key orders deeply enough. Any joins it needs were declared above, as _lookupColumn's FK.
        // The select_concat path aggregates each column in its own correlated subselect and can't take an ORDER BY here.
        SQLFragment orderBySql = groupConcat ? _junctionKey.getValueSql(joinAlias) : null;

        // Select and aggregate all columns in the far right table for now.  TODO: Select only required columns.
        for (ColumnInfo col : _rightFk.getLookupTableInfo().getColumns())
        {
            // Skip text and ntext and binary (including timestamp) columns -- aggregates don't work on them in some databases
            if (col.isLongTextType() || col.getJdbcType() == BINARY)
                continue;

            ColumnInfo lc = _rightFk.createLookupColumn(_rightFkParent, col.getName());
            if (null == lc)
                continue;
            strJoin.append(", \n\t\t\t");
            SQLFragment valueSql = new SQLFragment();
            boolean needsCast = "entityid".equalsIgnoreCase(lc.getSqlTypeName()) || "lsidtype".equalsIgnoreCase(lc.getSqlTypeName()) || "userid".equalsIgnoreCase(lc.getSqlTypeName());
            if (needsCast)
            {
                valueSql.append("CAST((");
            }
            valueSql.append(lc.getValueSql(joinAlias));
            if (needsCast)
            {
                String sqlType;
                if ("userid".equalsIgnoreCase(lc.getSqlTypeName()))
                    sqlType = "INTEGER";
                else if ("entityid".equalsIgnoreCase(lc.getSqlTypeName()))
                    sqlType = dialect.getGuidType();
                else if ("lsidtype".equalsIgnoreCase(lc.getSqlTypeName()))
                    sqlType = dialect.getLsidType();
                else
                    throw new IllegalStateException("Unexpected sql type '" + lc.getSqlTypeName() + "' for column '" + lc.getName() + "'");
                valueSql.append(") AS ").append(sqlType).append(")");
            }

            col.declareJoins(baseJoinTarget, joins);
            if (groupConcat)
            {
                strJoin.append(getAggregateFunction(valueSql, orderBySql));
            }
            else
            {
                SQLFragment select = new SQLFragment("SELECT ");
                select.append(valueSql);
                select.append(" FROM ");
                select.append(_lookupKey.getParentTable().getFromSQL(nestedAlias));

                for (SQLFragment fragment : joins.values())
                {
                    String join = StringUtils.replace(fragment.getSQL(), "\n\t", "\n\t\t\t\t");
                    join = join.replace("LEFT OUTER", "INNER");
                    select.append(join);
                    select.addAll(fragment.getParams());
                    select.addTempTokens(fragment);
                }

                select.append(" WHERE ");
                select.append(_lookupKey.getValueSql(fromAlias));
                select.append(" = ");
                select.append(_lookupKey.getValueSql(nestedAlias));

                // TODO: Always order by value

                strJoin.append(dialect.getSelectConcat(select, MultiValuedRenderContext.VALUE_DELIMITER));
            }

            strJoin.append(" AS ");
            strJoin.appendIdentifier(lc.getAlias());
        }

        strJoin.append("\n\t\tFROM ");
        strJoin.append(_lookupKey.getParentTable().getFromSQL(fromAlias));

        if (groupConcat)
        {
            for (SQLFragment fragment : joins.values())
            {
//                strJoin.append(StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t"));
                strJoin.append(fragment);
            }
        }

        // Restrict the aggregate to the outer row. The GROUP BY then covers at most one group, and no matching junction
        // rows means no row at all -- which the enclosing LEFT JOIN turns into the nulls the caller already expects.
        if (null != baseAlias && isLateralJoin())
        {
            strJoin.append("\n\t\tWHERE ");
            strJoin.append(getLateralCorrelation(fromAlias, baseAlias));
        }

        strJoin.append("\n\t\tGROUP BY ");
        strJoin.append(_lookupKey.getValueSql(fromAlias));
        strJoin.append("\n\t) ").appendIdentifier(alias);
        strJoin.appendComment("</MultiValuedForeignKey target=" + lookupTable.getName() + ">", dialect );
        return strJoin;
    }
    
    /**
     * The equivalent of {@link #getJoinCondition(String)} for a lateral join, with the junction side resolved against
     * the subquery's own FROM rather than the derived table it produces.
     */
    private SQLFragment getLateralCorrelation(String fromAlias, String baseAlias)
    {
        SQLFragment outerSql = _foreignKey.getValueSql(baseAlias);
        SQLFragment junctionSql = _lookupKey.getValueSql(fromAlias);
        boolean addCast = _foreignKey.getJdbcType() != _lookupKey.getJdbcType() && getSqlDialect().isPostgreSQL();

        SQLFragment condition = new SQLFragment();
        if (addCast)
            condition.append("CAST((").append(outerSql).append(") AS VARCHAR)");
        else
            condition.append(outerSql);
        condition.append(" = ");
        if (addCast)
            condition.append("CAST((").append(junctionSql).append(") AS VARCHAR)");
        else
            condition.append(junctionSql);
        return condition;
    }

    @Override
    // The multivalued column joins take place within the aggregate function sub-select; we don't want super class
    // including these columns as top-level joins.
    protected boolean includeLookupJoins()
    {
        return false;
    }

    // By default, use GROUP_CONCAT aggregate function, which returns a comma-separated list of values.  Override this
    // and (for non-varchar aggregate function) getSqlTypeName() to apply a different aggregate.
    protected SQLFragment getAggregateFunction(SQLFragment sql, @Nullable SQLFragment orderBySql)
    {
        // Sorting by value would order each column independently and break alignment; orderBySql keeps them in step.
        return getSqlDialect().getGroupConcat(sql, false, false, new SQLFragment().appendStringLiteral(MultiValuedRenderContext.VALUE_DELIMITER, getSqlDialect()), true, orderBySql);
    }

    @Override
    public boolean isMultiValued()
    {
        return true;
    }
}
