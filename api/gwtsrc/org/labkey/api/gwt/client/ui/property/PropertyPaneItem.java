/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public abstract class PropertyPaneItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor>
{
    protected PropertyPane _propertyPane;
    private boolean _enabled = true;
    private boolean _canEnable = true;
    protected static final int LABEL_COLUMN = 0;
    protected static final int INPUT_COLUMN = 1;

    public PropertyPaneItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        _propertyPane = propertyPane;
    }

    public static void addClass(UIObject uio, String className)
    {
        String clazz = uio.getElement().getAttribute("class");
        if (!clazz.contains(className))
            clazz += " " + className;
        uio.getElement().setAttribute("class", clazz);
    }

    // workaround for GWT not removing class names on Label
    public static void removeClass(UIObject uio, String className)
    {
        String clazz = uio.getElement().getAttribute("class");
        uio.getElement().setAttribute("class", clazz.replaceAll(" ?" + className, ""));
    }

    public abstract int addToTable(FlexTable flexTable, int row);

    /**
     * @param field object to stick values into
     * @return whether or not any values changed
     */
    public abstract boolean copyValuesToPropertyDescriptor(FieldType field);

    protected void setCanEnable(boolean can)
    {
        _canEnable = can;
    }

    protected boolean getCanEnable()
    {
        return _canEnable;
    }
    
    public final void setEnabled(boolean enabled)
    {
        if (!getCanEnable())
            enabled = false;
        if (enabled != _enabled)
        {
            _enabled = enabled;
            enabledChanged();
        }
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    public abstract void enabledChanged();

    public abstract void showPropertyDescriptor(DomainType domainType, FieldType pd);

    protected ClickHandler createClickHandler()
    {
        return new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
    }

    protected ChangeHandler createChangeHandler()
    {
        return new ChangeHandler()
        {
            public void onChange(ChangeEvent e)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
    }

    protected KeyUpHandler createKeyUpHandler()
    {
        return new KeyUpHandler()
        {
            public void onKeyUp(KeyUpEvent event)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
    }

    protected String trimValue(String text)
    {
        if (text == null)
        {
            return null;
        }
        text = text.trim();
        if (text.length() == 0)
        {
            return null;
        }
        return text;
    }

    public void propertyDescriptorChanged(FieldType field)
    {
    }
}