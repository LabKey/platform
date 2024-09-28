package org.labkey.api.assay.sample;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssaySampleLookupContext
{
    private final Set<Integer> _runIds;
    private final Map<FieldKey, Boolean> _sampleLookups;
    private final TableInfo _table;

    private record MaterialInput(Integer materialRowId, String role) {}

    public record SampleLookup(boolean isLookup, @Nullable ColumnInfo columnInfo, @Nullable DomainProperty domainProperty, @Nullable ExpSampleType expSampleType) {}

    private record TableInfoKey(TableInfo table, ColumnInfo keyColumn) {}

    public AssaySampleLookupContext(TableInfo table)
    {
        _runIds = new HashSet<>();
        _sampleLookups = new HashMap<>();
        _table = table;
    }

    public void markLookup(Container container, User user, ColumnInfo col, ExpRun run)
    {
        if (!_sampleLookups.containsKey(col.getFieldKey()))
            _sampleLookups.put(col.getFieldKey(), checkSampleLookup(container, user, _table, col).isLookup);

        if (_runIds.contains(run.getRowId()))
            return;

        AssayProvider assayProvider = AssayService.get().getProvider(run);
        if (_sampleLookups.get(col.getFieldKey()) && assayProvider != null)
            _runIds.add(run.getRowId());
    }

    public static SampleLookup checkSampleLookup(Container container, User user, DomainProperty dp)
    {
        return checkSampleLookup(container, user, null, dp);
    }

    private static SampleLookup checkSampleLookup(Container container, User user, @Nullable ColumnInfo col, DomainProperty dp)
    {
        if (dp == null)
            return new SampleLookup(false, null, null, null);

        ExpSampleType sampleType = ExperimentService.get().getLookupSampleType(dp, container, user);
        boolean isSampleLookup = sampleType != null || ExperimentService.get().isLookupToMaterials(dp);

        return new SampleLookup(isSampleLookup, col, dp, sampleType);
    }

    private SampleLookup checkSampleLookup(Container container, User user, TableInfo table, ColumnInfo col)
    {
        Domain domain = table.getDomain();
        return checkSampleLookup(container, user, col, domain == null ? null : domain.getPropertyByURI(col.getPropertyURI()));
    }

    private Map<TableInfoKey, List<SampleLookup>> getSampleLookups(Container container, User user, @NotNull ExpRun run, @NotNull AssayProvider assayProvider)
    {
        Map<TableInfoKey, List<SampleLookup>> sampleLookups = new HashMap<>();

        // Run domain lookups
        {
            var runDomain = assayProvider.getRunDomain(run.getProtocol());
            var table = runDomain.getDomainKind().getTableInfo(user, container, runDomain, null);
            var lookups = getSampleLookups(container, user, table);
            if (!lookups.isEmpty())
                sampleLookups.put(new TableInfoKey(table, table.getColumn(FieldKey.fromParts("RowId"))), lookups);
        }

        // Result domain lookups
        {
            var resultsDomain = assayProvider.getResultsDomain(run.getProtocol());
            var table = resultsDomain.getDomainKind().getTableInfo(user, container, resultsDomain, null);
            var lookups = getSampleLookups(container, user, table);
            if (!lookups.isEmpty())
                sampleLookups.put(new TableInfoKey(table, table.getColumn(FieldKey.fromParts("Run"))), lookups);
        }

        return sampleLookups;
    }

    private List<SampleLookup> getSampleLookups(Container container, User user, TableInfo table)
    {
        var sampleLookups = new ArrayList<SampleLookup>();
        for (var column : table.getColumns())
        {
            var sampleLookup = checkSampleLookup(container, user, table, column);
            if (sampleLookup.isLookup)
                sampleLookups.add(sampleLookup);
        }

        return sampleLookups;
    }

    public void syncLineage(Container container, User user, BatchValidationException errors) throws SQLException
    {
        if (_runIds.isEmpty())
            return;

        for (Integer expRunRowId : _runIds)
        {
            var run = ExperimentService.get().getExpRun(expRunRowId);
            if (run == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve run with rowId " + expRunRowId));
                return;
            }

            var assayProvider = AssayService.get().getProvider(run);
            if (assayProvider == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve assay provider for run with rowId " + expRunRowId));
                return;
            }

            var pa = run.getInputProtocolApplication();
            if (pa == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve input protocol application for run with rowId " + expRunRowId));
                return;
            }

            var sampleLookups = getSampleLookups(container, user, run, assayProvider);
            if (sampleLookups.isEmpty())
                continue;

            var currentInputs = getCurrentMaterialInputs(expRunRowId);
            var newInputs = computeMaterialInputs(currentInputs, sampleLookups, expRunRowId);

            if (currentInputs.equals(newInputs))
                return;

            pa.removeAllMaterialInputs(user);

            if (!newInputs.isEmpty())
            {
                var seen = new HashSet<Integer>();
                var inputGroups = new HashMap<String, Set<Integer>>();

                // TODO: Compute more consistently
                for (var input : newInputs)
                {
                    // A sample can be marked as a material input at most once.
                    if (seen.contains(input.materialRowId))
                        continue;

                    seen.add(input.materialRowId);
                    inputGroups.putIfAbsent(input.role, new HashSet<>());
                    inputGroups.get(input.role).add(input.materialRowId);
                }

                for (var entry : inputGroups.entrySet())
                    pa.addMaterialInputs(user, entry.getValue(), entry.getKey(), null);
            }

            if (errors.hasErrors())
                return;
        }
    }

    private Set<MaterialInput> computeMaterialInputs(
        Set<MaterialInput> currentInputs,
        Map<TableInfoKey, List<SampleLookup>> sampleLookups,
        int expRunRowId
    )
    {
        var newInputs = getNewMaterialInputs(sampleLookups, expRunRowId);

        var lineageRoles = new HashSet<String>();
        for (var entry : sampleLookups.entrySet())
        {
            for (var lookup : entry.getValue())
                lineageRoles.add(AssayService.get().getPropertyInputLineageRole(lookup.domainProperty));
        }

        for (var past : currentInputs)
        {
            // Retain all non-lineage inputs
            if (!lineageRoles.contains(past.role))
            {
                if (past.materialRowId != null)
                    newInputs.removeIf(input -> past.materialRowId.equals(input.materialRowId));

                newInputs.add(past);
            }
        }

        return newInputs;
    }

    private Set<MaterialInput> getCurrentMaterialInputs(Integer expRunRowId)
    {
        var sql = new SQLFragment("""
            SELECT MI.MaterialId AS MaterialRowId, MI.Role AS MaterialInputRole
            FROM exp.MaterialInput MI
            INNER JOIN exp.ProtocolApplication PA ON MI.TargetApplicationId = PA.RowId
            WHERE PA.RunId = ?
        """).add(expRunRowId);

        return getMaterialInputs(new SqlSelector(ExperimentService.get().getSchema(), sql));
    }

    private Set<MaterialInput> getNewMaterialInputs(Map<TableInfoKey, List<SampleLookup>> sampleLookups, Integer expRunRowId)
    {
        var sql = new SQLFragment();
        boolean first = true;

        for (var entry : sampleLookups.entrySet())
        {
            for (var sampleLookup : entry.getValue())
            {
                if (first)
                    first = false;
                else
                    sql.append(" UNION\n");

                sql.append(getLookupColumnSql(entry.getKey(), sampleLookup.columnInfo, sampleLookup.domainProperty, expRunRowId));
            }
        }

        return getMaterialInputs(new SqlSelector(ExperimentService.get().getSchema(), sql));
    }

    private Set<MaterialInput> getMaterialInputs(SqlSelector sqlSelector)
    {
        var inputs = new HashSet<MaterialInput>();
        sqlSelector.forEach(result -> inputs.add(new MaterialInput(result.getInt("MaterialRowId"), result.getString("MaterialInputRole"))));
        return inputs;
    }

    private SQLFragment getLookupColumnSql(TableInfoKey tableInfoKey, ColumnInfo column, DomainProperty dp, int expRunRowId)
    {
        var columns = Set.of(tableInfoKey.keyColumn, column);
        var table = tableInfoKey.table;
        var columnName = table.getSqlDialect().getColumnSelectName(column.getAlias());
        var keyColumnName = table.getSqlDialect().getColumnSelectName(tableInfoKey.keyColumn.getAlias());

        var sql = new SQLFragment();
        var tableSql = QueryService.get().getSelectBuilder(table).columns(columns).buildSqlFragment();
        var role = AssayService.get().getPropertyInputLineageRole(dp);

        if (column.getJdbcType().isInteger())
        {
            sql.append("SELECT DA.").append(columnName).append(" AS MaterialRowId");
            sql.append(", ?").add(role).append(" AS MaterialInputRole\n");
            sql.append(" FROM (").append(tableSql).append(") DA");
        }
        else if (column.getJdbcType().isText())
        {
            sql.append("SELECT MA.RowId").append(" AS MaterialRowId");
            sql.append(", ?").add(role).append(" AS MaterialInputRole\n");
            sql.append(" FROM exp.Material MA\n");
            sql.append(" INNER JOIN (").append(tableSql).append(") DA ON MA.name = DA.").append(columnName).append("\n");
        }

        sql.append(" WHERE DA.").append(columnName).append(" IS NOT NULL AND DA.").append(keyColumnName).append(" = ?").add(expRunRowId).append("\n");

        return sql;
    }
}
