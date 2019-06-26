/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.study.StudySchema;

/**
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class SpecimenCommentTable extends FilteredTable<StudyQuerySchema>
{
    public SpecimenCommentTable(final StudyQuerySchema schema, ContainerFilter cf)
    {
        this(schema, cf, true);
    }

    public SpecimenCommentTable(final StudyQuerySchema schema, ContainerFilter cf, boolean joinBackToSpecimens)
    {
        super(StudySchema.getInstance().getTableInfoSpecimenComment(), schema);
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if (joinBackToSpecimens && "GlobalUniqueId".equalsIgnoreCase(name))
            {
                AliasedColumn globalUniqueIdColumn = new AliasedColumn(this, "GlobalUniqueId", _rootTable.getColumn("GlobalUniqueId"));
                globalUniqueIdColumn.setFk(new LookupForeignKey(null, (String) null, "GlobalUniqueId", "GlobalUniqueId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new SpecimenDetailTable(schema, cf);
                    }
                });
                globalUniqueIdColumn.setKeyField(true);
                addColumn(globalUniqueIdColumn);
            }
            else if (!"Container".equalsIgnoreCase(name))
            {
                var wrappedColumn = addWrapColumn(baseColumn);
                if ("RowId".equalsIgnoreCase(name) || "QualityControlFlagForced".equalsIgnoreCase(name))
                    wrappedColumn.setHidden(true);
            }
        }

        var folderColumn = wrapColumn("Folder", _rootTable.getColumn("Container"));
        ContainerForeignKey.initColumn(folderColumn, schema);
        addColumn(folderColumn);

        setTitleColumn("Comment");
    }
}