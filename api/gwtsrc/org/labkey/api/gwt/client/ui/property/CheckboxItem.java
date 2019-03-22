/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
import org.labkey.api.gwt.client.ui.HelpPopup;
import com.google.gwt.user.client.ui.*;

/**
 * User: kevink
 */
public abstract class CheckboxItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    protected Label label = null;
    protected CheckBox checkbox = new CheckBox();
    protected HelpPopup helpPopup;

    public CheckboxItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        label = new InlineLabel(getCheckboxLabelText());
        labelPanel.add(label);
        if (getHelpBody() != null)
        {
            helpPopup = new HelpPopup(getCheckboxLabelText(), getHelpBody());
            labelPanel.add(helpPopup);
        }

        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);
        flexTable.setWidget(row, INPUT_COLUMN, checkbox);

        checkbox.addClickHandler(createClickHandler());
        checkbox.addKeyUpHandler(createKeyUpHandler());

        return ++row;
    }

    protected abstract String getCheckboxLabelText();

    protected String getHelpBody()
    {
        return null;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        boolean changed = !getFieldValue(field) == checkbox.getValue();
        setFieldValue(field, checkbox.getValue());
        return changed;
    }

    protected abstract boolean getFieldValue(FieldType field);

    protected abstract void setFieldValue(FieldType field, boolean b);

    public void enabledChanged()
    {
        if (isEnabled())
            removeClass(label, "labkey-disabled");
        else
            addClass(label, "labkey-disabled");
        helpPopup.setVisible(isEnabled());
        checkbox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
    }

    public boolean isChecked()
    {
        return checkbox.getValue();
    }
}