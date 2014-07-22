package org.labkey.api.data.validator;

/**
 * Validate that a string value is not longer than the column's scale.
 */
public class LengthValidator extends AbstractColumnValidator
{
    private final int scale;

    public LengthValidator(String columnLabel, int scale)
    {
        super(columnLabel);
        this.scale = scale;
    }

    @Override
    public String _validate(int rowNum, Object value)
    {
        if (value instanceof String)
        {
            String s = (String)value;
            if (s.length() > scale)
                return "Value is too long for column '" + _columnLabel + "', a maximum length of " + scale + " is allowed.";
        }

        return null;
    }
}
