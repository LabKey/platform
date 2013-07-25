/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Mar 5, 2007
 * Time: 4:54:30 PM
 */
public class StringListBox extends ListBox implements HasText, ChangeListener
{
    List/*<String>*/ values;
    boolean allowExtension;
    private String addItemText = "<Add New>";
    private ChangeListenerCollection externalListeners = new ChangeListenerCollection();
    private int lastSelected;

    public StringListBox(String[] values, String selected, boolean allowExtension)
    {
        List/*<String>*/ list = new ArrayList/*<String>*/();
        for (int i = 0; i < values.length; i++)
            list.add(values[i]);

        init(list, selected, allowExtension, false);
    }

    public StringListBox(List/*<String>*/ values, String selected, boolean allowExtension, boolean addSelectedWithoutMatch)
    {
        init(values, selected, allowExtension, addSelectedWithoutMatch);
    }

    private void init(List/*<String>*/ values, String selected, boolean allowExtension, boolean addSelectedWithoutMatch)
    {
        this.values = values;
        this.allowExtension = allowExtension;
        boolean match = false;
        addItem("");
        selected = StringUtils.trimToNull(selected);
        if (null == selected)
            setSelectedIndex(0);

        for (int i = 0; i < values.size(); i++)
        {
            String str = (String) values.get(i);
            addItem(str);
            if (str.equals(selected))
            {
                setSelectedIndex(i + 1);
                match = true;
            }
        }
        if (!match)
        {
            if (null != selected && !allowExtension && !addSelectedWithoutMatch)
                throw new IllegalArgumentException("Item " + selected + " not found in list.");
            if (null != selected)
            {
                addItem(selected);
                setSelectedIndex(values.size() + 1);
            }
        }
        if (allowExtension)
        {
            addItem(addItemText);
        }
        lastSelected = getSelectedIndex();
        super.addChangeListener(this);
    }


    public String getText()
    {
        int itemSelected = getSelectedIndex();
        if (itemSelected < 0)
            return null;

        return getItemText(itemSelected).length() == 0 ? null : getItemText(itemSelected);
    }

    public void setText(String text)
    {
        for (int i = 0; i < getItemCount(); i++)
            if (getItemText(i).equals(text))
            {
                setSelectedIndex(i);
                lastSelected = i;
                return;
            }

        if (allowExtension)
        {
            insertItem(text, getItemCount() - 1);
            setSelectedIndex(getItemCount() - 2);
            lastSelected = getItemCount() - 2;
        }
        else
            throw new IllegalArgumentException("Could not find text " + text);
    }

    public String getAddItemText()
    {
        return addItemText;
    }

    public void setAddItemText(String addItemText)
    {
        this.addItemText = addItemText;
    }


    public void addChangeListener(ChangeListener listener)
    {
        externalListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        externalListeners.remove(listener);
    }

    public void onChange(Widget sender)
    {
        int selectedIndex = getSelectedIndex();
        if (allowExtension && selectedIndex == getItemCount() - 1)
        {
            String val = WindowUtil.prompt("Enter new value.", "");
            if (null != val)
            {
                insertItem(val, getItemCount() - 1);
                setSelectedIndex(getItemCount() - 2);
                lastSelected = getItemCount() -2;
                externalListeners.fireChange(this);
            }
            else //Set back to the old selection on cancel
                setSelectedIndex(lastSelected);
        }
        else
        {
            lastSelected = getSelectedIndex();
            externalListeners.fireChange(this);
        }
    }
}
