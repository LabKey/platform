package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.study.StudySchema;

public class VisitMapTable extends BaseStudyTable
{
    private QueryForeignKey.Builder studyFK()
    {
        return QueryForeignKey.from(_userSchema,getContainerFilter());
    }

    public VisitMapTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitMap(), cf);

        addFolderColumn();
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Required")));

        var visitIdColumn = wrapColumn("Visit", _rootTable.getColumn("VisitRowId"));
        LookupForeignKey visitIdFk = new LookupForeignKey()
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new VisitTable(_userSchema, getLookupContainerFilter());
            }
        };
        visitIdColumn.setFk(visitIdFk);
        addColumn(visitIdColumn);

        var dataSetColumn = new AliasedColumn(this, "DataSet", _rootTable.getColumn("DataSetId"));
        dataSetColumn.setFk(studyFK().to("DataSets", "DataSetId", "Name"));
        addColumn(dataSetColumn);
    }
}
