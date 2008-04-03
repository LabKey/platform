package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;

public class AdditiveTypeTable extends StudyTable
{
    public AdditiveTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenAdditive());
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("ScharpId"));
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("LdmsAdditiveCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("LabwareAdditiveCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Additive")));
        setTitleColumn("Description");
    }
}
