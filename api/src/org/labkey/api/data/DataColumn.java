/*
 * Copyright (c) 2004-2009 Fred Hutchinson Cancer Research Center
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
import org.apache.log4j.Logger;
import org.labkey.api.util.NamedObject;
import org.labkey.api.util.NamedObjectList;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Set;

public class DataColumn extends DisplayColumn
{
    private ColumnInfo _boundColumn;
    private ColumnInfo _displayColumn;
    private ColumnInfo _sortColumn;
    private Sort.SortDirection _defaultSort = Sort.SortDirection.ASC;
    private ColumnInfo _filterColumn;

    private StringExpressionFactory.StringExpression _url;
    private String _inputType;
    private int _inputRows;
    private int _inputLength;
    private boolean _preserveNewlines;
    private boolean _editable = true;

    private static Logger _log = Logger.getLogger(DataColumn.class);


    //Careful, a renderer without a resultset is only good for input forms
    public DataColumn(ColumnInfo col)
    {
        _boundColumn = col;
        _displayColumn = col.getDisplayField();
        if (_displayColumn == null)
        {
            _displayColumn = _boundColumn;
        }
        _nowrap = _displayColumn.isNoWrap();
        _sortColumn = _displayColumn.getSortField();
        _defaultSort = _displayColumn.getSortDirection();
        _filterColumn = _displayColumn.getFilterField();

        _width = _displayColumn.getWidth();
        _url = _boundColumn.getURL();
        setFormatString(_displayColumn.getFormatString());
        setTsvFormatString(_displayColumn.getTsvFormatString());
        setExcelFormatString(_displayColumn.getExcelFormatString());
        setDescription(_boundColumn.getDescription());
        _inputType = _boundColumn.getInputType();
        _inputRows = _boundColumn.getInputRows();
        _inputLength = _boundColumn.getInputLength();
        _caption = StringExpressionFactory.create(_boundColumn.getCaption());
        _editable = !_boundColumn.isReadOnly() && _boundColumn.isUserEditable();
        _textAlign = _displayColumn.getTextAlign();
    }


    public String getURL(RenderContext ctx)
    {
        if (null == _url)
            return null;

        return _url.eval(ctx);
    }

    public String getURL()
    {
        if (null == _url)
            return null;

        return _url.getSource();
    }

    public void setURL(String url)
    {
        _url = url == null ? null : StringExpressionFactory.create(url, true);
    }

    public void setURLExpression(StringExpressionFactory.StringExpression se)
    {
        _url = se;
    }

    public int getInputRows()
    {
        return _inputRows;
    }

    public void setInputRows(int inputRows)
    {
        _inputRows = inputRows;
    }

    public int getInputLength()
    {
        return _inputLength;
    }

    public void setInputLength(int inputLength)
    {
        _inputLength = inputLength;
    }

    public boolean isPreserveNewlines()
    {
        return _preserveNewlines;
    }

    public void setPreserveNewlines(boolean preserveNewlines)
    {
        _preserveNewlines = preserveNewlines;
    }

    public ColumnInfo getColumnInfo()
    {
        return _boundColumn;
    }

    public boolean isFilterable()
    {
        return _filterColumn != null;
    }

    public boolean isQueryColumn()
    {
        return true;
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        if (_boundColumn != null)
            columns.add(_boundColumn);
        if (_displayColumn != null)
            columns.add(_displayColumn);
        if (_filterColumn != null)
            columns.add(_filterColumn);
        if (_sortColumn != null)
            columns.add(_sortColumn);
    }

    public boolean isSortable()
    {
        return _sortColumn != null;
    }

    public Sort.SortDirection getDefaultSortDirection()
    {
        return _defaultSort;
    }

    public void setDefaultSortDirection(Sort.SortDirection dir)
    {
        _defaultSort =  dir;
    }

    public Object getValue(RenderContext ctx)
    {
        return _boundColumn.getValue(ctx);
    }

    public Object getDisplayValue(RenderContext ctx)
    {
        return _displayColumn.getValue(ctx);
    }

    public Class getValueClass()
    {
        return _boundColumn.getJavaClass();
    }

    public Class getDisplayValueClass()
    {
        return _displayColumn.getJavaClass();
    }

    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = _boundColumn.getValue(ctx);
        if (null != value)
        {
            String url = getURL(ctx);

            if (null != url)
            {
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(url));

                String linkTarget = getLinkTarget();

                if (null != linkTarget)
                {
                    out.write("\" target=\"");
                    out.write(linkTarget);
                }

                out.write("\">");
            }

            out.write(getFormattedValue(ctx));

            if (null != url)
            {
                out.write("</a>");
            }
        }
    }

    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        if (_filterColumn == null)
            return;
        out.write("showFilterPanel(this, '");
        out.write(h(ctx.getCurrentRegion().getName()));
        out.write("','");
        out.write(h(_filterColumn.getName()));
        out.write("','");
        StringWriter strCaption = new StringWriter();
        _caption.render(strCaption, ctx);
        out.write(h(strCaption.toString()));
        out.write("', '");
        out.write(_filterColumn.getSqlDataTypeName());
        out.write("', ");
        out.write(Boolean.toString(_filterColumn.isQcEnabled()));
        out.write(")");
    }

    public String getClearFilter(RenderContext ctx)
    {
        if (_filterColumn == null)
            return "";
        return "LABKEY.DataRegions['" + h(ctx.getCurrentRegion().getName()) + "']" +
                ".clearFilter('" + h(_filterColumn.getName()) + "')";
    }

    @Override
    public String getClearSortScript(RenderContext ctx)
    {
        return "clearSort('" + h(ctx.getCurrentRegion().getName()) + "', '" + h(_sortColumn.getName()) + "');";
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            String url = getURL(ctx);

            if (null != url)
            {
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(url));

                String linkTarget = getLinkTarget();

                if (null != linkTarget)
                {
                    out.write("\" target=\"");
                    out.write(linkTarget);
                }

                out.write("\">");
            }

            out.write(getFormattedValue(ctx));

            if (null != url)
                out.write("</a>");
        }
        else
            out.write("&nbsp;");
    }

    public String getFormattedValue(RenderContext ctx)
    {
        Object value = _displayColumn.getValue(ctx);
        if (value == null)
        {
            if (_displayColumn != _boundColumn)
            {
                return PageFlowUtil.filter("<" + _boundColumn.getValue(ctx) + ">");
            }
            return "";
        }
        String formatted;
        if (null != _format)
            formatted = _format.format(value);
        else if (_htmlFiltered)
            formatted = PageFlowUtil.filter(ConvertUtils.convert(value));
        else
            formatted = ConvertUtils.convert(value);

        if (formatted.length() == 0)
            formatted = "&nbsp;";
        else if (_preserveNewlines)
            formatted = formatted.replaceAll("\\n", "<br>\n");
        return formatted;
    }

    private void renderHiddenFormInput(RenderContext ctx, Writer out, String formFieldName, Object value) throws IOException
    {
        out.write("<input type=hidden");
        outputName(ctx, out, formFieldName);
        out.write(" value=\"");
        if (null != value)
            out.write(PageFlowUtil.filter(value.toString()));
        out.write("\">");
    }

    protected boolean isDisabledInput()
    {
        return _boundColumn.getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE;
    }

    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        boolean disabledInput = isDisabledInput();
        String formFieldName = ctx.getForm().getFormFieldName(_boundColumn);
        if (_boundColumn.isVersionColumn())
        {
            //should be in hidden field.
        }
        else if (_boundColumn.isAutoIncrement())
        {
            renderHiddenFormInput(ctx, out, formFieldName, value);
            if (null != value)
            {
                if (null != _format)
                    try
                    {
                        out.write(_format.format(value));
                    }
                    catch (IllegalArgumentException x)
                    {
                        out.write(ConvertUtils.convert(value));
                    }
                else
                    out.write(PageFlowUtil.filter(ConvertUtils.convert(value)));
            }
        }
        else if (_inputType.equalsIgnoreCase("select"))
        {
            NamedObjectList entryList = _boundColumn.getFkTableInfo().getSelectList();
            NamedObject[] entries = entryList.toArray();

            out.write("<select");
            outputName(ctx, out, formFieldName);
            if (disabledInput)
                out.write(" DISABLED");
            out.write(">\n");
            out.write("<option value=\"\"></option>");
            for (NamedObject entry : entries)
            {
                String entryName = entry.getName();
                out.write("  <option value=\"");
                out.write(entryName);
                out.write("\"");
                if (null != value && entryName.equals(value.toString()))
                    out.write(" selected ");
                out.write(" >");
                if (null != entry.getObject())
                    out.write(entry.getObject().toString());
                out.write("</option>\n");
            }
            out.write("</select>");
            // disabled inputs are not posted with the form, so we output a hidden form element:
            if (disabledInput)
                renderHiddenFormInput(ctx, out, formFieldName, value);
        }
        else if (_inputType.equalsIgnoreCase("textarea"))
        {
            out.write("<textarea cols='");
            out.write(String.valueOf(_inputLength));
            out.write("' rows ='");
            out.write(String.valueOf(_inputRows));
            out.write("'");
            outputName(ctx, out, formFieldName);
            if (disabledInput)
                out.write(" DISABLED");
            out.write(">");
            out.write(null == value ? "" : PageFlowUtil.filter(value.toString()));
            out.write("</textarea>\n");
            // disabled inputs are not posted with the form, so we output a hidden form element:
            if (disabledInput)
                renderHiddenFormInput(ctx, out, formFieldName, value);
        }
        else if (_inputType.equalsIgnoreCase("file"))
        {
            out.write("<input");
            outputName(ctx, out, formFieldName);
            if (disabledInput)
                out.write(" DISABLED");
            out.write(" type='file'>\n");
        }
        else if (_inputType.equalsIgnoreCase("checkbox"))
        {
            boolean checked = ColumnInfo.booleanFromObj(value);
            out.write("<input type='checkbox'");
            if (checked)
                out.write(" CHECKED");
            if (disabledInput)
                out.write(" DISABLED");
            outputName(ctx, out, formFieldName);
            out.write(" value='1'>");
            /*
            * Checkboxes are weird. If set to FALSE they don't post
            * at all. So impossible to tell difference between values
            * that weren't on the html form at all and ones that were set to false
            * by the user.
            * To fix this each checkbox posts its name in a hidden field
            */
            out.write("<input type='hidden' name='~checkboxes' value=\"");
            out.write(formFieldName);
            out.write("\">");
            // disabled inputs are not posted with the form, so we output a hidden form element:
            if (disabledInput)
                renderHiddenFormInput(ctx, out, formFieldName, value);
        }
        else if (_inputType.equalsIgnoreCase("none"))
            ; //do nothing. Used 
        else
        {
            out.write("<input type='text' size='");
            out.write(Integer.toString(_inputLength));
            out.write("'");
            outputName(ctx, out, formFieldName);
            if (disabledInput)
                out.write(" DISABLED");
            out.write(" value=\"");
            String strVal = "";
            //UNDONE: Should use output format here.
            if (null != value)
            {
                if (null != _format)
                    try
                    {
                        strVal = _format.format(value);
                    }
                    catch (IllegalArgumentException x)
                    {
                        strVal = ConvertUtils.convert(value);
                    }
                else
                    strVal = ConvertUtils.convert(value);
            }
            out.write(value == null ? "" : PageFlowUtil.filter(strVal));
            out.write("\"");
            String autoCompletePrefix = getAutoCompleteURLPrefix();
            if (autoCompletePrefix != null)
            {
                out.write(" onKeyDown=\"return ctrlKeyCheck(event);\"");
                out.write(" onBlur=\"hideCompletionDiv();\"");
                out.write(" autocomplete=\"off\"");
                out.write(" onKeyUp=\"return handleChange(this, event, '" + autoCompletePrefix + "');\"");
            }
            out.write(">");
            // disabled inputs are not posted with the form, so we output a hidden form element:
            if (disabledInput)
                renderHiddenFormInput(ctx, out, formFieldName, value);
        }
    }

    protected String getAutoCompleteURLPrefix()
    {
        return null;
    }

    protected void outputName(RenderContext ctx, Writer out, String formFieldName) throws IOException
    {
        out.write(" name='");
        out.write(getInputPrefix());
        out.write(formFieldName);
        out.write("'");

        String setFocusId = (String)ctx.get("setFocusId");
        if (null != setFocusId)
        {
            out.write(" id='" + setFocusId + "'");
            ctx.remove("setFocusId");
        }
    }

    /**
     * put quotes around a JavaScript string, and HTML encode that.
     */
    protected String hq(Object value)
    {
        return PageFlowUtil.filterQuote(value);
    }

    protected String h(Object value)
    {
        return PageFlowUtil.filter(value);
    }

    public void renderSortHandler(RenderContext ctx, Writer out, Sort.SortDirection sort) throws IOException
    {
        if (_sortColumn == null)
        {
            return;
        }
        String uri;
        String regionName = ctx.getCurrentRegion().getName();
        uri = "doSort('"+ h(regionName) + "','" + h(_sortColumn.getName()) + "','" + h(sort.getDir()) + "')";
        out.write(uri);
    }

    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        String title = PageFlowUtil.filter(_caption.eval(ctx));
        if (title.length() == 0)
        {
            title = "&nbsp;";
        }
        out.write(title);
    }

    public void renderDetailsCaptionCell(RenderContext ctx, Writer out) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class='labkey-form-label'>");
        renderTitle(ctx, out);
        int mode = ctx.getMode();
        if ((mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE) && isEditable())
        {
            if (_boundColumn != null)
            {
                StringBuilder sb = new StringBuilder();
                if (_boundColumn.getFriendlyTypeName() != null && !_inputType.equalsIgnoreCase("select"))
                {
                    sb.append("Type: ").append(_boundColumn.getFriendlyTypeName()).append("\n");
                }
                if (_boundColumn.getDescription() != null)
                {
                    sb.append("Description: ").append(_boundColumn.getDescription()).append("\n");
                }
                if (_boundColumn.getPropertyURI() != null)
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(_boundColumn.getPropertyURI(), ctx.getContainer());
                    if (pd != null)
                    {
                        for (IPropertyValidator validator : PropertyService.get().getPropertyValidators(pd))
                            sb.append("Validator: ").append(validator).append("\n");
                    }
                }
                if (sb.length() > 0)
                {
                    out.write(PageFlowUtil.helpPopup(_boundColumn.getCaption(), sb.toString()));
                }
                if (renderRequiredIndicators() && !_boundColumn.isNullable())
                    out.write(" *");
            }
        }
        out.write("</td>");
    }

    protected boolean renderRequiredIndicators()
    {
        return true;
    }

    public boolean isEditable()
    {
        return _editable;
    }

    public void setEditable(boolean b)
    {
        _editable = b;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (ctx.getMode() == DataRegion.MODE_INSERT || ctx.getMode() == DataRegion.MODE_UPDATE)
            renderInputHtml(ctx, out, getInputValue(ctx));
        else
            renderDetailsCellContents(ctx, out);
    }

    public String getInputType()
    {
        return _inputType;
    }

    public void setInputType(String _inputType)
    {
        this._inputType = _inputType;
    }

    public void setBoundColumn(ColumnInfo column)
    {
        _boundColumn = column;
    }

    public ColumnInfo getBoundColumn()
    {
        return _boundColumn;
    }

    public void setDisplayColumn(ColumnInfo column)
    {
        _displayColumn = column;
    }

    public ColumnInfo getDisplayColumn()
    {
        return _displayColumn;
    }
}
