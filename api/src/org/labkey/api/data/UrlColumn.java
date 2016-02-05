/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

/** Column that renders a link (either fixed or dynamic) with fixed text */
public class UrlColumn extends SimpleDisplayColumn
{
    public UrlColumn(StringExpression urlExpression, String text)
    {
        setDisplayHtml(text);
        setURLExpression(urlExpression);
    }

    public UrlColumn(String url, String text)
    {
        setDisplayHtml(text);
        setURL(url);
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        String url = renderURL(ctx);

        if (value != null && url != null)
        {
            Map<String, String> props;
            if (_linkTarget != null)
            {
                props = Collections.singletonMap("target", _linkTarget);
            }
            else
            {
                props = Collections.emptyMap();
            }
            out.write(PageFlowUtil.textLink(value.toString(), url, null, null, props));
        }
    }
}
