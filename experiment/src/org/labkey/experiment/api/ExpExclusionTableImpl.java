package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpExclusionTable;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                UserIdForeignKey.initColumn(createdByColumn);
                return createdByColumn;
            case Modified:
                return wrapColumn(alias, _rootTable.getColumn("Modified"));
            case ModifiedBy:
                ColumnInfo modifiedByColumn = wrapColumn(alias, _rootTable.getColumn("ModifiedBy"));
                UserIdForeignKey.initColumn(modifiedByColumn);
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
            super(queryTable, ExperimentService.get().getTinfoExclusion(), _columnMapping);
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            // Select which exclusionMapss are to be deleted
            List<Integer> exclusionMapsToDelete = new ArrayList<>();
            Set<String> dataRowIds = new HashSet<>();
            TableInfo exclusionMapsTi = ExperimentService.get().getTinfoExclusionMap();
            int runId = -1;
            for (String key : oldRowMap.keySet())
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ExclusionId"), (Integer) oldRowMap.get(key));
                LinkedHashSet<ColumnInfo> cols = new LinkedHashSet<>();
                cols.add(exclusionMapsTi.getColumn("RowId"));
                cols.add(exclusionMapsTi.getColumn("ExclusionId"));
                cols.add(exclusionMapsTi.getColumn("DataRowId"));
                TableSelector ts = new TableSelector(exclusionMapsTi, cols, filter, null);
                try (TableResultSet rs = ts.getResultSet(false, false))
                {
                    for (Map<String, Object> row : rs)
                    {
                        exclusionMapsToDelete.add((Integer) row.get("RowId"));
                        dataRowIds.add(String.valueOf(row.get("DataRowId")));
                    }
                }

                TableInfo exclusionTi = ExperimentService.get().getTinfoExclusion();
                TableSelector tsExclusion = new TableSelector(exclusionTi, exclusionTi.getColumns("RunId"), new SimpleFilter(FieldKey.fromParts("RowId"), oldRowMap.get(key)), null);
                try (TableResultSet rs = tsExclusion.getResultSet(false, false))
                {
                    for (Map<String, Object> row : rs)
                    {
                        runId = (Integer) row.get("RunId");
                    }
                }
            }

            for (Integer rowId : exclusionMapsToDelete)
            {
                SimpleFilter exclusionMapsFilter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
                Table.delete(exclusionMapsTi, exclusionMapsFilter);
            }


            Map<String, Object> results = super.deleteRow(user, container, oldRowMap);
            if (runId > 0)
            {
                ExpRun run = ExperimentService.get().getExpRun(runId);
                String auditMsg = "Exclusion event" + (oldRowMap.keySet().size() > 1 ? "s have" : " has") + " been deleted from run '"  + run.getName()
                        + "'. DataRowId: " + StringUtils.join(dataRowIds, ",") + ".";
                ExperimentServiceImpl.get().auditRunEvent(user, run.getProtocol(), run, null, auditMsg);
            }

            return results;
        }

        @Override
        public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, @Nullable Map<Enum, Object> configParameters, @Nullable Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {

            List<Map<String, Object>> ret = new ArrayList<>();
            for (Map<String, Object> k : keys)
            {
                ret.add(this.deleteRow(user, container, k));
            }

            return ret;
        }
    }
}
