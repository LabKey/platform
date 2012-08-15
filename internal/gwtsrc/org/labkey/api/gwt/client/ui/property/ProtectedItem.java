package org.labkey.api.gwt.client.ui.property;

import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;

/**
 * User: cnathe
 * Date: Aug 8, 2012
 */
public class ProtectedItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends CheckboxItem<DomainType, FieldType>
{
    public ProtectedItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        checkbox.setName("protected");
    }

    protected String getCheckboxLabelText()
    {
        return "Protected";
    }

    @Override
    protected String getHelpBody()
    {
        return "Protected columns are those that contain protected or identifiable information about participants, "
            + " such as internal participant IDs, transplant dates, birth dates, etc.";
    }

    protected boolean getFieldValue(FieldType field)
    {
        return field.isProtected();
    }

    protected void setFieldValue(FieldType field, boolean b)
    {
        field.setProtected(b);
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        checkbox.setValue(getFieldValue(field));
    }
}
