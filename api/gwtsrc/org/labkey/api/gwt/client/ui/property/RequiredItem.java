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

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class RequiredItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public RequiredItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("required");
    }

    @Override
    protected String getCheckboxLabelText()
    {
        return "Required";
    }

    @Override
    protected String getHelpBody()
    {
        return "When 'Required,' a field cannot be empty.";
    }

    @Override
    protected boolean getFieldValue(FieldType field)
    {
        return field.isRequired();
    }

    @Override
    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setRequired(b);
    }
}