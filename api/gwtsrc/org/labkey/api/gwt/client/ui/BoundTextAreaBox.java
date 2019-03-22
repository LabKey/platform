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
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

/**
 * User: jeckels
* Date: Sep 9, 2008
*/
public class BoundTextAreaBox extends HorizontalPanel
{
    protected TextArea _box;
    protected String _caption;  // TODO: Remove?  We validate but never use this

    public BoundTextAreaBox(String caption, String id, final StringProperty property, final DirtyCallback dirtyCallback)
    {
        this(caption, id, property.getString(), new WidgetUpdatable(){
            public void update(Widget widget)
            {
                property.set(((TextArea)widget).getText());
            }
        }, dirtyCallback);
    }
    
    public BoundTextAreaBox(String caption, String id, String initialValue, final WidgetUpdatable updatable, final DirtyCallback dirtyCallback)
    {
        _caption = caption;
        _box = new TextArea();
        DOM.setElementAttribute(_box.getElement(), "id", id);
        DOM.setElementAttribute(_box.getElement(), "name", id);
        _box.setText(StringUtils.trimToEmpty(initialValue));
        _box.setWidth("500px");
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
                dirtyCallback.setDirty(true);
            }
        });
        add(_box);
    }

    public String validate()
    {
        if (_box.getText() == null || _box.getText().length() == 0)
            return "\"" + _caption + "\" is required.";
        return null;
    }

    public TextArea getBox()
    {
        return _box;
    }

    public void setEnabled(boolean enabled)
    {
        //this.setDisabled(!enabled);

        if (enabled)
        {
            removeStyleDependentName("disabled");
        }
        else
        {
            addStyleDependentName("disabled");
        }
    }
}