/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
 * User: jgarms
 * Date: Jan 7, 2009
 */
public class MeasureItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public MeasureItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("measure");
    }

    protected String getCheckboxLabelText()
    {
        return "Measure";
    }

    @Override
    protected String getHelpBody()
    {
        return "Measures are fields that contain data subject to charting and other analysis.  " +
                "Measures are typically numeric results/observations, like weight, or CD4 count.";
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isMeasure();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setMeasure(b);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
    }
}