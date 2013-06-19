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

package org.labkey.api.gwt.client.ui.reports;

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.ui.ChartServiceAsync;
import org.labkey.api.gwt.client.ui.WidgetUpdatable;

import java.util.Iterator;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: Dec 6, 2007
 */
public abstract class AbstractChartPanel
{
    protected GWTChart _chart;
    protected ChartServiceAsync _service;
    protected boolean _isAdmin;
    protected boolean _isGuest;

    public AbstractChartPanel(){}
    public AbstractChartPanel(GWTChart chart, ChartServiceAsync service)
    {
        _chart = chart;
        _service = service;
    }

    public abstract Widget createWidget();
    
    public GWTChart getChart()
    {
        return _chart;
    }

    public ChartServiceAsync getService()
    {
        return _service;
    }

    public void setChart(GWTChart chart)
    {
        _chart = chart;
    }

    public void setService(ChartServiceAsync service)
    {
        _service = service;
    }

    public static class BoundListBox extends ListBox
    {
        public BoundListBox(boolean isMultipleSelect, final WidgetUpdatable updatable)
        {
            super();

            setMultipleSelect(isMultipleSelect);
            setVisibleItemCount(5);
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
                String itemText = getItemText(i);
                if (text.equals(itemText))
                {
                    setItemSelected(i, true);
                    return;
                }
            }
        }
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

    public static class BoundRadioButton extends RadioButton
    {
        public BoundRadioButton(String name, String label, boolean checked, final WidgetUpdatable updatable)
        {
            super(name, label);

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
}
