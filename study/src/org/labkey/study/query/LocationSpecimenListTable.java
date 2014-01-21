/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
import org.labkey.api.query.QueryService;

import java.util.ArrayList;
import java.util.List;

public class LocationSpecimenListTable extends SpecimenDetailTable
{
    public LocationSpecimenListTable(StudyQuerySchema schema)
    {
        super(schema);
        setName("LocationSpecimenList");

        List<ColumnInfo> defaultColumns = new ArrayList<>();
        defaultColumns.add(getColumn("GlobalUniqueId"));
        defaultColumns.add(getColumn("ParticipantId"));
        defaultColumns.add(getColumn("Visit"));
        defaultColumns.add(getColumn("Volume"));
        defaultColumns.add(getColumn("VolumeUnits"));
        defaultColumns.add(getColumn("DrawTimestamp"));
        defaultColumns.add(getColumn("ProtocolNumber"));
        defaultColumns.add(getColumn("PrimaryType"));
        defaultColumns.add(getColumn("TotalCellCount"));
        defaultColumns.add(getColumn("Clinic"));
        defaultColumns.add(getColumn("FirstProcessedByInitials"));

        ColumnInfo column = getColumn("Freezer");
        if (null != column)
            defaultColumns.add(column);
        column = getColumn("Fr_Container");
        if (null != column)
            defaultColumns.add(column);
        column = getColumn("Fr_Position");
        if (null != column)
            defaultColumns.add(column);
        column = getColumn("Fr_Level1");
        if (null != column)
            defaultColumns.add(column);
        column = getColumn("Fr_Level2");
        if (null != column)
            defaultColumns.add(column);

        setDefaultVisibleColumns(QueryService.get().getDefaultVisibleColumns(defaultColumns));
    }
}
