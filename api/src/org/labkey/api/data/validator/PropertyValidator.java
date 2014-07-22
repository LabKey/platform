package org.labkey.api.data.validator;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.query.ValidationError;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around IPropertyValidator/ValidatorKind
 */
public class PropertyValidator implements ColumnValidator
{
    final String columnName;
    final ValidatorKind kind;
    final IPropertyValidator propertyValidator;
    final PropertyDescriptor pd;
    final List<ValidationError> errors = new ArrayList<>(1);

    public PropertyValidator(String columnName, PropertyDescriptor pd, IPropertyValidator propertyValidator)
    {
        this.columnName = columnName;
        this.propertyValidator = propertyValidator;
        this.kind = propertyValidator.getType();
        this.pd = pd;
    }

    @Override
    public String validate(int rowNum, Object value)
    {
        // CONSIDER: Add ValidatorContext parameter to the ColumnValidator.validate method.
        throw new UnsupportedOperationException("Use validate(rowNum, value, validatorContext) instead");
    }

    public String validate(int rowNum, Object value, ValidatorContext validatorContext)
    {
        // Don't validate null values, #15683, #19352
        if (null == value)
            return null;
        if (kind.validate(propertyValidator, pd , value, errors, validatorContext))
            return null;
        if (errors.isEmpty())
            return null;
        String msg = errors.get(0).getMessage();
        errors.clear();
        return msg;
    }

}
