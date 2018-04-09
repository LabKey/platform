package org.labkey.study.query;

import org.labkey.api.query.FieldKey;
import org.labkey.study.StudySchema;

public class VisitAliasesTable extends BaseStudyTable
{
    public VisitAliasesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitAliases());

        addFolderColumn();
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("RowId")));
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Name")));
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("SequenceNum")));
    }
}
