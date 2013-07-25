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
public class StudyDesignAssaysTable extends StudyDesignLookupBaseTable
{
    public StudyDesignAssaysTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudyDesignAssays());
        setName("StudyDesignAssays");

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Description"),
                FieldKey.fromParts("Inactive")
        ));
        setDefaultVisibleColumns(defaultColumns);
    }
}
