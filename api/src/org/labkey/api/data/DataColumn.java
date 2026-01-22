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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.collections.NamedObject;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.ontology.Quantity;
import org.labkey.api.ontology.Unit;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.OptionBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SelectBuilder;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TextAreaBuilder;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.TypeAheadSelectDisplayColumn;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.HtmlWriter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.id;

/** Subclass that wraps a ColumnInfo to pull values from the database */
public class DataColumn extends DisplayColumn
{
    public static final String EXPERIMENTAL_USE_QUERYSELECT_COMPONENT = "experimental-use-queryselect-component";

    private ColumnInfo _boundColumn;
    private ColumnInfo _displayColumn;
    private List<FieldKey> _sortFieldKeys;
    private final ColumnInfo _filterColumn;

    private String _inputType;
    private int _inputRows;
    private int _inputLength;
    private boolean _preserveNewlines;
    private boolean _editable;

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

        // Issue 45559 - use the width set on the bound column when configured. If not set, use the display column's
        // width (in non-lookup cases they will be the same)
        _width = _boundColumn.getWidth();
        if (_width.isEmpty())
        {
            _width = _displayColumn.getWidth();
        }
        StringExpression url = withLookups ? _boundColumn.getEffectiveURL() : _boundColumn.getURL();
        if (null != url)
            super.setURLExpression(url);
        setLinkTarget(_boundColumn.getURLTarget());
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
        // Assume that if the user can enter the value in a text area that they'll want to see
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
        {
            keys.add(_boundColumn.getFieldKey());
            StringExpression effectiveURL = _boundColumn.getEffectiveURL();
            if (effectiveURL instanceof DetailsURL url)
                keys.addAll(url.getFieldKeys());
        }
        if (_displayColumn != null)
            keys.add(_displayColumn.getFieldKey());
        if (_filterColumn != null)
            keys.add(_filterColumn.getFieldKey());
        if (_sortFieldKeys != null)
            keys.addAll(_sortFieldKeys);
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
        return _sortFieldKeys != null && !_sortFieldKeys.isEmpty();
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
    public Class<?> getValueClass()
    {
        return _boundColumn.getJavaClass();
    }

    @Override
    public Class<?> getDisplayValueClass()
    {
        return _displayColumn.getJavaClass();
    }

