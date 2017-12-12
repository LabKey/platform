/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.labkey.api.util.Button;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import javax.servlet.jsp.JspException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ButtonTag extends SimpleTagBase
{
    Boolean _disableOnClick;
    String _text;
    String _href;
    String _alt;
    String _onclick;
    String _action;
    String _name;
    String _value;
    String _id;
    String _target;
    Boolean _submit = true;

    public void doTag() throws JspException, IOException
    {
        Map<String, String> attributes = new HashMap<>();
        Button.ButtonBuilder button = PageFlowUtil.button(_text).id(_id);

        // TODO: This shouldn't have inconsistent logic from Button.java, should just be a pass through
        if (_href != null)
        {
            if (null != _target)
                attributes.put("target", _target);

            button.href(_href).onClick(_onclick).attributes(attributes);
        }
        else
        {
            if (_onclick != null && _action != null)
                throw new IllegalArgumentException("onclick and action cannot both be set");

            String onClickScript = "";
            if (_onclick != null)
                onClickScript = _onclick;
            if (_action != null)
                onClickScript = ("this.form.action='" + _action + "';this.form.method='POST';");

            if (_name != null)
                attributes.put("name", _name);
            if (_value != null)
                attributes.put("value", _value);

            button.submit(_submit).onClick(onClickScript).attributes(attributes);
        }

        if (null != _disableOnClick)
            button.disableOnClick(_disableOnClick);

        getOut().write(button.toString());
    }

    public void setDisableOnClick(Boolean disableOnClick)
    {
        _disableOnClick = disableOnClick;
    }

    public void setHref(Object value)
    {
        _href = value == null ? null : value.toString();
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

    public void setSubmit(Boolean submit)
    {
        _submit = submit;
    }
}
