package org.labkey.api.query;

/** Mix into a column enum to get a FieldKey from the constant's name. */
public interface EnumFieldKey
{
    String name();

    default FieldKey fieldKey()
    {
        return FieldKey.fromParts(name());
    }
}
