/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Factory for {@link MVDisplayColumn}s.
 * User: jgarms
 * Date: Jan 8, 2009
 */
public class MVDisplayColumnFactory implements DisplayColumnFactory
{
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        FieldKey qcKey = colInfo.getMvColumnName();
        assert qcKey != null : "Attempt to render MV state for a non-mv column";

        Map<FieldKey,ColumnInfo> map = QueryService.get().getColumns(colInfo.getParentTable(), Collections.singletonList(qcKey));

        ColumnInfo qcColumn = map.get(qcKey);
        if (qcColumn == null) // For a custom query, it's possible the user has excluded our QC column
            return new DataColumn(colInfo);

        return new MVDisplayColumn(colInfo, qcColumn);
    }

    public static List<ColumnInfo> createMvColumns(ColumnInfo valueColumn, PropertyDescriptor pd, ColumnInfo lsidColumn)
    {
        ColumnInfo mvColumn = new MvColumn(pd, lsidColumn);
        ColumnInfo rawValueCol = new RawValueColumn(lsidColumn.getParentTable(), valueColumn);
        valueColumn.setDisplayColumnFactory(new MVDisplayColumnFactory());

        return Arrays.asList(mvColumn, rawValueCol);
    }
    

    private static ColumnInfo[] createMvColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId, Container container, User user)
    {
        String mvColumnName = property.getName() + MvColumn.MV_INDICATOR_SUFFIX;

        //CONSIDER new PropertyColumn constructor
        PropertyColumn mvColumn = new PropertyColumn(property.getPropertyDescriptor(), colObjectId, container, user, false);
        mvColumn.setMvIndicatorColumn(true);
        mvColumn.setName(mvColumnName);
        mvColumn.setAlias(ColumnInfo.legalNameFromName(mvColumnName));
        mvColumn.setLabel(mvColumnName);
        mvColumn.setNullable(true);
        mvColumn.setUserEditable(false);
        mvColumn.setHidden(true);

        mvColumn.setParentIsObjectId(true);
        
        valueColumn.setMvColumnName(mvColumn.getFieldKey());

        ColumnInfo rawValueCol = new RawValueColumn(table, valueColumn);

        valueColumn.setDisplayColumnFactory(new MVDisplayColumnFactory());

        ColumnInfo[] result = new ColumnInfo[2];
        result[0] = mvColumn;
        result[1] = rawValueCol;

        return result;
    }


    public static void addMvColumns(AbstractTableInfo table, ColumnInfo valueColumn, DomainProperty property, ColumnInfo colObjectId, Container container, User user)
    {
        ColumnInfo[] newColumns = createMvColumns(table, valueColumn, property, colObjectId, container, user);
        for (ColumnInfo column : newColumns)
        {
            table.addColumn(column);
        }
    }

}
