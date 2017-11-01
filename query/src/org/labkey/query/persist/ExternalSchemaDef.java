/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.query.persist;

import org.labkey.api.data.DbScope;

public class ExternalSchemaDef extends AbstractExternalSchemaDef
{
    private boolean _editable;
    private boolean _indexable = true;
    private boolean _fastCacheRefresh;

    public DbScope lookupDbScope()
    {
        try
        {
            return DbScope.getDbScope(getDataSource());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public boolean isEditable()
    {
        return _editable;
    }

    public void setEditable(boolean editable)
    {
        _editable = editable;
    }

    public boolean isIndexable()
    {
        return _indexable;
    }

    @Override
    public boolean isFastCacheRefresh()
    {
        return _fastCacheRefresh;
    }

    public void setIndexable(boolean indexable)
    {
        _indexable = indexable;
    }

    public void setFastCacheRefresh(boolean fastCacheRefresh)
    {
        _fastCacheRefresh = fastCacheRefresh;
    }

    @Override
    public SchemaType getSchemaType()
    {
        return SchemaType.external;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExternalSchemaDef that = (ExternalSchemaDef) o;

        if (_editable != that._editable) return false;
        if (_indexable != that._indexable) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_editable ? 1 : 0);
        result = 31 * result + (_indexable ? 1 : 0);
        return result;
    }
}
