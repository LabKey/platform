/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.api.exp.api;

import com.google.common.base.Objects;
import org.labkey.api.util.URLHelper;
import org.labkey.api.exp.Lsid;

/**
 * Recognizes {@link ExpData} based on the namespace prefix in their LSIDs to identify specific flavors that have custom handling within the
 * application
 */
public class DataType
{
    protected String _namespacePrefix;

    public DataType(String namespacePrefix)
    {
        _namespacePrefix = namespacePrefix;
    }

    public String getNamespacePrefix()
    {
        return _namespacePrefix;
    }

    public URLHelper getDetailsURL(ExpData dataObject)
    {
        return null;
    }

    public String urlFlag(boolean flagged)
    {
        return null;
    }

    public boolean matches(Lsid lsid)
    {
        return lsid != null && lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().equals(_namespacePrefix);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof DataType)) return false;

        DataType dataType = (DataType) o;

        return !(_namespacePrefix != null ? !_namespacePrefix.equals(dataType._namespacePrefix) : dataType._namespacePrefix != null);
    }

    @Override
    public int hashCode()
    {
        return _namespacePrefix != null ? _namespacePrefix.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .addValue(_namespacePrefix)
                .toString();
    }

}
