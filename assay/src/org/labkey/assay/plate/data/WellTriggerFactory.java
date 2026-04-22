package org.labkey.assay.plate.data;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.collections.LongHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.triggers.Trigger;
import org.labkey.api.data.triggers.TriggerFactory;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.query.WellTable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;
import static org.labkey.api.util.IntegerUtils.asLong;

public final class WellTriggerFactory implements TriggerFactory
{
    @Override
    public @NotNull Collection<Trigger> createTrigger(@Nullable Container c, TableInfo table, Map<String, Object> extraContext)
    {
        return List.of(
            new ValidateRunImportedPlateTrigger(),
            new EnsureSampleWellTypeTrigger(),
            new ValidatePrimaryPlateSetUniqueSamplesTrigger(),
            new ComputeWellGroupsTrigger()
        );
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class ValidateRunImportedPlateTrigger implements Trigger
    {
        private final Set<Long> plateRowIds = new LongHashSet();

        @Override
        public void beforeUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable QueryUpdateService.InsertOption insertOption,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        ) throws ValidationException
        {
            if (oldRow == null || errors.hasErrors() || !oldRow.containsKey(WellTable.Column.PlateId.name()))
                return;

            Long plateRowId = asLong(oldRow.get(WellTable.Column.PlateId.name()));
            if (plateRowId == null)
                return;

            if (plateRowIds.contains(plateRowId))
                return;

            plateRowIds.add(plateRowId);
            Plate plate = PlateManager.get().getPlate(c, plateRowId);
            if (plate == null)
                return;

            int runsInUse = PlateManager.get().getRunCountUsingPlate(c, user, plate);
            if (runsInUse > 0)
                throw new ValidationException(String.format("This %s is used by %d runs and its wells cannot be modified.", plate.isTemplate() ? "Plate template" : "Plate", runsInUse));
        }
    }

    // When no "Type" is given but "SampleId" is populated, provide 'Sample' as the type
    private class EnsureSampleWellTypeTrigger implements Trigger
    {
        private final Map<Long, String> wellTypeMap = new LRUMap<>(PlateSet.MAX_PLATE_SET_WELLS);

        @Override
        public @Nullable ManagedColumns getManagedColumns()
        {
            // "Type" is a calculated column, so we do not include it as a managed column
            return ManagedColumns.ignored(WellTable.Column.Type.name());
        }

        private void addTypeSample(
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            Map<String, Object> extraContext
        )
        {
            if (newRow == null || isCopyOperation(extraContext))
                return;

            // The "SampleID" is not being modified
            if (newRow.get(WellTable.Column.SampleID.name()) == null)
                return;

            // A "Type" is being explicitly provided
            if (newRow.get(WellTable.Column.Type.name()) != null)
                return;

            // A "Type" is already specified
            if (hasWellType(c, user, oldRow))
                return;

            newRow.put(WellTable.Column.Type.name(), WellGroup.Type.SAMPLE.name());
        }

        // Since "Type" is a calculated column (i.e., not in the database), its value is not included in
        // the original row; thus, we need to query for it dynamically.
        private boolean hasWellType(Container c, User user, @Nullable Map<String, Object> oldRow)
        {
            if (oldRow == null)
                return false;

            Long wellRowId = asLong(oldRow.get(WellTable.Column.RowId.name()));
            if (wellRowId == null)
                return false;

            if (!wellTypeMap.containsKey(wellRowId))
            {
                var plateRowId = asInteger(oldRow.get(WellTable.Column.PlateId.name()));
                if (plateRowId == null)
                    return false;

                wellTypeMap.putAll(getWellTypes(c, user, plateRowId));
            }

            return wellTypeMap.get(wellRowId) != null;
        }

        @Override
        public void beforeInsert(
            TableInfo table,
            Container c,
            User user,
            @Nullable QueryUpdateService.InsertOption insertOption,
            @Nullable Map<String, Object> newRow,
            ValidationException errors,
            Map<String, Object> extraContext,
            @Nullable Map<String, Object> existingRecord
        )
        {
            addTypeSample(c, user, newRow, null, extraContext);
        }

