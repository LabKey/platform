package org.labkey.api.query;

import org.labkey.api.util.URLHelper;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.util.Map;
import java.util.Collection;
import java.io.Writer;
import java.io.IOException;

public class LookupURLExpression implements StringExpressionFactory.StringExpression
{
    URLHelper _base;
    Map<? extends Object, ? extends ColumnInfo> _joinParams;
    public LookupURLExpression(URLHelper base, Map<? extends Object, ? extends ColumnInfo> joinParams)
    {
        _base = base;
        _joinParams = joinParams;
    }

    public String eval(Map ctx)
    {
        RenderContext rc = (RenderContext) ctx;
        URLHelper ret = _base.clone();
        for (Map.Entry<? extends Object, ? extends ColumnInfo> entry : _joinParams.entrySet())
        {
            Object value = entry.getValue().getValue(rc);
            if (value == null)
                continue;
            ret.addParameter(String.valueOf(entry.getKey()), String.valueOf(value));
        }
        return ret.toString();
    }

    public String getSource()
    {
        throw new UnsupportedOperationException();
    }

    public void render(Writer out, Map ctx) throws IOException
    {
        out.write(eval(ctx));
    }

    public Collection<? extends ColumnInfo> getQueryColumns()
    {
        return _joinParams.values();
    }
}
