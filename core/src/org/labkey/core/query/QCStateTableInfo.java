/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.core.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.FilteredTable;

/**
 * Created by marty on 7/25/2017.
 */
public class QCStateTableInfo extends FilteredTable<CoreQuerySchema>
{
    public QCStateTableInfo(CoreQuerySchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoQCState(), schema);
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name))
                continue;
            ColumnInfo wrappedColumn = addWrapColumn(baseColumn);
            if ("RowId".equalsIgnoreCase(name))
                wrappedColumn.setHidden(true);
        }
    }
}