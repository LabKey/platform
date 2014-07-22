package org.labkey.api.data.validator;

import org.labkey.api.exp.MvFieldWrapper;

public abstract class AbstractColumnValidator implements ColumnValidator
{
    // Column label used only for formatting error messages
    final String _columnLabel;

    public AbstractColumnValidator(String columnLabel)
    {
        _columnLabel = columnLabel;
    }

    public String validate(int rowNum, Object o)
    {
        if (o instanceof MvFieldWrapper && !(this instanceof UnderstandsMissingValues))
            return null;
        return _validate(rowNum, o);
    }

    protected abstract String _validate(int rowNum, Object value);
}
