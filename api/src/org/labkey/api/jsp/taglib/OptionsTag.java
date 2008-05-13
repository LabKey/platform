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
import java.util.Map;
import java.util.Set;
import java.util.AbstractMap;

public class OptionsTag extends SimpleTagBase
{
    Map<?, String> _map;
    Object _value;
    public void doTag() throws JspException, IOException
    {
        JspWriter out = getOut();
        for (Map.Entry<?, String> option : _map.entrySet())
        {
            out.write("\n<option value=\"");
            out.write(h(option.getKey()));
            out.write("\"");
            if (ObjectUtils.equals(option.getKey(), _value))
            {
                out.write(" selected");
            }
            out.write(">");
            out.write(h(option.getValue()));
            out.write("</option>");
        }
    }

    public void setMap(Map<?, String> map)
    {
        _map = map;
    }

    public void setValue(Object value)
    {
        _value = value;
    }

}
