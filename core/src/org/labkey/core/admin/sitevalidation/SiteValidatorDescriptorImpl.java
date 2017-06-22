/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.core.admin.sitevalidation;

import org.labkey.api.admin.sitevalidation.SiteValidatorDescriptor;

/**
 * User: tgaluhn
 * Date: 10/30/2016
 */
public class SiteValidatorDescriptorImpl implements SiteValidatorDescriptor
{
    final String _name;
    final String _description;

    public SiteValidatorDescriptorImpl(String name, String description)
    {
        _name = name;
        _description = description;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SiteValidatorDescriptorImpl that = (SiteValidatorDescriptorImpl) o;

        if (!_name.equals(that._name)) return false;
        return _description != null ? _description.equals(that._description) : that._description == null;

    }

    @Override
    public int hashCode()
    {
        int result = _name.hashCode();
        result = 31 * result + (_description != null ? _description.hashCode() : 0);
        return result;
    }
}
