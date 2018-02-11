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
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.io.File;
import java.sql.Types;
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
    protected Sort.SortDirection sortDirection = Sort.SortDirection.ASC;
    protected String inputType;
    protected int inputLength = -1;
    protected int inputRows = -1;
    protected String displayWidth;
    protected String format;
    protected String excelFormatString;
    protected String tsvFormatString;
    protected StringExpression textExpression;
    protected int scale = 0;

    protected String propertyURI;
    protected String conceptURI;
    protected String rangeURI;
    protected PropertyType propertyType;

    // property descriptors default to nullable, while columninfos do not; PropertyDescriptor overrides this initializer
    // in its constructor:
    protected boolean nullable = false;
    protected boolean required = false;
    protected String label;
    /** The column's label, without any prefixes from parent lookups */
    protected String shortLabel;
    protected String description;
    protected boolean hidden;
    protected Boolean measure;
    protected Boolean dimension;
    protected Boolean recommendedVariable = false;
    protected DefaultScaleType defaultScale = DefaultScaleType.LINEAR;
    protected boolean shownInInsertView = true;
    protected boolean shownInUpdateView = true;
    protected boolean shownInDetailsView = true;
    protected StringExpression url;
    protected String urlTargetWindow;
    protected String urlCls;
    protected Set<String> importAliases = new LinkedHashSet<>();
    protected DefaultValueType _defaultValueType = null;
    protected FacetingBehaviorType facetingBehaviorType = FacetingBehaviorType.AUTOMATIC;
    protected PHI phi = PHI.NotPHI;
    protected String redactedText = null;
    protected Boolean isExcludeFromShifting = false;

    protected FieldKey crosstabColumnDimension;
    protected CrosstabMember crosstabColumnMember;

    public void copyTo(ColumnRenderProperties to)
    {
        to.sortDirection = sortDirection;
        to.setInputType(getInputType());
        to.setInputLength(getInputLength());
        to.setInputRows(getInputRows());
        to.nullable = nullable;
        to.required = required;
        to.displayWidth = displayWidth;
        to.format = format;
        to.excelFormatString = excelFormatString;
        to.tsvFormatString = tsvFormatString;
        to.textExpression = textExpression;
        to.label = label;
        to.shortLabel = shortLabel;
        to.description = description;
        to.hidden = hidden;
        to.shownInInsertView = shownInInsertView;
        to.shownInUpdateView = shownInUpdateView;
        to.shownInDetailsView = shownInDetailsView;
        to.measure = measure;
        to.dimension = dimension;
        to.recommendedVariable = recommendedVariable;
        to.defaultScale = defaultScale;
        to.url = url;
        to.importAliases = new LinkedHashSet<>(importAliases);
        to.facetingBehaviorType = facetingBehaviorType;
        to.crosstabColumnMember = crosstabColumnMember;
        to.phi = phi;
        to.redactedText = redactedText;
        to.isExcludeFromShifting = isExcludeFromShifting;
        to.scale = scale;
        to.propertyURI = propertyURI;
        to.conceptURI = conceptURI;
        to.rangeURI = rangeURI;
        to.propertyType = propertyType;
        to._defaultValueType = _defaultValueType;
    }

    public Sort.SortDirection getSortDirection()
    {
        return sortDirection;
    }

    public void setSortDirection(Sort.SortDirection sortDirection)
    {
        this.sortDirection = sortDirection;
    }

    public String getInputType()
    {
        return inputType;
    }

    public void setInputType(String inputType)
    {
        this.inputType = inputType;
    }

    public int getInputLength()
    {
        return inputLength;
    }

    public void setInputLength(int inputLength)
    {
        this.inputLength = inputLength;
    }

    public int getInputRows()
    {
        return inputRows;
    }

    public void setInputRows(int inputRows)
    {
        this.inputRows = inputRows;
    }

    public String getDisplayWidth()
    {
        return displayWidth;
    }

    public void setDisplayWidth(String displayWidth)
    {
        this.displayWidth = displayWidth;
    }

    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }

    public String getExcelFormatString()
    {
        return excelFormatString;
    }

    public void setExcelFormatString(String excelFormatString)
    {
        this.excelFormatString = excelFormatString;
    }

    public String getTsvFormatString()
    {
        return tsvFormatString;
    }

    public void setTsvFormatString(String tsvFormatString)
    {
        this.tsvFormatString = tsvFormatString;
    }

    public StringExpression getTextExpression()
    {
        return textExpression;
    }

    public void setTextExpression(StringExpression expr)
    {
        this.textExpression = expr;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getShortLabel()
    {
        return shortLabel == null ? getLabel() : shortLabel;
    }

    public void setShortLabel(String shortLabel)
    {
        this.shortLabel = shortLabel;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public boolean isHidden()
    {
        return hidden;
    }

    public void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    public boolean isShownInDetailsView()
    {
        return shownInDetailsView;
    }

    public void setShownInDetailsView(boolean shownInDetailsView)
    {
        this.shownInDetailsView = shownInDetailsView;
    }

    public boolean isShownInInsertView()
    {
        return shownInInsertView;
    }

    public void setShownInInsertView(boolean shownInInsertView)
    {
        this.shownInInsertView = shownInInsertView;
    }

    public boolean isShownInUpdateView()
    {
        return shownInUpdateView;
    }

    public void setShownInUpdateView(boolean shownInUpdateView)
    {
        this.shownInUpdateView = shownInUpdateView;
    }

    public StringExpression getURL()
    {
        return this.url;
    }

    public void setURL(StringExpression url)
    {
        this.url = url;
    }

    public String getURLTargetWindow()
    {
        return urlTargetWindow;
    }

    public void setURLTargetWindow(String urlTargetWindow)
    {
        this.urlTargetWindow = urlTargetWindow;
    }

    public String getURLCls()
    {
        return urlCls;
    }

    public void setURLCls(String urlCls)
    {
        this.urlCls = urlCls;
    }

    public boolean isRecommendedVariable()
    {
        return recommendedVariable;
    }

    public void setRecommendedVariable(boolean recommendedVariable)
    {
        this.recommendedVariable = recommendedVariable;
    }

    public DefaultScaleType getDefaultScale()
    {
        return defaultScale;
    }

    public void setDefaultScale(DefaultScaleType defaultScale)
    {
        this.defaultScale = defaultScale;
    }

    public void setMeasure(boolean measure)
    {
        this.measure = measure;
    }

    public void setDimension(boolean dimension)
    {
        this.dimension = dimension;
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
        if (dimension == null)
            return inferIsDimension(getName(), isLookup(), isHidden());
        else
            return dimension;
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
        if (measure == null)
            return inferIsMeasure(getName(), getLabel(), isNumericType(), isAutoIncrement(), isLookup(), isHidden());
        else
            return measure;
    }

    /** value must not be null/empty */
    public boolean isNullable()
    {
        return nullable;
    }

    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    /** value must not be null/empty OR a missing value indicator must be provided */
    public boolean isRequired()
    {
        // !nullable is stricter and implies required
        return !nullable || required;
    }

    /** Returns the 'raw' value of required which is useful for copying attributes.  see isRequired() */
    public boolean isRequiredSet()
    {
        return required;
    }

    public void setRequired(boolean required)
    {
        this.required = required;
    }

    @NotNull
    public Set<String> getImportAliasSet()
    {
        return importAliases;
    }

    public void setImportAliasesSet(Set<String> importAliases)
    {
        assert importAliases != null;
        this.importAliases = importAliases;
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
        if (propertyType == null && getRangeURI() != null)
            propertyType = PropertyType.getFromURI(getConceptURI(), getRangeURI(), null);

        return propertyType;
    }

    @Override
    public String getPropertyURI()
    {
        return propertyURI;
    }

    public String getConceptURI()
    {
        return conceptURI;
    }

    public String getRangeURI()
    {
        return rangeURI;
    }

    @Deprecated
    public int getSqlTypeInt()
    {
        return getJdbcType().sqlType;
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

    public static Class javaClassFromSqlType(int sqlType, boolean isObj)
    {
        switch (sqlType)
        {
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                if (isObj)
                    return Double.class;
                else
                    return Double.TYPE;
            case Types.REAL:
                if (isObj)
                    return Float.class;
                else
                    return Float.TYPE;
            case Types.BIT:
            case Types.BOOLEAN:
                if (isObj)
                    return Boolean.class;
                else
                    return Boolean.TYPE;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                if (isObj)
                    return Integer.class;
                else
                    return Integer.TYPE;
            case Types.BIGINT:
                if (isObj)
                    return Long.class;
                else
                    return Long.TYPE;
            case Types.TIMESTAMP:
                return java.sql.Timestamp.class;
            case Types.TIME:
                return java.sql.Time.class;
            case Types.DATE:
                return java.sql.Date.class;
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
                return String.class;
            default:
                return String.class;
        }
    }

    public String getFriendlyTypeName()
    {
        return getFriendlyTypeName(getJavaClass());
    }

    public static String getFriendlyTypeName(Class javaClass)
    {
        if (javaClass.equals(String.class))
            return "Text (String)";
        else if (javaClass.equals(Integer.class) || javaClass.equals(Integer.TYPE))
            return "Integer";
        else if (javaClass.equals(Double.class) || javaClass.equals(Double.TYPE))
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
    public Class getJavaObjectClass()
    {
        PropertyType pt = getPropertyType();
        if (pt != null)
            return pt.getJavaType();

        return javaClassFromSqlType(getSqlTypeInt(), true);
    }

    public Class getJavaClass()
    {
        PropertyType pt = getPropertyType();
        if (pt != null)
            return pt.getJavaType();

        return javaClassFromSqlType(getSqlTypeInt(), isNullable());
    }

    public void setFacetingBehaviorType(FacetingBehaviorType type)
    {
        facetingBehaviorType = type;
    }

    public FacetingBehaviorType getFacetingBehaviorType()
    {
        return facetingBehaviorType;
    }

    public FieldKey getCrosstabColumnDimension()
    {
        return crosstabColumnDimension;
    }

    public void setCrosstabColumnDimension(FieldKey crosstabColumnDimension)
    {
        this.crosstabColumnDimension = crosstabColumnDimension;
    }

    public CrosstabMember getCrosstabColumnMember()
    {
        return crosstabColumnMember;
    }

    public void setCrosstabColumnMember(CrosstabMember member)
    {
        this.crosstabColumnMember = member;
    }

    public void setPHI(PHI phi)
    {
        this.phi = phi;
    }

    public PHI getPHI()
    {
        return phi;
    }

    public String getRedactedText()
    {
        return redactedText;
    }

    public void setRedactedText(String redactedText)
    {
        this.redactedText = redactedText;
    }

    public boolean isExcludeFromShifting()
    {
        return isExcludeFromShifting;
    }

    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        this.isExcludeFromShifting = isExcludeFromShifting;
    }

    public int getScale()
    {
        return scale;
    }

    public void setScale(int scale)
    {
        this.scale = scale;
    }
}
