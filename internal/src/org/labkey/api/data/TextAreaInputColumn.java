/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

import java.io.Writer;
import java.io.IOException;

/**
 * User: migra
 * Date: Dec 5, 2005
 * Time: 5:03:15 PM
 */
public class TextAreaInputColumn extends SimpleInputColumn<String>
{
    public TextAreaInputColumn(String name, String value)
    {
        super(name, value, String.class);
    }

    @Override
    public void renderInputHtml(RenderContext ctx, Writer out, Object value) throws IOException
    {
        out.write("<textarea name=\"");
        out.write(name);
        out.write("\" cols=\"150\" rows=\"5\" style=\"width:100%;\">");
        if (null != value)
            out.write(PageFlowUtil.filter(value.toString()));
        out.write("</textarea>");
    }
}
