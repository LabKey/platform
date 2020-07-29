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

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Objects;

import static org.labkey.api.util.HtmlString.unsafe;

public class RadioTag extends SimpleTagBase
{
    protected String _name;
    protected String _id;
    protected Object _value;
    protected Object _currentValue;
    protected boolean _disabled=false;

    @Override
    public void doTag() throws IOException
    {
        // TODO: HtmlString

        JspWriter out = getOut();
        out.print(unsafe("<input type=\"radio\" name=\""));
        out.print(h(_name));
        out.print(unsafe("\" value=\""));
        out.print(h(_value));
        out.print(unsafe("\""));
        if (isChecked())
        {
            out.print(unsafe(" checked"));
        }
        if (_id != null)
        {
            out.print(unsafe(" id=\""));
            out.print(h(_id));
            out.print(unsafe("\""));
        }
        if (_disabled)
        {
            out.print(unsafe(" disabled"));
        }
        out.print(unsafe(">"));
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setValue(Object value)
    {
        _value = value;
    }
    public void setCurrentValue(Object currentValue)
    {
        _currentValue = currentValue;
    }

    public void setIsDisabled(boolean disabled)
    {
        _disabled = disabled;
    }

    public boolean isChecked()
    {
        return Objects.equals(_value, _currentValue);
    }
}
