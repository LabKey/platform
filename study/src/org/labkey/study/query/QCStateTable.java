package org.labkey.study.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.study.StudySchema;

/**
 * Copyright (c) 2008-2009 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class QCStateTable extends FilteredTable
{
    public QCStateTable(StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoQCState(), schema.getContainer());
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