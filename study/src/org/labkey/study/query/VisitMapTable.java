package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.study.StudySchema;

public class VisitMapTable extends BaseStudyTable
{
    public VisitMapTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(schema, StudySchema.getInstance().getTableInfoVisitMap(), cf);

        addFolderColumn();

//        var visitIdColumn = wrapColumn("Visit", _rootTable.getColumn("VisitRowId"));
//        LookupForeignKey visitIdFk = new LookupForeignKey()
//        {
//            @Override
//            public TableInfo getLookupTableInfo()
//            {
//                return schema.createTable("Visit");
//            }
//        };
//        visitIdColumn.setFk(visitIdFk);
//        addColumn(visitIdColumn);
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("VisitRowId")));

//        SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".DataSetId");
//        var datasetLookupCol = new ExprColumn(this, "DataSet", sql, JdbcType.INTEGER);
//        datasetLookupCol.setFk(new LookupForeignKey(cf, "DataSetId", null)
//        {
//            @Override
//            public TableInfo getLookupTableInfo()
//            {
//                return new DatasetsTable(schema, getLookupContainerFilter());
//            }
//        });
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("DatasetId")));
        addWrapColumn(getRealTable().getColumn(FieldKey.fromParts("Required")));
    }
}
