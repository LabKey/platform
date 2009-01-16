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
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class RequiredItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private CheckBox _requiredCheckBox = new CheckBox();

    public RequiredItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.setWidget(row, LABEL_COLUMN, new Label("Required"));
        flexTable.setWidget(row, INPUT_COLUMN, _requiredCheckBox);

        _requiredCheckBox.addClickListener(createClickListener());
        _requiredCheckBox.addKeyboardListener(createKeyboardListener());

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (_requiredCheckBox.isEnabled())
        {
            boolean changed = !field.isRequired() == _requiredCheckBox.isChecked();
            field.setRequired(_requiredCheckBox.isChecked());
            return changed;
        }
        return false;
    }

    public void enabledChanged()
    {
        _requiredCheckBox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _requiredCheckBox.setChecked(field.isRequired());
    }

    public boolean isChecked()
    {
        return _requiredCheckBox.isChecked();
    }
}