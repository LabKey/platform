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
package org.labkey.di;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Dax
 * Date: 9/13/13
 * Time: 12:27 PM
 */
public class TransformHistoryTable extends TransformBaseTable
{
    public TransformHistoryTable(UserSchema schema)
    {
        super(schema);
        _sql = new SQLFragment();
        _sql.append(getBaseSql());
        _sql.append(getWhereClause("t"));
        addBaseColumns();

        // history table should link to filtered run table for transform details
        ColumnInfo run = getColumn(getNameMap().get("StartTime"));
        run.setURL(DetailsURL.fromString("dataintegration/viewTransformDetails.view?transformRunId=${TransformRunId}&transformId=${" + getNameMap().get("TransformId") + "}"));
    }

    @Override
    public String getTransformTableName()
    {
        return DataIntegrationQuerySchema.TRANSFORMHISTORY_TABLE_NAME;
    }

    @Override
    protected HashMap<String, String> buildNameMap()
    {
        HashMap<String, String> colMap = super.buildNameMap();
        colMap.put("StartTime", "Run");
        colMap.put("Status", "Status");
        return colMap;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        // suppress the name field
        List<FieldKey> defaultVisibleColumns = super.getDefaultVisibleColumns();
        List<FieldKey> visibleColumns = new ArrayList<>();
        String hideColumn = getNameMap().get("TransformId");
        for (FieldKey fk : defaultVisibleColumns)
        {
            if (fk.getName().equalsIgnoreCase(hideColumn))
                continue;

            visibleColumns.add(fk);
        }

        return visibleColumns;
    }
}
