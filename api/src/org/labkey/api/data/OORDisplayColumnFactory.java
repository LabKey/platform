/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
 * User: jeckels
 * Date: Aug 1, 2007
 */
public class OORDisplayColumnFactory implements DisplayColumnFactory
{
    public static final String NUMBER_COLUMN_SUFFIX = "Number";
    public static final String IN_RANGE_COLUMN_SUFFIX = "InRange";
    public static final String OORINDICATOR_COLUMN_SUFFIX = "OORIndicator";

    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new OutOfRangeDisplayColumn(colInfo);
    }

    /** @return the merged value/indicator OOR ColumnInfo */
    public static ColumnInfo addOORColumns(FilteredTable table, ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn)
    {
        return addOORColumns(table, numberColumn, oorIndicatorColumn, numberColumn.getLabel());
    }

    /** @return the merged value/indicator OOR ColumnInfo */
    public static ColumnInfo addOORColumns(FilteredTable table, ColumnInfo numberColumn, ColumnInfo oorIndicatorColumn, String caption)
    {
        ColumnInfo combinedCol = table.addWrapColumn(numberColumn);
        combinedCol.setLabel(caption);

        ColumnInfo wrappedOORIndicatorCol = table.addWrapColumn(oorIndicatorColumn);
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

        combinedCol.setDisplayColumnFactory(new OORDisplayColumnFactory());

        ColumnInfo wrappedNumberColumn = table.addColumn(new AliasedColumn(table, numberColumn.getName() + NUMBER_COLUMN_SUFFIX, numberColumn));
        wrappedNumberColumn.setPropertyURI(null);
        wrappedNumberColumn.setLabel(caption + " " + NUMBER_COLUMN_SUFFIX);

        SQLFragment inRangeSQL = new SQLFragment("CASE WHEN ");
        inRangeSQL.append(ExprColumn.STR_TABLE_ALIAS);
        inRangeSQL.append(".");
        inRangeSQL.append(oorIndicatorColumn.getName());
        inRangeSQL.append(" IS NULL THEN ");
        inRangeSQL.append(ExprColumn.STR_TABLE_ALIAS);
        inRangeSQL.append(".");
        inRangeSQL.append(numberColumn.getName());
        inRangeSQL.append(" ELSE NULL END");

        ColumnInfo inRangeColumn = table.addColumn(new ExprColumn(table, numberColumn.getName() + IN_RANGE_COLUMN_SUFFIX,
                    inRangeSQL, numberColumn.getJdbcType(), wrappedNumberColumn, wrappedOORIndicatorCol));
        inRangeColumn.setLabel(caption + " In Range");
        inRangeColumn.setFormat(numberColumn.getFormat());

        return combinedCol;
    }
}
