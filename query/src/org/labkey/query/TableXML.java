/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.query;

import org.labkey.api.query.QueryParseException;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ColumnType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.query.RowIdForeignKey;
import org.labkey.api.query.FieldKey;

import java.util.Collection;
import java.util.Collections;

public class TableXML
{
    static public void initTable(TableType xbTable, TableInfo table, FieldKey key, Collection<ColumnInfo> colInfos)
    {
        if (key != null)
        {
            xbTable.setTableName(key.toString());
            xbTable.setTableTitle(key.getName());
        }
        TableType.Columns columns = xbTable.addNewColumns();
        if (table == null)
            return;
        for (ColumnInfo column : colInfos)
        {
            ColumnType xbColumn = columns.addNewColumn();
            xbColumn.setColumnName(column.getName());
            xbColumn.setColumnTitle(column.getLabel());
            xbColumn.setDatatype(column.getSqlTypeName());
            if (column.isHidden())
            {
                xbColumn.setIsHidden(column.isHidden());
            }
            if (column.isUnselectable())
            {
                xbColumn.setIsUnselectable(column.isUnselectable());
            }
            if (column.getDescription() != null)
            {
                xbColumn.setDescription(column.getDescription());
            }
            ForeignKey fk = column.getFk();
            if (fk instanceof RowIdForeignKey)
            {
                // We only allow drilling down into the rowid column if it was selected into a different table.
                if (((RowIdForeignKey) fk).getOriginalColumn() == column)
                {
                    fk = null;
                }
            }
            if (fk != null)
            {
                try
                {
                    TableInfo lookupTable = fk.getLookupTableInfo();
                    if (lookupTable != null)
                    {
                        ColumnType.Fk xbFk = xbColumn.addNewFk();
                        xbFk.setFkTable(new FieldKey(key, column.getName()).toString());
                    }
                }
                catch (QueryParseException x)
                {
                    // If there is a syntax error in the lookup table, we don't want to prevent
                    // making other changes to this view.
                }
            }
        }
    }

    static public void initTable(TableType xbTable, TableInfo table, FieldKey key)
    {
        initTable(xbTable, table, key, table == null ? Collections.emptyList() : table.getColumns());
    }

}