        @Override
        public void beforeUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable QueryUpdateService.InsertOption insertOption,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addTypeSample(c, user, newRow, oldRow, extraContext);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class ValidatePrimaryPlateSetUniqueSamplesTrigger implements Trigger
    {
        private final HashSet<Long> mutatedWellRowIds = new LongHashSet();

        private void addWellId(@Nullable Map<String, Object> newRow)
        {
            if (
                newRow != null &&
                newRow.containsKey(WellTable.Column.RowId.name()) &&
                newRow.getOrDefault(WellTable.Column.SampleID.name(), null) != null
            )
            {
                Long wellRowId = MapUtils.getLong(newRow,WellTable.Column.RowId.name());
                if (wellRowId != null)
                    mutatedWellRowIds.add(wellRowId);
            }
        }

        @Override
        public void complete(
            TableInfo table,
            Container c,
            User user,
            TableInfo.TriggerType event,
            BatchValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors())
                return;
            PlateManager.get().validatePrimaryPlateSetUniqueSamples(mutatedWellRowIds, errors);
        }

        @Override
        public void afterInsert(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addWellId(newRow);
        }

        @Override
        public void afterUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            addWellId(newRow);
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class ComputeWellGroupsTrigger implements Trigger
    {
        private final Map<Long, Map<Long, PlateManager.WellGroupChange>> wellGroupChanges = new LongHashMap<>();
        private final Set<Long> modifiedPlates = new LongHashSet();
        private final Map<Long, Map<Long, String>> wellReplicateGroupMap = new LongHashMap<>();

        private void checkForChanges(
            Container container,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            // Skip computing well groups when this is a plate copy operation
            if (newRow == null || isCopyOperation(extraContext))
                return;

            var hasSampleChange = hasSampleChange(newRow);
            var hasTypeGroupReplicateChange = hasTypeGroupReplicateChange(newRow);

            // If this is an insertion (newRow != null && oldRow == null),
            // then verify further to ignore when type, group, and replicateGroup are present but are set to null.
            if (hasTypeGroupReplicateChange && oldRow == null)
            {
                hasTypeGroupReplicateChange = (
                    newRow.get(WellTable.Column.Type.name()) != null ||
                    newRow.get(WellTable.Column.WellGroup.name()) != null ||
                    newRow.get(WellTable.Column.ReplicateGroup.name()) != null
                );
            }

            var wellRowId = MapUtils.getLong(newRow,WellTable.Column.RowId.name());
            if (wellRowId == null)
            {
                errors.addError(new SimpleValidationError("Failed to resolve well \"RowId\"."));
                return;
            }

            var plateRowId = MapUtils.getLong(newRow,WellTable.Column.PlateId.name());
            if (plateRowId == null)
            {
                if (oldRow != null)
                    plateRowId = MapUtils.getLong(oldRow,WellTable.Column.PlateId.name());
                if (plateRowId == null)
                {
                    String error = String.format("Failed to resolve \"PlateId\" for well RowId (%d)", wellRowId);
                    errors.addError(new SimpleValidationError(error));
                    return;
                }
            }

            // If the sample, type, or any data on a replicate well has been updated,
            // then mark the plate as modified and subsequently validate the well groups.
            if (!hasSampleChange && !hasTypeGroupReplicateChange && !hasReplicateChange(container, user, plateRowId, wellRowId))
                return;

            modifiedPlates.add(plateRowId);

            if (hasTypeGroupReplicateChange)
            {
                // If the row does contain the key, then it is treated as an explicit change.
                // In this case we set the value to the empty string.
                var type = getStringValue(WellTable.Column.Type, newRow);
                var group = getStringValue(WellTable.Column.WellGroup, newRow);
                var replicateGroup = getStringValue(WellTable.Column.ReplicateGroup, newRow);
                var change = new PlateManager.WellGroupChange(plateRowId, wellRowId, type, group, replicateGroup);

                wellGroupChanges.computeIfAbsent(plateRowId, (x) -> new LongHashMap<>()).put(wellRowId, change);
            }
        }

        private @Nullable String getStringValue(WellTable.Column column, @NotNull Map<String, Object> row)
        {
            String value = null;

            if (row.containsKey(column.name()))
            {
                value = (String) row.get(column.name());
                if (StringUtils.trimToNull(value) == null)
                    value = "";
            }

            return value;
        }

        private boolean hasReplicateChange(Container container, User user, @NotNull Long plateRowId, @NotNull Long wellRowId)
        {
            var wellMap = wellReplicateGroupMap.computeIfAbsent(plateRowId, (pid) -> getWellReplicateGroups(container, user, pid));

            return wellMap.get(wellRowId) != null;
        }

        private boolean hasSampleChange(@Nullable Map<String, Object> row)
        {
            return row != null && row.containsKey(WellTable.Column.SampleID.name());
        }

        private boolean hasTypeGroupReplicateChange(@Nullable Map<String, Object> row)
        {
            return row != null && (
                row.containsKey(WellTable.Column.Type.name()) ||
                row.containsKey(WellTable.Column.WellGroup.name()) ||
                row.containsKey(WellTable.Column.ReplicateGroup.name())
            );
        }

        @Override
        public void complete(
            TableInfo table,
            Container c,
            User user,
            TableInfo.TriggerType event,
            BatchValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors() || (wellGroupChanges.isEmpty() && modifiedPlates.isEmpty()))
                return;

            try
            {
                PlateManager.get().computeWellGroups(c, user, wellGroupChanges);
                PlateManager.get().validateWellGroups(c, user, modifiedPlates);
            }
            catch (ValidationException e)
            {
                errors.addRowError(e);
            }
        }

