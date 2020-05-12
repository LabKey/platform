/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.GUID;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Combines multiple ContainerFilters, gathers their Containers, and uses the union of them when filtering.
 * User: jeckels
 * Date: 10/25/12
 */
public class UnionContainerFilter extends ContainerFilter
{
    private final ContainerFilter[] _filters;

    public UnionContainerFilter(ContainerFilter... filters)
    {
        super(null, null);
        _filters = filters;
    }

    @Override
    public String getCacheKey()
    {
        StringBuilder sb = new StringBuilder(getDefaultCacheKey(_container,_user)).append("/");
        for (var cf : _filters)
            sb.append(cf.getCacheKey()).append("/");
        return sb.toString();
    }

    @Override @Nullable
    public Collection<GUID> getIds(Container currentContainer)
    {
        Set<GUID> result = new HashSet<>();
        for (ContainerFilter filter : _filters)
        {
            Collection<GUID> ids = filter.getIds(currentContainer);
            if (ids == null)
            {
                // Null means don't filter
                return null;
            }
            result.addAll(ids);
        }
        return result;
    }

    @Override
    public Type getType()
    {
        return _filters[0].getType();
    }

    @Override
    public String toString()
    {
        return getClass().getName();
    }
}
