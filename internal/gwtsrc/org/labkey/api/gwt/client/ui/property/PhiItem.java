/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.PhiType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.PropertyPane;

public class PhiItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private HelpPopup _helpPopup = new HelpPopup("Default Value Types", "");
    private FlexTable _phiTable;
    private InlineHTML _phiLabel;

    private ListBox _phiTypes = new ListBox();

    public PhiItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    private void updateEnabledState(FieldType field)
    {
        if (_phiTypes.getItemCount() == 0)
        {
            for (PhiType t : PhiType.values())
                _phiTypes.addItem(t.getLabel(), t.name());
        }

        String phi = field.getPhi();

        for (int i = 0; i < _phiTypes.getItemCount(); i++)
        {
            String currentItemValue = _phiTypes.getValue(i);

            if (currentItemValue.equals(phi))
            {
                _phiTypes.setSelectedIndex(i);
                return;
            }
        }
        _phiTypes.setSelectedIndex(0);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        _phiLabel = new InlineHTML("Default&nbsp;Type");
        labelPanel.add(_phiLabel);
        labelPanel.add(_helpPopup);
        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);

        _phiTable = new FlexTable();
        _phiTable.setWidget(0, 0, _phiTypes);
        flexTable.setWidget(row, INPUT_COLUMN, _phiTable);

        _phiTypes.addClickHandler(createClickHandler());
        _phiTypes.addKeyUpHandler(createKeyUpHandler());

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        String type = _phiTypes.getValue(_phiTypes.getSelectedIndex());

        boolean changed = !type.equals(field.getPhi());
        if (changed)
            field.setPhi(type);
        return changed;
    }


    public void enabledChanged()
    {
        boolean enabled = isEnabled();

        if (enabled)
            removeClass(_phiLabel, "labkey-disabled");
        else
            addClass(_phiLabel, "labkey-disabled");

        _phiTypes.setEnabled(enabled);
        _helpPopup.setVisible(enabled);
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
