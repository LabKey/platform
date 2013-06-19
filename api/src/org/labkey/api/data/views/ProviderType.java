/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.data.views;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public class ProviderType implements DataViewProvider.Type
{
    private String _name;
    private String _description;
    private boolean _showByDefault;

    public ProviderType(String name, String description, boolean showByDefault)
    {
        assert name != null : "name cannot be null";
        assert description != null : "description cannot be null";

        _name = name;
        _description = description;
        _showByDefault = showByDefault;
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
    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    @Override
    public int hashCode()
    {
        return _name.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof ProviderType))
            return false;

        ProviderType type = (ProviderType)obj;
        if (!type.getName().equals(this.getName())) return false;

        return true;
    }
}
