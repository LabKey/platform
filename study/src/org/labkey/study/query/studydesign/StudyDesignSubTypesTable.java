package org.labkey.study.query.studydesign;

import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

/**
 * User: cnathe
 * Date: 7/23/13
 */
public class StudyDesignSubTypesTable extends StudyDesignLookupBaseTable
{
    public StudyDesignSubTypesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignSubTypes());
        setName("StudyDesignSubTypes");
    }
}