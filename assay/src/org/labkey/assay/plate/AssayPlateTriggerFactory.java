package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssayPlateTriggerFactory implements TriggerFactory
{
    private final ExpProtocol _protocol;

    public AssayPlateTriggerFactory(ExpProtocol protocol)
    {
        _protocol = protocol;
    }

    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new ReplicateStatsTrigger()
        );
    }

    /**
     * Trigger to handle updates, inserts are handled during assay run creation
     */
    private class ReplicateStatsTrigger implements Trigger
    {
        private Map<String, Boolean> _replicateLsid = new HashMap<>();

        private void checkForChanges(@Nullable Map<String, Object> oldRow, boolean isUpdate)
        {
            if (oldRow != null)
            {
                // check if the change is to a replicate well row
                Object replicateLsid = oldRow.get(AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME);
                if (replicateLsid != null)
                    _replicateLsid.put(String.valueOf(replicateLsid), isUpdate);
            }
        }

        @Override
        public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            checkForChanges(oldRow, true);
        }

        @Override
        public void afterDelete(TableInfo table, Container c, User user, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            checkForChanges(oldRow, false);
        }

        @Override
        public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
        {
            if (!_replicateLsid.isEmpty())
            {
                // recompute the stats for the changed replicate rows
                AssayProvider provider = AssayService.get().getProvider(_protocol);
                if (provider == null)
                    throw new IllegalStateException(String.format("Unable to find the provider for protocol : %s", _protocol.getName()));

                AssayProtocolSchema schema = provider.createProtocolSchema(user, c, _protocol, null);
                TableInfo dataTable = schema.createDataTable(null, false);
                if (dataTable != null)
                {
                    try
                    {
                        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts(AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME), _replicateLsid.keySet());
                        Map<Lsid, List<Map<String, Object>>> replicates = new HashMap<>();

                        new TableSelector(dataTable, filter, null).getResults().forEach(row -> {
                            var lsid = row.get(AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME);
                            replicates.computeIfAbsent(Lsid.parse(String.valueOf(lsid)), m -> new ArrayList<>()).add(row);
                            _replicateLsid.remove(lsid.toString());
                        });

                        // if results are being deleted, check if all rows for the well group have been deleted
                        List<Map<String, Object>> deletedRows = new ArrayList<>();
                        for (Map.Entry<String, Boolean> entry : _replicateLsid.entrySet())
                        {
                            if (!entry.getValue())
                                deletedRows.add(Map.of(PlateReplicateStatsDomainKind.Column.Lsid.name(), entry.getKey()));
                        }

                        if (!deletedRows.isEmpty())
                            AssayPlateMetadataService.get().deleteReplicateStats(c, user, _protocol, deletedRows);

                        AssayPlateMetadataService.get().insertReplicateStats(c, user, _protocol, null, false, replicates);
                    }
                    catch (ExperimentException e)
                    {
                        throw UnexpectedException.wrap(e);
                    }
                }
            }
        }
    }
}
