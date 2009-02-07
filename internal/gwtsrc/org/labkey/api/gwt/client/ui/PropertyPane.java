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

package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.property.PropertyPaneItem;

import java.util.*;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class PropertyPane<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends FlexTable
{
    private FieldType _currentPD;

    private final Element _backgroundElement;
    private PropertiesEditor<DomainType, FieldType> _propertiesEditor;
    private ChangeListenerCollection _changeListeners = new ChangeListenerCollection();

    private Image _spacerImage;
    private int _spacerHeight;
    private int _currentRow;

    private List<PropertyPaneItem<DomainType, FieldType>> _items = new ArrayList<PropertyPaneItem<DomainType, FieldType>>();

    public PropertyPane(GWTDomain domain, Element backgroundElement, PropertiesEditor<DomainType, FieldType> propertiesEditor)
    {
        _backgroundElement = backgroundElement;
        _propertiesEditor = propertiesEditor;

        _spacerImage = new Image();
        _spacerImage.setWidth("1px");
        _spacerImage.setHeight("1px");
        _spacerHeight = 1;

        setWidget(_currentRow++, 0, _spacerImage);

        getFlexCellFormatter().setHorizontalAlignment(_currentRow, 0, HasHorizontalAlignment.ALIGN_CENTER);
        getFlexCellFormatter().setColSpan(_currentRow, 0, 2);
        setWidget(_currentRow, 0, new HTML("<b>Additional Properties</b>"));

        _currentRow++;

        propertiesEditor.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                if (_currentPD != null)
                {
                    for (PropertyPaneItem<DomainType, FieldType> item : _items)
                    {
                        item.propertyDescriptorChanged(_currentPD);
                    }
                }
            }
        });
    }

    public void addItem(PropertyPaneItem<DomainType, FieldType> item)
    {
        _items.add(item);
        _currentRow = item.addToTable(this, _currentRow);
    }

    public void copyValuesToPropertyDescriptor()
    {
        if (_currentPD != null)
        {
            boolean changed = false;

            for (PropertyPaneItem<DomainType, FieldType> item : _items)
            {
                if (item.copyValuesToPropertyDescriptor(_currentPD))
                    changed = true;
            }

            if (changed)
            {
                _changeListeners.fireChange(this);
            }
        }
    }

    public void addChangeListener(ChangeListener cl)
    {
        _changeListeners.add(cl);
    }

    private void setEnabled(boolean enabled)
    {
        for (PropertyPaneItem item : _items)
        {
            item.setEnabled(enabled);
        }
    }

    public int getDomainId()
    {
        return _propertiesEditor.getCurrentDomain().getDomainId();
    }

    public void showPropertyDescriptor(FieldType newPD, boolean editable)
    {
        showPropertyDescriptor(newPD, editable, 0);
    }

    public void showPropertyDescriptor(FieldType newPD, boolean editable, int rowAbsoluteY)
    {
        copyValuesToPropertyDescriptor();

        int newSpacerHeight = Math.max(0, rowAbsoluteY - getAbsoluteTop() - 25);
        int bottomOfEditor = _propertiesEditor.getMainPanel().getOffsetHeight() - 5;
        if (newSpacerHeight + (getOffsetHeight() - _spacerHeight) > bottomOfEditor)
        {
            newSpacerHeight = Math.max(0, bottomOfEditor - (getOffsetHeight() - _spacerHeight));
        }
        _spacerImage.setHeight(newSpacerHeight + "px");
        _spacerHeight = newSpacerHeight;

        _currentPD = newPD;

        setEnabled(editable);

        if (_currentPD != null)
        {
            for (PropertyPaneItem<DomainType, FieldType> item : _items)
            {
                item.showPropertyDescriptor(_propertiesEditor.getCurrentDomain(), _currentPD);
            }

            DOM.setStyleAttribute(getElement(), "visibility", "visible");
            DOM.setStyleAttribute(_backgroundElement, "backgroundColor", "#eeeeee");
        }
        else
        {
            DOM.setStyleAttribute(getElement(), "visibility", "hidden");
            DOM.setStyleAttribute(_backgroundElement, "backgroundColor", "#ffffff");
        }
    }
}
