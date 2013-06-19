/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.audit.data;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.util.Pair;

import java.util.Set;

/**
 * User: klum
 * Date: Mar 15, 2012
 */
public abstract class ExperimentAuditColumn extends DataColumn
{
    protected ColumnInfo _containerId;
    protected ColumnInfo _defaultName;

    public static final String KEY_SEPARATOR = "~~KEYSEP~~";

    public ExperimentAuditColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
    {
        super(col);
        _containerId = containerId;
        _defaultName = defaultName;
    }

    public String getName()
    {
        return getColumnInfo().getLabel();
    }

    public void addQueryColumns(Set<ColumnInfo> columns)
    {
        super.addQueryColumns(columns);
        if (_containerId != null)
            columns.add(_containerId);
        if (_defaultName != null)
            columns.add(_defaultName);
    }

    public boolean isFilterable()
    {
        return false;
    }

    protected static Pair<String, String> splitKey3(Object value)
    {
        if (value == null)
            return null;
        String[] parts = value.toString().split(KEY_SEPARATOR);
        if (parts == null || parts.length != 2)
            return null;
        return new Pair<String, String>(parts[0], parts[1].length() > 0 ? parts[1] : null);
    }
}
