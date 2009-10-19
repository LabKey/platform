/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 3:18:58 PM
 */
public class SpecimenRequestStatusTable extends BaseStudyTable
{
    public SpecimenRequestStatusTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSampleRequestStatus());
        setName("SpecimenRequestStatus");
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) || "EntityId".equalsIgnoreCase(name))
                continue;
            addWrapColumn(baseColumn);
        }
    }
}
