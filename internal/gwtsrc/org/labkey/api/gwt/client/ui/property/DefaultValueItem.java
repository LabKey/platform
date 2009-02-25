/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.DefaultValueType;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.http.client.URL;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class DefaultValueItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private ListBox _defaultValueTypes = new ListBox();
    private Saveable<GWTDomain> _owner;
    private HelpPopup _helpPopup = new HelpPopup("Default Value Types", "");

    public DefaultValueItem(Saveable<GWTDomain> owner, PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        _owner = owner;
        for (DefaultValueType type : DefaultValueType.values())
            _defaultValueTypes.addItem(type.getLabel(), type.name());
    }

    private void updateEnabledState(FieldType field)
    {
        _defaultValueTypes.setEnabled(!field.isFileType());
        String desiredState = field.isFileType() ? DefaultValueType.FIXED_EDITABLE.name() : field.getDefaultValueType();
        for (int i = 0; i < _defaultValueTypes.getItemCount(); i++)
        {
            String currentItemValue = _defaultValueTypes.getValue(i);
            if (currentItemValue.equals(desiredState))
            {
                _defaultValueTypes.setSelectedIndex(i);
                return;
            }
        }
        _defaultValueTypes.setSelectedIndex(0);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.setWidget(row, LABEL_COLUMN, new Label("Default value"));

        HTML valueLink = new HTML("[<a href=\"#\">set&nbsp;value</a>]");
        valueLink.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                final String baseURL = PropertyUtil.getRelativeURL("setDefaultValues", "list") +
                        "?returnUrl=" + URL.encodeComponent(PropertyUtil.getCurrentURL()) + "&domainId=";

                if (_owner.isDirty())
                {
                    if (Window.confirm("You must save your changes before setting default values.  Save changes?"))
                        _owner.save(new Saveable.SaveListener<GWTDomain>()
                        {
                            public void saveSuccessful(GWTDomain domain)
                            {
                                WindowUtil.setLocation(baseURL + domain.getDomainId());
                            }
                        });
                }
                else
                {
                    WindowUtil.setLocation(baseURL + _propertyPane.getDomainId());
                }
            }
        });

        StringBuilder helpString = new StringBuilder();
        DefaultValueType[] defaultTypes = DefaultValueType.values();
        for (int i = 0; i < defaultTypes.length; i++)
        {
            DefaultValueType type = defaultTypes[i];
            helpString.append("<b>").append(type.getLabel()).append("</b>: ").append(type.getHelpText());
            if (i < defaultTypes.length - 1)
                helpString.append("<br><br>");
        }
        _helpPopup.setBody(helpString.toString());

        FlexTable subTable = new FlexTable();
        subTable.setWidget(0, 0, _defaultValueTypes);
        subTable.setWidget(0, 1, _helpPopup);
        subTable.setWidget(0, 2, valueLink);
        flexTable.setWidget(row, INPUT_COLUMN, subTable);

        _defaultValueTypes.addClickListener(createClickListener());
        _defaultValueTypes.addKeyboardListener(createKeyboardListener());

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (_defaultValueTypes.isEnabled())
        {
            String name = _defaultValueTypes.getValue(_defaultValueTypes.getSelectedIndex());
            boolean changed = name.equals(field.getDefaultValueType());
            field.setDefaultValueType(name);
            return changed;
        }
        return false;
    }

    public void enabledChanged()
    {
        _defaultValueTypes.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        updateEnabledState(field);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateEnabledState(field);
    }

}