/*
 * Copyright (c) 2012-2026 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.GroupedResultSet;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.util.DOM;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.writer.HtmlWriter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.align;
import static org.labkey.api.util.DOM.Attribute.alt;
import static org.labkey.api.util.DOM.Attribute.colspan;
import static org.labkey.api.util.DOM.Attribute.id;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.valign;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.id;
import static org.labkey.api.util.PageFlowUtil.jsString;

public abstract class AbstractNestableDataRegion extends DataRegion
{
    protected boolean _expanded = false;
    private boolean _renderedInnerGrid = false; 
    protected DataRegion _nestedRegion = null;
    protected final String _uniqueColumnName;
    private final ActionURL _ajaxNestedGridURL;
    protected GroupedResultSet _groupedRS = null;
    protected Map<FieldKey, ColumnInfo> _nestedFieldMap;

    protected AbstractNestableDataRegion(String uniqueColumnName, ActionURL url)
    {
        _uniqueColumnName = uniqueColumnName;
        _ajaxNestedGridURL = url;
    }

    @Override
    public void renderTable(RenderContext ctx, HtmlWriter out) throws SQLException
    {
        if (_expanded)
        {
            List<DisplayColumn> displayColumnList = getDisplayColumns();
            displayColumnList.removeIf(col -> col instanceof EmptyDisplayColumn);
            displayColumnList.add(new EmptyDisplayColumn());
        }

        super.renderTable(ctx, out);
        ResultSetUtil.close(_groupedRS);
    }

    @Override
    protected void renderExtraRecordSelectorContent(RenderContext ctx, HtmlWriter out)
    {
        var page = HttpView.currentPageConfig();
        String madeId = page.makeId("a_");
        String value = getUniqueColumnValue(ctx);

        A(
            id(madeId),
            IMG(
                at(
                    id, getName() + "-Handle" + value,
                    valign, "middle",
                    src, ctx.getViewContext().getContextPath() + "/_images/" + (_expanded ? "minus" : "plus") + ".gif",
                    alt, _expanded ? "Collapse row" : "Expand row"
                )
            )
        ).appendTo(out);

        page.addHandler(madeId, "click", "return toggleNestedGrid(" + jsString(getName()) + "," + (_ajaxNestedGridURL == null ? "null" : jsString(_ajaxNestedGridURL + value)) + ", " + jsString(value) + ")");
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

    protected void renderNestedGrid(HtmlWriter out, RenderContext ctx, ResultSet nestedRS, int rowIndex)
    {
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
        String value = getUniqueColumnValue(ctx);
        long colCount = getDisplayColumns().stream()
            .filter(c -> c.isVisible(ctx))
            .count();

        TR(
            cl(isShadeAlternatingRows() && rowIndex % 2 == 0, "labkey-alternate-row")
            .at(!_expanded, style, "display:none;")
            .id(getName() + "-Row" + value),
            TD(),
            TD(
                at(colspan, colCount, align, "left")
                .id(getName() + "-Content" + value),
                (DOM.Renderable) ret -> {
                    // We need to make sure that we've rendered at least one nested grid because it contains JavaScript that needs
                    // to be evaluated with the initial page rendering - we can't send it down later. So, regardless of the
                    // expansion state, always render the nested grid. If we're not expanded, the CSS will still prevent it from
                    // being shown, and the browser will detect that it already has it so it won't make a separate request for it.

                    if (!_renderedInnerGrid)
                    {
                        JspView<String> scriptView = new JspView<>("/org/labkey/api/data/nestedGridScript.jsp", getName());
                        try
                        {
                            scriptView.render(ctx.getRequest(), ctx.getViewContext().getResponse());
                        }
                        catch (Exception e)
                        {
                            throw new RuntimeException(e);
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

                    return ret;
                }
            )
        ).appendTo(out);
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
