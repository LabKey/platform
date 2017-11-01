/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.wiki.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.RemappingDisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/**
 * User: kevink
 * Date: 7/2/15
 *
 * Renders the wiki "Path" column as a set of '/' parts with links to the wiki
 * page for each part. Since '/' is a legal character in wiki names, we split
 * the "PathParts" column value using an unlikely value delimiter and use it
 * to render the value.  This lets us sort and filter on the "Path" value, and
 * correctly split the path into component parts for rendering.
 */
public class PathDisplayColumn extends DataColumn
{
    private static final FieldKey PATH_PARTS = FieldKey.fromParts("PathParts");

    public static class Factory implements RemappingDisplayColumnFactory
    {
        private FieldKey _pathParts = PATH_PARTS;

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new PathDisplayColumn(colInfo, _pathParts);
        }

        @Override
        public void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap)
        {
            _pathParts = FieldKey.remap(_pathParts, parent, remap);
        }
    }

    private boolean _hasPathPartsDisplayCol = false;

    public PathDisplayColumn(ColumnInfo col, FieldKey pathParts)
    {
        super(col);

        ColumnInfo partsCol = col.getParentTable().getColumn(pathParts);
        if (partsCol != null)
        {
            setDisplayColumn(partsCol);
            _hasPathPartsDisplayCol = true;
        }
    }

    private String[] getPathParts(RenderContext ctx)
    {
        Object o = getDisplayValue(ctx);

        if (!(o instanceof String))
            return null;

        String[] parts = ((String)o).split(MultiValuedRenderContext.VALUE_DELIMITER_REGEX);
        if (parts.length == 0)
            return null;

        return parts;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (!_hasPathPartsDisplayCol)
        {
            super.renderGridCellContents(ctx, out);
            return;
        }

        String[] parts = getPathParts(ctx);
        if (parts == null)
        {
            out.write("&nbsp;");
            return;
        }

        StringExpression s = compileExpression(ctx.getViewContext());
        if (s == null)
        {
            out.write("&nbsp;");
            return;
        }

        Map<String, Object> row = ctx.getRow();
        try
        {
            Map<String, Object> newRow = new CaseInsensitiveHashMap<>(row);
            for (int i = 0; i < parts.length; i++)
            {
                if (i > 0)
                    out.write("/");

                String part = parts[i];
                newRow.put("Part", part);

                String url = s.eval(newRow);
                if (url != null)
                {
                    out.write("<a href='");
                    out.write(PageFlowUtil.filter(url));
                    out.write("'>");
                    out.write(PageFlowUtil.filter(part));
                    out.write("</a>");
                }
                else
                {
                    out.write(PageFlowUtil.filter(part));
                }
            }
        }
        finally
        {
            ctx.setRow(row);
        }
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        if (!_hasPathPartsDisplayCol)
            return super.getJsonValue(ctx);

        return getPathParts(ctx);
    }
}
