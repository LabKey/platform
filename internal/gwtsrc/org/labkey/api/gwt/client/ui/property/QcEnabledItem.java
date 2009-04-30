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

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.TypePicker;

/**
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class QcEnabledItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    private boolean allowsAllowsQc;

    public QcEnabledItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("allowsQc");
    }

    protected String getCheckboxLabelText()
    {
        return "Missing Value Indicators";
    }

    @Override
    protected String getHelpBody()
    {
        return "A field marked with 'Missing Value Indicators', can hold special values to indicate data " +
                "that has failed review or was originally missing";
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        // Called when clicked or keyed
        // Note that we store our value whether or not we're read-only,
        // since if our type has changed we have to 
        if (!getFieldValue(field) == checkbox.isChecked())
        {
            setFieldValue(field, checkbox.isChecked());
            return true;
        }
        // No change
        return false;
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isQcEnabled();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setQcEnabled(b);
    }

    private static boolean allowsAllowsQc(String rangeURI)
    {
        return !TypePicker.xsdAttachment.equals(rangeURI) &&
                !TypePicker.xsdFileLink.equals(rangeURI) &&
                !TypePicker.xsdMultiLine.equals(rangeURI);
    }

    private void updateEnabledState(FieldType field)
    {
        checkbox.setEnabled(allowsAllowsQc(field.getRangeURI()));
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        showPropertyDescriptor(null, field);
    }

    @Override
    public void enabledChanged()
    {
        checkbox.setEnabled(isEnabled());
    }

    @Override
    public boolean isEnabled()
    {
        return super.isEnabled() && allowsAllowsQc;
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        allowsAllowsQc = allowsAllowsQc(field.getRangeURI());
        checkbox.setChecked(field.isQcEnabled() && allowsAllowsQc);
        updateEnabledState(field);
    }
}