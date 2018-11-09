/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Sort.SortDirection;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: matthewb
 * Date: Jul 21, 2008
 * Time: 12:00:28 PM
 *
 * These are fields used by ColumnInfo and PropertyDescriptor that primarily affect
 * how the field is rendered in the HTML grids, forms, and pickers
 */
public abstract class ColumnRenderProperties implements ImportAliasable
{
    protected SortDirection _sortDirection = SortDirection.ASC;
    protected String _inputType;
    protected int _inputLength = -1;
    protected int _inputRows = -1;
    protected String _displayWidth;
    protected String _format;
    protected String _excelFormatString;
    protected String _tsvFormatString;
    protected StringExpression _textExpression;
    protected int _scale = 0;
    protected String _propertyURI;
    protected String _conceptURI;
    protected String _rangeURI;
    protected PropertyType _propertyType;

    // property descriptors default to nullable, while columninfos do not; PropertyDescriptor overrides this initializer
    // in its constructor:
    protected boolean _nullable = false;
    protected boolean _required = false;
    protected String _label;
    /** The column's label, without any prefixes from parent lookups */
    protected String _shortLabel;
    protected String _description;
    protected boolean _hidden;
    protected Boolean _measure;
    protected Boolean _dimension;
    protected Boolean _recommendedVariable = false;
    protected DefaultScaleType _defaultScale = DefaultScaleType.LINEAR;
    protected boolean _shownInInsertView = true;
    protected boolean _shownInUpdateView = true;
    protected boolean _shownInDetailsView = true;
    protected StringExpression _url;
    protected String _urlTargetWindow;
    protected String _urlCls;
    protected String _onClick;
    protected Set<String> _importAliases = new LinkedHashSet<>();
    protected DefaultValueType _defaultValueType = null;
    protected FacetingBehaviorType _facetingBehaviorType = FacetingBehaviorType.AUTOMATIC;
    protected PHI _phi = PHI.NotPHI;
    protected String _redactedText = null;
    protected Boolean _isExcludeFromShifting = false;
    protected FieldKey _crosstabColumnDimension;
    protected CrosstabMember _crosstabColumnMember;

    public void copyTo(ColumnRenderProperties to)
    {
        to._sortDirection = _sortDirection;
        to.setInputType(getInputType());
        to.setInputLength(getInputLength());
        to.setInputRows(getInputRows());
        to._nullable = _nullable;
        to._required = _required;
        to._displayWidth = _displayWidth;
        to._format = _format;
        to._excelFormatString = _excelFormatString;
        to._tsvFormatString = _tsvFormatString;
        to._textExpression = _textExpression;
        to._label = _label;
        to._shortLabel = _shortLabel;
        to._description = _description;
        to._hidden = _hidden;
        to._shownInInsertView = _shownInInsertView;
        to._shownInUpdateView = _shownInUpdateView;
        to._shownInDetailsView = _shownInDetailsView;
        to._measure = _measure;
        to._dimension = _dimension;
        to._recommendedVariable = _recommendedVariable;
        to._defaultScale = _defaultScale;
        to._url = _url;
        to._importAliases = new LinkedHashSet<>(_importAliases);
        to._facetingBehaviorType = _facetingBehaviorType;
        to._crosstabColumnMember = _crosstabColumnMember;
        to._phi = _phi;
        to._redactedText = _redactedText;
        to._isExcludeFromShifting = _isExcludeFromShifting;
        to._scale = _scale;
        to._propertyURI = _propertyURI;
        to._conceptURI = _conceptURI;
        to._rangeURI = _rangeURI;
        to._propertyType = _propertyType;
        to._defaultValueType = _defaultValueType;
    }

    public String getNonBlankCaption()
    {
        if (_label == null || "".equals(_label.trim()))
        {
            return getName();
        }
        return _label;
    }

    public SortDirection getSortDirection()
    {
        return _sortDirection;
    }

