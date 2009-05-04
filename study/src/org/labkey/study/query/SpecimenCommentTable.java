package org.labkey.study.query;

import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.study.StudySchema;

/**
 * Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class SpecimenCommentTable extends FilteredTable
{
    public SpecimenCommentTable(final StudyQuerySchema schema)
    {
        super(StudySchema.getInstance().getTableInfoSpecimenComment(), schema.getContainer());
        for (ColumnInfo baseColumn : _rootTable.getColumns())
        {
            String name = baseColumn.getName();
            if ("GlobalUniqueId".equalsIgnoreCase(name))
            {
                AliasedColumn globalUniqueIdColumn = new AliasedColumn(this, "GlobalUniqueId", _rootTable.getColumn("GlobalUniqueId"));
                globalUniqueIdColumn.setFk(new LookupForeignKey(null, (String) null, "GlobalUniqueId", "GlobalUniqueId")
                {
                    public TableInfo getLookupTableInfo()
                    {
                        return new SpecimenDetailTable(schema);
                    }
                });
                globalUniqueIdColumn.setKeyField(true);
                addColumn(globalUniqueIdColumn);
            }
            else if (!"Container".equalsIgnoreCase(name))
            {
                ColumnInfo wrappedColumn = addWrapColumn(baseColumn);
                if ("RowId".equalsIgnoreCase(name) || "QualityControlFlagForced".equalsIgnoreCase(name))
                    wrappedColumn.setIsHidden(true);
            }
        }
        setTitleColumn("Comment");
    }
}