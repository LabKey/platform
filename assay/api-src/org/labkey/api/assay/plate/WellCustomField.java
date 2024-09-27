package org.labkey.api.assay.plate;

/**
 * Represents a custom field, including value, that is assigned to a well location
 */
public class WellCustomField extends PlateCustomField
{
    private Object _value;

    public WellCustomField(PlateCustomField field)
    {
        super(field);
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
