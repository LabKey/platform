/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
 * User: cnathe
 * Date: March 18, 2014
 */
public class RecommendedVariableItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public RecommendedVariableItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("recommendedVariable");
    }

    @Override
    protected String getCheckboxLabelText()
    {
        return "Recommended Variable";
    }

    @Override
    protected String getHelpBody()
    {
        return "Define which fields in this table/query/etc. are important variables. These variables may be displayed "
                + "as recommended variables when creating new charts/reports/etc.";
    }

    @Override
    protected boolean getFieldValue(FieldType field)
    {
        return field.isRecommendedVariable();
    }

    @Override
    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setRecommendedVariable(b);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
    }
}
