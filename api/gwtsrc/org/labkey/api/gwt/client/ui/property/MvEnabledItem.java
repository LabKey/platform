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

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.PropertyType;

/**
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class MvEnabledItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    private boolean allowsMvEnabling;

    public MvEnabledItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("mvEnabled");
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
        return field.getMvEnabled();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setMvEnabled(b);
    }

    private static boolean allowsMvEnabling(String rangeURI)
    {
        PropertyType t = PropertyType.fromName(rangeURI);
        return PropertyType.expAttachment != t &&
               PropertyType.expFileLink != t &&
               PropertyType.expMultiLine != t;
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        showPropertyDescriptor(null, field);
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        allowsMvEnabling = allowsMvEnabling(field.getRangeURI());
        checkbox.setChecked(field.getMvEnabled() && allowsMvEnabling);
        setCanEnable(allowsMvEnabling);
    }
}