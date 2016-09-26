package org.labkey.study.query.studydesign;

import org.labkey.api.data.ContainerFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * Created by klum on 9/23/2016.
 */
public class DoseAndRouteTable extends StudyDesignLookupBaseTable
{
    public DoseAndRouteTable(StudyQuerySchema schema, ContainerFilter filter)
    {
        super(schema, StudySchema.getInstance().getTableInfoDoseAndRoute(), filter);
        setName("DoseAndRoute");
    }
}
