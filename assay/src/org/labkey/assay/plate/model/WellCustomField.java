package org.labkey.assay.plate.model;

import org.labkey.api.exp.property.DomainProperty;

/**
 * Represents a custom field, including value, that is assigned to a well location
 */
public class WellCustomField extends PlateCustomField
{
    private Object _value;

    public WellCustomField()
    {
    }

    public WellCustomField(DomainProperty dp)
    {
        super(dp);
    }

    public Object getValue()
    {
        return _value;
    }

    public void setValue(Object value)
    {
        _value = value;
    }
}
