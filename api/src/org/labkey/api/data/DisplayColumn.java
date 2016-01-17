/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.collections.NullPreventingSet;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.visualization.GenericChartReport;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Format;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * A column in a grid, details, insert, update, or similar view. May wrap a column from the underlying database,
 * or may get its value/rendering from elsewhere.
 */
public abstract class DisplayColumn extends RenderColumn
{
    protected String _textAlign = null;
    protected boolean _nowrap = false;
    protected String _width = "60";
    protected String _linkTarget = null;
    protected String _excelFormatString = null;
    protected Format _format = null;
    protected Format _tsvFormat = null;
    protected String _gridHeaderClass = "labkey-col-header-filter";
    private String _inputPrefix = "";
    private String _description = null;
    protected boolean _htmlFiltered = true;
    private String _displayClass;

    // for URL generation
    private String _url;
    private StringExpression _urlExpression;
    private StringExpression _urlCompiled;

    private StringExpression _urlTitle = null;
    private StringExpression _urlTitleCompiled = null;

    public abstract void renderGridCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderTitle(RenderContext ctx, Writer out) throws IOException;

    /** @return whether the underlying column knows how to sort the query that was executed (via SQL ORDER BY) */
    public abstract boolean isSortable();

    /** @return whether the underlying column knows how to filter the query that was executed (via SQL WHERE) */
    public abstract boolean isFilterable();

    public abstract boolean isEditable();

    public abstract void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException;

    public abstract void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException;

    // Do nothing by default
    public void renderGridEnd(RenderContext ctx, Writer out) throws IOException
    {
    }

    public String renderURL(RenderContext ctx)
    {
        StringExpression s = compileExpression(ctx.getViewContext());
        return null == s ? null : s.eval(ctx);
    }

    public String getURL()
    {
        return _url != null ? _url : _urlExpression != null ? _urlExpression.getSource() : null;
    }

    public void setURL(String url)
    {
        _url = url;
        _urlExpression = null;
    }

    public void setURLExpression(StringExpression se)
    {
        if (se == AbstractTableInfo.LINK_DISABLER)
            _urlExpression = null;
        else
            _urlExpression = se;
        _url = null;
    }


    public StringExpression getURLExpression()
    {
        return _urlExpression;
    }

    public boolean includeURL() {
        return _urlExpression != null || _url != null;
    }

    public StringExpression compileExpression(ViewContext context)
    {
        if (null == _urlCompiled)
        {
            if (null != _urlExpression)
                _urlCompiled = _urlExpression.copy();
            else if (null != _url)
                _urlCompiled = StringExpressionFactory.createURL(_url);
            if (_urlCompiled instanceof HasViewContext)
                ((HasViewContext) _urlCompiled).setViewContext(context);
        }
        return _urlCompiled;
    }

    public void setURLTitle(StringExpression urlTitle)
    {
        _urlTitle = urlTitle;
    }

    public StringExpression getURLTitle()
    {
        return _urlTitle;
    }

    public String renderURLTitle(RenderContext ctx)
    {
        if (_urlTitleCompiled == null)
        {
            if (null != _urlTitle)
            {
                _urlTitleCompiled = _urlTitle.copy();
                if (_urlTitleCompiled instanceof HasViewContext)
                    ((HasViewContext) _urlTitleCompiled).setViewContext(ctx.getViewContext());
            }
        }

        if (_urlTitleCompiled == null)
            return null;

        return _urlTitleCompiled.eval(ctx);
    }

    public @NotNull Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    public abstract boolean isQueryColumn();


    /** return a set of FieldKeys that this DisplayColumn depends on */
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        ColumnInfo col = getColumnInfo();
        if (col != null)
            keys.add(col.getFieldKey());

        StringExpression se = null;
        if (null != _urlExpression)
            se = _urlExpression;
        else if (null != _url)
            se = StringExpressionFactory.createURL(_url);

        if (se instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            Set<FieldKey> fields = ((StringExpressionFactory.FieldKeyStringExpression)se).getFieldKeys();
            keys.addAll(fields);
        }

