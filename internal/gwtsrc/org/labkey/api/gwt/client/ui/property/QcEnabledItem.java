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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable;

/**
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class QcEnabledItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private final CheckBox qcCheckbox = new CheckBox();
    private final RequiredItem requiredItem;

    public QcEnabledItem(PropertyPane<DomainType, FieldType> propertyPane, RequiredItem requiredItem)
    {
        super(propertyPane);
        this.requiredItem = requiredItem;
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.setWidget(row, LABEL_COLUMN, new Label("QC Enabled"));
        flexTable.setWidget(row, INPUT_COLUMN, qcCheckbox);

        qcCheckbox.addClickListener(createClickListener());
        qcCheckbox.addKeyboardListener(createKeyboardListener());

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        // Called when clicked or keyed

        if (qcCheckbox.isEnabled())
        {
            if (!field.isQcEnabled() == qcCheckbox.isChecked())
            {
                if (requiredItem.isChecked())
                {
                    // TODO: A required item can't have QC. This needs to pop a dialog or disable itself
                }
                field.setQcEnabled(qcCheckbox.isChecked());
                return true;
            }
            // No change
            return false;
        }
        return false;
    }

    public void enabledChanged()
    {
        qcCheckbox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        qcCheckbox.setChecked(field.isQcEnabled());
    }
}