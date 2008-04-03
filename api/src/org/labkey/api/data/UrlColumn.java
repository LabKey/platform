package org.labkey.api.data;

import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.LookupURLExpression;

import java.io.Writer;
import java.io.IOException;
import java.util.Set;

public class UrlColumn extends SimpleDisplayColumn
{
    public UrlColumn(StringExpressionFactory.StringExpression urlExpression, String text)
    {
        setDisplayHtml(text);
        setURL(urlExpression);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        String url = getURL(ctx);
        if (value != null && url != null)
        {
            out.write("[<a href=\"");
            out.write(PageFlowUtil.filter(url));
            out.write("\">");
            out.write(value.toString());
            out.write("</a>]");
        }
    }

    public void addQueryColumns(Set<ColumnInfo> set)
    {
        if (getURLExpression() instanceof LookupURLExpression)
        {
            set.addAll(((LookupURLExpression)getURLExpression()).getQueryColumns());
        }
    }
}
