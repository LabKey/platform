package org.labkey.experiment.api;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpExclusionTable;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.Map;

public class ExpExclusionTableImpl extends ExpTableImpl<ExpExclusionTable.Column> implements ExpExclusionTable
{
    private Map<String, String> _columnMapping = new CaseInsensitiveHashMap<>();

    public ExpExclusionTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoExclusion(), schema, null);
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
            case RunId:
                ColumnInfo runColumnInfo = wrapColumn(alias, _rootTable.getColumn("RunId"));
                runColumnInfo.setFk(getExpSchema().getRunIdForeignKey());
                return runColumnInfo;
            case Comment:
                return wrapColumn(alias, _rootTable.getColumn("Comment"));
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
        addColumn(Column.RunId);
        addColumn(Column.Comment);
        addColumn(Column.Created);
        addColumn(Column.CreatedBy);
        addColumn(Column.Modified);
        addColumn(Column.ModifiedBy);
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        FieldKey containerFieldKey = FieldKey.fromParts("Container");
        clearConditions(containerFieldKey);
        SQLFragment sql = new SQLFragment("RunId IN (SELECT er.RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" WHERE ");
        sql.append(filter.getSQLFragment(getSchema(), new SQLFragment("er.Container"), getContainer()));
        sql.append(")");

        addCondition(sql, containerFieldKey);
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
