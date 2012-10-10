/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.study.StudySchema;

/**
 * User: jeckels
 * Date: Jun 22, 2009
 */
public class VialTable extends BaseStudyTable
{
    public VialTable(final StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVial(), true);

        addWrapColumn(getRealTable().getColumn("RowID")).setHidden(true);

        ColumnInfo guid = addWrapColumn(getRealTable().getColumn("GlobalUniqueID"));
        guid.setDisplayColumnFactory(ColumnInfo.NOWRAP_FACTORY);

        setTitleColumn(guid.getName());

        addWrapColumn(getRealTable().getColumn("Volume"));
        ColumnInfo specimenCol = wrapColumn("Specimen", getRealTable().getColumn("SpecimenID"));
        specimenCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                SimpleSpecimenTable tableInfo = schema.createSimpleSpecimenTable();
                tableInfo.setContainerFilter(ContainerFilter.EVERYTHING);
                return tableInfo;
            }
        });
        addColumn(specimenCol);

        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn("Container"));
        containerCol.setFk(new ContainerForeignKey(schema));
        containerCol.setHidden(true);

        // Must add this after the container column so that we can use the container in the FK join for the comments
        addVialCommentsColumn(false);
    }
}
