/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.Writer;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 27, 2006
 * Time: 10:21:24 AM
 */
public class ContainerDisplayColumn extends DataColumn
{
    private Container _c;
    private ActionURL _url;

    public ContainerDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    public ContainerDisplayColumn(ColumnInfo column, ActionURL actionURL)
    {
        this(column);
        _url = actionURL.clone();
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // Get the container for this row; stash the path in the context so urls can use it
        String id = (String)ctx.get(getColumnInfo().getAlias());
        _c = ContainerManager.getForId(id);

        // Don't render link if container is deleted
        if (null == _c)
        {
            out.write(getFormattedValue(ctx));
        }
        else
        {
            ctx.put("ContainerPath", _c.getPath());  // TODO: Encoded path?
            super.renderGridCellContents(ctx, out);
        }
    }

    public String getFormattedValue(RenderContext ctx)
    {
        StringBuilder sb = new StringBuilder();
        if (_url != null)
        {
            _url.setExtraPath(_c.getPath());
            sb.append("<a href=\"");
            sb.append(_url.getLocalURIString());
            sb.append("\">");
        }
        sb.append(PageFlowUtil.filter(null == _c ? "<deleted>" : _c.getPath()));
        if (_url != null)
        {
            sb.append("</a>");
        }
        return sb.toString();
    }

    public boolean isFilterable()
    {
        return false;
    }
}
