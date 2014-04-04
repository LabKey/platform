/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

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
    String _target;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        if (_href != null)
        {
            String attributes = "";
            if (null != _target)
                attributes = "target='" + h(_target) + "'";
            out.write(PageFlowUtil.button(_text).href(_href).onClick(_onclick).attributes(attributes).toString());
        }
        else
        {
            if (_onclick != null && _action != null)
            {
                throw new IllegalArgumentException("onclick and action cannot both be set");
            }
            String onClickScript = "";
            StringBuilder attributes = new StringBuilder();
            if (_onclick != null)
            {
                onClickScript = (h(_onclick));
            }
            if (_action != null)
            {
                onClickScript = ("this.form.action='" + _action + "';this.form.method='POST';");
            }
            if (_name != null)
            {
                attributes.append(" name=\"");
                attributes.append(h(_name));
                attributes.append("\"");
            }
            if (_value != null)
            {
                attributes.append(" value=\"");
                attributes.append(h(_value));
                attributes.append("\"");
            }
            if (_id != null)
            {
                attributes.append(" id=\"");
                attributes.append(h(_id));
                attributes.append("\"");
            }
            out.write(PageFlowUtil.button(_text).submit(true).onClick(onClickScript).attributes(attributes.toString()).toString());
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

    public void setTarget(String target)
    {
        _target = target;
    }
}
