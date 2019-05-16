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

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

public class DerivativeTypeTable extends BaseStudyTable
{
    public DerivativeTypeTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDerivative(schema.getContainer()), cf, true);
        setName("SpecimenDerivative");
        setPublicSchemaName("study");
        addWrapColumn(_rootTable.getColumn("RowId")).setHidden(true);
        addWrapColumn(_rootTable.getColumn("ExternalId")).setHidden(true);
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("LdmsDerivativeCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("LabwareDerivativeCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Derivative")));
        var typeColumn = addWrapColumn("Derivative", _rootTable.getColumn("Derivative"));    // for lookups
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