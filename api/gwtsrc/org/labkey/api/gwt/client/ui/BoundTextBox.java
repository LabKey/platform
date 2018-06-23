/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import com.google.gwt.event.dom.client.*;
import com.google.gwt.user.client.Window;
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
    protected final String _initialValue;
    private final WidgetUpdatable _updateable;
    private final DirtyCallback _dirtyCallback;
    protected TextBox _box;
    protected String _caption;
    private boolean _required;

    public BoundTextBox(String caption, String id, String initialValue, final WidgetUpdatable updatable)
    {
        this(caption, id, initialValue, updatable, null);
    }

    public BoundTextBox(String caption, String id, StringProperty prop)
    {
        this(caption, id, prop, null);
    }

    public BoundTextBox(String caption, String id, final StringProperty prop, DirtyCallback dirtyCallback)
    {
        this(caption, id, prop.getString(), new WidgetUpdatable(){
            public void update(Widget widget)
            {
                prop.set(((TextBox)widget).getText());
            }
        }, dirtyCallback);
    }

    public BoundTextBox(String caption, String id, String initialValue, WidgetUpdatable updatable, final DirtyCallback dirtyCallback)
    {
        _initialValue = null==initialValue ? "" : initialValue;
        _updateable = updatable;
        _dirtyCallback = dirtyCallback;
        _caption = caption;
        _box = new TextBox();
        DOM.setElementAttribute(_box.getElement(), "id", id);
        DOM.setElementAttribute(_box.getElement(), "name", id);
        _box.setText(StringUtils.trimToEmpty(initialValue));
        _box.addBlurHandler(new BlurHandler()
        {
            public void onBlur(BlurEvent event)
            {
                _update((Widget)event.getSource());
            }
        });
        _box.addChangeHandler(new ChangeHandler()
        {
            public void onChange(ChangeEvent change)
            {
                _update((Widget)change.getSource());
            }
        });
        _box.addKeyPressHandler(new KeyPressHandler()
        {
            public void onKeyPress(KeyPressEvent e)
            {
                _dirty();
            }
        });
        _box.addKeyUpHandler(new KeyUpHandler()
        {
            public void onKeyUp(KeyUpEvent e)
            {
                _dirty();
            }
        });
        add(_box);
    }


    public void setRequired(boolean required)
    {
        _required = required;
    }


    void _update(Widget sender)
    {
        if (checkValid())
            _updateable.update(sender);
    }


    void _dirty()
    {
        if (_dirtyCallback != null && !_initialValue.equals(getBox().getText()))
            _dirtyCallback.setDirty(true);
    }


    public boolean checkValid()
    {
        String value = _box.getText();
        String msg = validateValue(value);
        if (null == msg)
        {
            clearErrorFormat(getBox());
            return true;
        }
        else
        {
            setErrorFormat(getBox(), msg, false);
            return false;
        }
    }

    public final String validate()
    {
        return validateValue(getBox().getText());
    }


    protected String validateValue(String text)
    {
        text = text.trim();
        if (_required && (text == null || text.length() == 0))
            return "\"" + _caption + "\" is required.";
        return null;
    }


    public TextBox getBox()
    {
        return _box;
    }



    static void setErrorFormat(Widget w, String message, boolean alert)
    {
        if (null == message)
            message = "illegal value";
        
        w.addStyleName("labkey-textbox-error");
        w.setTitle(message);
        if (alert)
            Window.alert(message);
    }

    static void clearErrorFormat(Widget w)
    {
        w.removeStyleName("labkey-textbox-error");
        w.setTitle("");
    }
}