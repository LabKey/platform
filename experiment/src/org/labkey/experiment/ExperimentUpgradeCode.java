/*
 * Copyright (c) 2008-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ontology.Unit;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.ClosureQueryHelper;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.property.DomainImpl;
import org.labkey.experiment.api.property.DomainPropertyImpl;
import org.labkey.experiment.api.property.StorageProvisionerImpl;
import org.labkey.experiment.samples.SampleTimelineAuditProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotUnit;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AvailableAliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Name;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;
import static org.labkey.experiment.ExperimentModule.AMOUNT_AND_UNIT_UPGRADE_PROP;
import static org.labkey.experiment.ExperimentModule.AUDIT_COUNT_PROP;
import static org.labkey.experiment.ExperimentModule.TRANSACTION_ID_PROP;

public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentUpgradeCode.class, "Experiment upgrade status");

    // called from exp-25.006-25.007.sql
    @SuppressWarnings("unused")
    public static void ensureBigObjectIds(ModuleContext context)
    {
        if (AppProps.getInstance().isDevMode() && ModuleLoader.getInstance().shouldInsertData())
        {
            DbScope primary = DbScope.getLabKeyScope();
            String schemaName = "exp";
            long desiredValue = Integer.MAX_VALUE + 1L;
            if (primary.getSqlDialect().isPostgreSQL())
            {
                String sequenceName = "object_objectid_seq";
                ensureBigObjectIds(
                    // Calling currval() is not an option since it requires a previous call to nextval() in this database session
                    new SqlSelector(primary, new SQLFragment("SELECT last_value FROM pg_sequences WHERE schemaname = ? AND sequencename = ?", schemaName, sequenceName)),
                    newValue -> new SqlExecutor(primary).execute("SELECT setval(?, ?)", schemaName + "." + sequenceName, newValue),
                    desiredValue
                );
            }
            else
            {
                String tableName = schemaName + "." + "Object";
                ensureBigObjectIds(
                    new SqlSelector(primary, new SQLFragment("SELECT IDENT_CURRENT(?)", tableName)),
                    newValue -> new SqlExecutor(primary).execute("DBCC CHECKIDENT(?, RESEED, ?)", tableName, newValue),
                    desiredValue
                );
            }
        }
    }

    private static void ensureBigObjectIds(Selector lastValueSelector, Consumer<Long> setValueConsumer, long desiredValue)
    {
        Long lastValue = lastValueSelector.getObject(Long.class);
        if (lastValue == null || lastValue < desiredValue)
        {
            setValueConsumer.accept(desiredValue);
            LOG.info("Setting exp.ObjectId next value to {}. Last value was previously {}.", desiredValue, lastValue);
        }
    }

    // called from exp-25.009-25.010.sql
    // When this is removed, the ExperimentWarningProvider class can also be removed.
    @SuppressWarnings("unused")
    public static void upgradeAmountsAndUnits(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = ExperimentService.get().getSchema().getScope();
        LimitedUser admin = new LimitedUser(context.getUpgradeUser(), SiteAdminRole.class);
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // create a single transaction event at the root container for use in tying all updates together
            TransactionAuditProvider.TransactionAuditEvent transactionEvent = AbstractQueryUpdateService.createTransactionAuditEvent(ContainerManager.getRoot(), QueryService.AuditAction.UPDATE);
            transaction.setAuditEvent(transactionEvent);
            Long transactionId = transaction.getAuditId();
            AtomicInteger auditCount = new AtomicInteger();
            ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(c ->
                    auditCount.addAndGet(convertAmountsToBaseUnits(c, admin))
            );
            transaction.commit();
            LOG.info("{} Total audit events expected", auditCount);
            if (auditCount.get() > 0)
            {
                PropertyManager.WritablePropertyMap props = PropertyManager.getWritableProperties(AMOUNT_AND_UNIT_UPGRADE_PROP, true);
                props.put(AUDIT_COUNT_PROP, auditCount.toString());
                props.put(TRANSACTION_ID_PROP, String.valueOf(transactionId));
                props.save();
            }
            ExperimentService.get().clearCaches();
        }

    }

    private static void getAmountAndUnitUpdates(Map<String, Object> sampleMap, Parameter unitsCol, Set<Parameter> amountCols, Unit currentDisplayUnit, Map<String, Object> oldDataMap, Map<String, Object> newDataMap, Map<String, Integer> sampleCounts, boolean aliquotFields)
    {
        Unit baseUnit = currentDisplayUnit.getBase();
        String unitsStr = (String) sampleMap.get(unitsCol.getName());
        Unit materialUnit = Unit.fromName(unitsStr);
        boolean isInBaseUnits = materialUnit == null ? currentDisplayUnit.isBase() : materialUnit.isBase();
        // have a unit value, but it did not convert to a known unit
        if (materialUnit == null && !StringUtils.isEmpty(unitsStr))
        {
            // invalid unit stored with sample. Leave as is.
            LOG.info("Found invalid {} '{}' for sample '{}'. No conversion done.", aliquotFields ? "aliquot unit" : "unit", unitsStr, sampleMap.get(Name.name()));
            sampleCounts.put("invalidUnits", sampleCounts.getOrDefault("invalidUnits", 0) + 1);
        }
        else if (materialUnit != null && !materialUnit.isCompatible(baseUnit))
        {
            LOG.info("{} '{}' for sample '{}' is not compatible with the base unit '{}'. No conversion done.", aliquotFields ? "Aliquot unit" : "Unit", materialUnit.name(), sampleMap.get(Name.name()), baseUnit);
            sampleCounts.put("invalidUnits", sampleCounts.getOrDefault("invalidUnits", 0) + 1);
        }
        else if (!isInBaseUnits || materialUnit == null)
        {
            if (!isInBaseUnits)
            {
                amountCols.forEach(amountCol -> {
                    if (sampleMap.get(amountCol.getName()) != null && !(sampleMap.get(amountCol.getName())).equals(0.0))
                    {
                        oldDataMap.put(amountCol.getName(), sampleMap.get(amountCol.getName()));
                        newDataMap.put(amountCol.getName(), Unit.convert((Double) sampleMap.get(amountCol.getName()), materialUnit == null ? currentDisplayUnit : materialUnit, baseUnit));
                        amountCol.setValue(newDataMap.get(amountCol.getName()));
                        sampleCounts.put("converted", sampleCounts.getOrDefault("converted", 0) + 1);
                    }
                });
                sampleCounts.put("converted", sampleCounts.getOrDefault("converted", 0) + 1);
            }
            else // in base unit, but not explicitly stored
                sampleCounts.put("setUnitsWithoutConvert", sampleCounts.getOrDefault("setUnitsWithoutConvert", 0) + 1);
            if (!baseUnit.name().equals(unitsStr))
            {
                unitsCol.setValue(baseUnit.name());
                oldDataMap.put(unitsCol.getName(), unitsStr);
                newDataMap.put(unitsCol.getName(), baseUnit.name());
            }
        }
        else if (!unitsStr.equals(baseUnit.name()))
        {
            oldDataMap.put(unitsCol.getName(), unitsStr);
            newDataMap.put(unitsCol.getName(), baseUnit.name());
            unitsCol.setValue(baseUnit.name());
            sampleCounts.put("changeUnitsLabel", sampleCounts.getOrDefault("changeUnitsLabel", 0) + 1);
        }
    }

    // Converts amounts for all sample types defined in the given container.
    // Picks up samples from all containers for each sample type.
    private static int convertAmountsToBaseUnits(Container container, User user)
    {
        DbScope scope = ExperimentService.get().getSchema().getScope();
        TableInfo tInfo = ExperimentService.get().getTinfoMaterial();

        try (Connection c = scope.getConnection())
        {
            AtomicInteger auditCount = new AtomicInteger();
            Parameter rowId = new Parameter("rowId", JdbcType.INTEGER);
            Parameter units = new Parameter(Units.name(), JdbcType.VARCHAR);
            Parameter amount = new Parameter(StoredAmount.name(), JdbcType.DOUBLE);
            Parameter aliquotUnits = new Parameter(AliquotUnit.name(), JdbcType.VARCHAR);
            Parameter aliquotAmount = new Parameter(AliquotVolume.name(), JdbcType.DOUBLE);
            Parameter availableAliquotAmount = new Parameter(AvailableAliquotVolume.name(), JdbcType.DOUBLE);
            ParameterMapStatement updateStmt = new ParameterMapStatement(scope, c,
                    new SQLFragment("UPDATE ")
                            .append(tInfo)
                            .append(" SET Units = ?, StoredAmount = ?, AliquotUnit = ?, AliquotVolume = ?, AvailableAliquotVolume = ? WHERE RowId = ?")
                            .addAll(units, amount, aliquotUnits, aliquotAmount, availableAliquotAmount, rowId), null);

            for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(container, false))
            {
                LOG.debug("** Starting upgrade for sample type {} in folder {}", sampleType.getName(), container.getPath());
                Map<String, Integer> sampleCounts = new HashMap<>();
                Map<String, Integer> aliquotCounts = new HashMap<>();

                Unit currentDisplayUnit = Unit.fromName(sampleType.getMetricUnit());
                boolean hasDisplayUnit = currentDisplayUnit != null;

                AtomicInteger batchCount = new AtomicInteger();
                List<AuditTypeEvent> auditEvents = new ArrayList<>();
                SQLFragment sql = new SQLFragment("SELECT m.RowId, m.Name, m.StoredAmount, m.Units, m.AliquotVolume, m.AliquotUnit, m.AvailableAliquotVolume, m.container FROM ")
                    .append(tInfo, "m")
                    .append(" WHERE cpastype = ?").add(sampleType.getLSID())
                    .append(" AND (m.StoredAmount IS NOT NULL OR m.Units IS NOT NULL OR m.AliquotVolume IS NOT NULL OR m.AliquotUnit IS NOT NULL OR m.AvailableAliquotVolume IS NOT NULL)");
                SqlSelector selector = new SqlSelector(scope, sql);

                selector.mapStream().forEach(sampleMap -> {

                    Map<String, Object> oldDataMap = new HashMap<>();
                    Map<String, Object> newDataMap = new HashMap<>();
                    // start out using the data already in the row
                    rowId.setValue(sampleMap.get(RowId.name()));
                    units.setValue(sampleMap.get(Units.name()));
                    amount.setValue(sampleMap.get(StoredAmount.name()));
                    aliquotUnits.setValue(sampleMap.get(AliquotUnit.name()));
                    aliquotAmount.setValue(sampleMap.get(AliquotVolume.name()));
                    availableAliquotAmount.setValue(sampleMap.get(AvailableAliquotVolume.name()));
                    if (!StringUtils.isEmpty((String) sampleMap.get(Units.name())) && sampleMap.get(StoredAmount.name()) == null)
                    {
                        // remove the unit if we had a unit but no amount
                        oldDataMap.put(Units.name(), sampleMap.get(Units.name()));
                        newDataMap.put(Units.name(), null);
                        units.setValue(null);
                        sampleCounts.put("unitsWithoutAmounts", sampleCounts.getOrDefault("unitsWithoutAmounts", 0) + 1);
                    }
                    if (!StringUtils.isEmpty((String) sampleMap.get(AliquotUnit.name())) && sampleMap.get(AliquotVolume.name()) == null && sampleMap.get(AvailableAliquotVolume.name()) == null)
                    {
                        // remove the aliquot unit if we had a unit but no amount
                        oldDataMap.put(AliquotUnit.name(), sampleMap.get(AliquotUnit.name()));
                        newDataMap.put(AliquotUnit.name(), null);
                        aliquotUnits.setValue(null);
                        aliquotCounts.put("unitsWithoutAmounts", aliquotCounts.getOrDefault("unitsWithoutAmounts", 0) + 1);
                    }

                    if (hasDisplayUnit)
                    {
                        if (sampleMap.get(StoredAmount.name()) != null)
                        {
                            getAmountAndUnitUpdates(sampleMap, units, Set.of(amount), currentDisplayUnit, oldDataMap, newDataMap, sampleCounts, false);
                        }
                        if (sampleMap.get(AliquotVolume.name()) != null || sampleMap.get(AvailableAliquotVolume.name()) != null)
                        {
                            getAmountAndUnitUpdates(sampleMap, aliquotUnits, Set.of(aliquotAmount, availableAliquotAmount), currentDisplayUnit, oldDataMap, newDataMap, aliquotCounts, true);
                        }
                    }


                    if (!newDataMap.isEmpty())
                    {
                        updateStmt.addBatch();
                        batchCount.getAndIncrement();
                        // All samples default to a 0 for AliquotVolume and a blank AliquotUnit. If the only
                        // change being made is to replace the blank AliquotUnit with the base unit, we do not
                        // need to audit that change here since these aliquot values are calculated values anyway.
                        if (newDataMap.size() > 1 || !newDataMap.containsKey(AliquotUnit.name()))
                        {
                            Container sampleContainer = ContainerManager.getForId((String) sampleMap.get("Container"));
                            SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(sampleContainer != null ? sampleContainer : container, SampleTimelineAuditEvent.AMOUNT_AND_UNIT_UPGRADE_COMMENT);
                            event.setSampleId((Integer) sampleMap.get(RowId.name()));
                            event.setSampleName((String) sampleMap.get(Name.name()));
                            event.setSampleType(sampleType.getName());
                            event.setSampleTypeId(sampleType.getRowId());
                            event.setLineageUpdate(false);
                            event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(oldDataMap));
                            event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(newDataMap));
                            auditEvents.add(event);
                            auditCount.getAndIncrement();
                        }
                    }
                    if (batchCount.get() > 1000)
                    {
                        updateStmt.executeBatch();
                        AuditLogService.get().addEvents(user, auditEvents, false);
                        auditEvents.clear();
                        batchCount.set(0);
                    }
                });
                if (batchCount.get() > 0)
                {
                    updateStmt.executeBatch();
                    AuditLogService.get().addEvents(user, auditEvents);
                }

                LOG.debug("    Sample data update counts {}", sampleCounts);
                LOG.debug("    Aliquot data update counts {}", aliquotCounts);
                LOG.debug("** Finished upgrade for sample type {} in folder {}", sampleType.getName(), container.getPath());
            }
            LOG.debug("{} Audit events expected for container {}", auditCount, container.getPath());
            return auditCount.get();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Called from exp-25.014-25.015.sql
     */
    @SuppressWarnings("unused")
    public static void dropProvisionedSampleTypeLsidColumn(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // Process all sample types across all containers
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            List<ExpSampleTypeImpl> sampleTypes = new TableSelector(sampleTypeTable, null, null)
                    .stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .toList();

            LOG.info("Dropping the lsid column from {} sample types", sampleTypes.size());

            int successCount = 0;
            for (ExpSampleTypeImpl st : sampleTypes)
            {
                boolean success = dropSampleLsid(st);
                if (success)
                    successCount++;
            }

            LOG.info("Dropped lsid column from {} of {} sample types successfully.", successCount, sampleTypes.size());

            tx.commit();
        }
    }

    private static boolean dropSampleLsid(ExpSampleTypeImpl st)
    {
        ProvisionedSampleTypeContext context = getProvisionedSampleTypeContext(st);
        if (context == null)
            return false;

        Domain domain = context.domain;
        TableInfo table = context.provisionedTable;

        String lsidColumnName = "lsid";
        ColumnInfo lsidColumn = table.getColumn(FieldKey.fromParts(lsidColumnName));
        if (lsidColumn == null)
        {
            LOG.info("No lsid column found on table '{}'. Skipping drop.", table.getName());
            return false;
        }

        Set<String> indicesToRemove = new HashSet<>();
        for (var index : table.getAllIndices())
        {
            var indexColumns = index.columns();
            if (indexColumns.contains(lsidColumn))
            {
                // We only expect to be dropping indices on the LSID column alone. However, if we encounter
                // another index on the provisioned table, log information about the index and continue to remove it.
                if (indexColumns.size() > 1)
                    LOG.info("Dropping index '{}' on table '{}' because it contains the lsid column.", index.name(), table.getName());

                indicesToRemove.add(index.name());
            }
        }

        if (!indicesToRemove.isEmpty())
            StorageProvisionerImpl.get().dropTableIndices(domain, indicesToRemove);
        else
            LOG.info("No indices found on table '{}' that contain the lsid column.", table.getName());

        // Remanufacture a property descriptor that matches the original LSID property descriptor.
        var spec = new PropertyStorageSpec(lsidColumnName, JdbcType.VARCHAR, 300).setNullable(false);
        PropertyDescriptor pd = new PropertyDescriptor();
        pd.setContainer(st.getContainer());
        pd.setDatabaseDefaultValue(spec.getDefaultValue());
        pd.setName(spec.getName());
        pd.setJdbcType(spec.getJdbcType(), spec.getSize());
        pd.setNullable(spec.isNullable());
        pd.setMvEnabled(spec.isMvEnabled());
        pd.setPropertyURI(DomainUtil.createUniquePropertyURI(domain.getTypeURI(), null, new CaseInsensitiveHashSet()));
        pd.setDescription(spec.getDescription());
        pd.setImportAliases(spec.getImportAliases());
        pd.setScale(spec.getSize());
        DomainPropertyImpl dp = new DomainPropertyImpl((DomainImpl) domain, pd);

        LOG.debug("Dropping lsid column from table '{}' for sample type '{}' in folder {}.", table.getName(), st.getName(), st.getContainer().getPath());
        StorageProvisionerImpl.get().dropProperties(domain, Set.of(dp));

        return true;
    }

    private record ProvisionedSampleTypeContext(Domain domain, SchemaTableInfo provisionedTable) {}

    private static @Nullable ProvisionedSampleTypeContext getProvisionedSampleTypeContext(@NotNull ExpSampleTypeImpl st)
    {
        Domain domain = st.getDomain();
        SampleTypeDomainKind kind = null;
        try
        {
            kind = (SampleTypeDomainKind) domain.getDomainKind();
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }

        if (kind == null)
        {
            LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no domain kind.");
            return null;
        }
        else if (kind.getStorageSchemaName() == null)
        {
            // e.g., SpecimenSampleTypeDomainKind is not provisioned
            LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned storage schema.");
            return null;
        }

        DbSchema schema = kind.getSchema();
        StorageProvisioner.get().ensureStorageTable(domain, kind, schema.getScope());
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert (null != domain && null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned table.");
            return null;
        }

        return new ProvisionedSampleTypeContext(domain, provisionedTable);
    }
}
