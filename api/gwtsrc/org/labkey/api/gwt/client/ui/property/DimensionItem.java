/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.PropertyType;

/**
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class DimensionItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public DimensionItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("dimension");
    }

    protected String getCheckboxLabelText()
    {
        return "Data Dimension";
    }

    @Override
    protected String getHelpBody()
    {
        return "Data dimensions define logical groupings of measures.  For example, 'Gender' could be a dimension for a " +
                "dataset containing a 'Height' measure, since it may be desirable to study height by gender.";
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isDimension();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setDimension(b);
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
        // Can't pivot on dates or attachments:
        boolean isValidDimension = (type == PropertyType.xsdBoolean  ||
                                    type == PropertyType.xsdDouble ||
                                    type == PropertyType.xsdInt ||
                                    type == PropertyType.xsdString);
        if (!isValidDimension && currentField.isDimension())
            setFieldValue(currentField, false);

        setCanEnable(isValidDimension);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
        updateEnabledState(field);
    }
}