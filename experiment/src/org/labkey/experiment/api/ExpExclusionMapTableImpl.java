package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpExclusionMapTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.List;
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
        setDeleteURL(LINK_DISABLER);
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        FieldKey containerFieldKey = FieldKey.fromParts("Container");
        clearConditions(containerFieldKey);
        SQLFragment sql = new SQLFragment("ExclusionId IN (SELECT exclusion.RowId FROM ");
        sql.append(ExperimentService.get().getTinfoExclusion(), "exclusion");
        sql.append(" INNER JOIN ");
        sql.append(ExperimentService.get().getTinfoExperimentRun(), "er");
        sql.append(" ON exclusion.RunId = er.RowId");
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

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRow)
        {
            throw new UnsupportedOperationException();
        }
    }
}
