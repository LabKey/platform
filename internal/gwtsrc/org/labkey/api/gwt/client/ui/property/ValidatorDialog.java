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
package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.Map;
import java.util.Iterator;

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
        super(false, true);
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

    public interface WidgetUpdatable
    {
        void update(Widget widget);
    }

    public static class BoundTextBox extends TextBox
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
            addKeyboardListener(new KeyboardListenerAdapter()
            {
                public void onKeyPress(Widget sender, char keyCode, int modifiers)
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

            setChecked(checked);

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

    public static class BoundListBox extends ListBox
    {
        public BoundListBox(boolean isMultipleSelect, final WidgetUpdatable updatable)
        {
            super();

            setMultipleSelect(isMultipleSelect);
            setVisibleItemCount(1);
            addChangeListener(new ChangeListener()
            {
                public void onChange(Widget sender)
                {
                    updatable.update(sender);
                }
            });
        }

        public void setColumns(Map columns)
        {
            clear();
            for (Iterator it = columns.entrySet().iterator(); it.hasNext();)
            {
                Map.Entry entry = (Map.Entry)it.next();
                addItem((String)entry.getKey(), (String)entry.getValue());
            }
        }

        public void setColumns(Pair[] columns)
        {
            clear();
            for (int i=0; i < columns.length; i++)
            {
                addItem(columns[i].getKey(), columns[i].getValue());
            }
        }

        public void setSelected(String[] selected)
        {
            for (int i=0; i < selected.length; i++)
            {
                selectItem(selected[i]);
            }
        }

        public void selectItem(String text)
        {
            for (int i=0; i < getItemCount(); i++)
            {
                String itemText = getValue(i);
                if (StringUtils.equals(text, itemText))
                {
                    setItemSelected(i, true);
                    return;
                }
            }
        }
    }

    public class Pair
    {
        private String _key;
        private String _value;

        public Pair(String key, String value)
        {
            _key = key;
            _value = value;
        }

        public String getKey()
        {
            return _key;
        }

        public void setKey(String key)
        {
            _key = key;
        }

        public String getValue()
        {
            return _value;
        }

        public void setValue(String value)
        {
            _value = value;
        }
    }
}