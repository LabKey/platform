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

package org.labkey.api.jsp.taglib;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ButtonServlet;
import org.labkey.api.jsp.taglib.SimpleTagBase;
import org.labkey.api.util.PageFlowUtil;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class ButtonTag extends SimpleTagBase
{
    String _text;
    String _href;
    String _alt;
    String _onclick;
    String _action;
    String _name;
    String _value;
    String _id;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        if (_href != null)
        {
            out.write("<a href=\"");
            out.write(h(_href));
            out.write("\"");
            if (_onclick != null)
            {
                out.write(" onclick=\"");
                out.write(h(_onclick));
                out.write("\"");
            }
            out.write(">");
            out.write("<img border=0 alt=\"" + PageFlowUtil.filter(_alt != null ? _alt : _text) + "\" src=\"" + ButtonServlet.buttonSrc(_text) + "\">");
            out.write("</a>");
        }
        else
        {
            out.write("<input type=\"image\" src=\"");
            out.write(PageFlowUtil.buttonSrc(_text));
            out.write("\"");
            if (_onclick != null)
            {
                if (_action != null)
                    throw new IllegalArgumentException("onclick and action cannot both be set");
                out.write(" onclick=\"");
                out.write(h(_onclick));
                out.write("\"");
            }
            if (_action != null)
            {
                out.write(" onclick=\"this.form.action='");
                out.write(_action);
                out.write("';this.form.method='POST';\"");
            }
            if (_name != null)
            {
                out.write(" name=\"");
                out.write(h(_name));
                out.write("\"");
            }
            if (_value != null)
            {
                out.write(" value=\"");
                out.write(h(_value));
                out.write("\"");
            }
            if (_id != null)
            {
                out.write(" id=\"");
                out.write(h(_id));
                out.write("\"");
            }
            out.write(">");
        }
    }

    public void setHref(Object value)
    {
        if (value != null)
        {
            _href = value.toString();
        }
    }
    public void setText(String text)
    {
        _text = text;
    }

    public void setOnclick(String onclick)
    {
        _onclick = onclick;
    }
    public void setAction(String action)
    {
        _action = action;
    }

    public void setAction(ActionURL action)
    {
        _action = action.toString();
    }

    public void setAction(Enum action)
    {
        _action = action.toString() + ".view";
    }
    public void setName(String name)
    {
        _name = name;
    }
    public void setValue(String value)
    {
        _value = value;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setAlt(String alt)
    {
        _alt = alt;
    }
}
