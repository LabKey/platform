/*
 * Copyright (c) 2017 LabKey Corporation
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
import org.labkey.api.gwt.client.PHIType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.util.PropertyUtil;

public class PHIItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private HelpPopup _helpPopup = new HelpPopup("PHI Level", "Protected Health Information level for this field.");
    private FlexTable _phiTable;
    private InlineHTML _phiLabel;

    private ListBox _phiTypes = new ListBox();

    public PHIItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        _phiTypes.setName("phiLevel");
    }

    private void updateState(FieldType field)
    {
        PHIType maxPHI = PHIType.fromString(PropertyUtil.getMaxAllowedPhi());
        if (null == maxPHI)
            maxPHI = PHIType.Restricted;

        boolean canEnable = true;
        String phi = field.getPHI();
        PHIType currentPHI = PHIType.fromString(phi);
        if (null != currentPHI && !currentPHI.isLevelAllowed(maxPHI))
        {
            canEnable = false;
            setCanEnable(false);
        }

        // There's only 1 instance of PhiItem for all fields, so may need to add or remove types
        if (_phiTypes.getItemCount() == 0)
        {
            for (PHIType t : PHIType.values())
                if (!canEnable || t.isLevelAllowed(maxPHI))    // Show level if field cannot be enabled or if level allowed to be set
                    _phiTypes.addItem(t.getLabel(), t.name());
        }
        else
        {
            int maxItem = _phiTypes.getItemCount() - 1;
            int maxAllowed = canEnable ? maxPHI.ordinal() : PHIType.Restricted.ordinal();       // add them all if !canEnable
            if (maxItem < maxAllowed)
            {
                // add items
                for (int i = maxItem; i < maxAllowed; i++)
                {
                    PHIType t = PHIType.fromOrdinal(i + 1);
                    _phiTypes.addItem(t.getLabel(), t.name());
                }
            }
            else if (maxItem > maxAllowed)
            {
                // remove items
                for (int i = maxItem; i > maxAllowed; i--)
                    _phiTypes.removeItem(i);
            }
        }

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

    private void updateEnabledState(DomainType domain, FieldType currentField)
    {
        boolean domainAllowsPhi = domain.allowsPhi(currentField);
        if (!domainAllowsPhi)
            currentField.setPHI(PHIType.NotPHI.toString());

        setCanEnable(domainAllowsPhi && isCurrentPHIAllowed(currentField));
    }

    private boolean isCurrentPHIAllowed(FieldType field)
    {
        PHIType maxPHI = PHIType.fromString(PropertyUtil.getMaxAllowedPhi());

        PHIType currentPHI = PHIType.fromString(field.getPHI());
        return currentPHI.isLevelAllowed(maxPHI);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        FlowPanel labelPanel = new FlowPanel();
        _phiLabel = new InlineHTML("PHI&nbsp;Level");
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

        boolean changed = !type.equals(field.getPHI());
        if (changed)
            field.setPHI(type);
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
        updateState(field);
        updateEnabledState(domain, field);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateState(field);
    }


}
