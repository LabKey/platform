/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.query.ExprColumn;
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
        column.setUserEditable(false);
        column.setShownInInsertView(false);
        column.setShownInUpdateView(false);
        column.setReadOnly(true);
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
        super("EntityId", "DisplayName");
        _url = url;
        setLookupSchemaName("Core");
        setTableName("Containers");
    }

    public TableInfo getLookupTableInfo()
    {
        TableInfo containersTable = new ContainerTable();

        FilteredTable ret = new FilteredTable(containersTable);

        ColumnInfo col = ret.addWrapColumn(containersTable.getColumn("EntityId"));
        col.setHidden(true);
        col.setKeyField(true);

        ret.setPublic(false);
        return ret;
    }
}
