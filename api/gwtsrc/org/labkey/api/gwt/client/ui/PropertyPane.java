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

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ChangeListenerCollection;
import com.google.gwt.user.client.ui.FlexTable;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.property.PropertyPaneItem;

import java.util.ArrayList;
import java.util.List;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class PropertyPane<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends FlexTable
{
    private FieldType _currentPD;

    private DomainProvider _domainProvider;
    private final String _name;
    private ChangeListenerCollection _changeListeners = new ChangeListenerCollection();

    private int _currentRow;

    private List<PropertyPaneItem<DomainType, FieldType>> _items = new ArrayList<PropertyPaneItem<DomainType, FieldType>>();

    public PropertyPane(DomainProvider provider, String name)
    {
        _domainProvider = provider;
        _name = name;
        DOM.setElementAttribute(getElement(), "name", name + "Pane");
    }

    public String getName()
    {
        return _name;
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
                if (item.isEnabled() && item.copyValuesToPropertyDescriptor(_currentPD))
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
        return getCurrentDomain().getDomainId();
    }

    public void showPropertyDescriptor(FieldType newPD, boolean editable)
    {
        copyValuesToPropertyDescriptor();

        _currentPD = newPD;

        if (_currentPD != null)
        {
            DOM.setStyleAttribute(getElement(), "display", "block");

            for (PropertyPaneItem<DomainType, FieldType> item : _items)
                item.showPropertyDescriptor(getCurrentDomain(), _currentPD);
        }
        else
            DOM.setStyleAttribute(getElement(), "display", "none");

        // note item.showProperty() may set/reset canEdit, so call setEnabled() after 
        setEnabled(editable);
    }

    public FieldType getField()
    {
        return _currentPD;
    }

    public DomainProvider getDomainProvider()
    {
        return _domainProvider;
    }

    public void setDomainProvider(DomainProvider domainProvider)
    {
        _domainProvider = domainProvider;
    }

    public DomainType getCurrentDomain()
    {
        return (DomainType)_domainProvider.getCurrentDomain();
    }

    public ChangeHandler getChangeListener()
    {
        return new ChangeHandler(){
            public void onChange(ChangeEvent e)
            {
                if (_currentPD != null)
                {
                    for (PropertyPaneItem<DomainType, FieldType> item : _items)
                    {
                        item.propertyDescriptorChanged(_currentPD);
                    }
                }
            }
        };
    }
}
