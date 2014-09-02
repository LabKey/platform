package org.labkey.list.view;

import org.labkey.api.data.UrlColumn;
import org.labkey.api.view.ActionURL;

/**
 * Created by Joe on 8/28/2014.
 */
public class ViewHistoryColumn extends UrlColumn
{
    public ViewHistoryColumn(ActionURL url)
    {
        super(url.toString(), "View History");
        setName("History");
        setGridHeaderClass("");
        setWidth("0");
        addDisplayClass("labkey-history");
    }
}
