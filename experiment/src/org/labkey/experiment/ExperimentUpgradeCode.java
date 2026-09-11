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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
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
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.data.dialect.PostgreSqlService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.files.FileContentService;
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
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.DataClass;
import org.labkey.experiment.api.DataClassDomainKind;
import org.labkey.experiment.api.ExpDataClassImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.experiment.api.property.DomainImpl;
import org.labkey.experiment.api.property.DomainPropertyImpl;
import org.labkey.experiment.api.property.StorageNameGenerator;
import org.labkey.experiment.api.property.StorageProvisionerImpl;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        try (Transaction transaction = scope.ensureTransaction())
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

        try (Transaction tx = ExperimentService.get().ensureTransaction())
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

    /**
     * Called from exp-26.001-26.002.sql
     */
    @SuppressWarnings("unused")
    public static void fixContainerForMovedSampleFiles(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (Transaction tx = ExperimentService.get().ensureTransaction())
        {
            FileContentService service = FileContentService.get();
            if (service == null)
            {
                LOG.error("No FileContentService found. Aborting.");
                return;
            }
            LimitedUser admin = new LimitedUser(context.getUpgradeUser(), SiteAdminRole.class);
            int numDuplicates = service.fixContainerForExpDataFiles(admin);
            LOG.info("Fixed {} duplicate data files.", numDuplicates);
        }
    }

    /**
     * Called from exp-26.002-26.003.sql
     * Drop the classid column and add a rowId column to existing provisioned DataClass tables.
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public static void addRowIdToProvisionedDataClassTables(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (Transaction tx = ExperimentService.get().ensureTransaction())
        {
            TableInfo source = ExperimentServiceImpl.get().getTinfoDataClass();
            new TableSelector(source, null, null).stream(DataClass.class)
                    .map(ExpDataClassImpl::new)
                    .forEach(ExperimentUpgradeCode::upgradeProvisionedDataClassTable);

            tx.commit();
        }
    }

    private static void upgradeProvisionedDataClassTable(ExpDataClassImpl dc)
    {
        Domain domain = dc.getDomain();
        DataClassDomainKind kind = null;
        try
        {
            kind = (DataClassDomainKind) domain.getDomainKind();
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
        if (null == kind || null == kind.getStorageSchemaName())
        {
            LOG.error("DataClass '" + dc.getName() + "' (" + dc.getRowId() + ") has no provisioned storage schema.");
            return;
        }

        DbSchema schema = DataClassDomainKind.getSchema();
        DbScope scope = schema.getScope();

        StorageProvisioner storageProvisioner = StorageProvisioner.get();

        storageProvisioner.ensureStorageTable(domain, kind, scope);
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert (null != domain && null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("DataClass '" + dc.getName() + "' (" + dc.getRowId() + ") has no provisioned table.");
            return;
        }

        // Drop classid column if present
        String classIdColumnName = "classid";
        ColumnInfo classIdColumn = provisionedTable.getColumn(FieldKey.fromParts(classIdColumnName));
        if (classIdColumn != null)
        {
            Set<String> indicesToRemove = new HashSet<>();
            for (var index : provisionedTable.getAllIndices())
            {
                if (index.columns().contains(classIdColumn))
                    indicesToRemove.add(index.name());
            }

            if (!indicesToRemove.isEmpty())
                StorageProvisionerImpl.get().dropTableIndices(domain, indicesToRemove);

            // Remanufacture a property descriptor that matches the original classid property descriptor.
            var spec = new PropertyStorageSpec(classIdColumnName, JdbcType.INTEGER);
            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setContainer(dc.getContainer());
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

            LOG.debug("Dropping classid column from table '{}' for data class '{}' in folder {}.", provisionedTable.getName(), dc.getName(), dc.getContainer().getPath());
            StorageProvisionerImpl.get().dropProperties(domain, Set.of(dp));
        }

        // Add rowId column if not present
        ColumnInfo rowIdCol = provisionedTable.getColumn("rowId");
        if (rowIdCol != null)
        {
            LOG.info("DataClass '{}' ({}) already has 'rowId' column. Skipping.", dc.getName(), dc.getRowId());
            return;
        }

        PropertyStorageSpec rowIdProp = new PropertyStorageSpec("rowId", JdbcType.INTEGER);  // nullable for initial add
        storageProvisioner.addStorageProperties(domain, Collections.singletonList(rowIdProp), true);
        LOG.info("DataClass '{}' ({}) added 'rowId' column", dc.getName(), dc.getRowId());

        // Populate rowId from exp.data using lsid join
        fillRowId(dc, domain, scope);

        // Set NOT NULL constraint
        SqlExecutor executor = new SqlExecutor(scope);
        boolean isPostgreSQL = scope.getSqlDialect().isPostgreSQL();
        if (isPostgreSQL)
            executor.execute(new SQLFragment("ALTER TABLE expdataclass.").append(domain.getStorageTableName()).append(" ALTER COLUMN rowId SET NOT NULL"));
        else
            executor.execute(new SQLFragment("ALTER TABLE expdataclass.").append(domain.getStorageTableName()).append(" ALTER COLUMN rowId INT NOT NULL"));

        // Add indexes back via StorageProvisioner
        storageProvisioner.ensureTableIndices(domain);
        LOG.info("DataClass '{}' ({}) added unique index on 'rowId'", dc.getName(), dc.getRowId());

        // Add FK constraint (no StorageProvisioner API for FKs on existing tables)
        String fkName = "fk_rowid_" + domain.getStorageTableName() + "_data";
        executor.execute(new SQLFragment("ALTER TABLE expdataclass.").append(domain.getStorageTableName())
                .append(" ADD CONSTRAINT ").append(fkName)
                .append(" FOREIGN KEY (rowId) REFERENCES exp.Data(RowId)"));
        LOG.info("DataClass '{}' ({}) added FK constraint on 'rowId'", dc.getName(), dc.getRowId());
    }

    private static void fillRowId(ExpDataClassImpl dc, Domain domain, DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment update = new SQLFragment()
                .append("UPDATE expdataclass.").append(tableName).append("\n")
                .append("SET rowId = i.rowid\n")
                .append("FROM (\n")
                .append("  SELECT d.lsid, d.RowId\n")
                .append("  FROM exp.data d\n")
                .append("  WHERE d.cpasType = ?\n").add(domain.getTypeURI())
                .append(") AS i\n")
                .append("WHERE i.lsid = ").append(tableName).append(".lsid");

        int count = new SqlExecutor(scope).execute(update);
        LOG.info("DataClass '{}' ({}) populated 'rowId' column, count={}", dc.getName(), dc.getRowId(), count);
    }

    record DomainRecord(Container container, int domainId, String name, String storageSchemaName, String storageTableName)
    {
        String fullName()
        {
            return storageSchemaName + "." + storageTableName;
        }
    }

    record Property(int domainId, int propertyId, String domainName, String name, String storageSchemaName, String storageTableName, String storageColumnName)
    {
        String fullName()
        {
            // Have to bracket storage column name since it could have special characters (like dots)
            return storageSchemaName + "." + storageTableName + "." + bracketIt(storageColumnName);
        }

        // Bracket name and escape any internal ending brackets
        private String bracketIt(String name)
        {
            return "[" + name.replace("]", "]]") + "]";
        }
    }

    /**
     * Called from exp-26.004-26.005.sql, on SQL Server only
     * GitHub Issue 869: Long table/column names cause SQL Server migration to fail
     * Query all table & column storage names and rename the ones that are too long for PostgreSQL
     * TODO: When this upgrade code is removed, get rid of the StorageProvisionerImpl.makeTableName() method it uses.
     */
    @SuppressWarnings("unused")
    public static void shortenAllStorageNames(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        // The PostgreSQL dialect knows which names are too long
        BasePostgreSqlDialect dialect = PostgreSqlService.get().getDialect();
        DbScope scope = DbScope.getLabKeyScope();
        SqlExecutor executor = new SqlExecutor(scope);

        // Stream all the storage table names and rename the ones that are too long for PostgreSQL. The filtering must
        // be done in code by the dialect; SQL Server has BYTELENGTH(), but that function returns values that are not
        // consistent with our dialect check. Also, it looks like the function's behavior changed starting in SS 2019.
        TableInfo tinfoDomainDescriptor = OntologyManager.getTinfoDomainDescriptor();
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("StorageSchemaName"), null, CompareType.NONBLANK);
        filter.addCondition(FieldKey.fromString("StorageTableName"), null, CompareType.NONBLANK);

        new TableSelector(tinfoDomainDescriptor, new CsvSet("Container, DomainId, Name, StorageSchemaName, StorageTableName"), filter, null)
            .setJdbcCaching(false)
            .stream(DomainRecord.class)
            .filter(domain -> dialect.isIdentifierTooLong(domain.storageTableName()))
            .forEach(domain -> {
                String oldName = domain.fullName();
                String newName = StorageProvisionerImpl.get().makeTableName(dialect, domain.container(), domain.domainId(), domain.name());

                try (Transaction transaction = scope.beginTransaction())
                {
                    executor.execute(new SQLFragment("EXEC sp_rename ?, ?").add(oldName).add(newName));
                    Table.update(null, tinfoDomainDescriptor, PageFlowUtil.map("StorageTableName", newName), domain.domainId());
                    transaction.commit();
                }

                LOG.info("   Table \"{}\" renamed to \"{}\" ({} bytes)", oldName, newName, newName.getBytes(StandardCharsets.UTF_8).length);
            });

        List<String> badTableNames = new TableSelector(tinfoDomainDescriptor, new CsvSet("StorageTableName"), filter, null)
            .setJdbcCaching(false)
            .stream(String.class)
            .filter(dialect::isIdentifierTooLong)
            .toList();

        if (!badTableNames.isEmpty())
            LOG.error("Some storage table names are still too long!! {}", badTableNames);

        // Collect all the domains that have one or more storage columns names that are too long for PostgreSQL
        TableInfo tinfoPropertyDomain = OntologyManager.getTinfoPropertyDomain();
        TableInfo tinfoPropertyDescriptor = OntologyManager.getTinfoPropertyDescriptor();
        SQLFragment sql = new SQLFragment("SELECT dd.DomainId, dd.Name AS DomainName, px.PropertyId, StorageSchemaName, StorageTableName, StorageColumnName, px.Name FROM ")
            .append(tinfoDomainDescriptor, "dd")
            .append(" INNER JOIN ")
            .append(tinfoPropertyDomain, "pd")
            .append(" ON dd.DomainId = pd.DomainId INNER JOIN ")
            .append(tinfoPropertyDescriptor, "px")
            .append(" ON pd.PropertyId = px.PropertyId ")
            .append("WHERE StorageSchemaName IS NOT NULL AND StorageTableName IS NOT NULL AND StorageColumnName IS NOT NULL");

        MultiValuedMap<DomainRecord, Property> badDomainMap = new SqlSelector(scope, sql)
            .setJdbcCaching(false)
            .stream(Property.class)
            .filter(property -> dialect.isIdentifierTooLong(property.storageColumnName()))
            .collect(LabKeyCollectors.toMultiValuedMap(
                property -> new DomainRecord(null, property.domainId(), property.domainName(), property.storageSchemaName(), property.storageTableName()),
                property -> property,
                ArrayListValuedHashMap::new)
            );

        if (!badDomainMap.isEmpty())
            LOG.info("   Found {} with storage column names that are too long for PostgreSQL:", StringUtilsLabKey.pluralize(badDomainMap.keySet().size(), "domain"));

        // Now enumerate the bad domains and rename their bad storage columns using the PostgreSQL truncation rules
        badDomainMap.keySet()
            .forEach(domain -> {
                Collection<Property> badColumns = badDomainMap.get(domain);
                List<String> badColumnNames = badColumns.stream().map(Property::storageColumnName).toList();

                // First, populate a new StorageNameGenerator with all the "good" names in this domain so we don't
                // accidentally try to re-use one of them
                StorageNameGenerator nameGenerator = new StorageNameGenerator(dialect);
                SQLFragment domainSql = new SQLFragment("SELECT StorageColumnName FROM ")
                    .append(tinfoPropertyDomain, "pd")
                    .append(" INNER JOIN ")
                    .append(tinfoPropertyDescriptor, "px")
                    .append(" ON pd.PropertyId = px.PropertyId ")
                    .append("WHERE DomainId = ? AND StorageColumnName NOT ")
                    .add(domain.domainId())
                    .appendInClause(badColumnNames, scope.getSqlDialect());
                new SqlSelector(scope, domainSql).forEach(String.class, nameGenerator::claimName);

                LOG.info("   Renaming {} in table \"{}\"", StringUtilsLabKey.pluralize(badColumns.size(), "column"), domain.fullName());

                // Now use that StorageNameGenerator to create new names. Rename the column and update the PropertyDescriptor table.
                badColumns.forEach(property -> {
                    String oldName = property.fullName();
                    String newName = nameGenerator.generateColumnName(property.name()); // No need to bracket or quote or escape: JDBC parameter takes care of all special characters

                    try (Transaction transaction = scope.beginTransaction())
                    {
                        executor.execute(new SQLFragment("EXEC sp_rename ?, ?, 'COLUMN'").add(oldName).add(newName));
                        Table.update(null, tinfoPropertyDescriptor, PageFlowUtil.map("StorageColumnName", newName), property.propertyId());
                        transaction.commit();
                    }

                    LOG.info("      Column \"{}\" renamed to \"{}\" ({} bytes)", oldName, newName, newName.getBytes(StandardCharsets.UTF_8).length);
                });
            });

        List<String> badColumnNames = new TableSelector(tinfoPropertyDescriptor, new CsvSet("StorageColumnName"), new SimpleFilter(FieldKey.fromString("StorageColumnName"), null, CompareType.NONBLANK), null)
            .setJdbcCaching(false)
            .stream(String.class)
            .filter(dialect::isIdentifierTooLong)
            .toList();

        if (!badColumnNames.isEmpty())
            LOG.error("Some storage column names are still too long!! {}", badColumnNames);
    }

    // Legacy prefixes, frozen at their historical values so later renames of the service constants can't change what this upgrade matches.
    private static final String LEGACY_CONTAINER_DEFAULTS_PREFIX = "DomainDefaultValue";
    private static final String LEGACY_USER_DEFAULTS_PARENT_PREFIX = "UserDefaultValueParent";

    /**
     * Called from exp-26.005-26.006.sql
     * GitHub Issue #1569: re-key folder-level default values by domain kind. The old key was the container plus the
     * domain typeURI objectId, which repeats across kinds and is shared outright by an assay design's domains, so
     * unrelated domains silently overwrote each other's defaults. Rows written before 25.7 are keyed by domain name
     * instead; both forms are handled here. The owning domain is resolved from the properties the object actually
     * holds, which is what disambiguates rows that several domains once shared.
     */
    @SuppressWarnings("unused")
    @DeferredUpgrade
    public static void migrateDefaultValueLsids(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (Transaction tx = ExperimentService.get().ensureTransaction())
        {
            migrateContainerDefaults();
            reparentUserDefaults();
            tx.commit();
        }
    }

    private static void migrateContainerDefaults()
    {
        SQLFragment sql = new SQLFragment()
            .append("SELECT o.ObjectId, o.ObjectURI, MIN(pdm.DomainId) AS DomainId, COUNT(DISTINCT pdm.DomainId) AS DomainCount\n")
            .append("FROM ").append(OntologyManager.getTinfoObject(), "o").append("\n")
            .append("INNER JOIN ").append(OntologyManager.getTinfoObjectProperty(), "op").append(" ON op.ObjectId = o.ObjectId\n")
            .append("INNER JOIN ").append(OntologyManager.getTinfoPropertyDomain(), "pdm").append(" ON pdm.PropertyId = op.PropertyId\n")
            // the legacy prefix is followed by '.'; the qualified form written below inserts '-<kind>' before it
            .append("WHERE o.ObjectURI LIKE ?\n").add("%:" + LEGACY_CONTAINER_DEFAULTS_PREFIX + ".Folder-%")
            .append("GROUP BY o.ObjectId, o.ObjectURI");

        List<DefaultValueRow> rows = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(DefaultValueRow.class);
        int migrated = 0;

        // Both legacy forms can map onto the same target. The objectId form was written by 25.7 or later, so it is the
        // newer of the two and is moved first; the name form then loses the NOT EXISTS race below, as it should.
        for (boolean objectIdForm : new boolean[] {true, false})
        {
            for (DefaultValueRow row : rows)
            {
                Domain domain = resolveDomain(row);
                if (domain == null)
                    continue;

                Lsid domainLsid = new Lsid(domain.getTypeURI());
                if (objectIdForm != new Lsid(row.getObjectURI()).getObjectId().equals(domainLsid.getObjectId()))
                    continue;

                String newUri = qualifiedLsid(row.getObjectURI(), domainLsid);
                if (newUri == null || newUri.equals(row.getObjectURI()))
                    continue;

                if (renameObject(row.getObjectId(), newUri))
                    migrated++;
                else
                    LOG.warn("Leaving default values at {}: {} already exists.", row.getObjectURI(), newUri);
            }
        }

        LOG.info("Folder-level default values re-keyed by domain kind: {} of {} moved.", migrated, rows.size());
    }

    /**
     * The per-user parent object groups a domain's scopes so they can be deleted together. A colliding parent collected
     * children from several domains, so only one of them can keep it -- the rest get a parent of their own.
     */
    private static void reparentUserDefaults()
    {
        SQLFragment sql = new SQLFragment()
            .append("SELECT child.ObjectId, child.ObjectURI, parent.ObjectId AS ParentId, parent.ObjectURI AS ParentURI, parent.Container,\n")
            .append("  MIN(pdm.DomainId) AS DomainId, COUNT(DISTINCT pdm.DomainId) AS DomainCount\n")
            .append("FROM ").append(OntologyManager.getTinfoObject(), "parent").append("\n")
            .append("INNER JOIN ").append(OntologyManager.getTinfoObject(), "child").append(" ON child.OwnerObjectId = parent.ObjectId\n")
            .append("INNER JOIN ").append(OntologyManager.getTinfoObjectProperty(), "op").append(" ON op.ObjectId = child.ObjectId\n")
            .append("INNER JOIN ").append(OntologyManager.getTinfoPropertyDomain(), "pdm").append(" ON pdm.PropertyId = op.PropertyId\n")
            .append("WHERE parent.ObjectURI LIKE ?\n").add("%:" + LEGACY_USER_DEFAULTS_PARENT_PREFIX + ".Folder-%")
            .append("GROUP BY child.ObjectId, child.ObjectURI, parent.ObjectId, parent.ObjectURI, parent.Container");

        List<DefaultValueRow> rows = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(DefaultValueRow.class);

        // children of each legacy parent, bucketed by the qualified parent they now belong under
        Map<Long, Map<String, List<DefaultValueRow>>> byLegacyParent = new LinkedHashMap<>();
        for (DefaultValueRow row : rows)
        {
            Domain domain = resolveDomain(row);
            if (domain == null)
                continue;

            String newParentUri = qualifiedLsid(row.getParentURI(), new Lsid(domain.getTypeURI()));
            if (newParentUri == null || newParentUri.equals(row.getParentURI()))
                continue;

            byLegacyParent.computeIfAbsent(row.getParentId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(newParentUri, k -> new ArrayList<>()).add(row);
        }

        int reparented = 0;

        for (Map.Entry<Long, Map<String, List<DefaultValueRow>>> legacyParent : byLegacyParent.entrySet())
        {
            boolean legacyParentReused = false;

            for (Map.Entry<String, List<DefaultValueRow>> group : legacyParent.getValue().entrySet())
            {
                // Renaming the legacy parent for the first group leaves nothing behind; its children still point at it
                if (!legacyParentReused && renameObject(legacyParent.getKey(), group.getKey()))
                {
                    legacyParentReused = true;
                    reparented += group.getValue().size();
                    continue;
                }

                Container container = ContainerManager.getForId(group.getValue().get(0).getContainer());
                if (container == null)
                    continue;

                long newParentId = OntologyManager.ensureObject(container, group.getKey());
                for (DefaultValueRow row : group.getValue())
                {
                    SQLFragment update = new SQLFragment("UPDATE ").append(OntologyManager.getTinfoObject())
                        .append(" SET OwnerObjectId = ? WHERE ObjectId = ?").addAll(newParentId, row.getObjectId());
                    new SqlExecutor(ExperimentService.get().getSchema()).execute(update);
                    reparented++;
                }
            }
        }

        LOG.info("Per-user default values re-parented by domain kind: {} of {} moved.", reparented, rows.size());
    }

    /** The owning domain, or null when the object's properties don't identify exactly one resolvable user-created domain. */
    private static Domain resolveDomain(DefaultValueRow row)
    {
        if (row.getDomainCount() != 1)
        {
            LOG.warn("Leaving default values at {}: properties span {} domains, so the owner is ambiguous.", row.getObjectURI(), row.getDomainCount());
            return null;
        }

        Domain domain = PropertyService.get().getDomain(row.getDomainId());
        if (domain == null)
        {
            LOG.warn("Leaving default values at {}: domain {} no longer exists.", row.getObjectURI(), row.getDomainId());
            return null;
        }

        // Domains whose kind can't be resolved have always used the name-based key, so they are already correct
        DomainKind<?> kind = domain.getDomainKind();
        return kind != null && kind.isUserCreatedType() ? domain : null;
    }

    /** Rebuild a default-value LSID with the domain kind folded into the namespace prefix, preserving Folder-/User- scope. */
    private static String qualifiedLsid(String objectURI, Lsid domainLsid)
    {
        Lsid existing = new Lsid(objectURI);
        if (existing.getNamespaceSuffix() == null)
            return null;
        return new Lsid(existing.getNamespacePrefix() + "-" + domainLsid.getNamespacePrefix(), existing.getNamespaceSuffix(), domainLsid.getObjectId()).toString();
    }

    /** A row already at the target was written under the new scheme and is newer, so it wins. */
    private static boolean renameObject(long objectId, String newUri)
    {
        SQLFragment update = new SQLFragment("UPDATE ").append(OntologyManager.getTinfoObject())
            .append(" SET ObjectURI = ? WHERE ObjectId = ? AND NOT EXISTS (SELECT 1 FROM ")
            .append(OntologyManager.getTinfoObject(), "existing").append(" WHERE existing.ObjectURI = ?)")
            .addAll(newUri, objectId, newUri);
        return new SqlExecutor(ExperimentService.get().getSchema()).execute(update) > 0;
    }

    public static class DefaultValueRow
    {
        private long _objectId;
        private String _objectURI;
        private long _parentId;
        private String _parentURI;
        private String _container;
        private int _domainId;
        private int _domainCount;

        public long getObjectId() { return _objectId; }
        public void setObjectId(long objectId) { _objectId = objectId; }
        public String getObjectURI() { return _objectURI; }
        public void setObjectURI(String objectURI) { _objectURI = objectURI; }
        public long getParentId() { return _parentId; }
        public void setParentId(long parentId) { _parentId = parentId; }
        public String getParentURI() { return _parentURI; }
        public void setParentURI(String parentURI) { _parentURI = parentURI; }
        public String getContainer() { return _container; }
        public void setContainer(String container) { _container = container; }
        public int getDomainId() { return _domainId; }
        public void setDomainId(int domainId) { _domainId = domainId; }
        public int getDomainCount() { return _domainCount; }
        public void setDomainCount(int domainCount) { _domainCount = domainCount; }
    }
}
