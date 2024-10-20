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
package org.labkey.api.query;

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.Objects;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class MetadataColumnJSON extends GWTPropertyDescriptor
{
    private String _wrappedColumnName;
    private String _valueExpression;
    private boolean _lookupCustom;
    private boolean _lockExistingField;

    public MetadataColumnJSON()
    {
        super();
    }

    public MetadataColumnJSON(MetadataColumnJSON ci)
    {
        super(ci);
        setWrappedColumnName(ci.getWrappedColumnName());
        setValueExpression(ci.getValueExpression());
        setLookupCustom(ci.isLookupCustom());
    }

    public MetadataColumnJSON(GWTPropertyDescriptor ci)
    {
        super(ci);
        setValueExpression(ci.getValueExpression());
    }

    public String getWrappedColumnName()
    {
        return _wrappedColumnName;
    }

    public void setWrappedColumnName(String wrappedColumnName)
    {
        _wrappedColumnName = wrappedColumnName;
    }

    public String getValueExpression()
    {
        return _valueExpression;
    }

    public void setValueExpression(String valueExpression)
    {
        _valueExpression = valueExpression;
    }

    @Override
    public GWTPropertyDescriptor copy()
    {
        return new MetadataColumnJSON(this);
    }

    public void setLookupCustom(boolean lookupCustom)
    {
        _lookupCustom = lookupCustom;
    }

    public boolean isLookupCustom()
    {
        return _lookupCustom;
    }

    @Override
    public void setLookupSchema(String lookupSchema)
    {
        _lookupCustom = false;
        super.setLookupSchema(lookupSchema);
    }

    public boolean isLockExistingField()
    {
        return _lockExistingField;
    }

    public void setLockExistingField(boolean lockExistingField)
    {
        _lockExistingField = lockExistingField;
    }

    @Override
    public void setLookupQuery(String lookupQuery)
    {
        _lookupCustom = false;
        super.setLookupQuery(lookupQuery);
    }

    @Override
    public String getLookupDescription()
    {
        if (_lookupCustom)
        {
            return "(custom)";
        }
        return super.getLookupDescription();
    }
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        MetadataColumnJSON that = (MetadataColumnJSON)o;

        if (_lookupCustom != that._lookupCustom) return false;
        if (!Objects.equals(_valueExpression, that._valueExpression)) return false;
        return Objects.equals(_wrappedColumnName, that._wrappedColumnName);
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_wrappedColumnName != null ? _wrappedColumnName.hashCode() : 0);
        result = 31 * result + (_valueExpression != null ? _valueExpression.hashCode() : 0);
        result = 31 * result + (_lookupCustom ? 1 : 0);
        return result;
    }
}