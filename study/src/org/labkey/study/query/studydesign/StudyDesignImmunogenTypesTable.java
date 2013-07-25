package org.labkey.study.query.studydesign;

import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * User: cnathe
 * Date: 7/22/13
 */
public class StudyDesignImmunogenTypesTable extends StudyDesignLookupBaseTable
{
    public StudyDesignImmunogenTypesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes());
        setName("StudyDesignImmunogenTypes");
    }
}
