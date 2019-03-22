/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 3:59:29 PM
*/
abstract public class ValidatorDialog extends DialogBox
{
    private UpdateListener _listener;
    protected GWTPropertyValidator _oldProp = new GWTPropertyValidator();

    public ValidatorDialog()
    {
        super(true, false);
    }

    public UpdateListener getListener()
    {
        return _listener;
    }

    public void setListener(UpdateListener listener)
    {
        _listener = listener;
    }

    public interface UpdateListener
    {
        void propertyChanged(GWTPropertyValidator changed);
    }

    protected static class BoundTextBox extends TextBox
    {
        public BoundTextBox(String name, String initialValue, final WidgetUpdatable updatable)
        {
            super();
            setText(initialValue);
            setName(name);
            addFocusListener(new FocusListenerAdapter()
            {
                public void onLostFocus(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            addKeyPressHandler(new KeyPressHandler()
            {
                public void onKeyPress(KeyPressEvent e)
                {
                    //setDirty(true);
                }
            });
        }
    }

    public static class BoundCheckBox extends CheckBox
    {
        public BoundCheckBox(String label, boolean checked, final WidgetUpdatable updatable)
        {
            super(label);

            setValue(checked);

            addFocusListener(new FocusListenerAdapter()
            {
                public void onLostFocus(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            addClickListener(new ClickListener()
            {
                public void onClick(Widget sender)
                {
                    updatable.update(sender);
                    //setDirty(true);
                }
            });
        }
    }

    public static class BoundTextArea extends TextArea
    {
        public BoundTextArea(String name, String initialValue, final WidgetUpdatable updatable)
        {
            super();
            setText(initialValue);
            setName(name);
            addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                }
            });
            addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
                {
                    //setDirty(true);
                }
            });
        }
    }
}