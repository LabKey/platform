/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.di;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;

import java.util.HashMap;

/**
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
 */
public class TransformSummaryTable extends TransformBaseTable
{
    @Override
    protected HashMap<String, String> buildNameMap()
    {
        HashMap<String, String> colMap = super.buildNameMap();
        colMap.put("StartTime", "LastRun");
        colMap.put("Status", "LastStatus");
        return colMap;
    }

    public TransformSummaryTable(UserSchema schema)
    {
        super(schema, DataIntegrationQuerySchema.TRANSFORMSUMMARY_TABLE_NAME);

        _sql = new SQLFragment();
        _sql.append(getBaseSql());
        _sql.append("INNER JOIN (SELECT TransformId, max(StartTime) AS StartTime\n");
        _sql.append("FROM ");
        _sql.append(DataIntegrationQuerySchema.getTransformRunTableName());
        _sql.append(" ");
        _sql.append(getWhereClause());
        _sql.append(" GROUP BY TransformId) m\n");
        _sql.append("ON t.TransformId=m.TransformId AND t.StartTime=m.StartTime\n");

        // add columns common to history and summary views
        addBaseColumns();

        // summary table should link to filtered history table
        String name = getNameMap().get("TransformId");
        ColumnInfo transformId = getColumn(name);
        transformId.setURL(DetailsURL.fromString("dataintegration/viewTransformHistory.view?transformId=${" + name + "}&transformRunId=${TransformRunId}"));
        transformId.setSortDirection(Sort.SortDirection.ASC);
    }
}