        if (_urlTitle instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            Set<FieldKey> fields = ((StringExpressionFactory.FieldKeyStringExpression) _urlTitle).getFieldKeys();
            keys.addAll(fields);
        }
    }


    /** implement addQueryFieldKeys() instead */
    @Deprecated
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
    }

    public abstract ColumnInfo getColumnInfo();

    public ColumnInfo getDisplayColumnInfo()
    {
        return getColumnInfo();
    }

    public abstract Object getValue(RenderContext ctx);

    public abstract Class getValueClass();

    public Object getJsonValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    public String getName()
    {
        if (null != getColumnInfo())
            return getColumnInfo().getPropertyName();
        else
            return super.getName();
    }

    protected String getInputPrefix()
    {
        return _inputPrefix;
    }

    protected void setInputPrefix(String inputPrefix)
    {
        _inputPrefix = inputPrefix;
    }

    /** If width is null, no width will be requested in the HTML table */
    public void setWidth(@Nullable String width)
    {
        _width = width;
    }

    public String getWidth()
    {
        return _width;
    }


    public void setNoWrap(boolean nowrap)
    {
        _nowrap = nowrap;
    }


    // Ideally, this would just set the string... and defer creation of the Format object until render time, when we would
    // have a Container and other context. That would avoid creating multiple Formats per DisplayColumn.
    public void setFormatString(String formatString)
    {
        super.setFormatString(formatString);
        _format = createFormat(formatString, null);
        if (null == getTsvFormatString() && null == getTsvFormat())
            _tsvFormat = createFormat(formatString, tsvFormatSymbols);
    }


    // java 7 changed to using infinity symbols for formatting, which is challenging for tsv import/export
    // use old school "Infinity" for now
    static DecimalFormatSymbols tsvFormatSymbols = new DecimalFormatSymbols();
    static
    {
        tsvFormatSymbols.setInfinity(String.valueOf(Double.POSITIVE_INFINITY));
        tsvFormatSymbols.setNaN(String.valueOf(Double.NaN));
    }

    public void setTsvFormatString(String formatString)
    {
        if (null == getTsvFormatString() && null == formatString)
            return;
        super.setTsvFormatString(formatString);
        _tsvFormat = createFormat(formatString, tsvFormatSymbols);
    }


    private Format createFormat(String formatString, @Nullable DecimalFormatSymbols dfs)
    {
        if (null != formatString)
        {
            Class valueClass = getDisplayValueClass();

            try
            {
                if (Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
                    return new BooleanFormat(formatString);

                if (valueClass.isPrimitive() || Number.class.isAssignableFrom(valueClass))
                {
                    if (null == dfs)
                        return new DecimalFormat(formatString);
                    else
                        return new DecimalFormat(formatString, dfs);
                }
                else if (Date.class.isAssignableFrom(valueClass))
                {
                    return FastDateFormat.getInstance(formatString);
                }
            }
            catch (IllegalArgumentException ignored)
            {
            }
        }

        return null;
    }


    public Format getFormat()
    {
        return _format;
    }


    public Format getTsvFormat()
    {
        return _tsvFormat;
    }


    /** @return the HTML version of this column's value */
    @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        Format format = getFormat();
        return formatValue(ctx, format);
    }


    @NotNull
    private String formatValue(RenderContext ctx, Format format)
    {
        Object value = getDisplayValue(ctx);

        if (null == value)
            return "";

        if (null != format)
            return format.format(value);
        else if (value instanceof String)
            return (String)value;
        return ConvertUtils.convert(value);
    }


    /** @return the TSV formatted value. Should not include surrounding quotes - the caller will handle this. */
    public String getTsvFormattedValue(RenderContext ctx)
    {
        Format format = getTsvFormat();
        if (format == null)
        {
            format = getFormat();
        }
        return formatValue(ctx, format);
    }

    public Object getExcelCompatibleValue(RenderContext ctx)
    {
        return getDisplayValue(ctx);
    }

    /**
     * Returns the JSON type name for the column's display value,
     * which might be different than its value (e.g., lookup column)
     * @return JSON type name
     */
    public String getDisplayJsonTypeName()
    {
        return getJsonTypeName(getDisplayValueClass());
    }

    /**
     * Returns the JSON type name for the column's value
     * @return JSON type name
     */
    public String getJsonTypeName()
    {
        return getJsonTypeName(getValueClass());
    }

    public static String getJsonTypeName(Class valueClass)
    {
        if(String.class.isAssignableFrom(valueClass))
            return "string";
        else if(Boolean.class.isAssignableFrom(valueClass) || boolean.class.isAssignableFrom(valueClass))
            return "boolean";
        else if(Integer.class.isAssignableFrom(valueClass) || int.class.isAssignableFrom(valueClass))
            return "int";
        else if(Double.class.isAssignableFrom(valueClass) || double.class.isAssignableFrom(valueClass)
                || Float.class.isAssignableFrom(valueClass) || float.class.isAssignableFrom(valueClass))
            return "float";
        else if(Date.class.isAssignableFrom(valueClass))
            return "date";
        else
            return "string";

    }

    public static Class getClassFromJsonTypeName(String typeName)
    {
        if (typeName == null)
            return String.class;

        switch (typeName)
        {
            case "boolean":  return Boolean.class;
            case "int":      return Integer.class;
            case "float":    return Float.class;
            case "date":     return Date.class;
            case "string":
            default:         return String.class;
        }
    }


    /** The value to display. Not HTML or otherwise encoded. */
    public Object getDisplayValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    public Class getDisplayValueClass()
    {
        return getValueClass();
    }

    public void setTextAlign(String textAlign)
    {
        _textAlign = textAlign;
    }

    public String getTextAlign()
    {
        return _textAlign;
    }

    public void setGridHeaderClass(String headerClass)
    {
        _gridHeaderClass = headerClass;
    }

    public void addGridHeaderClass(String headerClass)
    {
        if (_gridHeaderClass == null)
        {
            _gridHeaderClass = headerClass;
        }
        else if (!_gridHeaderClass.contains(headerClass))
        {
            _gridHeaderClass = _gridHeaderClass + " " + headerClass;
        }
    }

    public String getGridHeaderClass()
    {
        return _gridHeaderClass;
    }

    public void renderColTag(Writer out, boolean isLast) throws IOException
    {
        out.write("<col />");
    }

    public String getDefaultHeaderStyle()
    {
        return "";
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out) throws IOException
    {
        renderGridHeaderCell(ctx, out, null);
    }

    private Sort.SortField getSortColumn(Sort sort)
    {
        if (sort != null)
        {
            Set<ColumnInfo> ret = new NullPreventingSet<>(new HashSet<>());
            addQueryColumns(ret);
            for (ColumnInfo info : ret)
            {
                Sort.SortField sortField = sort.getSortColumn(info.getFieldKey());
                if (sortField != null)
                    return sortField;
            }
        }
        return null;
    }

    public void renderGridHeaderCell(RenderContext ctx, Writer out, String headerClass) throws IOException
    {
        Sort sort = getSort(ctx);
        Sort.SortField sortField = getSortColumn(sort);
        boolean filtered = isFiltered(ctx);
        String columnName = (getColumnInfo() != null ? getColumnInfo().getFieldKey() : super.getName()).toString();
        String baseId = ctx.getCurrentRegion().getName() + ":" + columnName;
        baseId = PageFlowUtil.filter(baseId);

        NavTree navtree = getPopupNavTree(ctx, baseId, sort, filtered);
        PopupMenu popup = new PopupMenu(navtree, PopupMenu.Align.LEFT, PopupMenu.ButtonStyle.TEXTBUTTON);
        boolean hasMenu = navtree != null;

        out.write("\n<td class='labkey-column-header ");
        out.write(getGridHeaderClass());
        if (sortField != null)
        {
            if (sortField.getSortDirection() == Sort.SortDirection.ASC)
                out.write(" labkey-sort-asc");
            else
                out.write(" labkey-sort-desc");
        }
        if (filtered)
            out.write(" labkey-filtered");
        if (headerClass != null)
        {
            out.write(" " + headerClass);
        }
        if (_displayClass != null)
        {
            out.write(" " + _displayClass);
        }
        out.write("'");

        out.write(" style='");
        out.write(getDefaultHeaderStyle());
        out.write("'");

        StringBuilder tooltip = new StringBuilder();
        if (null != getDescription())
        {
            tooltip.append(getDescription());
        }
        if (null != getColumnInfo() && !getColumnInfo().getFieldKey().toString().equals(getColumnInfo().getLabel()))
        {
            boolean suffix = tooltip.length() > 0;
            if (suffix)
            {
                tooltip.append(" (");
            }
            tooltip.append(getColumnInfo().getFieldKey().toString());
            if (suffix)
            {
                tooltip.append(")");
            }
        }

        if (tooltip.length() > 0)
        {
            out.write(" title=\"");
            out.write(PageFlowUtil.filter(tooltip.toString()));
            out.write("\"");
        }

        // Issue 11392: id is double html filtered: once for the baseId of the popupmenu and again for rendering into the attribute value.
        String safeHeaderId = "column-header-" + UniqueID.getServerSessionScopedUID();
        if (hasMenu)
        {
            out.write(" id=\"");
            out.write(PageFlowUtil.filter(safeHeaderId));
            out.write("\"");
        }

        out.write(" column-name=\"");
        out.write(PageFlowUtil.filter(ctx.getCurrentRegion().getName() + ":" + columnName));
        out.write("\"");

        out.write(">\n");
        out.write("<div>");

        renderTitle(ctx, out);

        out.write("<img src=\"" + ctx.getRequest().getContextPath() + "/_.gif\" class=\"labkey-grid-filter-icon\"/>");
        out.write("<img src=\"" + ctx.getRequest().getContextPath() + "/_.gif\" class=\"x-grid3-sort-icon\"/>");

        out.write("</div>");

        if (hasMenu)
        {
            popup.renderMenuScript(out);

            out.write("<script type='text/javascript'>\n");
            out.write("Ext4.onReady(function () {\n");
            out.write("var header = Ext4.get(");
            out.write(PageFlowUtil.jsString(safeHeaderId));
            out.write(");\n");
            out.write("if (header) {\n");
            out.write("  header.on('click', function (evt, el, o) {\n");
            out.write("    showMenu(el, ");
            out.write(PageFlowUtil.qh(popup.getSafeID()));
            out.write(", null);\n");
            out.write("  });\n");
            out.write("}\n");
            out.write("});\n");
            out.write("</script>\n");
        }

        out.write("</td>");
    }

    private Sort getSort(RenderContext ctx)
    {
        DataRegion rgn = ctx.getCurrentRegion();
        assert null != rgn;

        if (isSortable() && rgn.isSortable())
        {
            Sort sort = (Sort)ctx.get(rgn.getName() + ".sort");
            if (null == sort)
            {
                sort = ctx.getBaseSort();
                if (sort == null)
                    sort = new Sort();
                ActionURL url = ctx.getViewContext().getActionURL();
                sort.addURLSort(url, rgn.getName());
                ctx.put(rgn.getName() + ".sort", sort);
            }

            return sort;
        }
        return null;
    }

    private boolean isFiltered(RenderContext ctx)
    {
        DataRegion rgn = ctx.getCurrentRegion();
        assert null != rgn;

        if (isFilterable() && rgn.getShowFilters())
        {
            Set<FieldKey> filteredColSet = (Set<FieldKey>) ctx.get(rgn.getName() + ".filteredCols");

            if (null == filteredColSet)
            {
                TableInfo tinfo = rgn.getTable();
                assert null != tinfo;
                ActionURL url = ctx.getSortFilterURLHelper();
                SimpleFilter filter = new SimpleFilter(url, rgn.getName());

                filteredColSet = new HashSet<>();
                for (FieldKey fieldKey : filter.getWhereParamFieldKeys())
                {
                    filteredColSet.add(fieldKey);
                }
                ctx.put(rgn.getName() + ".filteredCols", filteredColSet);
            }

            return (null != this.getColumnInfo() &&
                    (filteredColSet.contains(this.getColumnInfo().getFieldKey())) ||
                        (this.getColumnInfo().getDisplayField() != null &&
                        filteredColSet.contains(this.getColumnInfo().getDisplayField().getFieldKey())));
        }
        return false;
    }

    private NavTree getPopupNavTree(RenderContext ctx, String baseId, Sort sort, boolean filtered) throws IOException
    {
        DataRegion rgn = ctx.getCurrentRegion();
        NavTree navtree = null;
        boolean addSortItems = isSortable() &&  rgn.isSortable();
        boolean addFilterItems = isFilterable() && rgn.getShowFilters();

        if (addSortItems || addFilterItems)
        {
            navtree = new NavTree();
            navtree.setId(baseId + ":menu");

            if (addSortItems)
            {
                Sort.SortField sortField = null;
                boolean primarySort = false;
                boolean isRemoveableSort = false;
                if (sort != null)
                {
                    sortField = getSortColumn(sort);
                    primarySort = sort.indexOf(getColumnInfo().getFieldKey()) == 0;
                    isRemoveableSort = isUserSort(ctx);
                }

                boolean selected = sortField != null && sortField.getSortDirection() == Sort.SortDirection.ASC;
                NavTree asc = new NavTree("Sort Ascending");
                asc.setScript(getSortHandler(ctx, Sort.SortDirection.ASC));
                asc.setSelected(selected);
                asc.setDisabled(primarySort && selected);
                navtree.addChild(asc);

                selected = sortField != null && sortField.getSortDirection() == Sort.SortDirection.DESC;
                NavTree desc = new NavTree("Sort Descending");
                desc.setScript(getSortHandler(ctx, Sort.SortDirection.DESC));
                desc.setSelected(selected);
                desc.setDisabled(primarySort && selected);
                navtree.addChild(desc);

                NavTree clearSort = new NavTree("Clear Sort");
                clearSort.setDisabled(null == sortField || !isRemoveableSort);
                if(null != sortField)
                    clearSort.setScript(getClearSortScript(ctx));
                navtree.addChild(clearSort);
            }

            if (addFilterItems)
            {
                if (navtree.hasChildren())
                    navtree.addSeparator();

                // Give a harmless URL to prevent an Ext event handling problem that causes the page to navigate to Ext's
                // default URL, #. See https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=11925
                NavTree filterItem = new NavTree("Filter...");
                filterItem.setId(baseId + ":filter");//PageFlowUtil.filter(baseId + ":filter"));
                filterItem.setScript(getFilterOnClick(ctx));
                navtree.addChild(filterItem);
                
                NavTree clearFilterItem = new NavTree("Clear Filter");
                clearFilterItem.setId(baseId + ":clear-filter");//PageFlowUtil.filter(baseId + ":clear-filter"));
                clearFilterItem.setDisabled(!filtered);
                clearFilterItem.setScript(getClearFilter(ctx));
                navtree.addChild(clearFilterItem);
            }

            NavTree chartItem = GenericChartReport.getQuickChartItem(rgn.getName(), ctx.getViewContext(), 
                    rgn.getDisplayColumns(), getColumnInfo(), rgn.getSettings());

            if (chartItem != null)
            {
                if (navtree.hasChildren())
                    navtree.addSeparator();

                chartItem.setId(baseId + ":quick-chart");
                navtree.addChild(chartItem);
            }
        }
        return navtree;
    }

    public boolean isUserSort(RenderContext ctx)
    {
        Sort userSort = new Sort(ctx.getViewContext().getActionURL(), ctx.getCurrentRegion().getName());
        return null != getSortColumn(userSort);
    }

    public String getGridDataCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderGridDataCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public final void renderGridDataCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        out.write("<td");
        if (_displayClass != null)
        {
            out.write(" class=' " + _displayClass + "'");
        }
        if (_textAlign != null)
        {
            out.write(" align=");
            out.write(_textAlign);
        }
        String style = getCssStyle(ctx);
        if (!style.isEmpty())
        {
            out.write(" style='");
            out.write(style);
            out.write("'");
        }
        String hoverContent = getHoverContent(ctx);
        if (hoverContent != null)
        {
            StringBuilder showHelpDivArgs = new StringBuilder("this, '" + getHoverTitle(ctx) + "',");
            // The value of the javascript string literal is used to set the innerHTML of an element.  For this reason, if
            // it is text, we escape it to make it HTML.  Then, we have to escape it to turn it into a javascript string.
            // Finally, since this is script inside of an attribute, it must be HTML escaped again.
            showHelpDivArgs.append(PageFlowUtil.filter(PageFlowUtil.jsString(hoverContent)));
            showHelpDivArgs.append(", null, 1000");
            out.append("\" onMouseOut=\"return hideHelpDivDelay();\" onMouseOver=\"return showHelpDivDelay(");
            out.append(showHelpDivArgs).append(");\"");
        }
        out.write(">");
        renderGridCellContents(ctx, out);
        out.write("</td>");
    }

    protected String getHoverContent(RenderContext ctx)
    {
        return null;
    }

    protected String getHoverTitle(RenderContext ctx)
    {
        return null;
    }

    @NotNull /** Always return a non-null string to make it easy to concatenate values */
    public String getCssStyle(RenderContext ctx)
    {
        if (_nowrap)
        {
            return "white-space:nowrap;";
        }
        return "";
    }

    public String getCaption()
    {
        return getCaption(null);
    }

    public String getCaption(RenderContext ctx)
    {
        return getCaption(ctx, true);
    }
    
    public String getCaption(RenderContext ctx, boolean htmlEncode)
    {
        if(!htmlEncode)
            return _caption == null ? getName() : _caption.eval(ctx);
        else
        {
            StringWriter writer = new StringWriter();
            try
            {
                renderTitle(ctx, writer);
            }
            catch (Exception e)
            {
                writer.write(e.getMessage());
            }
            return writer.toString();
        }
    }


    public String getDetailsCaptionCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsCaptionCell(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }


    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='labkey-form-label'>");
        renderTitle(ctx, out);
        out.write("</td>");
    }

    public String getDetailsData(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderDetailsData(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public void renderDetailsData(RenderContext ctx, Writer out, int span) throws IOException
    {
        if (null == _caption)
            out.write("<td colspan=" + (span + 1) + ">");
        else
            out.write("<td colspan=" + span + ">");
        renderDetailsCellContents(ctx, out);
        out.write("</td>");
    }

    public String getInputCell(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderInputCell(ctx, writer, 1);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    /** Get typed value or string value if form type conversion failed. */
    protected Object getInputValue(RenderContext ctx)
    {
        ColumnInfo col = getColumnInfo();
        Object val = null;
        TableViewForm viewForm = ctx.getForm();

        if (col != null)
        {
            if (null != viewForm && viewForm.contains(this, ctx))
            {
                String formFieldName = getFormFieldName(ctx);
                if (viewForm.hasTypedValue(formFieldName))
                    val = viewForm.getTypedValue(formFieldName);
                else
                    val = viewForm.get(formFieldName);
            }
            else if (ctx.getRow() != null)
                val = col.getValue(ctx);
        }

        return val;
    }

    public String getFormFieldName(RenderContext ctx)
    {
        if (ctx.getForm() != null && getColumnInfo() != null)
            return ctx.getForm().getFormFieldName(getColumnInfo());
        return getName();
    }

    protected void outputName(RenderContext ctx, Writer out, String formFieldName) throws IOException
    {
        out.write(" name='");
        out.write(PageFlowUtil.filter(getInputPrefix() + formFieldName));
        out.write("'");

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            out.write(" id='" + PageFlowUtil.filter(setFocusId) + "'");
            ctx.remove("setFocusId");
        }
    }

    public void renderHiddenFormInput(RenderContext ctx, Writer out) throws IOException
    {
        renderHiddenFormInput(ctx, out, getFormFieldName(ctx), getInputValue(ctx));
    }

    protected void renderHiddenFormInput(RenderContext ctx, Writer out, String formFieldName, Object value) throws IOException
    {
        out.write("<input type=hidden");
        outputName(ctx, out, formFieldName);
        out.write(" value=\"");
        if (null != value)
        {
            // it's important to use ConvertUtils here, since 'value' might be a string (if populated via
            // an initial values map), or it might be an array containing a single string (if populated via
            // request.getParameterMap() during an error reshow).  ConvertUtils normalizes these values.
            out.write(PageFlowUtil.filter(ConvertUtils.convert(value)));
        }
        out.write("\">");
    }

    public void renderInputCell(RenderContext ctx, Writer out, int span) throws IOException
    {
        out.write("<td colspan=" + span + ">");
        renderInputHtml(ctx, out, getInputValue(ctx));
        out.write("</td>");
    }

    public String getSortHandler(RenderContext ctx, Sort.SortDirection sort)
    {
        return "";
    }

    public String getFilterOnClick(RenderContext ctx)
    {
        StringWriter writer = new StringWriter();
        try
        {
            renderFilterOnClick(ctx, writer);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public String getClearFilter(RenderContext ctx)
    {
        return "";
    }

    public String getClearSortScript(RenderContext ctx)
    {
        return "";
    }

    public String getInputHtml(RenderContext ctx)
    {
        Object value = getInputValue(ctx);
        StringWriter writer = new StringWriter();
        try
        {
            renderInputHtml(ctx, writer, value);
        }
        catch (Exception e)
        {
            writer.write(e.getMessage());
        }
        return writer.toString();
    }

    public boolean isHtmlFiltered()
    {
        return _htmlFiltered;
    }

    public void setHtmlFiltered(boolean htmlFiltered)
    {
        _htmlFiltered = htmlFiltered;
    }

    public void setLinkTarget(String linkTarget)
    {
        _linkTarget = linkTarget;
    }

    public String getLinkTarget()
    {
        return _linkTarget;
    }

    public String getExcelFormatString()
    {
        return _excelFormatString;
    }

    public void setExcelFormatString(String excelFormatString)
    {
        _excelFormatString = excelFormatString;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String _description)
    {
        this._description = _description;
    }

    public void addDisplayClass(String className)
    {
        if (_displayClass == null)
        {
            _displayClass = className;
        }
        else if (!_displayClass.contains(className))
        {
            _displayClass = _displayClass + " " + className;
        }
    }
}
