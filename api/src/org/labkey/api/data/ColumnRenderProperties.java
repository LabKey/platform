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
}
