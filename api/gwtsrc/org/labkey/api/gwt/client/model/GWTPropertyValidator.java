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
package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/*
* User: Karl Lum
* Date: Aug 8, 2008
* Time: 3:49:42 PM
*/
public class GWTPropertyValidator implements Serializable, IsSerializable
{
    public static final String TYPE_REGEX = "regex";
    public static final String TYPE_RANGE = "range";
    public static final String TYPE_LOOKUP = "lookup";

    private int _rowId;
    private String _name;
    private PropertyValidatorType _type;
    private String _description;
    private String _expression;
    private String _errorMessage;
    private boolean _isNew;

    private Map<String, String> _properties = new HashMap<String, String>();

    public GWTPropertyValidator()
    {
        _isNew = true;
    }
    
    public GWTPropertyValidator(GWTPropertyValidator s)
    {
        _copyProperties(s, this);
    }

    public void copy(GWTPropertyValidator s)
    {
        _copyProperties(s, this);
    }

    private static void _copyProperties(GWTPropertyValidator s, GWTPropertyValidator d)
    {
        d.setRowId(s.getRowId());
        d.setName(s.getName());
        d.setType(s.getType());
        d.setDescription(s.getDescription());
        d.setExpression(s.getExpression());
        d.setErrorMessage(s.getErrorMessage());
        d.getProperties().putAll(s.getProperties());
        d.setNew(s.isNew());
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

    public PropertyValidatorType getType()
    {
        return _type;
    }

    public void setType(PropertyValidatorType type)
    {
        _type = type;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getExpression()
    {
        return _expression;
    }

    public void setExpression(String expression)
    {
        _expression = expression;
    }

    public String getErrorMessage()
    {
        return _errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        _errorMessage = errorMessage;
    }

    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        _properties = properties;
    }

    public boolean isNew()
    {
        return _isNew;
    }

    public void setNew(boolean aNew)
    {
        _isNew = aNew;
    }

    public void validate(List<String> errors)
    {
        if (StringUtils.trimToNull(getName()) == null)
            errors.add("Validator Name cannot be blank");
        if (StringUtils.trimToNull(getExpression()) == null)
            errors.add("Validator Expression cannot be blank");
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof GWTPropertyValidator))
            return false;

        GWTPropertyValidator that = (GWTPropertyValidator) o;

        if (getRowId() != that.getRowId()) return false;
        if (!StringUtils.equals(getName(), that.getName())) return false;
        if (!PropertyUtil.nullSafeEquals(getType(), that.getType())) return false;
        if (!StringUtils.equals(getDescription(), that.getDescription())) return false;
        if (!StringUtils.equals(getExpression(), that.getExpression())) return false;
        if (!StringUtils.equals(getErrorMessage(), that.getErrorMessage())) return false;
        if (!getProperties().equals(that.getProperties())) return false;

        return true;
    }

    public int hashCode()
    {
        int result = getRowId();
        result = 31 * result + (getName() != null ? getName().hashCode() : 0);
        result = 31 * result + (getType() != null ? getType().hashCode() : 0);
        result = 31 * result + (getDescription() != null ? getDescription().hashCode() : 0);
        result = 31 * result + (getExpression() != null ? getExpression().hashCode() : 0);
        result = 31 * result + (getErrorMessage() != null ? getErrorMessage().hashCode() : 0);
        result = 31 * result + (getProperties() != null ? getProperties().hashCode() : 0);

        return result;
    }
}