package org.labkey.experiment.api;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpExclusionMapTable;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.Map;

public class ExpExclusionMapTableImpl extends ExpTableImpl<ExpExclusionMapTable.Column> implements  ExpExclusionMapTable
{
    private Map<String, String> _columnMapping = new CaseInsensitiveHashMap<>();

    public ExpExclusionMapTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExclusionMap(), schema, null);
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return DatabaseTableType.TABLE;
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                ColumnInfo rowIdColumnInfo = wrapColumn(alias, _rootTable.getColumn("RowId"));
                rowIdColumnInfo.setHidden(true);
                return rowIdColumnInfo;
            case ExclusionId:
                ColumnInfo exclusionColumnInfo = wrapColumn(alias, _rootTable.getColumn("ExclusionId"));
                exclusionColumnInfo.setFk(getExpSchema().getExclusionForeignKey());
                return exclusionColumnInfo;
            case DataRowId:
                return wrapColumn(alias, _rootTable.getColumn("DataRowId"));
            case Created:
                return wrapColumn(alias, _rootTable.getColumn("Created"));
            case CreatedBy:
                ColumnInfo createdByColumn = wrapColumn(alias, _rootTable.getColumn("CreatedBy"));
                createdByColumn.setFk(new UserIdForeignKey(getUserSchema()));
                return createdByColumn;
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                ColumnInfo modifiedByColumn = wrapColumn(alias, _rootTable.getColumn("ModifiedBy"));
                modifiedByColumn.setFk(new UserIdForeignKey(getUserSchema()));
                return modifiedByColumn;
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.RowId);
        addColumn(Column.ExclusionId);
        addColumn(Column.DataRowId);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new UpdateService(this);
    }

    public class UpdateService extends DefaultQueryUpdateService
    {
        public UpdateService(TableInfo queryTable)
        {
            super(queryTable, ExperimentService.get().getTinfoAssayQCFlag(), _columnMapping);
        }
    }
}
