/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Element;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public abstract class PropertyPaneItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor>
{
    protected PropertyPane _propertyPane;
    private boolean _enabled;
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

    public final void setEnabled(boolean enabled)
    {
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

    protected ClickListener createClickListener()
    {
        return new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
    }

    protected ChangeListener createChangeListener()
    {
        return new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }
        };
    }

    protected KeyboardListener createKeyboardListener()
    {
        return new KeyboardListenerAdapter()
        {
            public void onKeyUp(Widget sender, char keyCode, int modifiers)
            {
                _propertyPane.copyValuesToPropertyDescriptor();
            }

            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
            }
        };
    }

    protected boolean nullSafeEquals(Object o1, Object o2)
    {
        if (o1 == o2)
        {
            return true;
        }

        return o1 != null && o1.equals(o2);
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