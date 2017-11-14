/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Table;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Aug 11, 2008
* Time: 1:20:17 PM
*/
public class PropertyValidatorImpl implements IPropertyValidator
{
    private PropertyValidator _validator;
    private PropertyValidator _validatorOld;
    private boolean _deleted;

    public PropertyValidatorImpl(PropertyValidator validator)
    {
        _validator = validator;
    }

    public String getName()
    {
        return _validator.getName();
    }

    public void setName(String name)
    {
        edit().setName(name);
    }

    public String getDescription()
    {
        return _validator.getDescription();
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public String getTypeURI()
    {
        return _validator.getTypeURI();
    }

    public void setTypeURI(String typeURI)
    {
        edit().setTypeURI(typeURI);
    }

    public String getExpressionValue()
    {
        return _validator.getExpression();
    }

    public void setExpressionValue(String expression)
    {
        edit().setExpression(expression);
    }

    public Container getContainer()
    {
        return ContainerManager.getForId(_validator.getContainer());
    }

    public void setContainer(String container)
    {
        edit().setContainer(container);
    }

    public void setRowId(int rowId)
    {
        edit().setRowId(rowId);
    }

    public int getRowId()
    {
        return _validator.getRowId();
    }

    public String getErrorMessage()
    {
        return _validator.getErrorMessage();
    }

    public Map<String, String> getProperties()
    {
        return PageFlowUtil.mapFromQueryString(_validator.getProperties());
    }

    public void setErrorMessage(String message)
    {
        edit().setErrorMessage(message);
    }

    public void setProperty(String key, String value)
    {
        Map<String, String> props = getProperties();
        props.put(key, value);
        edit().setProperties(PageFlowUtil.toQueryString(props.entrySet()));
    }

    public ValidatorKind getType()
    {
        return PropertyService.get().getValidatorKind(getTypeURI());
    }

    public IPropertyValidator save(User user, Container container) throws ValidationException
    {
        ValidatorKind kind = getType();
        List<ValidationError> errors = new ArrayList<>();

        if (kind != null && !kind.isValid(this, errors))
        {
            throw new ValidationException(errors);
        }

        if (isNew())
        {
            setContainer(container.getId());
            return new PropertyValidatorImpl(Table.insert(user, DomainPropertyManager.get().getTinfoValidator(), _validator));
        }
        else
            return new PropertyValidatorImpl(Table.update(user, DomainPropertyManager.get().getTinfoValidator(), _validator, getRowId()));
    }

    public void delete()
    {
        _deleted = true;
    }

    public boolean isDeleted()
    {
        return _deleted;
    }
    
    public boolean validate(PropertyDescriptor prop, Object value, List<ValidationError> errors, ValidatorContext validatorCache)
    {
        // Don't validate null values, #15683
        if (null == value)
            return true;

        ValidatorKind kind = getType();

        if (kind != null)
            return kind.validate(this, prop, value, errors, validatorCache);
        else
            errors.add(new SimpleValidationError("Validator type : " + getTypeURI() + " does not exist."));

        return false;
    }

    public boolean isNew()
    {
        return _validator.getRowId() == 0;
    }

    public boolean isDirty()
    {
        return _validatorOld != null || _deleted;
    }

    private PropertyValidator edit()
    {
        if (getRowId() == 0)
            return _validator;
        if (_validatorOld == null)
        {
            _validatorOld = _validator;
            _validator = _validatorOld.clone();
        }
        return _validator;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder(_validator.getName());
        if (_validator.getDescription() != null)
            sb.append(" (").append(_validator.getDescription()).append(")");

        return sb.toString();
    }
}