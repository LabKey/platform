/*
 * Copyright (c) 2015-2026 LabKey Corporation
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
import org.labkey.api.exp.property.DefaultValidatorKind;
import org.labkey.api.exp.property.PropertyValidatorDef;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.ValidationError;
import org.labkey.data.xml.ValidatorsType;

import java.util.List;

/**
 * Compares a text value's length to the field's scale using the expression's operator, e.g. "~lte="; it isn't
 * exported to XML because scale already conveys it
 */
public class LengthValidatorKind extends DefaultValidatorKind implements ValidatorKind
{
    @Override
    public String getName()
    {
        return "Length Property Validator";
    }

    @Override
    public String getTypeURI()
    {
        return createValidatorURI(PropertyValidatorType.TextLength).toString();
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public PropertyValidatorDef createInstance()
    {
        PropertyValidatorDefImpl validator = new PropertyValidatorDefImpl(new PropertyValidatorBean());
        validator.setTypeURI(getTypeURI());

        return validator;
    }

    @Override
    public boolean isValid(PropertyValidatorDef validator, List<ValidationError> errors)
    {
        return true;
    }

    @Override
    public boolean validate(PropertyValidatorDef validator, ColumnRenderProperties field, @NotNull Object value,
                            List<ValidationError> errors, ValidatorContext validatorCache, @Nullable Object providedValue)
    {
        assert value != null : "Shouldn't be validating a null value";
        String[] parts = validator.getExpressionValue().split("=");
        if (parts.length == 2)
        {
            // only 1 operator and 1 value support (e.g. "~lte=42")
            if (!isValid(value, parts[0], field.getScale()))
            {
                createErrorMessage(validator, field, value, errors);
                return false;
            }
        }

        return true;
    }

    private boolean isValid(Object value, String oper, int constraint)
    {
        int comparison = Long.compare(value.toString().length(), constraint);
        return comparisonValid(comparison, oper);
    }

    @Override
    public void convertToXml(PropertyValidatorDef v, ValidatorsType validatorsXml)
    {
        // Override to not write length validator to XML... these validators are conveyed via the "scale" property instead
    }
}
