package org.labkey.study;

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
    public boolean canDisable()
    {
        return true;
    }

    @Override
    public boolean hideFromAdminPage()
    {
        return false;
    }

    @Override
    public void run()
    {
        TableInfo ti = StudySchema.getInstance().getTableInfoParticipantVisit();
        DbSchema schema = ti.getSchema();
        SqlDialect dialect = ti.getSqlDialect();

        dialect.defragmentIndex(schema, ti.getSelectName(), "PK_ParticipantVisit");
        dialect.defragmentIndex(schema, ti.getSelectName(), "IX_ParticipantVisit_ParticipantId");
        dialect.defragmentIndex(schema, ti.getSelectName(), "IX_ParticipantVisit_SequenceNum");
        dialect.defragmentIndex(schema, ti.getSelectName(), "UQ_ParticipantVisit_ParticipantSequenceNum");
    }
}
