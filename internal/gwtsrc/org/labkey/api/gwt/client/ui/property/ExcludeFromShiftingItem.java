/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.PropertyType;

/**
 * User: cnathe
 * Date: 1/21/13
 */
public class ExcludeFromShiftingItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public ExcludeFromShiftingItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("excludeFromShifting");
    }

    protected String getCheckboxLabelText()
    {
        return "Exclude From Shifting";
    }

    @Override
    protected String getHelpBody()
    {
        return "Participant date columns with this property checked will not be shifted on export/publication when "
            + "the \"Shift Participant Dates\" option is selected.";
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isExcludeFromShifting();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setExcludeFromShifting(b);
    }

    @Override
     public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        super.showPropertyDescriptor(domain, field);
        updateEnabledState(field);
    }

    private void updateEnabledState(FieldType currentField)
    {
        PropertyType type = PropertyType.fromURI(currentField.getRangeURI());
        boolean isDateType = (type == PropertyType.xsdDateTime);
        if (!isDateType && currentField.isExcludeFromShifting())
            setFieldValue(currentField, false);

        setCanEnable(isDateType);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
        updateEnabledState(field);
    }
}
