/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.NamedObject;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.element.Input;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Select;
import org.labkey.api.util.element.TextArea;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.data.RemapCache.EXPERIMENTAL_RESOLVE_LOOKUPS_BY_VALUE;

/** Subclass that wraps a ColumnInfo to pull values from the database */
public class DataColumn extends DisplayColumn
{
    private static final Logger LOG = Logger.getLogger(DataColumn.class);

    private ColumnInfo _boundColumn;
    private ColumnInfo _displayColumn;
    private List<FieldKey> _sortFieldKeys;
    private ColumnInfo _filterColumn;

    private String _inputType;
    private int _inputRows;
    private int _inputLength;
    private boolean _preserveNewlines;
    private boolean _editable = true;

    //Careful, a renderer without a resultset is only good for input forms
    public DataColumn(ColumnInfo col)
    {
        this(col,true);
    }

    public DataColumn(ColumnInfo col, boolean withLookups)
    {
        _boundColumn = col;
        _displayColumn = getDisplayField(col, withLookups);
        _nowrap = _displayColumn.isNoWrap();
        _sortFieldKeys = _displayColumn.getSortFieldKeys();
        if (null == _sortFieldKeys && _displayColumn.isSortable())
            _sortFieldKeys = Collections.singletonList(_displayColumn.getFieldKey());
        _filterColumn = _displayColumn.getFilterField();

        _width = _displayColumn.getWidth();
        StringExpression url = withLookups ?
                _boundColumn.getEffectiveURL() :
                _boundColumn.getURL();
        if (null != url)
            super.setURLExpression(url);
        setLinkTarget(_boundColumn.getURLTargetWindow());
        setLinkCls(_boundColumn.getURLCls());
        setOnClick(_boundColumn.getOnClick());
        setFormatString(_displayColumn.getFormat());
        setTsvFormatString(_displayColumn.getTsvFormatString());
        setExcelFormatString(_displayColumn.getExcelFormatString());
        setTextExpression(_displayColumn.getTextExpression());
        setDescription(_boundColumn.getDescription());
        _inputType = _boundColumn.getInputType();
        try
        {
            ColumnInfo inputDisplayColumn = _displayColumn;
            if (!withLookups)
                inputDisplayColumn = getDisplayField(col, true);
            if (null != inputDisplayColumn && _boundColumn != inputDisplayColumn && null != _boundColumn.getFk() && null != _boundColumn.getFkTableInfo())
            {
                if (_boundColumn.getFk() instanceof MultiValuedForeignKey && ((MultiValuedForeignKey)_boundColumn.getFk()).isMultiSelectInput())
                    _inputType = "select.multiple";
                else
                    _inputType = "select";
            }

        }
        catch (QueryParseException qpe)
        {
            /* fall through */
        }
        _inputRows = _boundColumn.getInputRows();
        // Assume that if the use can enter the value in a text area that they'll want to see
        // their newlines in grid views as well
        _preserveNewlines = _inputRows > 1;
        _inputLength = _boundColumn.getInputLength();
        _caption = StringExpressionFactory.create(_boundColumn.getLabel());
        _editable = !_boundColumn.isReadOnly() && _boundColumn.isUserEditable();
        _textAlign = _displayColumn.getTextAlign();

    }


    boolean analyticsProviderInitialized = false;

    @Override
    public @NotNull List<ColumnAnalyticsProvider> getAnalyticsProviders()
    {
        if (!analyticsProviderInitialized)
        {
            // get the applicable ColumnAnalyticsProviders
            AnalyticsProviderRegistry analyticsProviderRegistry = AnalyticsProviderRegistry.get();
            if (analyticsProviderRegistry != null)
            {
                for (ColumnAnalyticsProvider columnAnalyticsProvider : analyticsProviderRegistry.getColumnAnalyticsProviders(_boundColumn, true))
                {
                    addAnalyticsProvider(columnAnalyticsProvider);
                    columnAnalyticsProvider.addClientDependencies(_clientDependencies);
                }
            }
            analyticsProviderInitialized = true;
        }

        return super.getAnalyticsProviders();
    }