    public void setSortDirection(SortDirection sortDirection)
    {
        _sortDirection = sortDirection;
    }

    public String getInputType()
    {
        return _inputType;
    }

    public void setInputType(String inputType)
    {
        _inputType = inputType;
    }

    public int getInputLength()
    {
        return _inputLength;
    }

    public void setInputLength(int inputLength)
    {
        _inputLength = inputLength;
    }

    public int getInputRows()
    {
        return _inputRows;
    }

    public void setInputRows(int inputRows)
    {
        _inputRows = inputRows;
    }

    public String getDisplayWidth()
    {
        return _displayWidth;
    }

    public void setDisplayWidth(String displayWidth)
    {
        _displayWidth = displayWidth;
    }

    public String getFormat()
    {
        return _format;
    }

    public void setFormat(String format)
    {
        _format = format;
    }

    public String getExcelFormatString()
    {
        return _excelFormatString;
    }

    public void setExcelFormatString(String excelFormatString)
    {
        _excelFormatString = excelFormatString;
    }

    public String getTsvFormatString()
    {
        return _tsvFormatString;
    }

    public void setTsvFormatString(String tsvFormatString)
    {
        _tsvFormatString = tsvFormatString;
    }

    public StringExpression getTextExpression()
    {
        return _textExpression;
    }

