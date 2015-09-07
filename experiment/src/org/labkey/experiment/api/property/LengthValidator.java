/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.experiment.api.property;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.ValidationError;
import org.labkey.api.util.NumberUtilsLabKey;

import java.util.List;

/**
 * Created by davebradlee on 8/31/15.
 *
 */
public class LengthValidator extends DefaultPropertyValidator implements ValidatorKind
{
    public String getName()
    {
        return "Length Property Validator";
    }

    public String getTypeURI()
    {
        return createValidatorURI(PropertyValidatorType.Length).toString();
    }

    public String getDescription()
    {
        return null;
    }

    public IPropertyValidator createInstance()
    {
        PropertyValidatorImpl validator = new PropertyValidatorImpl(new PropertyValidator());
        validator.setTypeURI(getTypeURI());

        return validator;
    }

    public boolean isValid(IPropertyValidator validator, List<ValidationError> errors)
    {
        return true;
    }

    public boolean validate(IPropertyValidator validator, PropertyDescriptor field, @NotNull Object value, List<ValidationError> errors, ValidatorContext validatorCache)
    {
        assert value != null : "Shouldn't be validating a null value";
        String[] parts = validator.getExpressionValue().split("=");
        if (parts.length == 2)
        {
            // only 1 operator and 1 value support (e.g. "~lte=42")
            if (!isValid(value, parts[0], parts[1]))
            {
                createErrorMessage(validator, field, value, errors);
                return false;
            }
        }

        return true;
    }

    private boolean isValid(Object value, String oper, String constraint)
    {
        if (NumberUtilsLabKey.isNumber(String.valueOf(constraint)))
        {
            int comparison = Long.compare(value.toString().length(), NumberUtils.toLong(constraint));
            return comparisonValid(comparison, oper);
        }
        return false;
    }
}