    @Override
    public @NotNull Set<ClientDependency> getClientDependencies()
    {
        // call getAnalyticsProviders() to make find any client dependencies
        getAnalyticsProviders();
        return super.getClientDependencies();
    }


    protected ColumnInfo getDisplayField(@NotNull ColumnInfo col, boolean withLookups)
    {
        if (!withLookups)
            return col;
        ColumnInfo display = col.getDisplayField();
        return null==display ? col : display;
    }


    @Override
    public String toString()
    {
        return getClass().getName() + ": " + getName();
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

    @Override
    public ColumnInfo getColumnInfo()
    {
        return _boundColumn;
    }

    @Override
    public ColumnInfo getDisplayColumnInfo()
    {
        return _displayColumn;
    }

    @Override
    public boolean isFilterable()
    {
        return _filterColumn != null;
    }

    @Override
    public boolean isQueryColumn()
    {
        return true;
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        if (_boundColumn != null)
            keys.add(_boundColumn.getFieldKey());
        if (_displayColumn != null)
            keys.add(_displayColumn.getFieldKey());
        if (_filterColumn != null)
            keys.add(_filterColumn.getFieldKey());
        if (_sortFieldKeys != null)
            keys.addAll(_sortFieldKeys);
        StringExpression effectiveURL = _boundColumn.getEffectiveURL();
        if (effectiveURL instanceof DetailsURL)
        {
            keys.addAll(((DetailsURL) effectiveURL).getFieldKeys());
        }
    }

    @Override
    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        if (_boundColumn != null)
            columns.add(_boundColumn);
        if (_displayColumn != null)
            columns.add(_displayColumn);
        if (_filterColumn != null)
            columns.add(_filterColumn);
    }

    @Override
    public boolean isSortable()
    {
        return _sortFieldKeys != null && _sortFieldKeys.size() > 0;
    }

    @Override
    public Object getValue(RenderContext ctx)
    {
        Object result = ctx.get(_boundColumn.getFieldKey());
        if (result == null)
        {
            // If we couldn't find it by FieldKey, check by alias as well
            result = _boundColumn.getValue(ctx);
        }
        return result;
    }

    @Override
    public Object getDisplayValue(RenderContext ctx)
    {
        Object result = ctx.get(_displayColumn.getFieldKey());
        if (result == null)
        {
            // If we couldn't find it by FieldKey, check by alias as well
            result = _displayColumn.getValue(ctx);
        }
        return result;
    }

    @Override
    public Object getJsonValue(RenderContext ctx)
    {
        return getValue(ctx);
    }

    @Override
    public Class getValueClass()
    {
        return _boundColumn.getJavaClass();
    }

    @Override
    public Class getDisplayValueClass()
    {
        return _displayColumn.getJavaClass();
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // By default, use the same rendering for both the details and grid views
        renderGridCellContents(ctx, out);
    }

    @Override
    @Nullable
    public FieldKey getFilterKey()
    {
        if (_filterColumn == null)
            return null;

        return _filterColumn.getFieldKey();
    }

    @Override
    public void renderFilterOnClick(RenderContext ctx, Writer out) throws IOException
    {
        if (_filterColumn == null)
            return;

        String regionName = ctx.getCurrentRegion().getName();
        String columnName = PageFlowUtil.jsString(_boundColumn.getFieldKey().toString());
        out.write(DataRegion.getJavaScriptObjectReference(regionName) + "._openFilter(" + columnName + ");");
    }

    @Override
    public String getClearFilter(RenderContext ctx)
    {
        if (_filterColumn == null)
            return "";

        String regionName = ctx.getCurrentRegion().getName();
        String fieldKey = _filterColumn.getFieldKey().toString();
        return DataRegion.getJavaScriptObjectReference(regionName) + ".clearFilter(" + PageFlowUtil.jsString(fieldKey) + ")";
    }

