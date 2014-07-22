package org.labkey.api.data.validator;

import org.labkey.api.exp.MvFieldWrapper;

/**
 * Validate that there is a value.
 * There seem to be two kinds of required:
 * those that accept MissingValue and those that don't.
 */
public class RequiredValidator extends AbstractColumnValidator implements UnderstandsMissingValues
{
    final boolean allowMV;

    public RequiredValidator(String columnName, boolean allowMissingValueIndicators)
    {
        super(columnName);
        allowMV = allowMissingValueIndicators;
    }

    @Override
    protected String _validate(int rowNum, Object value)
    {
        checkRequired:
        {
            if (null == value || (value instanceof String && ((String)value).length() == 0))
                break checkRequired;

            if (!(value instanceof MvFieldWrapper))
                return null;

            MvFieldWrapper mv = (MvFieldWrapper)value;
            if (null != mv.getValue())
                return null;

            if (!mv.isEmpty() && allowMV)
                return null;
        }

        // DatasetDefinition.importDatasetData:: errors.add("Row " + rowNumber + " does not contain required field " + col.getName() + ".");
        // OntologyManager.insertTabDelimited::  throw new ValidationException("Missing value for required property " + col.getName());
        return "Missing value for required property: " + _columnName;
    }
}
