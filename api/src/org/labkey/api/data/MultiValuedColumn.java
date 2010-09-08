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
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
public class MultiValuedColumn extends ColumnInfo
{
    private final ColumnInfo _parentPkColumn;
    private final ColumnInfo _childKey;
    private final ForeignKey _fk;
    private final ColumnInfo _junctionKey;

    /** constructor for junction table version of multi-valued column */
    public MultiValuedColumn(String name, ColumnInfo parentPkColumn, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
    {
        super(name, parentPkColumn.getParentTable());
        _parentPkColumn = parentPkColumn;
        _childKey = childKey;
        _fk = fk;
        _junctionKey = junctionKey;
        setIsUnselectable(true);
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
            Set<FieldKey> fieldKeys = new HashSet<FieldKey>();
            _column.addQueryFieldKeys(fieldKeys);
            fieldKeys.add(getColumnInfo().getFieldKey());

            MultiValuedRenderContext mvCtx = new MultiValuedRenderContext(ctx, fieldKeys);
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


    @Override
    public ForeignKey getFk()
    {
        return new MultiValuedForeignKey();
    }


    private class MultiValuedForeignKey extends AbstractForeignKey
    {
        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            ColumnInfo lookupColumn = _fk.createLookupColumn(_junctionKey, displayField);
            ColumnInfo col = new MultiValuedLookupColumn(new FieldKey(parent.getFieldKey(), displayField), _parentPkColumn, _childKey, _fk, lookupColumn);
            return col;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            if (null != _fk)
            {
                return _fk.getLookupTableInfo();
            }
//            else
//            {
//                return _ti;
//            }
            return null;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }
    }


    private class MultiValuedLookupColumn extends LookupColumn
    {
        private ColumnInfo display;
        private ForeignKey rightFk;

        public MultiValuedLookupColumn(FieldKey fieldKey, ColumnInfo parentPkColumn, ColumnInfo childKey, ForeignKey fk, ColumnInfo display)
        {
            super(parentPkColumn, childKey, display);
            this.display = display;
            this.rightFk = fk;
            setFieldKey(fieldKey);
            this.copyURLFrom(display, parentPkColumn.getFieldKey(), null);
        }

        // We don't traverse FKs from a multi-valued column
        @Override
        public ForeignKey getFk()
        {
            return null;
        }

        @Override
        public DisplayColumn getRenderer()
        {
            return new MultiValuedDisplayColumn(super.getRenderer());
        }

        @Override
        public SQLFragment getValueSql(String tableAliasName)
        {
            return new SQLFragment(getTableAlias(tableAliasName) + "." + display.getAlias());
        }

        protected void addLookupSql(SQLFragment strJoin, TableInfo lookupTable)
        {
            String keyTableName = _lookupKey.getParentTable().getSelectName();
            String keyColumnName = _lookupKey.getSelectName();
//            String valueColumnName = _lookupColumn.getSelectName();

            // First, get any lookup column joins so we have the correct alias
            Map<String, SQLFragment> joins = new LinkedHashMap<String, SQLFragment>();
            _lookupColumn.declareJoins("child", joins);

            strJoin.append("\n\t(\n\t\t");
            strJoin.append("SELECT child.");
            strJoin.append(keyColumnName);

            // Select and aggregate all columns in the far right table for now.  TODO: Select only required columns.
            for (ColumnInfo col : rightFk.getLookupTableInfo().getColumns())
            {
                ColumnInfo lc = rightFk.createLookupColumn(_junctionKey, col.getName());
                strJoin.append(", \n\t\t\t");
                strJoin.append(getAggregateFunction(lc.getValueSql("child").toString()));
                strJoin.append(" AS ");
                strJoin.append(lc.getAlias());
            }

            strJoin.append("\n\t\t\tFROM ");
            strJoin.append(keyTableName);
            strJoin.append(" child");

            for (SQLFragment fragment : joins.values())
            {
                strJoin.append(StringUtils.replace(fragment.toString(), "\n\t", "\n\t\t"));
            }

            // TODO: Add ORDER BY?

            strJoin.append("\n\t\tGROUP BY child.");
            strJoin.append(keyColumnName);
            strJoin.append("\n\t)");
        }

        @Override
        // The multivalued column joins take place within the aggregate function sub-select; we don't want super class
        // including these columns as top-level joins.
        protected boolean includeLookupJoins()
        {
            return false;
        }

        @Override
        public String getTableAlias(String baseAlias)
        {
            return AliasManager.makeLegalName(baseAlias + "$" + MultiValuedColumn.this.getName(), getSqlDialect());
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
    }
}
