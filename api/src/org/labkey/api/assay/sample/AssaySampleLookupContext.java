package org.labkey.api.assay.sample;

import org.apache.commons.collections4.map.LRUMap;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class AssaySampleLookupContext
{
    private final FieldKey _fieldKey;
    private final Set<Integer> _runsWithSampleLookupChanges;
    private final Map<FieldKey, Boolean> _sampleLookups;
    private final TableInfo _table;

    public AssaySampleLookupContext(TableInfo table, FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
        _runsWithSampleLookupChanges = new HashSet<>();
        _sampleLookups = new HashMap<>();
        _table = table;
    }

    public void markLookup(Container container, User user, ColumnInfo col, ExpRun run)
    {
        if (_runsWithSampleLookupChanges.contains(run.getRowId()))
            return;

        if (!_sampleLookups.containsKey(col.getFieldKey()))
            _sampleLookups.put(col.getFieldKey(), checkSampleLookup(container, user, col).isLookup);

        AssayProvider assayProvider = AssayService.get().getProvider(run);
        if (_sampleLookups.get(col.getFieldKey()) && assayProvider != null && assayProvider.supportsSampleLookupLineage())
            _runsWithSampleLookupChanges.add(run.getRowId());
    }

    public record SampleLookup(boolean isLookup, @Nullable ExpSampleType expSampleType) {}

    public static SampleLookup checkSampleLookup(Container container, User user, DomainProperty dp)
    {
        if (dp == null)
            return new SampleLookup(false, null);

        ExpSampleType sampleType = ExperimentService.get().getLookupSampleType(dp, container, user);
        boolean isSampleLookup = sampleType != null || ExperimentService.get().isLookupToMaterials(dp);

        return new SampleLookup(isSampleLookup, sampleType);
    }

    private SampleLookup checkSampleLookup(Container container, User user, ColumnInfo col)
    {
        Domain domain = _table.getDomain();
        return checkSampleLookup(container, user, domain == null ? null : domain.getPropertyByURI(col.getPropertyURI()));
    }

    public void syncLineage(Container container, User user, BatchValidationException errors)
    {
        for (Integer expRunRowId : _runsWithSampleLookupChanges)
        {
            ExpRun run = ExperimentService.get().getExpRun(expRunRowId);
            if (run == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve run with rowId " + expRunRowId));
                return;
            }

            AssayProvider assayProvider = AssayService.get().getProvider(run);
            if (assayProvider == null)
            {
                errors.addRowError(new ValidationException("Failed to resolve assay provider for run with rowId " + expRunRowId));
                return;
            }

            var inputContext = computeMaterialInputs(container, user, expRunRowId, errors);

            if (errors.hasErrors())
                return;

            var addedMaterialInputs = inputContext.materialInputs;
            var lineageRoles = inputContext.lineageRoles;
            var removedMaterialInputs = new HashSet<Integer>();

            for (var materialEntry : run.getMaterialInputs().entrySet())
            {
                // This material input is sourced from a different column or other source. Skip it.
                if (!lineageRoles.contains(materialEntry.getValue()))
                    continue;

                var material = materialEntry.getKey();
                if (addedMaterialInputs.containsKey(material))
                    addedMaterialInputs.remove(material);
                else
                    removedMaterialInputs.add(material.getRowId());
            }

            if (!removedMaterialInputs.isEmpty())
            {
                var pa = run.getInputProtocolApplication();
                if (pa != null)
                    pa.removeMaterialInputs(user, removedMaterialInputs);
            }

            if (!addedMaterialInputs.isEmpty())
            {
                var pa = run.getInputProtocolApplication();
                if (pa != null)
                {
                    for (var entry : addedMaterialInputs.entrySet())
                        pa.addMaterialInput(user, entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private record InputContext(Map<ExpMaterial, String> materialInputs, Set<String> lineageRoles) {}

    private InputContext computeMaterialInputs(
        Container container,
        User user,
        Integer expRunRowId,
        BatchValidationException errors
    )
    {
        var domain = _table.getDomain();
        if (domain == null)
        {
            errors.addRowError(new ValidationException(String.format("Failed to resolve domain for table \"%s\".", _table.getName())));
            return new InputContext(emptyMap(), emptySet());
        }

        var materialInputs = new HashMap<ExpMaterial, String>();
        var lineageRoles = new HashSet<String>();
        var sampleLookups = _sampleLookups.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        var columns = QueryService.get().getColumns(_table, sampleLookups);
        var columnContext = new HashMap<ColumnInfo, Pair<DomainProperty, ExpSampleType>>();
        var remapCache = new RemapCache();
        var materialsCache = new LRUMap<Integer, ExpMaterial>(1_000);

        for (ColumnInfo column : columns.values())
        {
            var dp = domain.getPropertyByURI(column.getPropertyURI());
            var sampleType = ExperimentService.get().getLookupSampleType(dp, container, user);
            columnContext.put(column, Pair.of(dp, sampleType));
        }

        SimpleFilter scopeFilter = new SimpleFilter(_fieldKey, expRunRowId);

        new TableSelector(_table, columns.values(), scopeFilter, null).forEachResults(results -> {
            for (var column : columns.values())
            {
                var context = columnContext.get(column);

                try
                {
                    var expMaterial = ExperimentService.get().findExpMaterial(container, user, column.getValue(results), context.second, remapCache, materialsCache);

                    if (expMaterial != null)
                    {
                        var role = AssayService.get().getPropertyInputLineageRole(context.first);
                        lineageRoles.add(role);
                        materialInputs.putIfAbsent(expMaterial, role);
                    }
                }
                catch (ValidationException v)
                {
                    // TODO: Is there a way to cancel out of this forEachResult so we stop processing?
                    errors.addRowError(v);
                }
            }
        });

        if (errors.hasErrors())
            return new InputContext(emptyMap(), emptySet());

        return new InputContext(materialInputs, lineageRoles);
    }
}
