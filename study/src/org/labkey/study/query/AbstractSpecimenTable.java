/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

/**
 * Superclass for specimen tables that adds and configures all the common columns.
 * User: jeckels
 * Date: May 8, 2009
 */
public abstract class AbstractSpecimenTable extends BaseStudyTable
{
    public AbstractSpecimenTable(StudyQuerySchema schema, TableInfo realTable, boolean skipPermissionChecks, boolean isProvisioned)
    {
        super(schema, realTable, true, skipPermissionChecks);

        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setKeyField(true);

        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);
        addWrapParticipantColumn("PTID");
        // Don't setKeyField. Use addQueryFieldKeys where needed

        addContainerColumn(isProvisioned);
    }

    @Override
    protected String getParticipantColumnName()
    {
        return "PTID";
    }

    protected void addSpecimenTypeColumns()
    {
        addWrapColumn(_rootTable.getColumn("VolumeUnits"));
        addWrapTypeColumn("PrimaryType", "PrimaryTypeId");
        addWrapTypeColumn("DerivativeType", "DerivativeTypeId");
        addWrapTypeColumn("AdditiveType", "AdditiveTypeId");
        addWrapTypeColumn("DerivativeType2", "DerivativeTypeId2");
        addWrapColumn(_rootTable.getColumn("SubAdditiveDerivative"));
        addWrapColumn(_rootTable.getColumn("DrawTimestamp"));
        ColumnInfo columnDrawDate =  addWrapColumn(_rootTable.getColumn("DrawDate"));
        columnDrawDate.setReadOnly(true);
        ColumnInfo columnDrawTime = addWrapColumn(_rootTable.getColumn("DrawTime"));
        columnDrawTime.setReadOnly(true);

        addWrapLocationColumn("Clinic", "OriginatingLocationId");
        addWrapLocationColumn("ProcessingLocation", "ProcessingLocation");
        addWrapColumn(_rootTable.getColumn("FirstProcessedByInitials"));
        
        addWrapColumn(_rootTable.getColumn("SalReceiptDate"));
        addWrapColumn(_rootTable.getColumn("ClassId"));
        addWrapColumn(_rootTable.getColumn("ProtocolNumber"));
    }

}

