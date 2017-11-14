/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 9:17:09 AM
*/
public interface IPropertyValidator
{
    int getRowId();
    String getName();
    String getTypeURI();
    String getDescription();
    String getExpressionValue();
    String getErrorMessage();
    Map<String, String> getProperties();

    Container getContainer();
    
    ValidatorKind getType();

    void setName(String name);
    void setDescription(String description);
    void setExpressionValue(String expression);
    void setErrorMessage(String message);
    void setProperty(String key, String value);

    IPropertyValidator save(User user, Container container) throws ValidationException;

    boolean validate(PropertyDescriptor prop, Object value, List<ValidationError> errors, ValidatorContext validatorCache);
}