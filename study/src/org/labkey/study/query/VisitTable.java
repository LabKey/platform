package org.labkey.study.query;

import org.labkey.study.StudySchema;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;

public class VisitTable extends StudyTable
{
    public VisitTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisit());
        addColumn(new AliasedColumn(this, "RowId", _rootTable.getColumn("RowId")));
        addColumn(new AliasedColumn(this, "TypeCode", _rootTable.getColumn("TypeCode")));
        addColumn(new AliasedColumn(this, "SequenceNumMin", _rootTable.getColumn("SequenceNumMin")));
        addColumn(new AliasedColumn(this, "SequenceNumMax", _rootTable.getColumn("SequenceNumMax")));
        addColumn(new AliasedColumn(this, "Label", _rootTable.getColumn("Label")));
        addColumn(new AliasedColumn(this, "ShowByDefault", _rootTable.getColumn("ShowByDefault")));
        ColumnInfo cohortColumn = new AliasedColumn(this, "Cohort", _rootTable.getColumn("CohortId"));
        cohortColumn.setFk(new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                return new CohortTable(_schema);
            }
        });
        addColumn(cohortColumn);
        
        setTitleColumn("Label");
    }
}
