/*
 * Copyright (c) 2015-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.PropertyStorageSpec;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: 11/9/15
 */
public class GWTIndex implements Serializable
{
    private List<String> _columnNames;
    private boolean _unique;

    public GWTIndex() { }

    public GWTIndex(List<String> columnNames, boolean unique)
    {
        _columnNames = columnNames;
        _unique = unique;
    }

    public GWTIndex(GWTIndex other)
    {
        setColumnNames(new ArrayList<>(other.getColumnNames()));
        setUnique(other.isUnique());
    }

    public GWTIndex copy()
    {
        return new GWTIndex(this);
    }


    public List<String> getColumnNames()
    {
        return _columnNames;
    }

    public void setColumnNames(List<String> columnNames)
    {
        _columnNames = columnNames;
    }

    public boolean isUnique()
    {
        return _unique;
    }

    public void setUnique(boolean unique)
    {
        _unique = unique;
    }

    public String toStringVal()
    {
        if (_columnNames == null || _columnNames.isEmpty())
            return "";

        return StringUtils.join(_columnNames, ", ") + ", unique: " + isUnique();
    }

    public static List<String> toStringVals(List<GWTIndex> indices, Set<PropertyStorageSpec.Index> excludeBaseIndices)
    {
        if (indices == null || indices.isEmpty())
            return null;

        Set<String> excludeIndices = excludeBaseIndices == null ? Collections.emptySet() : excludeBaseIndices.stream().map(PropertyStorageSpec.Index::toStringVal).collect(Collectors.toSet());
        return indices.stream().map(GWTIndex::toStringVal).filter(v -> !excludeIndices.contains(v)).sorted().toList();
    }

}
