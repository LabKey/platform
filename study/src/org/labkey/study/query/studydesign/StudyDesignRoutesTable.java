package org.labkey.study.query.studydesign;

import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * User: cnathe
 * Date: 7/23/13
 */
public class StudyDesignRoutesTable extends StudyDesignLookupBaseTable
{
    public StudyDesignRoutesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignRoutes());
        setName("StudyDesignRoutes");
    }
}
