/*
 * Copyright (c) 2010 LabKey Corporation
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

import org.labkey.api.query.FieldKey;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/*
* User: adam
* Date: Dec 19, 2010
* Time: 8:38:17 PM
*/
public class HighlightingDisplayColumn extends DisplayColumnDecorator
{
    private final LinkedHashMap<List<Object>, String> _distinctValuesToClass = new LinkedHashMap<List<Object>, String>();
    private final FieldKey[] _fields;

    private int _counter = 0;

    public HighlightingDisplayColumn(DisplayColumn column, FieldKey... distinguishingFields)
    {
        super(column);
        _fields = distinguishingFields;
    }

    protected String getStyleClass(RenderContext ctx)
    {
        List<Object> values = new ArrayList<Object>(_fields.length);

        for (FieldKey _field : _fields)
            values.add(ctx.get(_field));

        String className = _distinctValuesToClass.get(values);

        if (null == className)
        {
            className = "c" + _counter++;
            _distinctValuesToClass.put(values, className);
        }

        return className;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String styleClass = getStyleClass(ctx);
        out.write("<span class=\"" + styleClass + "\" onmouseover=\"hover(this);\" onmouseout=\"unhover(this);\">");
        super.renderGridCellContents(ctx, out);
        out.write("</span>");
    }

    @Override
    public void renderGridEnd(Writer out) throws IOException
    {
        super.renderGridEnd(out);

        out.write("\n<style type=\"text/css\" title=\"hdc\">\n");

        for (String style : _distinctValuesToClass.values())
        {
            out.write("span." + style + " {}\n");
        }

        out.write("</style>\n");

        out.write("<script type=\"text/javascript\">\n");

        out.write("var rules = new Array();\n" +
                "\n" +
                "for (i = 0; i < document.styleSheets.length; i++)\n" +
                "{\n" +
                "\tvar ss = document.styleSheets[i];\n" +
                "\n" +
                "\tif (ss.title == \"hdc\")\n" +
                "\t{\n" +
                "\t\tif (ss.cssRules)\n" +
                "\t\t{\n" +
                "\t\t\trules = ss.cssRules;\n" +
                "\t\t}\n" +
                "\t\telse if (ss.rules)\n" +
                "\t\t{\n" +
                "\t\t\trules = ss.rules;\n" +
                "\t\t}\n\n" +
                "\t\tbreak;\n" +
                "\t}\n" +
                "}\n\n" +
                "function hover(el)\n" +
                "{\n" +
                "\tvar style = getStyle(el.tagName + \".\" + el.className);\n" +
                "\n" +
                "\tif (style)\n" +
                "\t\tstyle.backgroundColor = 'yellow';\n" +
                "}\n" +
                "\n" +
                "function unhover(el)\n" +
                "{\n" +
                "\tvar style = getStyle(el.tagName + \".\" + el.className);\n" +
                "\n" +
                "\tif (style)\n" +
                "\t\tstyle.backgroundColor = null;\n" +
                "}\n" +
                "\n" +
                "function getStyle(className)\n" +
                "{\n" +
                "\tvar lcName = className.toLowerCase();  // different browsers change the case of tagname & rule selector text, so force everything to lowercase\n" +
                "\n" +
                "\tfor (i = 0; i < rules.length; i++)\n" +
                "\t\tif (rules[i].selectorText.toLowerCase() == lcName)\n" +
                "\t\t\treturn rules[i].style;\n" +
                "\n" +
                "\treturn null;\n" +
                "}\n" +
                "</script>");
    }
}
