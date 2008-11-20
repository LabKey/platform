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
package org.labkey.query.metadata.client;

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class GWTColumnInfo extends GWTPropertyDescriptor
{
    private String _wrappedColumnName;
    private boolean _lookupCustom;

    public GWTColumnInfo()
    {
        super();
    }

    public GWTColumnInfo(GWTColumnInfo ci)
    {
        super(ci);
        setWrappedColumnName(ci.getWrappedColumnName());
        setLookupCustom(ci.isLookupCustom());
    }

    public String getWrappedColumnName()
    {
        return _wrappedColumnName;
    }

    public void setWrappedColumnName(String wrappedColumnName)
    {
        _wrappedColumnName = wrappedColumnName;
    }

    public GWTPropertyDescriptor copy()
    {
        return new GWTColumnInfo(this);
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

    @Override
    public void setLookupQuery(String lookupQuery)
    {
        _lookupCustom = false;
        super.setLookupQuery(lookupQuery);
    }

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

        GWTColumnInfo that = (GWTColumnInfo)o;

        if (_lookupCustom != that._lookupCustom) return false;
        return !(_wrappedColumnName != null ? !_wrappedColumnName.equals(that._wrappedColumnName) : that._wrappedColumnName != null);

    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_wrappedColumnName != null ? _wrappedColumnName.hashCode() : 0);
        result = 31 * result + (_lookupCustom ? 1 : 0);
        return result;
    }
}