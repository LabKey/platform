package org.labkey.study.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.study.StudySchema;

public class VisitMapTable extends BaseStudyTable
{
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
                return schema.createTable("Visit");
            }
        };
        visitIdColumn.setFk(visitIdFk);
        addColumn(visitIdColumn);

        SQLFragment sql = new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".DataSetId");
        var datasetLookupCol = new ExprColumn(this, "DataSet", sql, JdbcType.VARCHAR);
        datasetLookupCol.setFk(new LookupForeignKey(cf, "DataSetId", "Name")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return new DatasetsTable(schema, getLookupContainerFilter());
            }
        });
        addColumn(datasetLookupCol);
    }

//    static class VisitMapService extends DefaultQueryUpdateService
//    {
//        public VisitMapService(FilteredTable table) { super(table, table.getRealTable()); }
//    }
//
//    @Override
//    public @Nullable QueryUpdateService getUpdateService()
//    {
//        return new VisitMapService(this);
//    }
}
