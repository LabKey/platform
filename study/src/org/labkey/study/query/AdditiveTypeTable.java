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
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;

public class AdditiveTypeTable extends BaseStudyTable
{
    public AdditiveTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenAdditive(schema.getContainer()), true);
        setName("SpecimenAdditive");
        setPublicSchemaName("study");
        addWrapColumn(_rootTable.getColumn("RowId")).setHidden(true);
        addWrapColumn(_rootTable.getColumn("ExternalId")).setHidden(true);
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("LdmsAdditiveCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("LabwareAdditiveCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Additive")));
        ColumnInfo typeColumn = addWrapColumn("Additive", _rootTable.getColumn("Additive"));    // for lookups
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
