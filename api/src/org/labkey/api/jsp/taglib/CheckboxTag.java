/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;

public class CheckboxTag extends SimpleTagBase
{
    protected String _id;
    protected String _name;
    protected Object _value;
    protected Boolean _checked;
    protected Set _checkedSet;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        out.write("<input type=\"checkbox\" id=\"");
        out.write(h(_id));
        out.write("\" name=\"");
        out.write(h(_name));
        out.write("\" value=\"");
        out.write(h(_value));
        out.write("\"");

        if (_checked != null && _checked)
        {
            out.write(" checked");
        }
        else if (_checkedSet != null && _checkedSet.contains(_value))
        {
            out.write(" checked");
        }
        out.write(">");
        out.write("<input type=\"hidden\" name=\"");
        out.write(SpringActionController.FIELD_MARKER);
        out.write(h(_name));
        out.write("\">");
    }

    public void setId(String id)
    {
        _id = id;
    }
    
    public void setName(String name)
    {
        _name = name;
    }

    public void setValue(Object value)
    {
        _value = value;
    }

    public void setChecked(Boolean checked)
    {
        _checked = checked;
    }

    public void setCheckedSet(Set set)
    {
        _checkedSet = set;
    }
}
