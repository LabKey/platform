package org.labkey.api.data;

/**
 * User: jeckels
 * Date: Apr 6, 2007
 */
public interface DisplayColumnFactory
{
    public DisplayColumn createRenderer(ColumnInfo colInfo);
}
