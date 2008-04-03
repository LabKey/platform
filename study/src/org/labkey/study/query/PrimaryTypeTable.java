package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;

public class PrimaryTypeTable extends StudyTable
{
    public PrimaryTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenPrimaryType());
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("ScharpId"));
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("PrimaryTypeLdmsCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("PrimaryTypeLabwareCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("PrimaryType")));
        setTitleColumn("Description");
    }
}
