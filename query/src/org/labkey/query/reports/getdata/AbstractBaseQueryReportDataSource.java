/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * User: jeckels
 * Date: 5/29/13
 */
public abstract class AbstractBaseQueryReportDataSource implements QueryReportDataSource
{
    public Map<FieldKey, ColumnInfo> getColumnMap(Collection<FieldKey> requiredInputs)
    {
        QueryDefinition sourceQueryDef = getQueryDefinition();
        ArrayList<QueryException> errors = new ArrayList<>();
        TableInfo table = sourceQueryDef.getTable(getSchema(), errors, true);
        if (!errors.isEmpty())
        {
            throw errors.get(0);
        }
        return QueryService.get().getColumns(table, requiredInputs);
    }
}
