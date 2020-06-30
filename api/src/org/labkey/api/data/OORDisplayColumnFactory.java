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

package org.labkey.api.data;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.AliasedColumn;

/**
 * Factory for creating {@link OutOfRangeDisplayColumn}s. Connects the separate indicator and raw value columns together.
 *
 * User: jeckels
 * Date: Aug 1, 2007
 */
public class OORDisplayColumnFactory implements DisplayColumnFactory
{
    public static final String NUMBER_COLUMN_SUFFIX = "Number";
    public static final String IN_RANGE_COLUMN_SUFFIX = "InRange";
    public static final String OOR_INDICATOR_COLUMN_SUFFIX = "OORIndicator";

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new OutOfRangeDisplayColumn(colInfo);
    }

    /** @return the merged value/indicator OOR ColumnInfo */
    public static MutableColumnInfo addOORColumns(FilteredTable table, ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn)
    {
        return addOORColumns(table, numberColumn, oorIndicatorColumn, numberColumn.getLabel());
    }

    /** @return the merged value/indicator OOR ColumnInfo */
    public static MutableColumnInfo addOORColumns(FilteredTable table, ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn, String caption)
    {
        return addOORColumns(table, numberColumn, oorIndicatorColumn, caption, true);
    }

    /** @return the merged value/indicator OOR ColumnInfo */
    public static MutableColumnInfo addOORColumns(FilteredTable table, ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn, String caption, boolean fromRealTable)
    {
        var combinedCol = fromRealTable ? table.addWrapColumn(numberColumn) : table.addColumn((MutableColumnInfo)numberColumn);
        combinedCol.setLabel(caption);

        var wrappedOORIndicatorCol = fromRealTable ? table.addWrapColumn(oorIndicatorColumn) : table.addColumn((MutableColumnInfo)oorIndicatorColumn);
        wrappedOORIndicatorCol.setLabel(caption + " OOR Indicator");

        // Only add new columns if there is no name conflict with either the real or virtual table
        TableInfo realTable = table.getRealTable();
        if (realTable.getColumn(numberColumn.getName() + NUMBER_COLUMN_SUFFIX) != null ||
                realTable.getColumn(numberColumn.getName() + IN_RANGE_COLUMN_SUFFIX) != null ||
                table.getColumn(numberColumn.getName() + NUMBER_COLUMN_SUFFIX) != null ||
                table.getColumn(numberColumn.getName() + IN_RANGE_COLUMN_SUFFIX) != null )
        {
            return null;
        }

        var wrappedNumberColumn = table.addColumn(new AliasedColumn(table, numberColumn.getName() + NUMBER_COLUMN_SUFFIX, numberColumn));
        wrappedNumberColumn.setPropertyURI(null);
        wrappedNumberColumn.setLabel(caption + " " + NUMBER_COLUMN_SUFFIX);
        wrappedNumberColumn.setShownInInsertView(false);
        wrappedNumberColumn.setShownInUpdateView(false);
        wrappedNumberColumn.setUserEditable(false);

        combinedCol.setDisplayColumnFactory(new OORDisplayColumnFactory());     // Do this after wrappedNumberColumn in case we did addColumn above instead of addWrappedColumn

        SQLFragment inRangeSQL = new SQLFragment("CASE WHEN ");
        SQLFragment valueSql = oorIndicatorColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS);
        inRangeSQL.append(valueSql);
//        inRangeSQL.append(ExprColumn.STR_TABLE_ALIAS);                                      // TODO: getValueSql   (oorIndicatorColumn)
//        inRangeSQL.append(".");
//        inRangeSQL.append(oorIndicatorColumn.getName());
        inRangeSQL.append(" IS NULL THEN ");
        valueSql = numberColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS);
        inRangeSQL.append(valueSql);
//        inRangeSQL.append(ExprColumn.STR_TABLE_ALIAS);
//        inRangeSQL.append(".");
//        inRangeSQL.append(numberColumn.getName());
        inRangeSQL.append(" ELSE NULL END");

        var inRangeColumn = table.addColumn(new ExprColumn(table, numberColumn.getName() + IN_RANGE_COLUMN_SUFFIX,
                    inRangeSQL, numberColumn.getJdbcType(), wrappedNumberColumn, wrappedOORIndicatorCol));
        inRangeColumn.setLabel(caption + " In Range");
        inRangeColumn.setFormat(numberColumn.getFormat());
        inRangeColumn.setShownInInsertView(false);
        inRangeColumn.setShownInUpdateView(false);
        inRangeColumn.setUserEditable(false);

        return combinedCol;
    }
}
