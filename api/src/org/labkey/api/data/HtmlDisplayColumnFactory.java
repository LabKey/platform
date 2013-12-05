package org.labkey.api.data;

/**
 * Created by matthew on 12/5/13.
 */
public class HtmlDisplayColumnFactory implements DisplayColumnFactory
{
    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        DataColumn dc = new DataColumn(colInfo, false);
        dc.setHtmlFiltered(false);
        return dc;
    }
}
