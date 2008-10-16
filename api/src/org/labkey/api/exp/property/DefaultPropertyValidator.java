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
package org.labkey.api.exp.property;

import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/*
* User: Karl Lum
* Date: Aug 15, 2008
* Time: 5:12:50 PM
*/
public abstract class DefaultPropertyValidator implements ValidatorKind
{
    protected void createErrorMessage(IPropertyValidator validator, String field, Object value, List<ValidationError> errors)
    {
        StringBuffer sb = new StringBuffer();

        sb.append("Value '");
        sb.append(value);
        sb.append("' for field '");
        sb.append(field);
        sb.append("' is invalid. ");

        if (validator.getErrorMessage() != null)
            sb.append(validator.getErrorMessage());
        errors.add(new SimpleValidationError(sb.toString()));
    }
}