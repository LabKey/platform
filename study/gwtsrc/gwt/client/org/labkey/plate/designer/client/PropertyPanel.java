/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.DOM;

import java.util.*;

import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: brittp
 * Date: Feb 8, 2007
 * Time: 1:23:51 PM
 */
public abstract class PropertyPanel extends DockPanel
{
    private TemplateView _view;
    private FlexTable _propertyTable;
    private Map<String, Object> _propertyTextBoxes;

    private ChangeListener _changeListener = new ChangeListener()
    {
        public void onChange(Widget sender)
        {
            _view.markAsDirty();
        }
    };

    private KeyboardListener _keyboardListener = new KeyboardListenerAdapter()
    {
        public void onKeyPress(Widget sender, char keyCode, int modifiers)
        {
            _view.markAsDirty();
        }
    };

    private MouseListenerAdapter _tooltipListener = new MouseListenerAdapter()
    {
        private PopupPanel _tooltip = new PopupPanel(true);
        {
            _tooltip.add(new Label("Delete"));
            _tooltip.setStyleName("labkey-form-label");
        }

        public void onMouseEnter(Widget sender)
        {
            _tooltip.show();
            int width = _tooltip.getOffsetWidth();
            _tooltip.setPopupPosition(sender.getAbsoluteLeft() - width - 10, sender.getAbsoluteTop());
        }

        public void onMouseLeave(Widget sender)
        {
            _tooltip.hide();
        }
    };
    private ImageButton _addPropertyButton;

    public PropertyPanel(TemplateView view)
    {
        _view = view;
        _propertyTable = new FlexTable();
        add(_propertyTable, CENTER);

        _addPropertyButton = new ImageButton("Add a new property");
        _addPropertyButton.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                PropertyCreationDialog dialog = new PropertyCreationDialog(PropertyPanel.this);
                dialog.show();
            }
        });
    }

    public void redraw(String message)
    {
        _propertyTable.clear();
        _propertyTextBoxes = new HashMap<String, Object>();
        _propertyTable.setWidget(0, 0, new Label(message));
        addNewPropertyButton(1);
        _addPropertyButton.setEnabled(false);
    }

    public Map<String, Object> getProperties()
    {
        Map<String, Object> result = new HashMap<String, Object>();
        for (String name : _propertyTextBoxes.keySet())
            result.put(name, ((TextBox) _propertyTextBoxes.get(name)).getText());
        return result;
    }

    public void redraw(Map<String, Object> properties)
    {
        _propertyTable.clear();
        while (_propertyTable.getRowCount() > 0)
        {
            _propertyTable.removeRow(0);
        }
        _propertyTextBoxes = new HashMap<String, Object>();
        int row = 0;
        if (properties != null)
        {
            List<String> names = new ArrayList<String>(properties.keySet());
            Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
            for (String name : names)
            {
                Object value = properties.get(name);
                insertPropertyRow(row, name, value);
                row++;
            }
        }
        addNewPropertyButton(row);
        _addPropertyButton.setEnabled(true);
    }

    private void addNewPropertyButton(int row)
    {
        _propertyTable.setWidget(row, 0, _addPropertyButton);
        _propertyTable.getFlexCellFormatter().setColSpan(row, 0, 3);
    }

    private void insertPropertyRow(int row, final String name, Object value)
    {
        _propertyTable.setWidget(row, 0, new Label(name));
        _propertyTable.getFlexCellFormatter().setStyleName(row, 0, "labkey-form-label");
        TextBox textBox = new TextBox();
        DOM.setElementAttribute(textBox.getElement(), "id", "property-" + name);
        textBox.addChangeListener(_changeListener);
        textBox.addKeyboardListener(_keyboardListener);
        if (value != null)
        {
            textBox.setText(value.toString());
        }
        _propertyTable.setWidget(row, 1, textBox);
        final Image image = new Image(PropertyUtil.getContextPath() + "/_images/partdelete.gif");
        image.addMouseListener(_tooltipListener);
        image.addClickHandler(new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                deleteProperty(name);
            }
        });
        _propertyTable.setWidget(row, 2, image);
        _propertyTextBoxes.put(name, textBox);
    }

    private void deleteProperty(String name)
    {
        for (int i = 0; i < _propertyTable.getRowCount(); i++)
        {
            Widget widget = _propertyTable.getWidget(i, 0);
            if (widget instanceof Label && ((Label)widget).getText().equals(name))
            {
                _propertyTextBoxes.remove(name);
                _propertyTable.removeRow(i);
                _view.markAsDirty();
                return;
            }
        }
    }

    public boolean addProperty(String propName)
    {
        if (getProperties().containsKey(propName))
        {
            Window.alert("There is already a property with that name.");
            return false;
        }

        int rowIndex = _propertyTable.insertRow(_propertyTable.getRowCount() - 1);
        insertPropertyRow(rowIndex, propName, "");
        _view.markAsDirty();
        return true;
    }
}
