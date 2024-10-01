package org.labkey.api.assay.sample;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocolApplication;
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
import org.labkey.api.util.Pair;

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

    /** Structure representing a material and its designated role. */
    private record MaterialInput(Integer materialRowId, String role) {}

    /** Structure containing the resolved metadata for an assay "sample lookup". */
    public record SampleLookup(
        boolean isLookup,
        @Nullable ColumnInfo columnInfo,
        @Nullable DomainProperty domainProperty,
        @Nullable ExpSampleType expSampleType
    ) {}

    /** A table info and its "key" run identification column. */
    private record TableInfoKey(TableInfo table, ColumnInfo keyColumn) {}

    public AssaySampleLookupContext()
    {
        _runIds = new HashSet<>();
        _sampleLookups = new HashMap<>();
    }

    /**
     * Keeps track of experiment runs where the provided column is a sample lookup.
     * Useful in data updates of assay run/result domains where sample lookups are subsequently reflected
     * as material inputs to an experiment run.
     * @param container Container from which to resolve sample lookup information.
     * @param user User to utilize to resolve sample lookup information.
     * @param table The table to utilize to resole sample lookup information.
     * @param col The potential sample lookup column.
     * @param run The experiment run to track.
     */
    public void trackSampleLookupChange(Container container, User user, TableInfo table, ColumnInfo col, ExpRun run)
    {
        if (!_sampleLookups.containsKey(col.getFieldKey()))
            _sampleLookups.put(col.getFieldKey(), checkSampleLookup(container, user, table, col).isLookup);

        if (_runIds.contains(run.getRowId()))
            return;

        if (_sampleLookups.get(col.getFieldKey()) && AssayService.get().getProvider(run) != null)
            _runIds.add(run.getRowId());
    }

    /**
     * Check if a domain property is considered a valid sample lookup.
     * @param container Container from which to resolve sample lookup information.
     * @param user User to utilize to resolve sample lookup information.
     * @param dp Domain property to check.
     * @return A SampleLookup.
     */
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

    private Map<TableInfoKey, List<SampleLookup>> getSampleLookups(
        Container container,
        User user,
        @NotNull ExpRun run,
        @NotNull AssayProvider assayProvider
    )
    {
        var sampleLookups = new HashMap<TableInfoKey, List<SampleLookup>>();
        var cf = QueryService.get().getContainerFilterForLookups(container, user);

        // Run domain lookups
        {
            var runDomain = assayProvider.getRunDomain(run.getProtocol());
            var table = runDomain.getDomainKind().getTableInfo(user, container, runDomain, cf);
            var lookups = getSampleLookups(container, user, table);
            if (!lookups.isEmpty())
                sampleLookups.put(new TableInfoKey(table, table.getColumn(FieldKey.fromParts("RowId"))), lookups);
        }

        // Result domain lookups
        {
            var resultsDomain = assayProvider.getResultsDomain(run.getProtocol());
            var table = resultsDomain.getDomainKind().getTableInfo(user, container, resultsDomain, cf);
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

    public void syncLineage(Container container, User user, BatchValidationException errors)
    {
        if (_runIds.isEmpty() || errors.hasErrors())
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

            var sampleLookups = getSampleLookups(container, user, run, assayProvider);
            if (sampleLookups.isEmpty())
                continue;

            var coreProtocolApplication = run.getCoreProtocolApplication();
            if (coreProtocolApplication == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve core protocol application for run with rowId " + expRunRowId));
                return;
            }

            var inputProtocolApplication = run.getInputProtocolApplication();
            if (inputProtocolApplication == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve input protocol application for run with rowId " + expRunRowId));
                return;
            }

            syncLineageForRun(user, expRunRowId, sampleLookups, coreProtocolApplication, inputProtocolApplication);
        }
    }

    private void syncLineageForRun(
        User user,
        int expRunRowId,
        Map<TableInfoKey, List<SampleLookup>> sampleLookups,
        @NotNull ExpProtocolApplication coreProtocolApplication,
        @NotNull ExpProtocolApplication inputProtocolApplication
    )
    {
        var inputLineageRoles = getInputLineageRoles(sampleLookups);
        var computed = computeMaterialInputs(expRunRowId, sampleLookups, inputLineageRoles);
        var isSynced = computed.first;
        var newInputs = computed.second;

        if (isSynced)
            return;

        coreProtocolApplication.removeAllMaterialInputs(user);
        inputProtocolApplication.removeAllMaterialInputs(user);

        if (!newInputs.isEmpty())
        {
            var seen = new HashSet<Integer>();
            var inputGroups = new HashMap<String, Set<Integer>>();

            // TODO: Compute more consistently
            // Always choose non-lookup based roles first so they do not get evicted in the future.
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
            {
                coreProtocolApplication.addMaterialInputs(user, entry.getValue(), entry.getKey(), null);
                inputProtocolApplication.addMaterialInputs(user, entry.getValue(), entry.getKey(), null);
            }
        }
    }

    private Set<String> getInputLineageRoles(Map<TableInfoKey, List<SampleLookup>> sampleLookups)
    {
        var inputLineageRoles = new HashSet<String>();
        for (var entry : sampleLookups.entrySet())
        {
            for (var lookup : entry.getValue())
            {
                if (lookup.domainProperty != null)
                    inputLineageRoles.add(AssayService.get().getPropertyInputLineageRole(lookup.domainProperty));
            }
        }

        return inputLineageRoles;
    }

    private Pair<Boolean, Set<MaterialInput>> computeMaterialInputs(
        int expRunRowId,
        Map<TableInfoKey, List<SampleLookup>> sampleLookups,
        Set<String> inputLineageRoles
    )
    {
        var currentInputs = getCurrentMaterialInputs(expRunRowId);
        var newInputs = getNewMaterialInputs(sampleLookups, expRunRowId);
        var seen = new HashSet<Integer>();

        for (var current : currentInputs)
        {
            if (inputLineageRoles.contains(current.role))
                continue;

            // Retain all non-"input lineage roles" inputs
            if (current.materialRowId != null && !seen.contains(current.materialRowId))
            {
                seen.add(current.materialRowId);
                newInputs.removeIf(input -> current.materialRowId.equals(input.materialRowId));
            }

            newInputs.add(current);
        }

        return Pair.of(currentInputs.equals(newInputs), newInputs);
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

                sql.append(getLookupColumnSql(entry.getKey(), sampleLookup, expRunRowId));
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

    private SQLFragment getLookupColumnSql(TableInfoKey tableInfoKey, SampleLookup sampleLookup, int expRunRowId)
    {
        // Check invariants
        if (sampleLookup.domainProperty == null)
            throw new IllegalArgumentException("Assay domain sample lookup does not have a resolved domain property.");

        if (sampleLookup.columnInfo == null)
            throw new IllegalArgumentException("Assay domain sample lookup does not have a resolved column.");

        var column = sampleLookup.columnInfo;
        var table = tableInfoKey.table;
        var columnName = table.getSqlDialect().getColumnSelectName(column.getAlias());

        var tableFilter = new SimpleFilter(tableInfoKey.keyColumn.getFieldKey(), expRunRowId);
        tableFilter.addCondition(column.getFieldKey(), null, CompareType.NONBLANK);
        var tableSql = QueryService.get().getSelectBuilder(table)
                .columns(Set.of(tableInfoKey.keyColumn, column))
                .filter(tableFilter)
                .buildSqlFragment();

        var role = AssayService.get().getPropertyInputLineageRole(sampleLookup.domainProperty);
        var sql = new SQLFragment();

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

            if (sampleLookup.expSampleType != null)
                sql.append(" WHERE MA.MaterialSourceId = ?").add(sampleLookup.expSampleType.getRowId()).append("\n");
        }

        return sql;
    }
}
