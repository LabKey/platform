/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Date: Apr 20, 2007
 * Time: 2:55:18 PM
 */
public class SpecimenRequestTable extends BaseStudyTable
{
    public SpecimenRequestTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSampleRequest());

        AliasedColumn rowIdColumn = new AliasedColumn(this, "RequestId", _rootTable.getColumn("RowId"));
        rowIdColumn.setKeyField(true);
        addColumn(rowIdColumn);
        AliasedColumn statusColumn = new AliasedColumn(this, "Status", _rootTable.getColumn("StatusId"));
        statusColumn.setFk(new LookupForeignKey(null, (String) null, "RowId", "Label")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SpecimenRequestStatusTable(_userSchema);
            }
        });
        statusColumn.setKeyField(true);
        addColumn(statusColumn);

        addWrapLocationColumn("Destination", "DestinationSiteId").setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Comments"));
        // there are links to filter by 'createdby' in the UI; it's necessary that this column always
        // be available, so we set it as a key field.
        addWrapColumn(_rootTable.getColumn("CreatedBy")).setKeyField(true);
        addWrapColumn(_rootTable.getColumn("Created"));
        addWrapColumn(_rootTable.getColumn("ModifiedBy"));
        addWrapColumn(_rootTable.getColumn("Modified"));
        ColumnInfo hiddenColumn = addWrapColumn(_rootTable.getColumn("Hidden"));
        hiddenColumn.setHidden(true);
        hiddenColumn.setIsUnselectable(true);

        List<FieldKey> fieldKeys = new ArrayList<>();
        fieldKeys.add(FieldKey.fromParts("RequestId"));
        fieldKeys.add(FieldKey.fromParts("Status"));
        fieldKeys.add(FieldKey.fromParts("Destination"));
        fieldKeys.add(FieldKey.fromParts("CreatedBy"));
        fieldKeys.add(FieldKey.fromParts("Created"));
        setDefaultVisibleColumns(fieldKeys);
    }
}
