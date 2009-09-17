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

package org.labkey.api.util;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.RenderContext;

import java.util.Map;
import java.io.Writer;
import java.io.IOException;

public class URLExpression implements StringExpression
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

    public void addParameter(String key, String value)
    {
        _baseURL.addParameter(key, value);
    }
}
