package org.labkey.study.query.studydesign;

import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: cnathe
 * Date: 7/24/13
 */
public class StudyDesignSampleTypesTable extends StudyDesignLookupBaseTable
{
    public StudyDesignSampleTypesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignSampleTypes());
        setName("StudyDesignSampleTypes");

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("PrimaryType"),
                FieldKey.fromParts("ShortSampleCode"),
                FieldKey.fromParts("Inactive")
        ));
        setDefaultVisibleColumns(defaultColumns);

    }
}
