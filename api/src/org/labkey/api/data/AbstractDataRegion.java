/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 23, 2011
 * Time: 7:20:16 PM
 */
public abstract class AbstractDataRegion extends DisplayElement
{
    private static final Logger _log = Logger.getLogger(DataRegion.class);
    private String _name = null;
    private QuerySettings _settings = null;

    public enum PaginationLocation
    {
        TOP,
        BOTTOM,
    }

    public String getName()
    {
        if (null == _name)
        {
            if (null != getSettings() && null != getSettings().getDataRegionName())
                _name = getSettings().getDataRegionName();
            else if (getTable() != null)
                _name = getTable().getName();
        }
        return _name;
    }

    public TableInfo getTable()
    {
        return null;
    }

    /**
     * Use {@link DataRegion#setSettings(QuerySettings)} to set the name instead.
     */
    @Deprecated
    public void setName(String name)
    {
        _name = name;
    }

    public void setSettings(QuerySettings settings)
    {
        _settings = settings;
    }

    public QuerySettings getSettings()
    {
        return _settings;
    }

    protected final void getHeaderScriptStart(StringBuilder sb, String headerMessage)
    {
        sb.append("<script type=\"text/javascript\">\n");
        sb.append("Ext.onReady(\n");
        sb.append("function () {\n");
        sb.append("new LABKEY.DataRegion({\n");
        sb.append("'name' : " + PageFlowUtil.jsString(getName()) + "\n");

        if (getSettings() != null)
        {
            sb.append(",'schemaName' : " + PageFlowUtil.jsString(getSettings().getSchemaName()) + "\n");
            sb.append(",'queryName' : " + PageFlowUtil.jsString(getSettings().getQueryName()) + "\n");
            sb.append(",'viewName' : " + PageFlowUtil.jsString(getSettings().getViewName()) + "\n");
        }
    }

    protected final void getHeaderScriptEnd(StringBuilder sb, String headerMessage)
    {
        sb.append("});\n");
        if (headerMessage != null && headerMessage.length() > 0)
        {
            sb.append("LABKEY.DataRegions[" + PageFlowUtil.jsString(getName()) + "].showMessage(" +
                    PageFlowUtil.jsString(headerMessage) + ");\n");
        }

        sb.append("});\n");
        sb.append("</script>\n");
    }

    protected void renderHeaderScript(RenderContext ctx, Writer out, String headerMessage) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        getHeaderScriptStart(sb, headerMessage);
        getHeaderScriptEnd(sb, headerMessage);

