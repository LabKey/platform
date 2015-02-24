/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
import org.labkey.api.data.*;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.util.Path;
import org.labkey.study.StudySchema;

import java.util.ArrayList;
import java.util.List;


public class SpecimenWrapTable extends BaseStudyTable
{
    private Path _notificationKey;
    protected List<DomainProperty> _optionalSpecimenProperties = new ArrayList<>();
    protected List<DomainProperty> _optionalVialProperties = new ArrayList<>();

    public SpecimenWrapTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDetail(schema.getContainer()), true, true);

        addWrapTypeColumn("PrimaryTypeId", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeTypeId", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveTypeId", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeTypeId2", "DerivativeTypeId2");
        addWrapLocationColumn("OriginatingLocationId", "OriginatingLocationId");
        addWrapLocationColumn("ProcessingLocation", "ProcessingLocation");
        addWrapLocationColumn("CurrentLocation", "CurrentLocation");

        // wrap the rest regularly
        for (ColumnInfo columnInfo : _rootTable.getColumns())
            if (!"container".equalsIgnoreCase(columnInfo.getName()) && null == getColumn(columnInfo.getName()))
                addWrapColumn(columnInfo);

        // Add optional fields
        _optionalSpecimenProperties = new ArrayList<>();
        _optionalVialProperties = new ArrayList<>();
        SpecimenDetailTable.getOptionalSpecimenAndVialProperties(schema.getContainer(), _optionalSpecimenProperties, _optionalVialProperties);
        addOptionalColumns(_optionalVialProperties, false, null);
        addOptionalColumns(_optionalSpecimenProperties, false, null);

        addContainerColumn(true);
        _notificationKey = new Path("study", getClass().getName(), getName());
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return SpecimenDetailTable.getSpecimenAndVialFromSQL(alias, getSchema(), getContainer(), _optionalSpecimenProperties, _optionalVialProperties);
    }

    public Path getNotificationKey()
    {
        return _notificationKey;
    }

    @Override
    public boolean hasUnionTable()
    {
        return true;
    }
}
