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

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.Widget;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.Map;
import java.util.Iterator;

/**
 * User: jeckels
* Date: Sep 9, 2008
*/
public class BoundListBox extends ListBox
{
    public BoundListBox(String id, boolean isMultipleSelect, final IPropertyWrapper property, final DirtyCallback dirtyCallback)
    {
        this(isMultipleSelect, new WidgetUpdatable(){
            public void update(Widget widget)
            {
                ListBox lb = (ListBox)widget;
                if (lb.getSelectedIndex() != -1)
                    property.set(lb.getValue(lb.getSelectedIndex()));
            }
        }, dirtyCallback);
        DOM.setElementAttribute(getElement(), "id", id);
    }


    public BoundListBox(boolean isMultipleSelect, final WidgetUpdatable updatable, final DirtyCallback dirtyCallback)
    {
        super();

        setMultipleSelect(isMultipleSelect);
        setVisibleItemCount(1);
        addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                updatable.update(sender);
                if (dirtyCallback != null)
                {
                    dirtyCallback.setDirty(true);
                }
            }
        });
    }

    public BoundListBox(boolean isMultipleSelect, final WidgetUpdatable updatable)
    {
        this(isMultipleSelect, updatable, null);
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

    public static class Pair
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