    @Override
    public String getClearSortScript(RenderContext ctx)
    {
        String regionName = ctx.getCurrentRegion().getName();
        String fieldKey = _displayColumn.getFieldKey().toString();
        return DataRegion.getJavaScriptObjectReference(regionName) + ".clearSort(" + PageFlowUtil.jsString(fieldKey) + ");";
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            String url = renderURLorValueURL(ctx);

            if (StringUtils.isNotBlank(url))
            {
                out.write("<a href=\"");
                out.write(PageFlowUtil.filter(url));

                String linkTitle = renderURLTitle(ctx);
                if (null != linkTitle)
                {
                    out.write("\" title=\"");
                    out.write(linkTitle);
                }

                String linkTarget = getLinkTarget();
                if (null != linkTarget)
                {
                    out.write("\" target=\"");
                    out.write(linkTarget);
                    out.write("\" rel=\"noopener noreferrer\"");
                }

                String linkCls = getLinkCls();
                if (null != linkCls)
                {
                    out.write("\" class=\"");
                    out.write(linkCls);
                }

                String onClick = getOnClick();
                if (null != onClick)
                {
                    out.write("\" onclick=\"");
                    out.write(onClick);
                }

                String css = getCssStyle(ctx);
                if (!css.isEmpty())
                {
                    out.write("\" style=\"");
                    out.write(css);
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

    protected String renderURLorValueURL(RenderContext ctx)
    {
        String url = renderURL(ctx);

        if (url == null)
        {
            // See if the value is itself a URL
            Object value = ctx.get(_displayColumn.getFieldKey());
            if (value != null)
            {
                String toString = value.toString();
                if (StringUtilsLabKey.startsWithURL(toString) &&
                        !toString.contains(" ") &&
                        !toString.contains("\n") &&
                        !toString.contains("\r") &&
                        !toString.contains("\t"))
                {
                    // Could do more sophisticated URL extraction to try to pull out, but this is likely
                    // to link most real URLs
                    url = toString;
                }
            }
        }
        return url;
    }
    
    @Override
    public String renderURL(RenderContext ctx)
    {
        Object displayValue = getDisplayValue(ctx);
        if (null == displayValue || "".equals(displayValue))
            return null;
        return super.renderURL(ctx);
    }

    @Override
    protected String getHoverContent(RenderContext ctx)
    {
        ConditionalFormat format = findApplicableFormat(ctx);
        if (format == null)
        {
            return null;
        }
        StringBuilder sb = new StringBuilder("Formatting applied because ");
        String separator = "";
        for (SimpleFilter.FilterClause clause : format.getSimpleFilter().getClauses())
        {
            sb.append(separator);
            separator = " and ";
            clause.appendFilterText(sb, new SimpleFilter.ColumnNameFormatter());
        }
        sb.append(".");
        return sb.toString();
    }

    @Override
    protected String getHoverTitle(RenderContext ctx)
    {
        return "Formatting Details";
    }

    @Nullable
    private ConditionalFormat findApplicableFormat(RenderContext ctx)
    {
        if (getBoundColumn() == null)
        {
            return null;
        }

        for (ConditionalFormat format : getBoundColumn().getConditionalFormats())
        {
            Object value = ctx.get(_displayColumn.getFieldKey());
            if (format.meetsCriteria(_displayColumn, value))
            {
                return format;
            }
        }

        if (_displayColumn != getBoundColumn())
        {
            // If we're not showing the bound column, as in a lookup, check the display column to see if it has a
            // format preference
            for (ConditionalFormat format : _displayColumn.getConditionalFormats())
            {
                Object value = ctx.get(_displayColumn.getFieldKey());
                if (format.meetsCriteria(_displayColumn, value))
                {
                    return format;
                }
            }
        }
        return null;
    }

    @Override @NotNull
    public String getCssStyle(RenderContext ctx)
    {
        String result = super.getCssStyle(ctx);
        ConditionalFormat format = findApplicableFormat(ctx);
        if (format != null)
        {
            result = result + ";" + format.getCssStyle();
        }
        return result;
    }

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        StringBuilder sb = new StringBuilder();
        Object value = ctx.get(_displayColumn.getFieldKey());
        if (value == null)
        {
            // If we couldn't find it by FieldKey, check by alias as well
            value = _displayColumn.getValue(ctx);
        }
        if (value == null)
        {
            if (_displayColumn != _boundColumn)
            {
                Object boundValue = _boundColumn.getValue(ctx);
                // In many entry paths we've already checked for null, but not all (for example, MVDisplayColumn or when the TargetStudy no longer exists or is empty string)
                if (boundValue == null || "".equals(boundValue))
                {
                    sb.append("&nbsp;");
                }
                else
                {
                    sb.append(PageFlowUtil.filter("<" + boundValue + ">"));
                }
            }
        }
        else
        {
            String formatted = formatValue(ctx, value, getTextExpressionCompiled(ctx), getFormat());

            if (getRequiresHtmlFiltering())
                formatted = PageFlowUtil.filter(formatted);

            if (formatted.length() == 0)
                formatted = "&nbsp;";
            else if (isPreserveNewlines())
                formatted = formatted.replaceAll("\\n", "<br>\n");
            else if (value instanceof Date)
                formatted = "<nobr>" + formatted + "</nobr>";

            sb.append(formatted);
        }

        return sb.toString();
    }

    protected boolean isDisabledInput()
    {
        return _boundColumn.getDefaultValueType() == DefaultValueType.FIXED_NON_EDITABLE ||
                _boundColumn.isReadOnly() || !_boundColumn.isUserEditable();
    }

    protected boolean isDisabledInput(RenderContext ctx)
    {
        return isDisabledInput();
    }

    protected boolean isSelectInputSelected(String entryName, Object value, String valueStr)
    {
        if (value instanceof Collection)
        {
            // CONSIDER: stringify values in collection?
            return ((Collection)value).contains(entryName);
        }
        return null != entryName && entryName.equals(valueStr);
    }

    protected String getSelectInputDisplayValue(NamedObject entry)
    {
        return entry.getObject().toString();
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        if (_boundColumn.isVersionColumn() || _inputType.equalsIgnoreCase("none"))
            return;

        boolean disabledInput = isDisabledInput(ctx);
        final String formFieldName = getFormFieldName(ctx);

        String strVal = "";
        //UNDONE: Should use output format here.
        if (null != value)
        {
            // 4934: Don't render form input values with formatter since we don't parse formatted inputs on post.
            // For now, we can at least render disabled inputs with formatting since a
            // hidden input with the actual value is emitted for disabled items.
            if (null != _format && disabledInput)
            {
                try
                {
                    strVal = _format.format(value);
                }
                catch (IllegalArgumentException x)
                {
                    strVal = ConvertUtils.convert(value);
                }
            }
            else
                strVal = ConvertUtils.convert(value);
        }

        if (_boundColumn.isAutoIncrement())
        {
            renderHiddenFormInput(ctx, out, formFieldName, value);
            if (null != value)
            {
                out.write(PageFlowUtil.filter(strVal));
            }
        }
        else if (_inputType.toLowerCase().startsWith("select"))
        {
            renderSelectFormInput(ctx, out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("textarea"))
        {
            renderTextAreaFormInput(ctx, out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("file"))
        {
            renderFileFormInput(ctx, out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("checkbox"))
        {
            renderCheckboxFormInput(ctx, out, formFieldName, value, strVal, disabledInput);
        }
        else
        {
            if (getAutoCompleteURLPrefix() != null)
            {
                renderAutoCompleteFormInput(ctx, out, formFieldName, value, strVal, disabledInput, getAutoCompleteURLPrefix());
            }
            else
            {
                renderTextFormInput(ctx, out, formFieldName, value, strVal, disabledInput);
            }
        }

        HtmlString errors = getErrors(ctx);
        if (!StringUtils.isEmpty(errors.toString()))
        {
            out.write("<span class=\"help-block form-text\">");
            out.write(errors.toString());
            out.write("</span>");
        }
    }

    protected @NotNull HtmlString getErrors(RenderContext ctx)
    {
        ColumnInfo col = null;
        if (isQueryColumn())
            col = getColumnInfo();

        return ctx.getForm() == null || col == null ? HtmlString.EMPTY_STRING : ctx.getErrors(col);
    }

    protected void renderSelectFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput)
            throws IOException
    {
        NamedObjectList entryList = _boundColumn.getFk().getSelectList(ctx);
        if (!entryList.isComplete())
        {
            // When incomplete, there are too many select options to render -- use a simple text input instead.
            // TODO: if the FK target is public, we can generate an auto-complete input
            String textInputValue = strVal;
            if (ExperimentalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_RESOLVE_LOOKUPS_BY_VALUE))
            {
                Object displayValue = null;
                TableViewForm viewForm = ctx.getForm();
                if (viewForm != null && viewForm.contains(this, ctx))
                {
                    // On error reshow, use the user supplied form value
                    displayValue = viewForm.get(formFieldName);
                }
                if (displayValue == null)
                    displayValue = getDisplayValue(ctx);
                textInputValue = Objects.toString(displayValue, strVal);
            }
            renderTextFormInput(ctx, out, formFieldName, value, textInputValue, disabledInput);
        }
        else
        {
            Select.SelectBuilder select = new Select.SelectBuilder()
                    .disabled(disabledInput)
                    .multiple("select.multiple".equalsIgnoreCase(_inputType))
                    .name(getInputPrefix() + formFieldName);

            List<Option> options = new ArrayList<>();

            // add empty option
            options.add(new Option.OptionBuilder().build());

            for (NamedObject entry : entryList)
            {
                String entryName = entry.getName();
                Option.OptionBuilder option = new Option.OptionBuilder()
                        .selected(isSelectInputSelected(entryName, value, strVal))
                        .value(entryName);

                if (null != entry.getObject())
                    option.label(getSelectInputDisplayValue(entry));

                options.add(option.build());
            }

            out.write(select.addOptions(options).toString());

            // disabled inputs are not posted with the form, so we output a hidden form element:
            if (disabledInput)
                renderHiddenFormInput(ctx, out, formFieldName, value);
        }
    }

    protected void renderFileFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput)
            throws IOException
    {
        Input.InputBuilder input = new Input.InputBuilder()
                .type("file")
                .name(getInputPrefix() + formFieldName)
                .disabled(disabledInput)
                .needsWrapping(false);

        out.write(input.build().toString());
    }

    protected void renderCheckboxFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput)
            throws IOException
    {
        boolean checked = ColumnInfo.booleanFromObj(ConvertUtils.convert(value));

        Input.InputBuilder input = new Input.InputBuilder()
                .type("checkbox")
                .name(getInputPrefix() + formFieldName)
                .disabled(disabledInput)
                .value("1")
                .checked(checked)
                .needsWrapping(false);

        out.write(input.build().toString());

        /*
         * Checkboxes are weird. If set to FALSE they don't post at all, so it's impossible to tell
         * the difference between values that weren't on the html form at all and ones that were set
         * to false by the user.
         *
         * To fix this, each checkbox posts a hidden field named @columnName.  Spring parameter
         * binding uses these special fields to set all unposted checkbox values to false.
         */
        out.write("<input type=\"hidden\" name=\"");
        out.write(SpringActionController.FIELD_MARKER);
        out.write(PageFlowUtil.filter(formFieldName));
        out.write("\" value=\"1\">");
        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(ctx, out, formFieldName, checked ? "1" : "");
    }

    protected void renderTextAreaFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput)
            throws IOException
    {
        TextArea.TextAreaBuilder input = new TextArea.TextAreaBuilder()
                .columns(_inputLength)
                .rows(_inputRows)
                .name(getInputPrefix() + formFieldName)
                .disabled(disabledInput)
                .value(strVal);

        out.write(input.build().toString());

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(ctx, out, formFieldName, value);
    }