        out.write(sb.toString());
    }

    private SimpleFilter getValidFilter(RenderContext ctx)
    {
        SimpleFilter urlFilter = new SimpleFilter(ctx.getViewContext().getActionURL(), getName());
        for (FieldKey fk : ctx.getIgnoredFilterColumns())
            urlFilter.deleteConditions(fk.toString());
        if (urlFilter.getClauses().isEmpty())
            return null;
        return urlFilter;
    }

    private static final String[] HIDDEN_FILTER_COLUMN_SUFFIXES = { "RowId", "DisplayName", "Description", "Label", "Caption", "Value" };
    protected String getFilterDescription(RenderContext ctx) throws IOException
    {
        SimpleFilter urlFilter = getValidFilter(ctx);
        if (urlFilter != null && !urlFilter.getWhereParamNames().isEmpty())
        {
            StringBuilder filterDesc = new StringBuilder();
            filterDesc.append(urlFilter.getFilterText(new SimpleFilter.ColumnNameFormatter()
            {
                @Override
                public String format(String columnName)
                {
                    String formatted = super.format(columnName);
                    for (String hiddenFilter : HIDDEN_FILTER_COLUMN_SUFFIXES)
                    {
                        if (formatted.toLowerCase().endsWith("/" + hiddenFilter.toLowerCase()) ||
                            formatted.toLowerCase().endsWith("." + hiddenFilter.toLowerCase()))
                        {
                            formatted = formatted.substring(0, formatted.length() - (hiddenFilter.length() + 1));
                        }
                    }
                    int dotIndex = formatted.lastIndexOf('.');
                    if (dotIndex >= 0)
                        formatted = formatted.substring(dotIndex + 1);
                    int slashIndex = formatted.lastIndexOf('/');
                    if (slashIndex >= 0)
                        formatted = formatted.substring(slashIndex);
                    return formatted;
                }
            }));

            return filterDesc.toString();
        }

        return null;
    }

    protected String getFilterErrorMessage(RenderContext ctx) throws IOException
    {
        StringBuilder buf = new StringBuilder();
        Set<FieldKey> ignoredColumns = ctx.getIgnoredFilterColumns();
        if (!ignoredColumns.isEmpty())
        {
            if (ignoredColumns.size() == 1)
            {
                FieldKey field = ignoredColumns.iterator().next();
                buf.append("Ignoring filter/sort on column '").append(field.getDisplayString()).append("' because it does not exist.");
            }
            else
            {
                String comma = "";
                buf.append("Ignoring filter/sort on columns ");
                for (FieldKey field : ignoredColumns)
                {
                    buf.append(comma);
                    comma = ", ";
                    buf.append("'");
                    buf.append(field.getDisplayString());
                    buf.append("'");
                }
                buf.append(" because they do not exist.");
            }
        }
        return buf.toString();
    }

    protected void addViewMessage(StringBuilder headerMessage, RenderContext ctx) throws IOException
    {
        headerMessage.append("<span class='labkey-strong'>View:</span>&nbsp;");
        headerMessage.append("<span style='padding:5px 45px 5px 0;'>");
        if (isDefaultView(ctx))
            headerMessage.append("default");
        else
            headerMessage.append(ctx.getView().getName());

        headerMessage.append("</span>&nbsp;");
    }

    protected boolean isDefaultView(RenderContext ctx)
    {
        return (ctx.getView() == null || StringUtils.isEmpty(ctx.getView().getName()));
    }

    /**
     * Adds any filter error messages and optionally the filter description that is applied to the current context.
     * @param headerMessage The StringBuilder to append messages to
     * @param showFilterDescription Specifies whether the filter description should be added
     */
    protected void addFilterMessage(StringBuilder headerMessage, RenderContext ctx, boolean showFilterDescription) throws IOException
    {
        String filterErrorMsg = getFilterErrorMessage(ctx);
        String filterDescription = showFilterDescription ? getFilterDescription(ctx) : null;
        if (filterErrorMsg != null && filterErrorMsg.length() > 0)
            headerMessage.append("<span class=\"labkey-error\">").append(PageFlowUtil.filter(filterErrorMsg)).append("</span>");

        if (filterDescription != null)
        {
            headerMessage.append("<span class='labkey-strong'>Filter:</span>&nbsp;");
            headerMessage.append(PageFlowUtil.filter(filterDescription)).append("&nbsp;&nbsp;");
            headerMessage.append(PageFlowUtil.generateButtonHtml("Clear", "#", "LABKEY.DataRegions['" +
                    PageFlowUtil.filter(getName()) + "'].clearAllFilters(); return false;", null));
        }
    }

    protected abstract boolean shouldRenderHeader(boolean renderButtons);
    protected abstract void renderButtons(RenderContext ctx, Writer out) throws IOException;
    protected abstract void renderPagination(RenderContext ctx, Writer out, PaginationLocation location) throws IOException;

    protected void renderHeader(RenderContext ctx, Writer out, boolean renderButtons, int colCount) throws IOException
    {
        out.write("\n<tr");
        if (!shouldRenderHeader(renderButtons))
            out.write(" style=\"display:none\"");
        out.write(">");

        out.write("<td colspan=\"");
        out.write(String.valueOf(colCount));
        out.write("\" class=\"labkey-data-region-header-container\">\n");

        out.write("<table class=\"labkey-data-region-header\" id=\"" + PageFlowUtil.filter("dataregion_header_" + getName()) + "\">\n");
        out.write("<tr><td nowrap>\n");
        if (renderButtons)
        {
            renderButtons(ctx, out);
        }
        out.write("</td>");

        out.write("<td align=\"right\" valign=\"top\" nowrap>\n");
        renderPagination(ctx, out, PaginationLocation.TOP);
        out.write("</td></tr>\n");

        renderRibbon(ctx, out);
        renderMessageBox(ctx, out, colCount);

        // end table.labkey-data-region-header
        out.write("</table>\n");

        out.write("\n</td></tr>");
    }

    protected void renderRibbon(RenderContext ctx, Writer out) throws IOException
    {
        out.write("<tr>");
        out.write("<td colspan=\"2\" class=\"labkey-ribbon extContainer\" style=\"display:none;\"></td>");
        out.write("</tr>\n");
    }

    protected void renderMessageBox(RenderContext ctx, Writer out, int colCount) throws IOException
    {
        out.write("<tr id=\"" + PageFlowUtil.filter("dataregion_msgbox_" + getName()) + "\" style=\"display:none\">");
        out.write("<td colspan=\"");
        out.write("2");
        out.write("\" class=\"labkey-dataregion-msgbox\">");
        out.write("<span class=\"labkey-tool labkey-tool-close\" style=\"float:right;\" onclick=\"LABKEY.DataRegions[" + PageFlowUtil.filterQuote(getName()) + "].hideMessage();\" title=\"Close this message\" alt=\"close\"></span>");
        out.write("<div></div>");
        out.write("</td>");
        out.write("</tr>\n");
    }
}
