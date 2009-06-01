/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.*;
import org.labkey.study.StudySchema;

import java.sql.Types;

public abstract class BaseStudyTable extends FilteredTable
{
    protected StudyQuerySchema _schema;
    public BaseStudyTable(StudyQuerySchema schema, TableInfo realTable)
    {
        super(realTable, schema.getContainer());
        _schema = schema;
    }

    protected ColumnInfo addWrapParticipantColumn(String rootTableColumnName)
    {
        ColumnInfo participantColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn(rootTableColumnName));
        participantColumn.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", null));
        participantColumn.setKeyField(true);
        return addColumn(participantColumn);
    }

    protected ColumnInfo addWrapLocationColumn(String wrappedName, String rootTableColumnName)
    {
        ColumnInfo originatingSiteCol = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        originatingSiteCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                SiteTable result = new SiteTable(_schema);
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        });
        return addColumn(originatingSiteCol);
    }

    protected ColumnInfo addWrapTypeColumn(String wrappedName, final String rootTableColumnName)
    {
        ColumnInfo typeColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        typeColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                BaseStudyTable result;
                if (rootTableColumnName.equals("PrimaryTypeId"))
                    result = new PrimaryTypeTable(_schema);
                else if (rootTableColumnName.equals("DerivativeTypeId") || rootTableColumnName.equals("DerivativeTypeId2"))
                    result = new DerivativeTypeTable(_schema);
                else if (rootTableColumnName.equals("AdditiveTypeId"))
                    result = new AdditiveTypeTable(_schema);
                else
                    throw new IllegalStateException(rootTableColumnName + " is not recognized as a valid specimen type column.");
                result.setContainerFilter(ContainerFilter.EVERYTHING);
                return result;
            }
        });
        return addColumn(typeColumn);
    }

    protected void addSpecimenVisitColumn(boolean dateBased)
    {
        ColumnInfo visitColumn;
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        if (dateBased)
        {
            //consider:  use SequenceNumMin for visit-based studies too (in visit-based studies VisitValue == SequenceNumMin)
            // could change to visitrowid but that changes datatype and displays rowid
            // instead of sequencenum when label is null
            SQLFragment sqlFragVisit = new SQLFragment("(SELECT V.SequenceNumMin FROM " + StudySchema.getInstance().getTableInfoParticipantVisit() + " PV, " +
                    StudySchema.getInstance().getTableInfoVisit() + " V WHERE V.RowId = PV.VisitRowId AND " +
                    ExprColumn.STR_TABLE_ALIAS + ".ParticipantId = PV.ParticipantId AND" +
                    ExprColumn.STR_TABLE_ALIAS + ".Container = PV.Container)");
            visitColumn = addColumn(new ExprColumn(this, "Visit", sqlFragVisit, Types.VARCHAR));
            visitColumn.setCaption("Timepoint");
            visitDescriptionColumn.setIsHidden(true);
        }
        else
        {
            visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue"));
        }

        LookupForeignKey visitFK = new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
        {
            public TableInfo getLookupTableInfo()
            {
                VisitTable visitTable = new VisitTable(_schema);
                visitTable.setContainerFilter(ContainerFilter.EVERYTHING);
                return visitTable;
            }
        };
        visitFK.setJoinOnContainer(true);
        visitColumn.setFk(visitFK);
        visitColumn.setKeyField(true);
        addColumn(visitColumn);
    }

}
