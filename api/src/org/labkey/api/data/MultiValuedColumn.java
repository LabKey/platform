package org.labkey.api.data;

import org.apache.commons.lang.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Jul 27, 2010
 * Time: 8:54:04 AM
 */

/*
   Similar to a regular lookup column, but adds a parent-child relationship.  Implications:

   - A single row in the parent table can map to multiple child rows.  Therefore, selecting this column requires
     a GROUP BY and an aggregate function of some kind to return a single value.
   - Rows in the child table are owned exclusively by rows in the parent table.  When inserting to the parent table,
     child table rows are inserted to as well; when deleting rows from the parent table, child table rows are deleted.

 */
public class MultiValuedColumn extends LookupColumn
{
    public MultiValuedColumn(ColumnInfo parentPkColumn, ColumnInfo childKey, ColumnInfo childValue)
    {
        super(parentPkColumn, childKey, childValue);
    }

    @Override
    public String getName()
    {
        return super.getName();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public String getSelectName()
    {
        return super.getSelectName();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public String getAlias()
    {
        return super.getAlias();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        return super.getValueSql(tableAliasName);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable)
    {
        String keyTableName = _lookupKey.getParentTable().getSelectName();
        String keyColumnName = _lookupKey.getSelectName();
        String valueColumnName = _lookupColumn.getSelectName();

        // First, get any lookup column joins so we have the correct alias
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        _lookupColumn.declareJoins("kt", joins);
        String valueColumnAlias = joins.isEmpty() ? valueColumnName : joins.keySet().iterator().next();

        strJoin.append("\n\t(\n\t\t");
        strJoin.append("SELECT kt.");
        strJoin.append(keyColumnName);
        strJoin.append(", ");
        strJoin.append(getAggregateFunction(valueColumnAlias));
        strJoin.append(" AS ");
        strJoin.append(valueColumnName);
        strJoin.append(" FROM ");
        strJoin.append(keyTableName);
        strJoin.append(" kt");

        for (SQLFragment fragment : joins.values())
        {
            strJoin.append(StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t\t"));
        }

        // TODO: Add ORDER BY?

        strJoin.append("\n\t\tGROUP BY kt.");
        strJoin.append(_foreignKey.getSelectName());
        strJoin.append("\n\t)");
    }

    // By default, return common-separated list of values.  Override to apply a different aggregate.
    // TODO: This is PostgreSQL only; need to add SQL Server support (see ViabilityAssaySchema for code)
    protected String getAggregateFunction(String selectName)
    {
//        return "COUNT(" + selectName + ")";
        return "array_to_string(viability.array_accum(" + selectName + "), ',')";
    }

    @Override
    // Any lookup columns are owned by the parent... the joins must take place within the sub-select above. 
    protected boolean includeLookupJoins()
    {
        return false;
    }
}
