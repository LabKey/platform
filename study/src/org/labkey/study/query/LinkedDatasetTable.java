/*
 * Copyright (c) 2023-2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.study.model.DatasetDefinition;

abstract class LinkedDatasetTable extends DatasetTableImpl
{
    LinkedDatasetTable(@NotNull StudyQuerySchema schema, ContainerFilter cf, @NotNull DatasetDefinition dsd)
    {
        super(schema, cf, dsd);
    }

    @Override
    protected boolean acceptColumn(ColumnInfo column)
    {
        if (getUserSchema().getStudy().getTimepointType().isVisitBased())
        {
            // issue : 47937 don't add the date field to linked datasets
            return (!"date".equalsIgnoreCase(column.getName()));
        }
        return true;
    }
}
