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
package org.labkey.api.exp.property;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;

import java.util.List;

/*
* User: Karl Lum
* Date: Aug 15, 2008
* Time: 5:12:50 PM
*/
public abstract class DefaultPropertyValidator implements ValidatorKind
{
    public static Lsid createValidatorURI(PropertyValidatorType type)
    {
        return new Lsid("urn:lsid:labkey.com:" + NAMESPACE + ':' + type.getTypeName());
    }

    protected void createErrorMessage(IPropertyValidator validator, PropertyDescriptor field, Object value, List<ValidationError> errors)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Value '");
        sb.append(value);
        sb.append("' for field '");
        sb.append(field.getNonBlankCaption());
        sb.append("' is invalid. ");

        if (validator.getErrorMessage() != null)
            sb.append(validator.getErrorMessage());
        errors.add(new SimpleValidationError(sb.toString()));
    }

    protected boolean comparisonValid(int comparison, String type)
    {
        if (StringUtils.equals("~eq", type))
            return comparison == 0;
        else if (StringUtils.equals("~neq", type))
            return comparison != 0;
        else if (StringUtils.equals("~lte", type))
            return comparison != 1;
        else if (StringUtils.equals("~gte", type))
            return comparison != -1;
        else if (StringUtils.equals("~gt", type))
            return comparison == 1;
        else if (StringUtils.equals("~lt", type))
            return comparison == -1;
        return false;
    }
}