        @Override
        public void afterInsert(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors())
                return;

            checkForChanges(c, user, newRow, null, errors, extraContext);
        }

        @Override
        public void afterUpdate(
            TableInfo table,
            Container c,
            User user,
            @Nullable Map<String, Object> newRow,
            @Nullable Map<String, Object> oldRow,
            ValidationException errors,
            Map<String, Object> extraContext
        )
        {
            if (errors.hasErrors())
                return;

            checkForChanges(c, user, newRow, oldRow, errors, extraContext);
        }
    }

    /** Provides the well types for all wells in a plate. Mapped from well "RowId" -> "Type". */
    private Map<Long, String> getWellTypes(Container container, User user, long plateRowId)
    {
        var map = new LongHashMap<String>();
        UserSchema schema = QueryService.get().getUserSchema(user, container, "plate");
        SQLFragment sql = new SQLFragment("SELECT RowId, Type FROM plate.Well WHERE PlateId = ?").add(plateRowId);
        QueryService.get().getSelectBuilder(schema, sql.toDebugString())
                .buildSqlSelector(null)
                .forEach(r -> map.put(r.getLong(WellTable.Column.RowId.name()), r.getString(WellTable.Column.Type.name())));

        return map;
    }

    private Map<Long, String> getWellReplicateGroups(Container container, User user, long plateRowId)
    {
        var map = new LongHashMap<String>();
        UserSchema schema = QueryService.get().getUserSchema(user, container, "plate");
        SQLFragment sql = new SQLFragment("SELECT RowId, ReplicateGroup FROM plate.Well WHERE PlateId = ?").add(plateRowId);
        QueryService.get().getSelectBuilder(schema, sql.toDebugString())
                .buildSqlSelector(null)
                .forEach(r -> map.put(r.getLong(WellTable.Column.RowId.name()), r.getString(WellTable.Column.ReplicateGroup.name())));

        return map;
    }

    private static boolean isCopyOperation(Map<String, Object> extraContext)
    {
        return isOperation(extraContext, PlateManager.PLATE_COPY_FLAG);
    }

    private static boolean isSaveOperation(Map<String, Object> extraContext)
    {
        return isOperation(extraContext, PlateManager.PLATE_SAVE_FLAG);
    }

    private static boolean isOperation(Map<String, Object> extraContext, String flag)
    {
        return extraContext != null && (boolean) extraContext.getOrDefault(flag, false);
    }
}
