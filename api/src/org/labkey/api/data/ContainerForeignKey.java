/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import org.labkey.api.query.LookupForeignKey;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 2, 2007
 */
public class ContainerForeignKey extends LookupForeignKey
{
    static public ColumnInfo initColumn(ColumnInfo column)
    {
        column.setFk(new ContainerForeignKey());
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ContainerDisplayColumn displayColumn = new ContainerDisplayColumn(colInfo, false);
                displayColumn.setEntityIdColumn(colInfo);
                return displayColumn;
            }
        });
        return column;
    }

    public ContainerForeignKey()
    {
        super("EntityId", "Name");
        setLookupSchemaName("Core");
    }

    public TableInfo getLookupTableInfo()
    {
        TableInfo containersTable = CoreSchema.getInstance().getTableInfoContainers();
        FilteredTable ret = new FilteredTable(containersTable);
        ColumnInfo nameColumn = ret.addWrapColumn(containersTable.getColumn("Name"));
        nameColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false);
            }
        });

        ColumnInfo pathColumn = ret.wrapColumn("Path", containersTable.getColumn("Name"));
        pathColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, true);
            }
        });
        ret.addColumn(pathColumn);
        ret.addWrapColumn(containersTable.getColumn("EntityId")).setIsHidden(true);
        ret.addWrapColumn(containersTable.getColumn("RowId")).setIsHidden(true);
        ret.setTitleColumn("Name");
        return ret;
    }
}
