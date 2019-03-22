/*
 * Copyright (c) 2005-2013 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.util.Pair;
import org.labkey.api.view.DisplayElement;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * User: arauch
 * Date: Feb 4, 2005
 * Time: 11:58:36 AM
 */
public class DropDownList extends DisplayElement implements Cloneable
{
    private String _name;
    private List<Pair<String, String>> _list = new ArrayList<>(10);

    public DropDownList(String name)
    {
        _name = name;
    }

    public void add(String value, String display)
    {
        _list.add(new Pair<>(value, display));
    }

    public void add(String display)
    {
        add(display, display);
    }

    private void render(Writer out) throws IOException
    {
        out.write("<select id=\"" + _name + "\" name=\"" + _name + "\">");

        for (Pair<String, String> element : _list)
            out.write("<option value=\"" + element.first + "\">" + element.second + "</option>");

        out.write("</select>");
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (shouldRender(ctx))
            render(out);
    }
}