    protected void renderTextFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput)
            throws IOException
    {
        Input.InputBuilder input = new Input.InputBuilder()
                .name(getInputPrefix() + formFieldName)
                .disabled(disabledInput)
                .size(_inputLength)
                .value(strVal)
                .needsWrapping(false);

        out.write(input.build().toString());

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(ctx, out, formFieldName, value);
    }

    protected void renderAutoCompleteFormInput(RenderContext ctx, Writer out, String formFieldName, Object value, String strVal, boolean disabledInput, String autoCompleteURLPrefix)
            throws IOException
    {
        String renderId = "auto-complete-div-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
        StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"text/javascript\">");
        sb.append("Ext4.onReady(function(){\n" +
                "        Ext4.create('LABKEY.element.AutoCompletionField', {\n" +
                "            renderTo        : " + PageFlowUtil.jsString(renderId) + ",\n" +
                "            completionUrl   : " + PageFlowUtil.jsString(getAutoCompleteURLPrefix()) + ",\n" +
                "            sharedStore     : true,\n" +
                "            sharedStoreId   : " + PageFlowUtil.jsString(getAutoCompleteURLPrefix()) + ",\n" +
                "            tagConfig   : {\n" +
                "                tag     : 'input',\n" +
                "                type    : 'text',\n" +
                "                name    : " + PageFlowUtil.jsString(formFieldName) + ",\n" +
                "                size    : " + _inputLength + ",\n" +
                "                value   : " + PageFlowUtil.jsString(strVal) + ",\n" +
                "                autocomplete : 'off'\n" +
                "            }\n" +
                "        });\n" +
                "      });\n");
        sb.append("</script>\n");
        sb.append("<div id='").append(renderId).append("'></div>");
        out.write(sb.toString());
    }

    protected String getAutoCompleteURLPrefix()
    {
        return null;
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

    @Override
    public String getSortHandler(RenderContext ctx, Sort.SortDirection sort)
    {
        if (_displayColumn == null || _sortFieldKeys == null || _sortFieldKeys.size() == 0)
            return "";

        String regionName = ctx.getCurrentRegion().getName();
        String fieldKey = _displayColumn.getFieldKey().toString();
        return DataRegion.getJavaScriptObjectReference(regionName) +
                ".changeSort(" + PageFlowUtil.jsString(fieldKey) + ", '" + h(sort.getDir()) + "')";
    }

    @Override
    public void renderTitle(RenderContext ctx, Writer out) throws IOException
    {
        String title = PageFlowUtil.filter(getTitle(ctx));
        if (title.length() == 0)
        {
            title = "&nbsp;";
        }
        out.write(title);
    }

    @Override
    public String getTitle(RenderContext ctx)
    {
        if (_caption == null)
            return null;
        return _caption.eval(ctx);
    }

    @Override
    public void renderDetailsCaptionCell(RenderContext ctx, Writer out, @Nullable String cls) throws IOException
    {
        if (null == _caption)
            return;

        out.write("<td class=\"" + (cls != null ? cls : " lk-form-label") + "\">");

        renderTitle(ctx, out);
        if (ctx.getMode() == DataRegion.MODE_DETAILS)
            out.write(":");
        int mode = ctx.getMode();
        if ((mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE) && isEditable())
        {
            if (_boundColumn != null)
            {
                StringBuilder sb = new StringBuilder();
                if (_boundColumn.getFriendlyTypeName() != null && !_inputType.toLowerCase().startsWith("select"))
                {
                    sb.append("Type: ").append(_boundColumn.getFriendlyTypeName()).append("\n");
                }
                if (_boundColumn.getDescription() != null)
                {
                    sb.append("Description: ").append(_boundColumn.getDescription()).append("\n");
                }
                for (IPropertyValidator validator : _boundColumn.getValidators())
                    sb.append("Validator: ").append(validator).append("\n");
                if (renderRequiredIndicators() && _boundColumn.isRequired() && !_boundColumn.isBooleanType())
                {
                    out.write(" *");
                    sb.append("This field is required.\n");
                }
                if (sb.length() > 0)
                {
                    out.write(PageFlowUtil.helpPopup(_boundColumn.getLabel(), sb.toString()));
                }
            }
        }
        out.write("</td>");
    }

    protected boolean renderRequiredIndicators()
    {
        return true;
    }

    @Override
    public boolean isEditable()
    {
        return _editable;
    }

    public void setEditable(boolean b)
    {
        _editable = b;
    }

    @Override
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

    public void setInputType(String inputType)
    {
        _inputType = inputType;
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
