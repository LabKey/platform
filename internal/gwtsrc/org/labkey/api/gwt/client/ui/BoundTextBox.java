/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: jeckels
* Date: Sep 9, 2008
*/
public class BoundTextBox extends HorizontalPanel
{
    protected TextBox _box;
    protected String _caption;
    private boolean _required;

    public BoundTextBox(String caption, String id, String initialValue, final WidgetUpdatable updatable)
    {
        this(caption, id, initialValue, updatable, null);
    }

    public BoundTextBox(String caption, String id, final StringProperty prop)
    {
        this(caption, id, prop.getString(), new WidgetUpdatable(){
            public void update(Widget widget)
            {
                prop.set(((TextBox)widget).getText());
            }
        }, null);
    }

    public BoundTextBox(String caption, String id, String initialValue, final WidgetUpdatable updatable, final DirtyCallback dirtyCallback)
    {
        _caption = caption;
        _box = new TextBox();
        DOM.setElementAttribute(_box.getElement(), "id", id);
        _box.setText(StringUtils.trimToEmpty(initialValue));
        _box.addFocusListener(new FocusListenerAdapter()
        {
            public void onLostFocus(Widget sender)
            {
                updatable.update(sender);
            }
        });
        _box.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                updatable.update(sender);
            }
        });
        _box.addKeyboardListener(new KeyboardListenerAdapter()
        {
            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
                if (dirtyCallback != null)
                {
                    dirtyCallback.setDirty(true);
                }
            }
        });
        add(_box);
    }

    public void setRequired(boolean required)
    {
        _required = required;
    }

    public String validate()
    {
        if (_required)
        {
            if (_box.getText() == null || _box.getText().length() == 0)
                return "\"" + _caption + "\" is required.";
        }
        return null;
    }

    public TextBox getBox()
    {
        return _box;
    }
}