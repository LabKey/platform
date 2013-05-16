/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OptionsTag extends SimpleTagBase
{
    Map<?, String> _map;
    Set<String> _set;
    Object _value;

    public boolean isSelected(Object test)
    {
        if (_value instanceof Collection)
            return ((Collection)_value).contains(test);
        else
            return Objects.equals(test, _value);
    }

    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        if (_map != null)
        {
            for (Map.Entry<?, String> option : _map.entrySet())
            {
                out.write("\n<option value=\"");
                out.write(h(option.getKey()));
                out.write("\"");
                if (isSelected(option.getKey()))
                {
                    out.write(" selected");
                }
                out.write(">");
                out.write(h(option.getValue()));
                out.write("</option>");
            }
        }
        else if (_set != null)
        {
            for (String value : _set)
            {
                out.write("\n<option value=\"");
                out.write(h(value));
                out.write("\"");
                if (isSelected(value))
                {
                    out.write(" selected");
                }
                out.write(">");
                out.write(h(value));
                out.write("</option>");
            }
        }
        else
        {
            throw new IllegalArgumentException("Either 'map' or 'set' is required for the labkey:options tag.");
        }
    }

    public void setMap(Map<?, String> map)
    {
        _map = map;
    }

    public void setSet(Set<String> set)
    {
        _set = set;
    }

    public void setValue(Object value)
    {
        _value = value;
    }

}
