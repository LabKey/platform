package org.labkey.api.util;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.util.Map;
import java.io.Writer;
import java.io.IOException;

public class URLExpression implements StringExpressionFactory.StringExpression
{
    protected URLHelper _baseURL;
    protected Map<? extends Object, ColumnInfo> _params;
    public URLExpression(URLHelper baseURL, Map<? extends Object, ColumnInfo> params)
    {
        _baseURL = baseURL;
        _params = params;
    }

    public String eval(Map ctx)
    {
        if (!(ctx instanceof RenderContext))
            return null;
        URLHelper ret = _baseURL.clone();
        for (Map.Entry<? extends Object, ColumnInfo> entry : _params.entrySet())
        {
            Object value = entry.getValue().getValue((RenderContext) ctx);
            if (value != null)
            {
                ret.addParameter(entry.getKey().toString(), String.valueOf(value));
            }
        }
        return ret.toString();
    }

    public void render(Writer out, Map ctx) throws IOException
    {
        out.write(PageFlowUtil.filter(eval(ctx)));
    }

    public String getSource()
    {
        return null;
    }
}
