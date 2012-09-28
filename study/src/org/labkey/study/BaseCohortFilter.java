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
package org.labkey.study;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;

import java.util.Collections;
import java.util.Map;

public abstract class BaseCohortFilter implements CohortFilter
{
    protected final Type _type;

    protected BaseCohortFilter(Type type)
    {
        _type = type;
    }

    public CohortFilter.Type getType()
    {
        return _type;
    }

    protected ColumnInfo getCohortColumn(TableInfo table, Container container)
    {
        FieldKey cohortColKey = _type.getFilterColumn(container);
        Map<FieldKey, ColumnInfo> cohortColumnMap = QueryService.get().getColumns(table, Collections.singleton(cohortColKey));
        ColumnInfo cohortColumn = cohortColumnMap.get(cohortColKey);
        if (cohortColumn == null)
            throw new IllegalStateException("A column with key '" + cohortColKey.toString() + "'  was not found on table " + table.getName());
        return cohortColumn;
    }
}