    @Override
    public void renderDetailsCellContents(RenderContext ctx, HtmlWriter out)
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
    public String getFilterOnClick(RenderContext ctx)
    {
        if (_filterColumn == null)
            return "";

        String regionName = ctx.getCurrentRegion().getName();
        String columnName = PageFlowUtil.jsString(_boundColumn.getFieldKey().toString());

        return DataRegion.getJavaScriptObjectReference(regionName) + "._openFilter(" + columnName + ");";
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
    public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
    {
        Object o = getValue(ctx);

        if (null != o)
        {
            String url = renderURLorValueURL(ctx);

            HtmlString formattedValue = getFormattedHtml(ctx);

            if (StringUtils.isNotBlank(url))
            {
                LinkBuilder link = LinkBuilder.simpleLink(formattedValue).href(url);

                String linkTitle = renderURLTitle(ctx);
                if (null != linkTitle)
                {
                    link.title(linkTitle);
                }

                String linkTarget = getLinkTarget();
                if (null != linkTarget)
                {
                    link.target(linkTarget).rel("noopener noreferrer");
                }

                String linkCls = getLinkCls();
                if (null != linkCls)
                {
                    link.addClass(linkCls);
                }

                String onClick = getOnClick();
                if (null != onClick)
                {
                    link.onClick(onClick);
                }

                String css = getCssStyle(ctx);
                if (!css.isEmpty())
                {
                    link.style(css);
                }

                out.write(link);
            }
            else
            {
                out.write(formattedValue);
            }
        }
        else
        {
            out.write(HtmlString.NBSP);
        }
    }

    protected String renderURLorValueURL(RenderContext ctx)
    {
        String url = renderURL(ctx);

        if (url == null)
        {
            // See if the value is itself a URL
            Object value = getDisplayValue(ctx);
            if (value instanceof String toString)
            {
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
    protected ConditionalFormat findApplicableFormat(RenderContext ctx)
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
    public HtmlString getFormattedHtml(RenderContext ctx)
    {
        HtmlStringBuilder hsb = HtmlStringBuilder.of();
        Object value = getDisplayValue(ctx);
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
                    hsb.append(HtmlString.NBSP);
                }
                else
                {
                    hsb.append("<" + boundValue + ">");
                }
            }
        }
        else
        {
            String formatted = formatValue(ctx, value, getTextExpressionCompiled(ctx), getFormat(), getDisplayUnit());

            if (getRequiresHtmlFiltering())
                formatted = PageFlowUtil.filter(formatted);

            if (formatted.isEmpty())
                formatted = "&nbsp;";
            else if (isPreserveNewlines())
                formatted = formatted.replaceAll("\\n", "<br>\n");
            else if (value instanceof Date)
                formatted = "<nobr>" + formatted + "</nobr>";

            hsb.unsafeAppend(formatted);
        }

        return hsb.getHtmlString();
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
        if (entryName == null)
            return false;

        if (value instanceof Collection<?> collection)
        {
            // Issue 53254: Multi-value lookups should reselect values in form
            for (var item : collection)
            {
                if (item != null && entryName.equals(item.toString()))
                    return true;
            }

            return false;
        }

        return entryName.equals(valueStr);
    }

    protected String getSelectInputDisplayValue(NamedObject entry)
    {
        return entry.getObject().toString();
    }

    protected String getStringValue(Object value, Unit unit, boolean disabledInput)
    {
        String strVal = "";
        //UNDONE: Should use output format here.
        if (null != value)
        {
            if (unit != null && value instanceof Number num)
            {
                Quantity quantity = (value instanceof Quantity q) ? q : unit.getKindOfQuantity().toQuantity(num);
                value = quantity.value(unit);
            }

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
        return strVal;
    }

    @Override
    public void renderInputHtml(RenderContext ctx, HtmlWriter out, Object value)
    {
        if (_boundColumn.isVersionColumn() || _inputType.equalsIgnoreCase("none"))
            return;

        boolean disabledInput = isDisabledInput(ctx);
        final String formFieldName = getFormFieldName(ctx);
        String strVal = getStringValue(value, _boundColumn.getDisplayUnit(), disabledInput);

        if (_boundColumn.isAutoIncrement())
        {
            renderHiddenFormInput(out, formFieldName, value);
            if (null != value)
            {
                out.write(strVal);
            }
        }
        else if (_inputType.toLowerCase().startsWith("disabled"))
        {
            renderTextFormInput(out, formFieldName, value, strVal, true);
        }
        else if (_inputType.toLowerCase().startsWith("select"))
        {
            if (OptionalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_USE_QUERYSELECT_COMPONENT) && !"select.multiple".equalsIgnoreCase(_inputType))
            {
                TypeAheadSelectDisplayColumn displayColumn = new TypeAheadSelectDisplayColumn(_boundColumn, null);
                displayColumn.renderInputHtml(ctx, out, value);
            }
            else
                renderSelectFormInputFromFk(ctx, out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("textarea"))
        {
            renderTextAreaFormInput(out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("file"))
        {
            renderFileFormInput(out, formFieldName, value, strVal, disabledInput);
        }
        else if (_inputType.equalsIgnoreCase("checkbox"))
        {
            renderCheckboxFormInput(out, formFieldName, value, strVal, disabledInput);
        }
        else
        {
            if (getAutoCompleteURLPrefix() != null)
            {
                renderAutoCompleteFormInput(ctx, out, formFieldName, value, strVal, disabledInput, getAutoCompleteURLPrefix());
            }
            else
            {
                IPropertyValidator textChoiceValidator = PropertyService.get().getValidatorForColumn(_boundColumn, PropertyValidatorType.TextChoice);
                if (textChoiceValidator != null)
                    renderTextChoiceFormInput(out, formFieldName, value, toListForRender(strVal), disabledInput, textChoiceValidator);
                else
                    renderTextFormInput(out, formFieldName, value, strVal, disabledInput);
            }
        }

        HtmlString errors = getErrors(ctx);
        if (!errors.isEmpty())
        {
            SPAN(
                cl("help-block form-text"),
                errors
            ).appendTo(out);
        }
    }

    protected @NotNull HtmlString getErrors(RenderContext ctx)
    {
        ColumnInfo col = null;
        if (isQueryColumn())
            col = getColumnInfo();

        return ctx.getForm() == null || col == null ? HtmlString.EMPTY_STRING : ctx.getErrors(col);
    }

    private void renderSelectFormInput(HtmlWriter out, String formFieldName, Object value, List<String> strValues, boolean disabledInput, NamedObjectList entryList)
    {
        boolean isMultiple = "select.multiple".equalsIgnoreCase(_inputType);
        SelectBuilder select = new SelectBuilder()
            .disabled(disabledInput)
            .multiple(isMultiple)
            .name(formFieldName);

        List<OptionBuilder.Option> options = new ArrayList<>();

        // add empty option
        if (!isMultiple)
            options.add(new OptionBuilder().build());

        Set<String> selectedValues = strValues.isEmpty() ? Set.of() :
                strValues.size()==1 ? (null == strValues.get(0) ? Set.of() : Set.of(strValues.get(0))) :
                new HashSet<>(strValues);
        for (NamedObject entry : entryList)
        {
            String entryName = entry.getName();
            OptionBuilder option = new OptionBuilder()
                    .selected(selectedValues.contains(entryName))
                    .value(entryName);

            if (null != entry.getObject())
                option.label(getSelectInputDisplayValue(entry));

            options.add(option.build());
        }

        out.write(select.addOptions(options));

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(out, formFieldName, value);
    }

    protected void renderTextChoiceFormInput(HtmlWriter out, String formFieldName, Object value, List<String> strValues, boolean disabledInput, IPropertyValidator textChoiceValidator)
    {
        LinkedHashSet<String> choices = new LinkedHashSet<>(PropertyService.get().getTextChoiceValidatorOptions(textChoiceValidator));

        // if the already saved strVal is not in the current choice set, add it (as it seems wrong to remove a value that the user hasn't explicitly touched)
        choices.addAll(strValues);

        NamedObjectList options = new NamedObjectList();
        for (String choice : choices)
            options.put(new SimpleNamedObject(choice, choice));

        renderSelectFormInput(out, formFieldName, value, strValues, disabledInput, options);
    }

    protected void renderSelectFormInputFromFk(RenderContext ctx, HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput)
    {
        ForeignKey boundColumnFK = _boundColumn.getFk();
        NamedObjectList entryList = boundColumnFK.getSelectList(ctx);
        if (!entryList.isComplete())
        {
            // When incomplete, there are too many select options to render -- use a simple text input instead.
            Object displayValue = null;
            TableViewForm viewForm = ctx.getForm();
            if (viewForm != null && viewForm.contains(this, ctx))
            {
                // On error reshow, use the user supplied form value
                displayValue = viewForm.getAsString(formFieldName);
            }
            if (displayValue == null)
                displayValue = getDisplayValue(ctx);
            String textInputValue = Objects.toString(displayValue, strVal);

            renderTextFormInput(out, formFieldName, value, textInputValue, disabledInput);
        }
        else
        {
            renderSelectFormInput(out, formFieldName, value, toListForRender(value), disabledInput, entryList);
        }
    }

    List<String> toListForRender(Object value)
    {
        if (null == value || value instanceof String)
            return StringUtils.isEmpty((String) value) ? List.of() : List.of((String)value);
        if (value instanceof MultiChoice.Array arr)
            return arr;
        if (value instanceof String[] strArray)
            return List.of(strArray);
        if (value instanceof List<?> l)
            return l.stream().map(o -> Objects.toString(o, null)).toList();
        return List.of(Objects.toString(value));
    }

    protected void renderFileFormInput(HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput)
    {
        var input = InputBuilder.file()
            .name(formFieldName)
            .disabled(disabledInput)
            .needsWrapping(false);

        out.write(input);
    }

    protected void renderCheckboxFormInput(HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput)
    {
        boolean checked = ColumnInfo.booleanFromObj(ConvertUtils.convert(value));

        var input = InputBuilder.checkbox()
            .name(formFieldName)
            .disabled(disabledInput)
            .value("1")
            .checked(checked)
            .needsWrapping(false);

        out.write(input);

        /*
         * Checkboxes are weird. If set to FALSE they don't post at all, so it's impossible to tell
         * the difference between values that weren't on the html form at all and ones that were set
         * to false by the user.
         *
         * To fix this, each checkbox posts a hidden field named @columnName.  Spring parameter
         * binding uses these special fields to set all unposted checkbox values to false.
         */
        out.write(InputBuilder.hidden()
            .name(SpringActionController.FIELD_MARKER + formFieldName)
            .value(1));

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(out, formFieldName, checked ? "1" : "");
    }

    protected void renderTextAreaFormInput(HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput)
    {
        TextAreaBuilder input = new TextAreaBuilder()
            .columns(_inputLength)
            .rows(_inputRows)
            .name(formFieldName)
            .disabled(disabledInput)
            .value(strVal);

        out.write(input);

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(out, formFieldName, value);
    }

    protected void renderTextFormInput(HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput)
    {
        var input = InputBuilder.text()
            .name(formFieldName)
            .disabled(disabledInput)
            .size(_inputLength)
            .value(strVal)
            .needsWrapping(false);

        out.write(input);

        // disabled inputs are not posted with the form, so we output a hidden form element:
        if (disabledInput)
            renderHiddenFormInput(out, formFieldName, value);
    }

    protected void renderAutoCompleteFormInput(RenderContext ctx, HtmlWriter out, String formFieldName, Object value, String strVal, boolean disabledInput, @NotNull ActionURL autoCompleteURLPrefix)
    {
        String renderId = "auto-complete-div-" + UniqueID.getRequestScopedUID(ctx.getRequest());
        DIV(
            id(renderId)
        ).appendTo(out);

        String initScript =
            "Ext4.onReady(function(){\n" +
            "        Ext4.create('LABKEY.element.AutoCompletionField', {\n" +
            "            renderTo        : " + PageFlowUtil.jsString(renderId) + ",\n" +
            "            completionUrl   : " + PageFlowUtil.jsString(autoCompleteURLPrefix) + ",\n" +
            "            sharedStore     : true,\n" +
            "            sharedStoreId   : " + PageFlowUtil.jsString(autoCompleteURLPrefix) + ",\n" +
            "            tagConfig   : {\n" +
            "                tag     : 'input',\n" +
            "                type    : 'text',\n" +
            "                name    : " + PageFlowUtil.jsString(formFieldName) + ",\n" +
            "                size    : " + _inputLength + ",\n" +
            "                value   : " + PageFlowUtil.jsString(strVal) + ",\n" +
            "                autocomplete : 'off'\n" +
            "            }\n" +
            "        });\n" +
            "      });\n"
                ;
        HttpView.currentPageConfig().addDOMContentLoadedHandler(JavaScriptFragment.unsafe(initScript));
    }

    protected @Nullable ActionURL getAutoCompleteURLPrefix()
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
        if (_displayColumn == null || _sortFieldKeys == null || _sortFieldKeys.isEmpty())
            return "";

        String regionName = ctx.getCurrentRegion().getName();
        String fieldKey = _displayColumn.getFieldKey().toString();
        return DataRegion.getJavaScriptObjectReference(regionName) +
                ".changeSort(" + PageFlowUtil.jsString(fieldKey) + ", '" + h(sort.getDir()) + "')";
    }

    @Override
    public @Nullable HtmlString getTitle(RenderContext ctx)
    {
        // TODO: Treat null and empty the same instead?
        if (_caption == null)
            return null;
        var title = _caption.eval(ctx);
        if (null != _displayColumn && null != _displayColumn.getDisplayUnit() && !StringUtils.isEmpty(_displayColumn.getDisplayUnit().toString()))
            title += " (" + _displayColumn.getDisplayUnit() + ")";
        return title.isEmpty() ? HtmlString.NBSP : HtmlString.of(title);
    }

    @Override
    public void renderDetailsCaptionCell(RenderContext ctx, HtmlWriter out, @Nullable String cls)
    {
        if (null == _caption)
            return;

        TD(
            cl(cls != null, cls, "lk-form-label"),
            getTitle(ctx),
            ctx.getMode() == DataRegion.MODE_DETAILS ? ":" : null,
            (Renderable) ret -> {

                int mode = ctx.getMode();
                if ((mode == DataRegion.MODE_INSERT || mode == DataRegion.MODE_UPDATE) && isEditable())
                {
                    if (_boundColumn != null)
                    {
                        List<HtmlString> helpLines = new LinkedList<>();
                        if (_boundColumn.getFriendlyTypeName() != null && !_inputType.toLowerCase().startsWith("select"))
                        {
                            helpLines.add(HtmlString.of("Type: " + _boundColumn.getFriendlyTypeName()));
                        }
                        if (_boundColumn.getDescription() != null)
                        {
                            helpLines.add(HtmlString.of("Description: " + _boundColumn.getDescription()));
                        }
                        for (IPropertyValidator validator : _boundColumn.getValidators())
                            helpLines.add(HtmlString.of("Validator: " + validator));
                        if (renderRequiredIndicators() && _boundColumn.isRequired() && !_boundColumn.isBooleanType())
                        {
                            out.write(" *");
                            helpLines.add(HtmlString.of("This field is required."));
                        }
                        if (!helpLines.isEmpty())
                        {
                            HtmlString helpHtml = helpLines.stream().collect(LabKeyCollectors.joining(HtmlString.BR));
                            out.write(PageFlowUtil.popupHelp(helpHtml, _boundColumn.getLabel()));
                        }
                    }
                }

                return ret;
            }
        ).appendTo(out);
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
    public void render(RenderContext ctx, HtmlWriter out)
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
