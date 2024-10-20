/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.io.Writer;

/** Column that renders a link (either fixed or dynamic) with fixed text */
public class UrlColumn extends SimpleDisplayColumn
{
    public UrlColumn(StringExpression urlExpression, String text)
    {
        setDisplayHtml(text);
        setURLExpression(urlExpression);
    }

    public UrlColumn(ActionURL url, String text)
    {
        setDisplayHtml(text);
        setURL(url);
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        Object value = getValue(ctx);
        String url = renderURL(ctx);

        if (value != null && url != null)
            out.write(PageFlowUtil.link(value.toString()).href(url).target(_linkTarget).toString());
    }
}
