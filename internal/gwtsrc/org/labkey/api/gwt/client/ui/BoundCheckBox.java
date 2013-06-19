/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.CheckBox;
import org.labkey.api.gwt.client.util.BooleanProperty;

/**
 * User: matthewb
 * Date: Mar 29, 2010
 * Time: 11:36:09 AM
 */
public class BoundCheckBox extends CheckBox
{
    public BoundCheckBox(String id, BooleanProperty property, DirtyCallback dirtyCallback)
    {
        this(id, null, property, dirtyCallback);
    }

    public BoundCheckBox(String id, String name, final BooleanProperty property, final DirtyCallback dirtyCallback)
    {
        super("", true);
        DOM.setElementAttribute(getElement(), "id", id);
        if (name != null)
        {
            setName(name);
        }

        setValue(property.booleanValue());
        
        addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent event)
            {
                property.set(Boolean.TRUE == getValue());
                if (dirtyCallback != null)
                {
                    dirtyCallback.setDirty(true);
                }
            }
        });
    }
}