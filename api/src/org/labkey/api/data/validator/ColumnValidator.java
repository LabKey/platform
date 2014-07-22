package org.labkey.api.data.validator;

/**
 * Column-level value validation run just before insert or update.
 */
public interface ColumnValidator
{
    public String validate(int rowNum, Object value);

}
