/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryService;

import java.sql.Types;
import java.util.Collections;
import java.util.Map;

/**
 * User: jgarms
 * Date: Jan 8, 2009
 */
public class MVDisplayColumnFactory implements DisplayColumnFactory
{
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        String mvColumnName = colInfo.getMvColumnName();
        assert mvColumnName != null : "Attempt to render MV state for a non-mv column";

        FieldKey key = FieldKey.fromString(colInfo.getName());
        FieldKey qcKey = new FieldKey(key.getParent(), mvColumnName);

        Map<FieldKey,ColumnInfo> map = QueryService.get().getColumns(colInfo.getParentTable(), Collections.singletonList(qcKey));

        ColumnInfo qcColumn = map.get(qcKey);
        if (qcColumn == null) // For a custom query, it's possible the user has excluded our QC column
            return new DataColumn(colInfo);

        return new MVDisplayColumn(colInfo, qcColumn);
    }

    public static ColumnInfo[] createMvColumns(ColumnInfo valueColumn, PropertyDescriptor pd, TableInfo table, String parentLsidColumn)
    {
        ColumnInfo mvColumn = new MvColumn(pd, table, parentLsidColumn);

        ColumnInfo rawValueCol = new RawValueColumn(table, valueColumn);

        valueColumn.setDisplayColumnFactory(new MVDisplayColumnFactory());

        ColumnInfo[] result = new ColumnInfo[2];
        result[0] = mvColumn;
        result[1] = rawValueCol;

        return result;
    }

    private static ColumnInfo[] createMvColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId)
    {
        String mvColumnName = property.getName() + MvColumn.MV_INDICATOR_SUFFIX;
        ColumnInfo mvColumn = new ExprColumn(table,
                mvColumnName,
                PropertyForeignKey.getValueSql(colObjectId.getValueSql(ExprColumn.STR_TABLE_ALIAS), property.getMvIndicatorSQL(), property.getPropertyId(), false),
                Types.VARCHAR);

        mvColumn.setCaption(mvColumnName);
        mvColumn.setNullable(true);
        mvColumn.setUserEditable(false);
        mvColumn.setIsHidden(true);

        valueColumn.setMvColumnName(mvColumn.getName());

        ColumnInfo rawValueCol = new RawValueColumn(table, valueColumn);

        valueColumn.setDisplayColumnFactory(new MVDisplayColumnFactory());

        ColumnInfo[] result = new ColumnInfo[2];
        result[0] = mvColumn;
        result[1] = rawValueCol;

        return result;
    }

    public static void addMvColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId)
    {
        ColumnInfo[] newColumns = createMvColumns(table, valueColumn, property, colObjectId);
        for (ColumnInfo column : newColumns)
        {
            table.addColumn(column);
        }
    }

}
