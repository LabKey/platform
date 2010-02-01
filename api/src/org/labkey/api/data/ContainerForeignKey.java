/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.view.ActionURL;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 2, 2007
 */
public class ContainerForeignKey extends LookupForeignKey
{
    private ActionURL _url;

    static public ColumnInfo initColumn(ColumnInfo column)
    {
        return initColumn(column, null);
    }

    static public ColumnInfo initColumn(ColumnInfo column, final ActionURL url)
    {
        column.setFk(new ContainerForeignKey(url));
        column.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ContainerDisplayColumn displayColumn = new ContainerDisplayColumn(colInfo, false, url);
                displayColumn.setEntityIdColumn(colInfo);
                return displayColumn;
            }
        });
        return column;
    }

    public ContainerForeignKey()
    {
        this(null);
    }

    public ContainerForeignKey(ActionURL url)
    {
        super("EntityId", "Name");
        _url = url;
        setLookupSchemaName("Core");
        setTableName("Containers");
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
                return new ContainerDisplayColumn(colInfo, false, _url);
            }
        });

        ColumnInfo pathColumn = ret.wrapColumn("Path", containersTable.getColumn("Name"));
        pathColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, true, _url);
            }
        });
        ret.addColumn(pathColumn);
        ret.addWrapColumn(containersTable.getColumn("EntityId")).setHidden(true);
        ret.addWrapColumn(containersTable.getColumn("RowId")).setHidden(true);
        ret.addWrapColumn(containersTable.getColumn("Workbook"));
        ret.addWrapColumn(containersTable.getColumn("Description"));
        ret.setTitleColumn("Name");
        ret.setPublic(false);
        return ret;
    }
}
