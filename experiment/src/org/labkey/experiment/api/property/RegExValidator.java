/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.SimpleValidationError;
import org.apache.commons.lang.BooleanUtils;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.regex.Matcher;

/*
* User: Karl Lum
* Date: Aug 11, 2008
* Time: 10:52:22 AM
*/
public class RegExValidator extends DefaultPropertyValidator implements ValidatorKind
{
    public static final String FAIL_ON_MATCH = "failOnMatch";

    public String getName()
    {
        return "Regular Expression Property Validator";
    }

    public String getTypeURI()
    {
        return new Lsid(NAMESPACE, GWTPropertyValidator.TYPE_REGEX).toString();
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
        try {
            Pattern.compile(validator.getExpressionValue());
            return true;
        }
        catch (PatternSyntaxException se)
        {
            errors.add(new SimpleValidationError(se.getMessage()));
        }
        return false;
    }

    public boolean validate(IPropertyValidator validator, Object value, List<ValidationError> errors)
    {
        try {
            Pattern expression = Pattern.compile(validator.getExpressionValue());
            Matcher matcher = expression.matcher(String.valueOf(value));
            boolean failOnMatch = BooleanUtils.toBoolean(validator.getProperties().get(FAIL_ON_MATCH));
            boolean matched = matcher.find();

            if ((matched && failOnMatch) || (!matched && !failOnMatch))
            {
                createErrorMessage(validator, value, errors);
                return false;
            }
            return true;
        }
        catch (PatternSyntaxException se)
        {
            errors.add(new SimpleValidationError(se.getMessage()));
        }
        return false;
    }
}