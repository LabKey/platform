/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UnexpectedException;

import java.io.Serializable;

/*
* User: Karl Lum
* Date: Aug 11, 2008
* Time: 1:07:12 PM
*/
public class PropertyValidator implements Serializable, Cloneable
{
    private int _rowId;
    private String _name;
    private String _description;
    private String _typeURI;
    private String _expression;
    private String _container;
    private String _properties;
    private String _errorMessage;
    // NOTE: _propertyId is stored in the junction table, so it's not needed for persisting a PropertyValidator
    private Integer _propertyId;

    public void copy(IPropertyValidator v)
    {
        _rowId = v.getRowId();
        _name = v.getName();
        _description = v.getDescription();
        _typeURI = v.getTypeURI();
        _expression = v.getExpressionValue();
        _properties = PageFlowUtil.toQueryString(v.getProperties().entrySet());
        _errorMessage = v.getErrorMessage();
        if (v.getContainer() != null)
            _container = v.getContainer().getId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    public void setTypeURI(String typeURI)
    {
        _typeURI = typeURI;
    }

    public String getExpression()
    {
        return _expression;
    }

    public void setExpression(String expression)
    {
        _expression = expression;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getProperties()
    {
        return _properties;
    }

    public void setProperties(String properties)
    {
        _properties = properties;
    }

    public String getErrorMessage()
    {
        return _errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        _errorMessage = errorMessage;
    }

    public Integer getPropertyId()
    {
        return _propertyId;
    }

    public void setPropertyId(Integer propertyId)
    {
        _propertyId = propertyId;
    }

    public final PropertyValidator clone()
    {
        try
        {
            return (PropertyValidator) super.clone();
        }
        catch (CloneNotSupportedException cnse)
        {
            throw UnexpectedException.wrap(cnse);
        }
    }
}