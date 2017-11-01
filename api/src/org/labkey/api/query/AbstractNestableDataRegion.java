/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;

import java.io.Writer;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Feb 22, 2006
 */
public abstract class AbstractNestableDataRegion extends DataRegion
{
    protected boolean _expanded = false;
    private boolean _renderedInnerGrid = false; 
    protected DataRegion _nestedRegion = null;
    protected final String _uniqueColumnName;
    private final String _ajaxNestedGridURL;
    protected GroupedResultSet _groupedRS = null;
    protected Map<FieldKey, ColumnInfo> _nestedFieldMap;

    protected AbstractNestableDataRegion(String uniqueColumnName, String url)
    {
        _uniqueColumnName = uniqueColumnName;
        _ajaxNestedGridURL = url;
    }

    public void renderTable(RenderContext ctx, Writer out) throws SQLException, IOException
    {
        if (_expanded)
        {
            List<DisplayColumn> displayColumnList = getDisplayColumns();
            displayColumnList.removeIf(col -> col instanceof EmptyDisplayColumn);
            displayColumnList.add(new EmptyDisplayColumn());
        }

        super.renderTable(ctx, out);
        if (_groupedRS != null)
        {
            _groupedRS.close();
        }
    }

    protected void renderExtraRecordSelectorContent(RenderContext ctx, Writer out) throws IOException
    {
        String value = getUniqueColumnValue(ctx);
        out.write("<a onclick=\"return toggleNestedGrid(");
        out.write(PageFlowUtil.jsString(getName()));
        out.write(", ");
        out.write(_ajaxNestedGridURL == null ? "null" : PageFlowUtil.jsString(PageFlowUtil.filter(_ajaxNestedGridURL + value)));
        out.write(", '");
        out.write(value);
        out.write("');\"><img valign=\"middle\" id=\"");
        out.write(getName());
        out.write("-Handle");
        out.write(value);
        out.write("\" src=\"");
        out.write(ctx.getViewContext().getContextPath());
        out.write("/_images/");
        out.write(_expanded ? "minus" : "plus");
        out.write(".gif\"/></a>");
    }

    private String getUniqueColumnValue(RenderContext ctx)
    {
        Object value = ctx.getRow().get(_uniqueColumnName);
        return value == null ? "null" : value.toString();
    }

    public void setGroupedResultSet(GroupedResultSet groupedRS)
    {
        _groupedRS = groupedRS;
    }

    public void setExpanded(boolean expanded)
    {
        _expanded = expanded;
    }

    public void setNestedRegion(DataRegion nestedRegion)
    {
        _nestedRegion = nestedRegion;
        _nestedRegion.setSettings(getSettings());
        _nestedRegion.setShowPagination(false);
    }

    protected void renderNestedGrid(Writer out, RenderContext ctx, ResultSet nestedRS, int rowIndex)
        throws IOException, SQLException
    {
        renderRowStart(rowIndex, out, ctx);

        RenderContext nestedCtx = new RenderContext(ctx.getViewContext());
        if (_nestedFieldMap == null)
        {
            nestedCtx.setResults(new ResultsImpl(nestedRS));
            // Stash this so we don't have to calculate it for every group
            _nestedFieldMap = nestedCtx.getFieldMap();
        }
        else
        {
            nestedCtx.setResults(new ResultsImpl(nestedRS, _nestedFieldMap));
        }
        nestedCtx.setMode(DataRegion.MODE_GRID);

        // We need to make sure that we've rendered at least one nested grid because it contains JavaScript that needs
        // to be evaluated with the initial page rendering - we can't send it down later. So, regardless of the
        // expansion state, always render the nested grid. If we're not expanded, the CSS will still prevent it from
        // being shown, and the browser will detect that it already has it so it won't make a separate request for it.

        if (!_renderedInnerGrid)
        {
            JspView scriptView = new JspView<>("/org/labkey/api/data/nestedGridScript.jsp", getName());
            try
            {
                scriptView.render(ctx.getRequest(), ctx.getViewContext().getResponse());
            }
            catch (Exception e)
            {
                throw new IOException(e);
            }
        }

        if (_expanded || !_renderedInnerGrid)
        {
            _nestedRegion.render(nestedCtx, out);
            _renderedInnerGrid = true;
        }
        else
        {
            try
            {
                while(nestedRS.next());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        renderRowEnd(out);
    }

    private void renderRowEnd(Writer out)
            throws IOException
    {
        out.write("</td></tr>\n");
    }

    private void renderRowStart(int rowIndex, Writer out, RenderContext ctx)
            throws IOException
    {
        out.write("<tr");
        if (isShadeAlternatingRows() && rowIndex % 2 == 0)
        {
            out.write(" class=\"labkey-alternate-row\"");
        }
        if (!_expanded)
        {
            out.write(" style=\"display:none\"");
        }
        out.write(" id=\"");
        out.write(getName());
        out.write("-Row");
        String value = getUniqueColumnValue(ctx);
        out.write(value);
        out.write("\"><td></td><td colspan=\"");
        int colspan = 0;
        for (DisplayColumn dc : getDisplayColumns())
        {
            if (dc.isVisible(ctx))
            {
                colspan++;
            }
        }
        out.write(Integer.toString(colspan));
        out.write("\" align=\"left\" id=\"");
        out.write(getName());
        out.write("-Content");
        out.write(value);
        out.write("\">");
    }

    private static class EmptyDisplayColumn extends SimpleDisplayColumn
    {
        public EmptyDisplayColumn()
        {
            super("");
            setWidth(null);
        }
    }

    @Override
    public boolean getAllowHeaderLock()
    {
        return false;
    }
}
