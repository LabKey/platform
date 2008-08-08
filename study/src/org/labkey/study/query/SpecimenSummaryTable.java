/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.study.StudySchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.LookupForeignKey;

public class SpecimenSummaryTable extends BaseStudyTable
{
    public SpecimenSummaryTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenSummary());
        addWrapColumn(_rootTable.getColumn("SpecimenNumber"));
        ColumnInfo participantColumn = new AliasedColumn(this, "ParticipantId", _rootTable.getColumn("PTID"));
        participantColumn.setFk(new QueryForeignKey(_schema, "Participant", "ParticipantId", null));
        participantColumn.setKeyField(true);
        addColumn(participantColumn);
        ColumnInfo visitColumn = new AliasedColumn(this, "Visit", _rootTable.getColumn("VisitValue"));
        ColumnInfo visitDescriptionColumn = addWrapColumn(_rootTable.getColumn("VisitDescription"));
        //In date based studies, joining sequenceNum does not make sense.
        if (_schema.getStudy().isDateBased())
        {
            visitColumn.setIsHidden(true);
            visitDescriptionColumn.setIsHidden(true);
        }
        else
        {
            visitColumn.setFk(new LookupForeignKey(null, (String) null, "SequenceNumMin", null)
            {
                public TableInfo getLookupTableInfo()
                {
                    return new VisitTable(_schema);
                }
            });
        }
        visitColumn.setKeyField(true);
        addColumn(visitColumn);

        ColumnInfo pvColumn = new ParticipantVisitColumn(
                "ParticipantVisit",
                new AliasedColumn(this, "PVParticipant", getRealTable().getColumn("PTID")),
                new AliasedColumn(this, "PVVisit", getRealTable().getColumn("VisitValue")));
        addColumn(pvColumn);
        pvColumn.setFk(new LookupForeignKey("ParticipantVisit")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ParticipantVisitTable(_schema, null);
            }
        });

        addWrapColumn(_rootTable.getColumn("TotalVolume"));
        addWrapColumn(_rootTable.getColumn("AvailableVolume"));
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        ColumnInfo primaryTypeColumn = new AliasedColumn(this, "PrimaryType", _rootTable.getColumn("PrimaryTypeId"));
        primaryTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new PrimaryTypeTable(_schema);
            }
        });
        addColumn(primaryTypeColumn);
        ColumnInfo additiveTypeColumn = new AliasedColumn(this, "AdditiveType", _rootTable.getColumn("AdditiveTypeId"));
        additiveTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new AdditiveTypeTable(_schema);
            }
        });
        addColumn(additiveTypeColumn);
        ColumnInfo derivativeTypeColumn = new AliasedColumn(this, "DerivativeType", _rootTable.getColumn("DerivativeTypeId"));
        derivativeTypeColumn.setFk(new LookupForeignKey("ScharpId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new DerivativeTypeTable(_schema);
            }
        });
        addColumn(derivativeTypeColumn);

        ColumnInfo originatingSiteCol = new AliasedColumn(this, "Clinic", _rootTable.getColumn("OriginatingLocationId"));
        originatingSiteCol.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new SiteTable(_schema);
            }
        });
        addColumn(originatingSiteCol);

        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("VialCount"));
        addWrapColumn(_rootTable.getColumn("LockedInRequestCount"));
        addWrapColumn(_rootTable.getColumn("AtRepositoryCount"));
        addWrapColumn(_rootTable.getColumn("AvailableCount"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
    }
}
