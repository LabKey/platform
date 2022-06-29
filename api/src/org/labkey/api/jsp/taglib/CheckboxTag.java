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
import org.labkey.api.util.element.Input.InputBuilder;

import java.io.IOException;

public class CheckboxTag extends SimpleTagBase
{
    protected String _id;
    protected String _name;
    protected String _value;
    protected Boolean _checked;
    protected Boolean _disabled;
    protected String _onChange;


    @Override
    public void doTag() throws IOException
    {
        // TODO: We need a CheckBoxBuilder
        InputBuilder checkbox = new InputBuilder()
            .type("checkbox")
            .id(_id)
            .name(_name)
            .value(_value);

        if (_checked != null && _checked)
            checkbox.checked(true);

        if (_disabled != null && _disabled)
            checkbox.disabled(true);

        if (_onChange != null)
            checkbox.onChange(_onChange);

        getOut().print(checkbox);

        InputBuilder springMarker = new InputBuilder()
            .type("hidden")
            .name(SpringActionController.FIELD_MARKER + _name);

        getOut().print(springMarker);
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

    public void setDisabled(Boolean disabled)
    {
        _disabled = disabled;
    }

    public void setOnChange(String onChange)
    {
        _onChange = onChange;
    }
}
