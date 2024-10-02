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
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;

public class AssaySampleLookupContext
{
    private final Set<Integer> _runIds;
    private final Map<FieldKey, Boolean> _sampleLookups;

    /** Structure representing a material and its designated role. */
    public record MaterialInput(int materialRowId, String role) {}

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
        this(emptySet());
    }

    public AssaySampleLookupContext(Collection<Integer> runIds)
    {
        _runIds = new HashSet<>(runIds);
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

        if (_sampleLookups.get(col.getFieldKey()))
        {
            AssayProvider provider = AssayService.get().getProvider(run);
            if (provider != null && provider.supportsSampleLookupsAsMaterialInputs())
                _runIds.add(run.getRowId());
        }
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
        var protocolSchema = assayProvider.createProtocolSchema(user, container, run.getProtocol(), null);

        populateSampleLookups(container, user, protocolSchema.createRunsTable(cf), FieldKey.fromParts("RowId"), sampleLookups);
        populateSampleLookups(container, user, protocolSchema.createDataTable(cf), FieldKey.fromParts("Run"), sampleLookups);

        return sampleLookups;
    }

    private void populateSampleLookups(
        Container container,
        User user,
        @Nullable TableInfo table,
        FieldKey fieldKey,
        Map<TableInfoKey, List<SampleLookup>> sampleLookups
    )
    {
        if (table == null)
            return;

        var lookups = getSampleLookups(container, user, table);
        var keyColumn = table.getColumn(fieldKey);

        if (!lookups.isEmpty() && keyColumn != null)
            sampleLookups.put(new TableInfoKey(table, keyColumn), lookups);
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

        // Perform the sync using the service user to ensure that all pre-existing
        // material inputs are retained regardless of the current user's permissions.
        var serviceUser = User.getAdminServiceUser();

        for (Integer expRunRowId : _runIds)
        {
            var run = ExperimentService.get().getExpRun(expRunRowId);
            if (run == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve run with rowId " + expRunRowId));
                return;
            }

            // Ensure the current user has permissions to update in the run's container
            if (!run.getContainer().hasPermission(user, UpdatePermission.class))
                throw new UnauthorizedException("You do not have permissions to update run with rowId " + expRunRowId);

            var assayProvider = AssayService.get().getProvider(run);
            if (assayProvider == null || !assayProvider.supportsSampleLookupsAsMaterialInputs())
                continue;

            var sampleLookups = getSampleLookups(container, serviceUser, run, assayProvider);

            // CONSIDER: Do not short-circuit if there are no sample lookups. A domain could have been modified
            // to no longer contain sample lookups in which case we still need to perform an update.
            // Unfortunately, that means a lot of extra processing for the more common case, so I have elected to
            // leave this as-is. Could consider some alternative flag for domain mutations which would not short-circuit.
            if (sampleLookups.isEmpty())
                continue;

            var protocolApplications = new ArrayList<ExpProtocolApplication>();

            var protocolApplication = run.getProtocolApplication();
            if (protocolApplication != null)
                protocolApplications.add(protocolApplication);

            var inputProtocolApplication = run.getInputProtocolApplication();
            if (inputProtocolApplication != null)
                protocolApplications.add(inputProtocolApplication);

            if (protocolApplications.isEmpty())
                return;

            syncLineageForRun(serviceUser, expRunRowId, sampleLookups, protocolApplications);
        }
    }

    private void syncLineageForRun(
        User user,
        int expRunRowId,
        Map<TableInfoKey, List<SampleLookup>> sampleLookups,
        List<ExpProtocolApplication> protocolApplications
    )
    {
        var inputLineageRoles = getInputLineageRoles(sampleLookups);
        var computed = computeMaterialInputs(expRunRowId, sampleLookups, inputLineageRoles);
        var isSynced = computed.first;
        var materialInputs = computed.second;

        if (isSynced || materialInputs.isEmpty())
            return;

        try (var tx = ExperimentService.get().ensureTransaction())
        {
            for (var pa : protocolApplications)
                pa.removeAllMaterialInputs(user);

            for (var entry : getInputGroups(materialInputs, inputLineageRoles).entrySet())
            {
                for (var pa : protocolApplications)
                    pa.addMaterialInputs(user, entry.getValue(), entry.getKey(), null);
            }

            tx.commit();
        }
    }

    private Map<String, Set<Integer>> getInputGroups(Set<MaterialInput> inputs, Set<String> inputLineageRoles)
    {
        var groups = new LinkedHashMap<String, Set<Integer>>();
        var seen = new HashSet<Integer>();

        var sortedInputs = new ArrayList<>(inputs);
        sortedInputs.sort(new MaterialInputRoleComparator(inputLineageRoles));

        for (var input : inputs)
        {
            // A sample can be marked as a material input at most once
            if (seen.contains(input.materialRowId))
                continue;

            seen.add(input.materialRowId);
            groups.computeIfAbsent(input.role, r -> new HashSet<>()).add(input.materialRowId);
        }

        return groups;
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
            if (!seen.contains(current.materialRowId))
            {
                seen.add(current.materialRowId);
                newInputs.removeIf(input -> current.materialRowId == input.materialRowId);
            }

            newInputs.add(current);
        }

        return Pair.of(currentInputs.equals(newInputs), newInputs);
    }

    private Set<MaterialInput> getCurrentMaterialInputs(int expRunRowId)
    {
        var sql = new SQLFragment("""
            SELECT MI.MaterialId AS MaterialRowId, MI.Role AS MaterialInputRole
            FROM exp.MaterialInput MI
            INNER JOIN exp.ProtocolApplication PA ON MI.TargetApplicationId = PA.RowId
            WHERE PA.RunId = ?
        """).add(expRunRowId);

        return getMaterialInputs(new SqlSelector(ExperimentService.get().getSchema(), sql));
    }

    private Set<MaterialInput> getNewMaterialInputs(Map<TableInfoKey, List<SampleLookup>> sampleLookups, int expRunRowId)
    {
        if (sampleLookups.isEmpty())
            return new HashSet<>();

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
