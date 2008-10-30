/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.RadioButton;

/**
 * Standard GWT radio buttons don't have a value field that can be set.
 * See http://code.google.com/p/google-web-toolkit/issues/detail?id=458
 *
 * User: jgarms
 * Date: Oct 30, 2008
 */
public class RadioButtonWithValue extends RadioButton
{
    private String value;

    public RadioButtonWithValue(String name)
    {
        super(name);
    }

    public RadioButtonWithValue(String name, String label)
    {
        super(name, label);
    }

    public RadioButtonWithValue(String name, String label, boolean asHTML)
    {
        super(name, label, asHTML);
    }

    public void setValue(String value)
    {
        this.value = value;
        Element span = getElement();
        Element input = DOM.getChild(span, 0);
        DOM.setElementAttribute(input, "value", value);
    }

    public String getValue()
    {
        return value;
    }

    public void setName(String name)
    {
        // Setting the name of the radio button actually creates a new element
        // causing any value in the element to be lost.
        String value = getValue();
        super.setName(name);

        if (value != null)
            setValue(value);
    }
}
