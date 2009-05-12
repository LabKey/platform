package org.labkey.study.query;

import org.labkey.study.StudySchema;

/**
 * User: jeckels
 * Date: May 8, 2009
 */
public class SimpleSpecimenTable extends AbstractSpecimenTable
{
    public SimpleSpecimenTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimen());

        getColumn("ParticipantId").setFk(null);

        addVisitColumn(false);
        addVolumeAndTypeColumns();
    }
}
