/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.labkey.api.gwt.client.DefaultValueType;
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
 * how the field in rendered in the HTML grids, forms, and pickers
 */
public abstract class ColumnRenderProperties
{
    protected Sort.SortDirection sortDirection = Sort.SortDirection.ASC;
    protected String inputType;
    protected int inputLength = -1;
    protected int inputRows = -1;
    protected String displayWidth;
    protected String format;
    protected String excelFormatString;
    protected String tsvFormatString;

    // property descriptors default to nullable, while columninfos do not; PropertyDescriptor overrides this initializer
    // in its constructor:
    protected boolean nullable = false;
    protected String label;
    protected String name;
    protected String description;
    protected boolean hidden;
    protected Boolean measure;
    protected Boolean dimension;
    protected boolean shownInInsertView = true;
    protected boolean shownInUpdateView = true;
    protected boolean shownInDetailsView = true;
    protected StringExpression url;
    protected Set<String> importAliases = new LinkedHashSet<String>();
    protected DefaultValueType _defaultValueType = null;

    public void copyTo(ColumnRenderProperties to)
    {
        to.sortDirection = sortDirection;
        to.inputType = inputType;
        to.inputLength = inputLength;
        to.inputRows = inputRows;
        to.displayWidth = displayWidth;
        to.format = format;
        to.excelFormatString = excelFormatString;
        to.tsvFormatString = tsvFormatString;
        to.label = label;
        to.hidden = hidden;
        to.shownInInsertView = shownInInsertView;
        to.shownInUpdateView = shownInUpdateView;
        to.shownInDetailsView = shownInDetailsView;
        to.measure = measure;
        to.dimension = dimension;
        to.name = name;
        to.url = url;
        to.importAliases = new LinkedHashSet<String>(importAliases);
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

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
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

    public void setMeasure(boolean measure)
    {
        this.measure = measure;
    }

    public void setDimension(boolean dimension)
    {
        this.dimension = dimension;
    }

    public boolean isDimension()
    {
        // If dimension is unspecified/null, make a best guess based on the type of the field:
        if (dimension == null)
            return isLookup() && !isHidden();
        else
            return dimension.booleanValue();
    }

    public boolean isMeasure()
    {
        // If measure is unspecified/null, make a best guess based on the type of the field:
        if (measure == null)
            return isNumericType() && !isAutoIncrement() && !isLookup() && !isHidden();
        else
            return measure.booleanValue();
    }

    public boolean isNullable()
    {
        return nullable;
    }

    public void setNullable(boolean nullable)
    {
        this.nullable = nullable;
    }

    public Set<String> getImportAliasesSet()
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
            if (alias.indexOf(" ") != -1)
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

    public abstract int getSqlTypeInt();

    public abstract boolean isLookup();

    protected abstract boolean isAutoIncrement();

    private static Pattern STRING_PATTERN = Pattern.compile("[^,; \\t\\n\\f\"]+|\"[^\"]*\"");

    public static Set<String> convertToSet(String s)
    {
        Set<String> result = new LinkedHashSet<String>();
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
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.DATE) ||
                (sqlType == Types.TIME) ||
                (sqlType == Types.TIMESTAMP);
    }

    public boolean isStringType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.CLOB) ||
                (sqlType == Types.CHAR) ||
                (sqlType == Types.VARCHAR) ||
                (sqlType == Types.LONGVARCHAR);
    }

    public boolean isLongTextType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.CLOB) ||
                (sqlType == Types.LONGVARCHAR);
    }

    public boolean isBooleanType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.BOOLEAN) ||
                (sqlType == Types.BIT);
    }

    public boolean isNumericType()
    {
        int sqlType = getSqlTypeInt();
        return (sqlType == Types.INTEGER) ||
                (sqlType == Types.DECIMAL) ||
                (sqlType == Types.DOUBLE) ||
                (sqlType == Types.SMALLINT) ||
                (sqlType == Types.BIGINT) ||
                (sqlType == Types.FLOAT) ||
                (sqlType == Types.NUMERIC) ||
                (sqlType == Types.TINYINT);
    }

    public static String javaTypeFromSqlType(int sqlType, boolean isObj)
    {
        switch (sqlType)
        {
            case Types.DOUBLE:
                if (isObj)
                    return "Double";
                else
                    return "double";
            case Types.BIT:
            case Types.BOOLEAN:
                if (isObj)
                    return "Boolean";
                else
                    return "boolean";
            case Types.INTEGER:
                if (isObj)
                    return "Integer";
                else
                    return "int";
            case Types.TIMESTAMP:
            case Types.TIME:
            case Types.DATE:
                return "java.util.Date";
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
                return "String";
            default:
                return "String";
        }
    }

    public static Class javaClassFromSqlType(int sqlType, boolean isObj)
    {
        switch (sqlType)
        {
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.DECIMAL:
                if (isObj)
                    return Double.class;
                else
                    return Double.TYPE;
            case Types.FLOAT:
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
            case Types.TIME:
            case Types.DATE:
                return java.util.Date.class;
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
        else if (Date.class.isAssignableFrom(javaClass))
            return "Date and Time";
        else
            return "Other";
    }

    /* Don't return TYPEs just real java objects */
    public Class getJavaObjectClass()
    {
        return javaClassFromSqlType(getSqlTypeInt(), true);
    }

    public Class getJavaClass()
    {
        return javaClassFromSqlType(getSqlTypeInt(), isNullable());
    }
}
