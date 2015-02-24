/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

public class PrimaryTypeTable extends BaseStudyTable
{
    public PrimaryTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenPrimaryType(schema.getContainer()), true);
        setName("SpecimenPrimaryType");
        setPublicSchemaName("study");
        addWrapColumn(_rootTable.getColumn("RowId")).setHidden(true);
        addWrapColumn(_rootTable.getColumn("ExternalId")).setHidden(true);
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("PrimaryTypeLdmsCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("PrimaryTypeLabwareCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("PrimaryType")));
        ColumnInfo typeColumn = addWrapColumn("PrimaryType", _rootTable.getColumn("PrimaryType"));    // for lookups
        typeColumn.setHidden(true);
        ContainerForeignKey.initColumn(addWrapColumn(_rootTable.getColumn(FieldKey.fromParts("Container"))), schema).setHidden(true);
        setTitleColumn("Description");
    }

    @Override
    public boolean hasUnionTable()
    {
        return true;
    }
}
