package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;

public class DerivativeTypeTable extends StudyTable
{
    public DerivativeTypeTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenDerivative());
        addWrapColumn(_rootTable.getColumn("RowId"));
        addWrapColumn(_rootTable.getColumn("ScharpId"));
        addColumn(new AliasedColumn(this, "LdmsCode", _rootTable.getColumn("LdmsDerivativeCode")));
        addColumn(new AliasedColumn(this, "LabwareCode", _rootTable.getColumn("LabwareDerivativeCode")));
        addColumn(new AliasedColumn(this, "Description", _rootTable.getColumn("Derivative")));
        setTitleColumn("Description");
    }
}