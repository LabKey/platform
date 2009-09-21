/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.query;

import org.labkey.api.util.URLHelper;
import org.labkey.api.util.StringExpression;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.util.Map;
import java.util.Collection;
import java.io.Writer;
import java.io.IOException;

public class LookupURLExpression implements StringExpression
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
        Map row = ctx instanceof RenderContext ? ((RenderContext)ctx).getRow() : ctx;
        URLHelper ret = _base.clone();
        for (Map.Entry<? extends Object, ? extends ColumnInfo> entry : _joinParams.entrySet())
        {
            ColumnInfo column = entry.getValue();
            Object value = column.getValue(row);
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

    public void addParameter(String key, String value)
    {
        _base.addParameter(key, value);
    }

    public void render(Writer out, Map ctx) throws IOException
    {
        out.write(eval(ctx));
    }

    public Collection<? extends ColumnInfo> getQueryColumns()
    {
        return _joinParams.values();
    }

    public LookupURLExpression copy()
    {
        return clone();
    }

    @Override
    protected LookupURLExpression clone()
    {
        try
        {
            LookupURLExpression clone = (LookupURLExpression)super.clone();
            clone._base = this._base.clone();
            return clone;
        }
        catch (CloneNotSupportedException x)
        {
            throw new RuntimeException(x);
        }
    }
}
