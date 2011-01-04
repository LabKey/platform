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
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
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

    private int _uid;
    private String _hoverFunctionName = "hover" + _uid;
    private String _unhoverFunctionName = "unhover" + _uid;
    private String _highlightColor = "yellow";

    public HighlightingDisplayColumn(DisplayColumn column, FieldKey... distinguishingFields)
    {
        super(column);
        _fields = distinguishingFields;
    }

    // Call to set a different highlight color (yellow is the default)
    @SuppressWarnings({"UnusedDeclaration"})
    public void setHighlightColor(String color)
    {
        _highlightColor = color;
    }

    // Override to provide custom javascript that sets the span style on hover
    public String getHoverStyle()
    {
        return "\t\tstyle.backgroundColor = '" + _highlightColor + "';\n";
    }

    // Override to provide custom javascript that resets the span style on unhover
    public String getUnhoverStyle()
    {
        // Note: Setting backgroundColor = null works in every browser except IE; blank string seems to work universally.
        return "\t\tstyle.backgroundColor = '';\n";
    }

    protected String getStyleClass(RenderContext ctx)
    {
        List<Object> values = new ArrayList<Object>(_fields.length);

        for (FieldKey _field : _fields)
            values.add(ctx.get(_field));

        String className = _distinctValuesToClass.get(values);

        if (null == className)
        {
            className = "c" + UniqueID.getRequestScopedUID(ctx.getRequest());
            _distinctValuesToClass.put(values, className);
        }

        return className;
    }

    @Override
    public void renderGridHeaderCell(RenderContext ctx, Writer out) throws IOException, SQLException
    {
        super.renderGridHeaderCell(ctx, out);
        _uid = UniqueID.getRequestScopedUID(ctx.getRequest());
        _hoverFunctionName = "hover" + _uid;
        _unhoverFunctionName = "unhover" + _uid;
    }

    @Override
    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        String styleClass = getStyleClass(ctx);
        out.write("<span class=\"" + styleClass + "\" onmouseover=\"" + _hoverFunctionName + "(this);\" onmouseout=\"" + _unhoverFunctionName + "(this);\">");
        super.renderGridCellContents(ctx, out);
        out.write("</span>");
    }

    @Override
    public void renderGridEnd(RenderContext ctx, Writer out) throws IOException
    {
        super.renderGridEnd(ctx, out);

        String styleMapName = "styleMap" + _uid;
        String stylesheetName = "hdc" + _uid;

        out.write("<script type=\"text/javascript\">\n");

        out.write("var " + styleMapName + " = {};\n" +
            "init(" + styleMapName + ");\n\n" +
            "function init(styleMap)\n" +
            "{\n" +
            "\t// Create a new stylesheet for this column's styles\n" +
            "\tvar cssNode = document.createElement('style');\n" +
            "\tcssNode.type = 'text/css';\n" +
            "\tcssNode.rel = 'stylesheet';\n" +
            "\tcssNode.media = 'screen';\n" +
            "\tcssNode.title = '" + stylesheetName + "';\n" +
            "\tdocument.getElementsByTagName(\"head\")[0].appendChild(cssNode);\n\n" +

            "\t// Get a reference to our new stylesheet\n" +
            "\tvar ss = cssNode.sheet ? cssNode.sheet : cssNode.styleSheet;\n\n" +

            "\t// Add all the styles\n");

        for (String style : _distinctValuesToClass.values())
        {
            out.write("\taddStyle(ss, \"span." + style.toLowerCase() + "\");\n");
        }

        out.write("\n\t// Add all the styles to a name -> style \"map\", which may perform better than iterating all the styles every time\n" +
            "\tvar rules = rules(ss);\n\n" +
            "\t// Force name to lowercase -- different browsers use different casing\n" +
            "\tfor (i = 0; i < rules.length; i++)\n" +
            "\t\tstyleMap[rules[i].selectorText.toLowerCase()] = rules[i].style;\n\n" +
            "\tfunction addStyle(ss, newName)\n" +
            "\t{\n" +
            "\t\tif (ss.addRule)\n" +
            "\t\t{\n" +
            "\t\t\tss.addRule(newName, null, 0);       // IE\n" +
            "\t\t}\n" +
            "\t\telse\n" +
            "\t\t{\n" +
            "\t\t\tss.insertRule(newName + ' { }', 0); // Non-IE\n" +
            "\t\t}\n" +
            "\t}\n" +
            "\n" +
            "\tfunction rules(ss)\n" +
            "\t{\n" +
            "\t\tvar rules = new Array();\n" +
            "\n" +
            "\t\tif (ss.cssRules)\n" +
            "\t\t{\n" +
            "\t\t\trules = ss.cssRules;\n" +
            "\t\t}\n" +
            "\t\telse if (ss.rules)\n" +
            "\t\t{\n" +
            "\t\t\trules = ss.rules;\n" +
            "\t\t}\n" +
            "\n" +
            "\t\treturn rules;\n" +
            "\t}\n" +
            "}\n\n" +
            "function " + _hoverFunctionName + "(el)\n" +
            "{\n" +
            "\tvar style = " + styleMapName + "[(el.tagName + \".\" + el.className).toLowerCase()];\n" +
            "\n" +
            "\tif (style)\n" +
            getHoverStyle() +
            "}\n" +
            "\n" +
            "function " + _unhoverFunctionName + "(el)\n" +
            "{\n" +
            "\tvar style = " + styleMapName + "[(el.tagName + \".\" + el.className).toLowerCase()];\n" +
            "\n" +
            "\tif (style)\n" +
            getUnhoverStyle() +
            "}\n" +
            "</script>");
    }
}
