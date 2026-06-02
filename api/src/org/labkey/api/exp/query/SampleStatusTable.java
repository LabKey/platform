/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.exp.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FilteredTable;

import java.util.Arrays;
import java.util.stream.Collectors;

public class SampleStatusTable extends FilteredTable<ExpSchema>
{
    public SampleStatusTable(ExpSchema expSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoDataStates(), expSchema, cf);
        setName(ExpSchema.TableType.SampleStatus.name());
        if (cf != null)
            this.setContainerFilter(cf);
        SQLFragment sql = new SQLFragment(("(stateType IN ("));
        sql.append(Arrays.stream(ExpSchema.SampleStateType.values()).map(type -> "'" + type.name() + "'").collect(Collectors.joining(",")));
        sql.append(") )");
        addCondition(sql);
        addWrapColumn(getRealTable().getColumn("RowId"));
        addWrapColumn(getRealTable().getColumn("Label"));
        addWrapColumn(getRealTable().getColumn("Description"));
        addWrapColumn(getRealTable().getColumn("Container"));
        addWrapColumn("StatusType", getRealTable().getColumn("StateType"));
        addWrapColumn("Color", getRealTable().getColumn("Color"));
    }
}
