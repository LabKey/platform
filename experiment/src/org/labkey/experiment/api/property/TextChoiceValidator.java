/*
 * Copyright (c) 2008-2018 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.MultiChoice;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TextChoiceValidator extends RegExValidator implements ValidatorKind
{
    @Override
    public String getName()
    {
        return "Text Choice Validator";
    }

    @Override
    public String getTypeURI()
    {
        return createValidatorURI(PropertyValidatorType.TextChoice).toString();
    }

    @Override
    public boolean isValid(IPropertyValidator validator, List<ValidationError> errors)
    {
        return true;
    }

    @Override
    public boolean validate(IPropertyValidator validator, ColumnRenderProperties field, @NotNull Object value,
                            List<ValidationError> errors, ValidatorContext validatorCache, @Nullable Object providedValue)
    {
        assert value != null : "Shouldn't be validating a null value";

        Set<String> validValues = (Set<String>)validatorCache.get(TextChoiceValidator.class, validator.getExpressionValue());
        if (validValues == null)
        {
            validValues = new LinkedHashSet<>(PropertyService.get().getTextChoiceValidatorOptions(validator));
            // Cache the validValues so that it can be reused
            validatorCache.put(TextChoiceValidator.class, validator.getExpressionValue(), validValues);
        }

        Object errorValue = null;

        if (value instanceof String)
        {
            if (!validValues.contains(value))
                errorValue = value;
        }
        else if (value instanceof Collection col)
        {
            if (col.size() > 10)
            {
                errors.add(new SimpleValidationError("At most 10 values are allowed for field '" + field.getNonBlankCaption() + "'"));
                return false;
            }
            for (Object item : col)
            {
                if (null == item || !validValues.contains(Objects.toString(item)))
                {
                    errorValue = item;
                    break;
                }
            }
        }
        else
        {
            errorValue = value;
        }

        if (null != errorValue)
            createErrorMessage(validator, field, value, errors);
        return null == errorValue;
    }
}