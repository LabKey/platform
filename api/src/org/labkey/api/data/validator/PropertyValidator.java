/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