    public void setTextExpression(StringExpression expr)
    {
        _textExpression = expr;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getShortLabel()
    {
        return _shortLabel == null ? getLabel() : _shortLabel;
    }

    public void setShortLabel(String shortLabel)
    {
        _shortLabel = shortLabel;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isHidden()
    {
        return _hidden;
    }

    public void setHidden(boolean hidden)
    {
        _hidden = hidden;
    }

    public boolean isShownInDetailsView()
    {
        return _shownInDetailsView;
    }

    public void setShownInDetailsView(boolean shownInDetailsView)
    {
        _shownInDetailsView = shownInDetailsView;
    }

    public boolean isShownInInsertView()
    {
        return _shownInInsertView;
    }

    public void setShownInInsertView(boolean shownInInsertView)
    {
        _shownInInsertView = shownInInsertView;
    }

    public boolean isShownInUpdateView()
    {
        return _shownInUpdateView;
    }

    public void setShownInUpdateView(boolean shownInUpdateView)
    {
        _shownInUpdateView = shownInUpdateView;
    }

    public StringExpression getURL()
    {
        return _url;
    }

    public void setURL(StringExpression url)
    {
        _url = url;
    }

    public String getURLTargetWindow()
    {
        return _urlTargetWindow;
    }

    public void setURLTargetWindow(String urlTargetWindow)
    {
        _urlTargetWindow = urlTargetWindow;
    }

    public String getURLCls()
    {
        return _urlCls;
    }

    public void setURLCls(String urlCls)
    {
        _urlCls = urlCls;
    }

    public String getOnClick()
    {
        return _onClick;
    }

    public void setOnClick(String onClick)
    {
        _onClick = onClick;
    }

    public boolean isRecommendedVariable()
    {
        return _recommendedVariable;
    }

    public void setRecommendedVariable(boolean recommendedVariable)
    {
        _recommendedVariable = recommendedVariable;
    }

    public DefaultScaleType getDefaultScale()
    {
        return _defaultScale;
    }

    public void setDefaultScale(DefaultScaleType defaultScale)
    {
        _defaultScale = defaultScale;
    }

    public void setMeasure(boolean measure)
    {
        _measure = measure;
    }

    public void setDimension(boolean dimension)
    {
        _dimension = dimension;
    }

    public static boolean inferIsDimension(ColumnRenderProperties col)
    {
        return inferIsDimension(col.getName(), col.isLookup(), col.isHidden());
    }

    public static boolean inferIsDimension(String name, boolean isLookup, boolean isHidden)
    {
        return isLookup &&
                !isHidden &&
                !"CreatedBy".equalsIgnoreCase(name) &&
                !"ModifiedBy".equalsIgnoreCase(name);
    }

    public boolean isDimension()
    {
        // If dimension is unspecified/null, make a best guess based on the type of the field:
        if (_dimension == null)
            return inferIsDimension(getName(), isLookup(), isHidden());
        else
            return _dimension;
    }

    public static boolean inferIsMeasure(ColumnRenderProperties col)
    {
        return inferIsMeasure(col.getName(),
                col.getLabel(),
                col.isNumericType(),
                col.isAutoIncrement(),
                col.isLookup(),
                col.isHidden());
    }

    public static boolean inferIsMeasure(String name, String label, boolean isNumeric, boolean isAutoIncrement, boolean isLookup, boolean isHidden)
    {
        if (label != null)
        {
            String[] parts = label.toLowerCase().split("[ _]");
            for (String part : parts)
            {
                if (part.equals("code") || part.equals("id") || part.equals("identifier") || part.equals("datafax"))
                    return false;
            }
        }
        return isNumeric &&
                !isAutoIncrement &&
                !isLookup &&
                !isHidden
                && !"ParticipantID".equalsIgnoreCase(name)
                && !"VisitID".equalsIgnoreCase(name)
                && !"SequenceNum".equalsIgnoreCase(name)
                && !"RowId".equalsIgnoreCase(name)
                && !"ObjectId".equalsIgnoreCase(name);
    }

    public boolean isMeasure()
    {
        // If measure is unspecified/null, make a best guess based on the type of the field:
        if (_measure == null)
            return inferIsMeasure(getName(), getLabel(), isNumericType(), isAutoIncrement(), isLookup(), isHidden());
        else
            return _measure;
    }

    /** value must not be null/empty */
    public boolean isNullable()
    {
        return _nullable;
    }

    public void setNullable(boolean nullable)
    {
        _nullable = nullable;
    }

    /** value must not be null/empty OR a missing value indicator must be provided */
    public boolean isRequired()
    {
        // !nullable is stricter and implies required
        return !_nullable || _required;
    }

    /** Returns the 'raw' value of required which is useful for copying attributes.  see isRequired() */
    public boolean isRequiredSet()
    {
        return _required;
    }

    public void setRequired(boolean required)
    {
        _required = required;
    }

    @Override
    @NotNull
    public Set<String> getImportAliasSet()
    {
        return _importAliases;
    }

    public void setImportAliasesSet(Set<String> importAliases)
    {
        assert importAliases != null;
        _importAliases = importAliases;
    }

    public static String convertToString(Set<String> set)
    {
        if (set.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (String alias : set)
        {
            sb.append(separator);
            separator = ", ";
            alias = alias.trim();
            if (alias.contains(" "))
            {
                // Quote any values with spaces
                sb.append("\"");
                sb.append(alias);
                sb.append("\"");
            }
            else
            {
                sb.append(alias);
            }
        }
        return sb.toString();
    }

    @Nullable
    public PropertyType getPropertyType()
    {
        if (_propertyType == null && getRangeURI() != null)
            _propertyType = PropertyType.getFromURI(getConceptURI(), getRangeURI(), null);

        return _propertyType;
    }

    @Override
    public String getPropertyURI()
    {
        return _propertyURI;
    }

    public String getConceptURI()
    {
        return _conceptURI;
    }

    public String getRangeURI()
    {
        return _rangeURI;
    }

    @NotNull
    public abstract JdbcType getJdbcType();

    public abstract boolean isLookup();

    protected abstract boolean isAutoIncrement();

    private static Pattern STRING_PATTERN = Pattern.compile("[^,; \\t\\n\\f\"]+|\"[^\"]*\"");

    public static Set<String> convertToSet(String s)
    {
        Set<String> result = new LinkedHashSet<>();
        if (s != null)
        {
            Matcher m = STRING_PATTERN.matcher(s);
            while (m.find())
            {
                String alias = m.group();
                if (alias.startsWith("\"") && alias.endsWith("\""))
                {
                    // Strip off the leading and trailing quotes
                    alias = alias.substring(1, alias.length() - 1);
                }
                result.add(alias);
            }
        }
        return result;
    }


    public boolean isDateTimeType()
    {
        JdbcType type = getJdbcType();
        return type==JdbcType.DATE || type==JdbcType.TIME || type==JdbcType.TIMESTAMP;
    }

    public boolean isStringType()
    {
        JdbcType type = getJdbcType();
        return type.cls == String.class;
    }

    public boolean isLongTextType()
    {
        JdbcType type = getJdbcType();
        return type == JdbcType.LONGVARCHAR;
    }

    public boolean isBooleanType()
    {
        return getJdbcType() == JdbcType.BOOLEAN;
    }

    public boolean isNumericType()
    {
        return getJdbcType().isNumeric();
    }

    public String getFriendlyTypeName()
    {
        return getFriendlyTypeName(getJavaClass());
    }

    public static String getFriendlyTypeName(Class javaClass)
    {
        if (javaClass.equals(String.class))
            return "Text (String)";
        else if (javaClass.equals(Integer.class) || javaClass.equals(Integer.TYPE) || javaClass.equals(Short.class) || javaClass.equals(Short.TYPE))
            return "Integer";
        else if (javaClass.equals(Double.class) || javaClass.equals(Double.TYPE) || javaClass.equals(BigDecimal.class))
            return "Number (Double)";
        else if (javaClass.equals(Float.class) || javaClass.equals(Float.TYPE))
            return "Number (Float)";
        else if (javaClass.equals(Boolean.class) || javaClass.equals(Boolean.TYPE))
            return "True/False (Boolean)";
        else if (javaClass.equals(Long.class) || javaClass.equals(Long.TYPE))
            return "Long Integer";
        else if (javaClass.equals(File.class))
            return "File";
        else if (java.sql.Date.class.isAssignableFrom(javaClass))
            return "Date";
        else if (Date.class.isAssignableFrom(javaClass))
            return "Date and Time";
        else
            return "Other";
    }

    /** Don't return TYPEs just real java objects */
    public final Class getJavaObjectClass()
    {
        return getJavaClass(true);
    }

    /** Return Class or TYPE, based on isNullable */
    public final Class getJavaClass()
    {
        return getJavaClass(isNullable());
    }

    protected Class getJavaClass(boolean isNullable)
    {
        PropertyType pt = getPropertyType();
        if (pt != null)
            return pt.getJavaType();

        return getJdbcType().getJavaClass(isNullable);
    }

    public void setFacetingBehaviorType(FacetingBehaviorType type)
    {
        _facetingBehaviorType = type;
    }

    public FacetingBehaviorType getFacetingBehaviorType()
    {
        return _facetingBehaviorType;
    }

    public FieldKey getCrosstabColumnDimension()
    {
        return _crosstabColumnDimension;
    }

    public void setCrosstabColumnDimension(FieldKey crosstabColumnDimension)
    {
        _crosstabColumnDimension = crosstabColumnDimension;
    }

    public CrosstabMember getCrosstabColumnMember()
    {
        return _crosstabColumnMember;
    }

    public void setCrosstabColumnMember(CrosstabMember member)
    {
        _crosstabColumnMember = member;
    }

    public void setPHI(PHI phi)
    {
        _phi = phi;
    }

    public PHI getPHI()
    {
        return _phi;
    }

    public String getRedactedText()
    {
        return _redactedText;
    }

    public void setRedactedText(String redactedText)
    {
        _redactedText = redactedText;
    }

    public boolean isExcludeFromShifting()
    {
        return _isExcludeFromShifting;
    }

    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        _isExcludeFromShifting = isExcludeFromShifting;
    }

    public int getScale()
    {
        return _scale;
    }

    public void setScale(int scale)
    {
        _scale = scale;
    }
}
