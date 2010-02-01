/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.ValidationError;

import java.util.List;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 10:45:38 AM
*/
public interface ValidatorKind
{
    public static final String NAMESPACE = "PropertyValidator";
    
    String getName();
    String getTypeURI();
    String getDescription();

    IPropertyValidator createInstance();
    boolean isValid(IPropertyValidator validator, List<ValidationError> errors);
    boolean validate(IPropertyValidator validator, PropertyDescriptor field, Object value, List<ValidationError> errors, ValidatorContext validatorCache);
}