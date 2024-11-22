package org.labkey.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssayPlateTriggerFactory implements TriggerFactory
{
    private final ExpProtocol _protocol;
    private DomainProperty _qcStateProp;

    public AssayPlateTriggerFactory(ExpProtocol protocol)
    {
        _protocol = protocol;

        if (_protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(_protocol);
            if (provider != null)
                _qcStateProp = AssayPlateMetadataServiceImpl.getAssayStateProp(provider.getResultsDomain(_protocol));
        }
    }

    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new ReplicateStatsTrigger(),
            new DataStateTrigger()
        );
    }

    /**
     * Recompute the stats for the changed replicate rows.
     * Trigger to handle updates, inserts are handled during assay run creation.
     */
    private class ReplicateStatsTrigger implements Trigger
    {
        private final Map<String, Boolean> _replicateLsid = new HashMap<>();

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
        public void afterUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            if (errors.hasErrors())
                return;

            checkForChanges(oldRow, true);
        }

        @Override
        public void afterDelete(TableInfo table, Container c, User user, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext)
        {
            if (errors.hasErrors())
                return;

            checkForChanges(oldRow, false);
        }

        @Override
        public void complete(TableInfo table, Container c, User user, TableInfo.TriggerType event, BatchValidationException errors, Map<String, Object> extraContext)
        {
            if (_replicateLsid.isEmpty() || errors.hasErrors())
                return;

            var filter = new SimpleFilter(FieldKey.fromParts(AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME), _replicateLsid.keySet(), CompareType.IN);

            try (TableResultSet rs = new TableSelector(table, filter, null).getResultSet())
            {
                var replicates = new HashMap<Lsid, List<Map<String, Object>>>();

                while (rs.next())
                {
                    var lsid = rs.getString(AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME);
                    replicates.computeIfAbsent(Lsid.parse(String.valueOf(lsid)), m -> new ArrayList<>()).add(rs.getRowMap());
                    _replicateLsid.remove(lsid);
                }

                // If results are being deleted, check if all rows for the well group have been deleted
                var deletedRows = new ArrayList<Map<String, Object>>();
                for (var entry : _replicateLsid.entrySet())
                {
                    if (!entry.getValue())
                        deletedRows.add(Map.of(PlateReplicateStatsDomainKind.Column.Lsid.name(), entry.getKey()));
                }

                if (!deletedRows.isEmpty())
                    AssayPlateMetadataService.get().deleteReplicateStats(c, user, _protocol, deletedRows);

                AssayPlateMetadataService.get().updateReplicateStats(c, user, _protocol, replicates);
            }
            catch (ExperimentException | SQLException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    /**
     * Trigger to help validate state values on update. Inserts will be handled on assay
     * run creation.
     */
    private class DataStateTrigger implements Trigger
    {
        @Override
        public void beforeUpdate(TableInfo table, Container c, User user, @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, ValidationException errors, Map<String, Object> extraContext) throws ValidationException
        {
            if (newRow != null && _qcStateProp != null)
                AssayPlateMetadataServiceImpl.validateRowDataStates(c, newRow, _qcStateProp);
        }
    }
}
