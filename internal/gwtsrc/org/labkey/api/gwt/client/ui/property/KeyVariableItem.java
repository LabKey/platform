
package org.labkey.api.gwt.client.ui.property;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;

/**
 * User: cnathe
 * Date: March 18, 2014
 */
public class KeyVariableItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public KeyVariableItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("keyVariable");
    }

    @Override
    protected String getCheckboxLabelText()
    {
        return "Key Variable";
    }

    @Override
    protected String getHelpBody()
    {
        return "Define which fields in this table/query/etc. are key variables. These variables may be displayed "
                + "as recommended variables when creating new charts/reports/etc.";
    }

    @Override
    protected boolean getFieldValue(FieldType field)
    {
        return field.isKeyVariable();
    }

    @Override
    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setKeyVariable(b);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
    }
}
