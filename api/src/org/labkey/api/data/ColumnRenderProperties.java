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
    protected String formatString;
    protected String excelFormatString;
    protected String tsvFormatString;

    protected String label;
    protected String name;
    protected String description;
    protected boolean hidden;

    public void copyTo(ColumnRenderProperties to)
    {
        to.sortDirection = sortDirection;
        to.inputType = inputType;
        to.inputLength = inputLength;
        to.inputRows = inputRows;
        to.displayWidth = displayWidth;
        to.formatString = formatString;
        to.excelFormatString = excelFormatString;
        to.tsvFormatString = tsvFormatString;
        to.label = label;
        to.name = name;
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

    public String getFormatString()
    {
        return formatString;
    }

    public void setFormatString(String formatString)
    {
        this.formatString = formatString;
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
}
