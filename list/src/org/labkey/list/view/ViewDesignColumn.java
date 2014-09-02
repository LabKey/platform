package org.labkey.list.view;

import org.labkey.api.data.UrlColumn;
import org.labkey.api.view.ActionURL;

/**
 * Created by Joe on 8/28/2014.
 */
public class ViewDesignColumn extends UrlColumn
{
    public ViewDesignColumn(ActionURL url)
    {
        super(url.toString(), "View Design");
        setName("Design");
        setGridHeaderClass("");
        setWidth("0");
        addDisplayClass("labkey-design");
    }
}
