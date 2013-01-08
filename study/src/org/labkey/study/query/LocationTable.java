/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.query;

import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.study.StudySchema;

public class LocationTable extends BaseStudyTable
{
    static public ForeignKey fkFor(StudyQuerySchema schema)
    {
        return new QueryForeignKey(schema, "Site", "RowId", "Label");
    }

    public LocationTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSite());
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("EntityId".equalsIgnoreCase(name))
                continue;
            ColumnInfo column = addWrapColumn(baseColumn);
            if ("Container".equalsIgnoreCase(name))
            {
                column = ContainerForeignKey.initColumn(column, schema);
                column.setHidden(true);
            }
            if ("RowId".equalsIgnoreCase(name))
            {
                // If there were a pageflow action which showed details on a particular site, we would set the fk of rowid here.
                // column.setFk(new TitleForeignKey(getBaseDetailsURL(), _rootTable.getColumn("RowId"), _rootTable.getColumn("Label"), "siteId"));
            }
        }
    }

    @Override
    public String getTitleColumn()
    {
        return "Label";
    }
}
