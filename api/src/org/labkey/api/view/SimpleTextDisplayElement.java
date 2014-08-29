/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;

import java.io.Writer;
import java.io.IOException;

/**
 * Just renders the text directly into the page, HTML-encoding as needed.
 *
 * User: jeckels
 * Date: Jan 11, 2008
 */
public class SimpleTextDisplayElement extends DisplayElement
{
    private final String _text;
    private final boolean _html;

    /**
     * @param text the text to be rendered
     * @param isHtml whether the text is already in HTML, and thus shouldn't be encoded
     */
    public SimpleTextDisplayElement(String text, boolean isHtml)
    {
        _text = text;
        _html = isHtml;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (_html)
        {
            out.write(_text);
        }
        else
        {
            out.write(PageFlowUtil.filter(_text));
        }
    }
}
