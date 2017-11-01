/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.study;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

/**
 * Created by adam on 9/29/2015.
 */
public class DefragmentParticipantVisitIndexesTask implements MaintenanceTask
{
    @Override
    public String getDescription()
    {
        return "Defragment ParticipantVisit indexes";
    }

    @Override
    public String getName()
    {
        return "DefragmentParticipantVisitIndexes";
    }

    @Override
    public void run(Logger log)
    {
        TableInfo ti = StudySchema.getInstance().getTableInfoParticipantVisit();
        DbSchema schema = ti.getSchema();
        SqlDialect dialect = ti.getSqlDialect();

        dialect.defragmentIndex(schema, ti.getSelectName(), "PK_ParticipantVisit");
        dialect.defragmentIndex(schema, ti.getSelectName(), "ix_participantvisit_sequencenum");
        dialect.defragmentIndex(schema, ti.getSelectName(), "ix_participantvisit_visitrowid");
        dialect.defragmentIndex(schema, ti.getSelectName(), "UQ_ParticipantVisit_ParticipantSequenceNum");
    }
}
