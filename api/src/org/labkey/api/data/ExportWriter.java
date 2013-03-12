package org.labkey.api.data;

/**
 * User: jeckels
 * Date: 3/11/13
 */
public interface ExportWriter
{
    /** @return the number of data rows exported by this writer */
    public int getDataRowCount();
}
