package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.io.Writer;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 27, 2006
 * Time: 10:21:24 AM
 */
public class ContainerDisplayColumn extends DataColumn
{
    private Container _c;
    private ActionURL _url;

    public ContainerDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    public ContainerDisplayColumn(ColumnInfo column, ActionURL actionURL)
    {
        this(column);
        _url = actionURL.clone();
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        // Get the container for this row; stash the path in the context so urls can use it
        String id = (String)ctx.get(getColumnInfo().getAlias());
        _c = ContainerManager.getForId(id);

        // Don't render link if container is deleted
        if (null == _c)
        {
            out.write(getFormattedValue(ctx));
        }
        else
        {
            ctx.put("ContainerPath", _c.getPath());  // TODO: Encoded path?
            super.renderGridCellContents(ctx, out);
        }
    }

    public String getFormattedValue(RenderContext ctx)
    {
        StringBuilder sb = new StringBuilder();
        if (_url != null)
        {
            _url.setExtraPath(_c.getPath());
            sb.append("<a href=\"");
            sb.append(_url.getLocalURIString());
            sb.append("\">");
        }
        sb.append(PageFlowUtil.filter(null == _c ? "<deleted>" : _c.getPath()));
        if (_url != null)
        {
            sb.append("</a>");
        }
        return sb.toString();
    }

    public boolean isFilterable()
    {
        return false;
    }
}
