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

package org.labkey.api.jsp.taglib;

import org.labkey.api.action.SpringActionController;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;

public class CheckboxTag extends SimpleTagBase
{
    protected String _id;
    protected String _name;
    protected String _value;
    protected Boolean _checked;

    @Override
    public void doTag() throws IOException
    {
        // TODO: HtmlString

        JspWriter out = getOut();
        out.print("<input type=\"checkbox\" id=\"");
        out.print(h(_id));
        out.print("\" name=\"");
        out.print(h(_name));
        out.print("\" value=\"");
        out.print(h(_value));
        out.print("\"");

        if (_checked != null && _checked)
        {
            out.print(" checked");
        }
        out.print(">");
        out.print("<input type=\"hidden\" name=\"");
        out.print(SpringActionController.FIELD_MARKER);
        out.print(h(_name));
        out.print("\">");
    }

    public void setId(String id)
    {
        _id = id;
    }
    
    public void setName(String name)
    {
        _name = name;
    }

    public void setValue(String value)
    {
        _value = value;
    }

    public void setChecked(Boolean checked)
    {
        _checked = checked;
    }
}
