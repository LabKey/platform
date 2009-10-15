/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.util.StringExpression;

import java.util.Set;
import java.util.LinkedHashSet;
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
public class ColumnRenderProperties
{
    protected Sort.SortDirection sortDirection = Sort.SortDirection.ASC;
    protected String inputType;
    protected int inputLength = -1;
    protected int inputRows = -1;
    protected String displayWidth;
    protected String format;
    protected String excelFormatString;
    protected String tsvFormatString;

    protected String label;
    protected String name;
    protected String description;
    protected boolean hidden;
    protected boolean shownInInsertView = true;
    protected boolean shownInUpdateView = true;
    protected boolean shownInDetailsView = true;
    protected StringExpression url;
    protected Set<String> importAliases = new LinkedHashSet<String>();

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
}
