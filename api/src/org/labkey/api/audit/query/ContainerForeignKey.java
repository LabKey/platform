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

package org.labkey.api.audit.query;

import org.labkey.api.data.*;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

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
                return new ContainerDisplayColumn(colInfo);
            }
        });
        return column;
    }

    public ContainerForeignKey()
    {
        super("EntityId", "Name");
    }

    public TableInfo getLookupTableInfo()
    {
        TableInfo tinfoUsersData = CoreSchema.getInstance().getTableInfoContainers();
        FilteredTable ret = new FilteredTable(tinfoUsersData);
        ret.addWrapColumn(tinfoUsersData.getColumn("EntityId"));
        ret.addColumn(ret.wrapColumn("Name", tinfoUsersData.getColumn("Name")));
        ret.setTitleColumn("Name");
        return ret;
    }

    private static class ContainerDisplayColumn extends DataColumn
    {
        public ContainerDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String id = (String)getBoundColumn().getValue(ctx);
            Container c = ContainerManager.getForId(id);
            if (c != null)
            {
                out.write("<a href=\"" + c.getStartURL(ctx.getViewContext()).getLocalURIString() + "\">");
/*
                if (c.isRoot())
                    out.write("root");
                else
*/
                    out.write(PageFlowUtil.filter(c.getName()));
                out.write("</a>");
                return;
            }
            out.write("&nbsp;");
        }
    }
}
