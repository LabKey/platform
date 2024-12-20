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

import lombok.EqualsAndHashCode;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

@EqualsAndHashCode(callSuper = true)
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
        setLockExistingField(ci.isLockExistingField());
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

    @Override
    public String getValueExpression()
    {
        return _valueExpression;
    }

    @Override
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
            return "(custom)";
        return super.getLookupDescription();
    }
}