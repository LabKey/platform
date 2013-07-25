package org.labkey.study.query.studydesign;

import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * User: cnathe
 * Date: 7/24/13
 */
public class StudyDesignUnitsTable extends StudyDesignLookupBaseTable
{
    public StudyDesignUnitsTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignUnits());
        setName("StudyDesignUnits");
    }
}
