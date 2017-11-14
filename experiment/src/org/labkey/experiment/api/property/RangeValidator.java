/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.ValidationError;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.NumberUtilsLabKey;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*
* User: Karl Lum
* Date: Aug 17, 2008
* Time: 12:43:30 PM
*/
public class RangeValidator extends DefaultPropertyValidator implements ValidatorKind
{
    public String getName()
    {
        return "Range Property Validator";
    }

    public String getTypeURI()
    {
        return createValidatorURI(PropertyValidatorType.Range).toString();
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
        //noinspection ConstantConditions
        assert value != null : "Shouldn't be validating a null value";

        for (Pair<String, String> constraint : parseExpression(validator.getExpressionValue()))
        {
            if (!isValid(value, constraint))
            {
                createErrorMessage(validator, field, value, errors);
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private Pair<String, String>[] parseExpression(String expression)
    {
        List<Pair<String, String>> constraints = new ArrayList<>();
        String[] parts = expression.split("&");
        for (String part : parts)
        {
            Pair<String, String> constraint = parsePart(part);
            if (constraint != null)
                constraints.add(constraint);
        }
        return constraints.toArray(new Pair[constraints.size()]);
    }

    private Pair<String, String> parsePart(String expression)
    {
        String[] parts = expression.split("=");
        if (parts.length == 2)
        {
            return new Pair<>(parts[0], parts[1]);
        }
        return null;
    }

    private boolean isValid(Object value, Pair<String, String> constraint)
    {
        if (NumberUtilsLabKey.isNumber(String.valueOf(value)))
        {
            int comparison = Double.compare(NumberUtils.toDouble(String.valueOf(value)), NumberUtils.toDouble(constraint.getValue()));
            return comparisonValid(comparison, constraint.getKey());
        }
        else if (value instanceof Date)
        {
            Date dateConstraint = new Date(DateUtil.parseDateTime(constraint.getValue()));
            int comparison = ((Date) value).compareTo(dateConstraint);

            return comparisonValid(comparison, constraint.getKey());
        }
        return false;
    }
}