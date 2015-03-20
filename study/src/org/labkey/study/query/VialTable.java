/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.SQLFragment;
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
        super(schema, StudySchema.getInstance().getTableInfoVial(schema.getContainer()), true);

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
                TableInfo tableInfo = schema.getTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);
                if (tableInfo instanceof ContainerFilterable)
                {
                    ((ContainerFilterable) tableInfo).setContainerFilter(ContainerFilter.EVERYTHING);    // TODO: what would this do without provisioned?
                }
                return tableInfo;
            }
        });
        addColumn(specimenCol);

//        ColumnInfo containerCol = addWrapColumn(getRealTable().getColumn("Container"));
        ColumnInfo containerCol = addContainerColumn(true);
        containerCol.setFk(new ContainerForeignKey(schema));
        containerCol.setHidden(true);

        // Must add this after the container column so that we can use the container in the FK join for the comments
        addVialCommentsColumn(false);
    }

    // don't really _need_ this because containerCol works (see above), however this simplifies SpecimenForeignKey sql generation a little
    @Override @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment ret = new SQLFragment();
        ret.appendComment("<org.labkey.study.query.VialTable>",getSqlDialect());
        ret.append("(SELECT ");
        ret.append(getColumn("Container").getValueSql("_")).append(" AS Container, *");
        ret.append(" FROM ");
        ret.append(_rootTable.getFromSQL("_"));
        ret.append(") ").append(alias);
        ret.appendComment("</org.labkey.study.query.VialTable>",getSqlDialect());
        return ret;
    }

    @Override
    public boolean hasUnionTable()
    {
        return true;
    }
}
