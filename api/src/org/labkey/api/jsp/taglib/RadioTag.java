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

import org.labkey.api.jsp.taglib.SimpleTagBase;
import org.apache.commons.lang.ObjectUtils;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class RadioTag extends SimpleTagBase
{
    protected String _name;
    protected Object _value;
    protected Object _currentValue;

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        out.write("<input type=\"radio\" name=\"");
        out.write(h(_name));
        out.write("\" value=\"");
        out.write(h(_value));
        out.write("\"");
        if (isChecked())
        {
            out.write(" checked");
        }
        out.write(">");
    }

    public void setName(String name)
    {
        _name = name;
    }
    public void setValue(Object value)
    {
        _value = value;
    }
    public void setCurrentValue(Object currentValue)
    {
        _currentValue = currentValue;
    }

    public boolean isChecked()
    {
        return ObjectUtils.equals(_value, _currentValue);
    }
}
