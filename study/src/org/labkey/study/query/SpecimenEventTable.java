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
import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

import java.util.List;
import java.util.ArrayList;

/**
 * User: brittp
 * Date: Jan 26, 2007
 * Time: 9:49:46 AM
 */
public class SpecimenEventTable extends BaseStudyTable
{
    private static final String[] DEFAULT_VISIBLE_COLS = {
    "SpecimenNumber",
    "LabId",
    "Stored",
    "StorageFlag",
    "StorageDate",
    "ShipFlag",
    "ShipBatchNumber",
    "ShipDate",
    "LabReceiptDate",
    "SpecimenCondition",
    "Comments",
    "fr_container",
    "fr_level1",
    "fr_level2",
    "fr_position",
    "freezer"
    };

    public SpecimenEventTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenEvent());

        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("Container".equalsIgnoreCase(name) ||
                "RowId".equalsIgnoreCase(name) ||
                "ExternalId".equalsIgnoreCase(name))
                continue;

            if (getColumn(name) == null)
                addWrapColumn(baseColumn);
        }

        List<FieldKey> defaultVisible = new ArrayList<FieldKey>();
        for (String col : DEFAULT_VISIBLE_COLS)
            defaultVisible.add(new FieldKey(null, col));
        setDefaultVisibleColumns(defaultVisible);

        getColumn("LabId").setCaption("Location");
    }
}
