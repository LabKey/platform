/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.ValidationError;
import org.labkey.data.xml.ValidatorPropertyType;
import org.labkey.data.xml.ValidatorType;
import org.labkey.data.xml.ValidatorsType;

import java.util.LinkedList;
import java.util.List;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 10:45:38 AM
*/
public interface ValidatorKind
{
    String NAMESPACE = "PropertyValidator";
    
    String getName();
    String getTypeURI();
    String getDescription();

    IPropertyValidator createInstance();
    boolean isValid(IPropertyValidator validator, List<ValidationError> errors);
    boolean validate(IPropertyValidator validator, PropertyDescriptor field, @NotNull Object value, List<ValidationError> errors, ValidatorContext validatorCache);

    // Standard save-validator-to-XML method. ValidatorKind implementations can customize this by overriding.
    default void convertToXml(IPropertyValidator v, ValidatorsType validatorsXml)
    {
        ValidatorType validatorType = validatorsXml.addNewValidator();
        validatorType.setTypeURI(v.getTypeURI());
        validatorType.setName(v.getName());

        if (null != v.getDescription())
            validatorType.setDescription(v.getDescription());
        if (null != v.getErrorMessage())
            validatorType.setErrorMessage(v.getErrorMessage());
        if (null != v.getExpressionValue())
            validatorType.setExpression(v.getExpressionValue());

        v.getProperties().forEach((name, value) -> {
            ValidatorPropertyType pv = validatorType.addNewProperty();
            pv.setName(name);
            pv.setValue(value);
        });
    }

    static List<? extends IPropertyValidator> convertFromXML(ValidatorsType validatorsXml)
    {
        List<IPropertyValidator> list = new LinkedList<>();

        if (null != validatorsXml)
        {
            ValidatorType[] validators = validatorsXml.getValidatorArray();

            for (ValidatorType v : validators)
            {
                IPropertyValidator pv = PropertyService.get().createValidator(v.getTypeURI());
                pv.setName(v.getName());

                if (null != v.getDescription())
                    pv.setDescription(v.getDescription());
                if (null != v.getErrorMessage())
                    pv.setErrorMessage(v.getErrorMessage());
                if (null != v.getExpression())
                    pv.setExpressionValue(v.getExpression());

                for (ValidatorPropertyType prop : v.getPropertyArray())
                    pv.setProperty(prop.getName(), prop.getValue());

                list.add(pv);
            }
        }

        return list;
    }
}