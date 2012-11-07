package org.labkey.api.reader;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.IOException;

/**
 * User: jeckels
 * Date: 11/2/12
 */
public class ExcelFormatException extends IOException
{
    public ExcelFormatException(InvalidFormatException e)
    {
        super("Unable to open Excel file." + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
    }
}
