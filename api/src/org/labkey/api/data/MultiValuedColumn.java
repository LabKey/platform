/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
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
   - Rows in the child table are owned exclusively by rows in the parent table.  When inserting to the parent table, child
     table rows must be inserted as well; when deleting rows from the parent table, child table rows must be deleted.
 */
public class MultiValuedColumn extends LookupColumn
{
    public MultiValuedColumn(String name, ColumnInfo parentPkColumn, ColumnInfo childKey, ColumnInfo childValue)
    {
        super(parentPkColumn, childKey, childValue);
        setFieldKey(new FieldKey(null, name));
        setAlias(name);
    }

    @Override
    public DisplayColumn getRenderer()
    {
        return new MultiValuedDisplayColumn(super.getRenderer());
    }

    @Override
    public SQLFragment getValueSql(String tableAliasName)
    {
        // TODO: Hack?
        return new SQLFragment(getTableAlias(tableAliasName) + "." + _lookupColumn.getAlias());
    }

    @Override
    protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable)
    {
        String keyTableName = _lookupKey.getParentTable().getSelectName();
        String keyColumnName = _lookupKey.getSelectName();
        String valueColumnName = _lookupColumn.getSelectName();

        // First, get any lookup column joins so we have the correct alias
        Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
        _lookupColumn.declareJoins("child", joins);

         // TODO: Hack?
        String valueColumnAlias = joins.isEmpty() ? valueColumnName : joins.keySet().iterator().next() + "." + _lookupColumn.getFieldKey().getName();
        String keyColumnAlias = joins.isEmpty() ? keyColumnName : joins.keySet().iterator().next() + "." + _lookupKey.getFieldKey().getName();

        strJoin.append("\n\t(\n\t\t");
        strJoin.append("SELECT child.");
        strJoin.append(keyColumnName);
        strJoin.append(", ");
        strJoin.append(getAggregateFunction(valueColumnAlias));
        strJoin.append(" AS ");
        strJoin.append(valueColumnName);
        strJoin.append(" FROM ");
        strJoin.append(keyTableName);
        strJoin.append(" child");

        for (SQLFragment fragment : joins.values())
        {
            strJoin.append(StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t\t"));
        }

        // TODO: Add ORDER BY?

        strJoin.append("\n\t\tGROUP BY child.");
        strJoin.append(keyColumnName);
        strJoin.append("\n\t)");
    }

    // By default, use GROUP_CONCAT aggregate function, which returns a common-separated list of values.  Override this
    // and (for non-varchar aggregate function) getSqlTypeName() to apply a different aggregate.
    protected String getAggregateFunction(String selectName)
    {
        return getSqlDialect().getGroupConcatAggregateFunction(selectName);
    }

    @Override  // Must match the type of the aggregate function specified above.
    public String getSqlTypeName()
    {
        return "varchar";
    }

    @Override
    // The multivalued column joins take place within the aggregate function sub-select; we don't want super class
    // including these columns as top-level joins.
    protected boolean includeLookupJoins()
    {
        return false;
    }


    // Wraps any DisplayColumn and causes it to render each value separately
    private static class MultiValuedDisplayColumn extends DisplayColumnDecorator
    {
        public MultiValuedDisplayColumn(DisplayColumn dc)
        {
            super(dc);
        }

        @Override         // TODO: Need similar for renderDetailsCellContents()
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, getColumnInfo().getFieldKey());
            String sep = "";
            
            while (mvCtx.next())
            {
                out.append(sep);
                super.renderGridCellContents(mvCtx, out);
                sep = ", ";
            }

            // TODO: Call super in empty values case?
        }
    }
}
