package org.labkey.api.data;

import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jan 11, 2008
 */
public class IconDisplayColumn extends DataColumn
{
    private int _height;
    private int _width;
    private String _imageTitle;
    private final ActionURL _linkURL;
    private final String _parameterName;
    private final String _imageURL;

    public IconDisplayColumn(ColumnInfo col, int height, int width, ActionURL linkURL, String parameterName, String imageURL)
    {
        super(col);
        _linkURL = linkURL;
        _parameterName = parameterName;
        _imageURL = imageURL;
        _imageTitle = getCaption();
        super.setCaption("");
        _height = height;
        _width = width;
        setWidth(Integer.toString(_width));
    }

    public void setCaption(String caption)
    {
        _imageTitle = caption;
    }

    public boolean isFilterable()
    {
        return false;
    }

    public boolean isSortable()
    {
        return false;
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        ActionURL linkURL = _linkURL.clone();
        Object value = ctx.getRow().get(getColumnInfo().getAlias());
        if (value != null)
        {
            linkURL.addParameter(_parameterName, value.toString());
            out.write("<a href=\"" + linkURL.getLocalURIString() + "\" title=\"" + PageFlowUtil.filter(_imageTitle) + "\"><img src=\"" + _imageURL + "\" height=\"" + _height + "\" width=\"" + _width + "\"/></a>");
        }
    }
}
