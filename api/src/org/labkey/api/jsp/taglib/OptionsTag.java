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

import org.labkey.api.util.element.Option.OptionBuilder;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OptionsTag extends SimpleTagBase
{
    private Map<?, String> _map;
    private Set<String> _set;
    private Object _value;

    public boolean isSelected(Object test)
    {
        if (_value instanceof Collection)
            return ((Collection)_value).contains(test);
        else
            return Objects.equals(test, _value);
    }

    @Override
    public void doTag() throws IOException
    {
        JspWriter out = getOut();
        if (_map != null)
        {
            for (Map.Entry<?, String> option : _map.entrySet())
            {
                OptionBuilder builder = new OptionBuilder(null != option.getKey() ? option.getKey().toString() : null, option.getValue());
                if (isSelected(option.getKey()))
                    builder.selected(true);
                out.print(builder);
            }
        }
        else if (_set != null)
        {
            for (String value : _set)
            {
                OptionBuilder builder = new OptionBuilder(value, value);
                if (isSelected(value))
                    builder.selected(true);
                out.print(builder);
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
