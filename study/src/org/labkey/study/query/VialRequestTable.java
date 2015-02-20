/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.*;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class VialRequestTable extends FilteredTable<StudyQuerySchema>
{
    public VialRequestTable(final StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), schema);
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("SpecimenGlobalUniqueId".equalsIgnoreCase(name))
            {
                AliasedColumn globalUniqueIdColumn = new AliasedColumn(this, "Vial", _rootTable.getColumn("SpecimenGlobalUniqueId"))
                {
                    @Override
                    public ColumnInfo getDisplayField()
                    {
                        return null;
                    }
                };
                LookupForeignKey fk = new LookupForeignKey(null, (String) null, "GlobalUniqueId", "GlobalUniqueId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new SpecimenDetailTable(schema);
                    }
                };
                fk.setPrefixColumnCaption(false);
                globalUniqueIdColumn.setFk(fk);
                globalUniqueIdColumn.setKeyField(true);
                globalUniqueIdColumn.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new DataColumn(colInfo, false);
                    }
                });

                addColumn(globalUniqueIdColumn);
            }
            else if ("SampleRequestId".equalsIgnoreCase(name))
            {
                AliasedColumn requestIdColumn = new AliasedColumn(this, "Request", _rootTable.getColumn("SampleRequestId"));
                LookupForeignKey fk = new LookupForeignKey(null, (String) null, "RequestId", "RequestId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new SpecimenRequestTable(schema);
                    }
                };
                fk.setPrefixColumnCaption(false);
                requestIdColumn.setFk(fk);
                requestIdColumn.setKeyField(true);
                addColumn(requestIdColumn);
            }
            else if (!"Container".equalsIgnoreCase(name))
            {
                ColumnInfo wrappedColumn = addWrapColumn(baseColumn);
                if ("RowId".equalsIgnoreCase(name))
                    wrappedColumn.setHidden(true);
            }
        }
    }
}
