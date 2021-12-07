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
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.ValidationError;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public boolean validate(IPropertyValidator validator, ColumnRenderProperties field, @NotNull Object value,
                            List<ValidationError> errors, ValidatorContext validatorCache)
    {
        assert value != null : "Shouldn't be validating a null value";

        Pattern expression = getExpression(TextChoiceValidator.class, validator, validatorCache);
        Matcher matcher = expression.matcher(String.valueOf(value));
        if (matcher.matches())
            return true;

        createErrorMessage(validator, field, value, errors);
        return false;
    }
}