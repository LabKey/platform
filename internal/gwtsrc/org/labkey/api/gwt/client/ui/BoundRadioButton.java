/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.RadioButton;
import org.labkey.api.gwt.client.util.IPropertyWrapper;

/**
 * User: matthewb
 * Date: Mar 30, 2010
 * Time: 1:53:26 PM
 */
public class BoundRadioButton extends RadioButton
{
    final Object _boundValue;
    final IPropertyWrapper _prop;
    
    public BoundRadioButton(String name, String label, IPropertyWrapper prop)
    {
        this(name, label, prop, Boolean.TRUE);
    }

    public BoundRadioButton(String name, String label, IPropertyWrapper prop, Object value)
    {
        this(name, label, prop, value, null);
    }

    public BoundRadioButton(String name, String label, IPropertyWrapper prop, Object value, final DirtyCallback dirtyCallback)
    {
        super(name, label);
        _boundValue = value;
        _prop = prop;

        if (value.equals(prop.get()))
            setValue(true);
        
        this.addClickHandler(new ClickHandler(){
            public void onClick(ClickEvent event)
            {
                _prop.set(_boundValue);
            }
        });

        if (null != dirtyCallback)
        {
            addClickHandler(new ClickHandler()
            {
                public void onClick(ClickEvent event)
                {
                    dirtyCallback.setDirty(true);
                }
            });
        }
    }
}
