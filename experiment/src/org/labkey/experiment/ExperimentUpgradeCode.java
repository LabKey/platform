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
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.ontology.Unit;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.experiment.api.ClosureQueryHelper;
import org.labkey.experiment.samples.SampleTimelineAuditProvider;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotUnit;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.AvailableAliquotVolume;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Created;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.CreatedBy;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Modified;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.ModifiedBy;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Name;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.RowId;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.Units;

public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogHelper.getLogger(ExperimentUpgradeCode.class, "Experiment upgrade status");

    // called from exp-24.003-24.004.sql
    @SuppressWarnings("unused")
    public static void addMissingSampleTypeIdsForSampleTimelineAudit(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = ExperimentService.get().getSchema().getScope();
        List<String> tableNames = new SqlSelector(scope, "SELECT StorageTableName FROM exp.domainDescriptor WHERE StorageSchemaName='audit' AND name='" + SampleTimelineAuditProvider.SampleTimelineAuditDomainKind.NAME + "'").getArrayList(String.class);
        if (tableNames.size() > 1)
            LOG.warn("Found {} tables for " + SampleTimelineAuditProvider.SampleTimelineAuditDomainKind.NAME, tableNames.size());

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            for (String table : tableNames)
            {
                SQLFragment countSql = new SQLFragment("SELECT COUNT(*) FROM audit.").append(table).append(" WHERE sampleTypeId = 0");
                SqlSelector countSelector = new SqlSelector(scope, countSql);

                long toUpdate = countSelector.getObject(Long.class);
                LOG.info("There are {} audit log entries to be updated in audit.{}.", toUpdate, table);
                // first update the type id by finding other audit entries that reference the same sample id.
                if (toUpdate > 0)
                {
                    LOG.info("Updating table audit.{} via self-join.", table);
                    SQLFragment updateSql = new SQLFragment("UPDATE audit.").append(table)
                            .append(" SET sampleTypeId = a3.sampleTypeId\n")
                            .append(" FROM\n")
                            .append("   (SELECT sampleId, rowId as rowIdToUpdate FROM audit.").append(table).append(" WHERE sampleTypeId = 0").append(") a2 ")
                            .append("   LEFT JOIN\n")
                            .append("   (SELECT MAX(sampleTypeId) as sampleTypeId, sampleId FROM audit.").append(table).append(" GROUP BY sampleId) a3")
                            .append("   ON a2.sampleId = a3.sampleId")
                            .append(" WHERE rowId = a2.rowIdToUpdate");
                    long start = System.currentTimeMillis();
                    SqlExecutor executor = new SqlExecutor(scope);
                    int numRows = executor.execute(updateSql);
                    long elapsed = System.currentTimeMillis() - start;
                    LOG.info("Updated {} rows via self-join for table {} in {} sec", numRows, table, elapsed / 1000);
                }

                toUpdate = countSelector.getObject(Long.class);
                if (toUpdate > 0)
                {
                    // It may have happened that there's only one audit entry for a sample and that entry has a 0 for the type id, in which case we may be able
                    // to find the type id from the exp.materials table. Since samples may have been deleted, it isn't sufficient to do only this update
                    LOG.info("Updating table audit.{} via exp.materials.", table);
                    SQLFragment updateSql = new SQLFragment("UPDATE audit.").append(table)
                            .append(" SET sampleTypeId = m.materialSourceId\n")
                            .append(" FROM\n")
                            .append("   (SELECT sampleId, rowId as rowIdToUpdate FROM audit.").append(table).append(" WHERE sampleTypeId = 0").append(") a2 ")
                            .append("   LEFT JOIN\n")
                            .append("   (SELECT materialSourceId, rowId AS sampleRowId FROM exp.material) m")
                            .append("   ON a2.sampleId = m.sampleRowId")
                            .append(" WHERE rowId = a2.rowIdToUpdate");
                    long start = System.currentTimeMillis();
                    SqlExecutor executor = new SqlExecutor(scope);
                    int numRows = executor.execute(updateSql);
                    long elapsed = System.currentTimeMillis() - start;
                    LOG.info("Updated {} rows from exp.material table join in {} sec", numRows, elapsed / 1000);
                }
                long remaining = countSelector.getObject(Long.class);
                LOG.info("There are {} rows in audit.{} that could not be updated with a proper sample type id.", remaining, table);
            }
            transaction.commit();
        }
    }

    // called from exp-24.005-24.006.sql
    @SuppressWarnings("unused")
    public static void repopulateAncestors(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        ClosureQueryHelper.truncateAndRecreate(LOG);
    }

    // called from exp-25.006-25.007.sql
    @SuppressWarnings("unused")
    public static void ensureBigObjectIds(ModuleContext context)
    {
        if (AppProps.getInstance().isDevMode())
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

    // called from exp-25.007-25.008.sql
    @SuppressWarnings("unused")
    public static void upgradeAmountsAndUnits(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        LOG.info("Starting upgrade of amounts and units");
        DbScope scope = ExperimentService.get().getSchema().getScope();
        LimitedUser admin = new LimitedUser(context.getUpgradeUser(), SiteAdminRole.class);
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // create a single transaction event at the root container for use in tying all updates together
            TransactionAuditProvider.TransactionAuditEvent transactionEvent = AbstractQueryUpdateService.createTransactionAuditEvent(ContainerManager.getRoot(), QueryService.AuditAction.UPDATE);
            transaction.setAuditEvent(transactionEvent);
            ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(c ->
                    convertAmountsToBaseUnits(c, admin)
            );
            transaction.commit();
            LOG.info("Finished upgrade of amounts and units");
        }

    }

    private static void getAmountAndUnitUpdates(Map<String, Object> sampleMap, Parameter unitsCol, Set<Parameter> amountCols, Unit currentDisplayUnit, Map<String, Object> oldDataMap, Map<String, Object> newDataMap, Map<String, Integer> sampleCounts, boolean isAliquot)
    {
        Unit baseUnit = currentDisplayUnit.getBase();
        String unitsStr = (String) sampleMap.get(unitsCol.getName());
        Unit materialUnit = Unit.fromName(unitsStr);
        boolean isInBaseUnits = materialUnit == null ? currentDisplayUnit.isBase() : materialUnit.isBase();
        // have a unit value, but it did not convert to a known unit
        if (materialUnit == null && !StringUtils.isEmpty(unitsStr))
        {
            // invalid unit stored with sample. Leave as is.
            LOG.info("Found invalid {} '{}' for sample '{}'. No conversion done.", isAliquot ? "aliquot unit" : "unit", unitsStr, sampleMap.get(Name.name()));
            sampleCounts.put("invalidUnits", sampleCounts.getOrDefault("invalidUnits", 0) + 1);
        }
        else if (materialUnit != null && !materialUnit.isCompatible(baseUnit))
        {
            LOG.info("{} '{}' for sample '{}' is not compatible with the base unit '{}'. No conversion done.", isAliquot ? "Aliquot unit" : "Unit", materialUnit.name(), sampleMap.get(Name.name()), baseUnit);
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
    private static void convertAmountsToBaseUnits(Container container, User user)
    {
        DbScope scope = ExperimentService.get().getSchema().getScope();
        TableInfo tInfo = ExperimentService.get().getTinfoMaterial();
        try (Connection c = scope.getConnection())
        {
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

            for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(container, user, false))
            {
                LOG.info("** Starting upgrade for sample type {} in folder {}", sampleType.getName(), container.getPath());
                Map<String, Integer> sampleCounts = new HashMap<>();
                Map<String, Integer> aliquotCounts = new HashMap<>();

                Unit currentDisplayUnit = Unit.fromName(sampleType.getMetricUnit());
                boolean hasDisplayUnit = currentDisplayUnit != null;

                AtomicInteger batchCount = new AtomicInteger();
                List<AuditTypeEvent> auditEvents = new ArrayList<>();
                SQLFragment sql = new SQLFragment("SELECT m.RowId, m.Name, m.StoredAmount, m.Units, m.AliquotVolume, m.AliquotUnit, m.AvailableAliquotVolume FROM ")
                        .append(tInfo, "m")
                        .append(" WHERE cpastype = ?").add(sampleType.getLSID());
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
                    else // no display unit
                    {
                        // Have an amount and no unit, update to a Unit.unit type
                        if (sampleMap.get(StoredAmount.name()) != null && StringUtils.isEmpty((String) sampleMap.get(Units.name())))
                        {
                            newDataMap.put(Units.name(), Unit.unit.name());
                            units.setValue(Unit.unit.name());
                            sampleCounts.put("amountWithoutMaterialOrDisplayUnits", sampleCounts.getOrDefault("amountWithoutMaterialOrDisplayUnits", 0) + 1);
                        }
                        if (StringUtils.isEmpty((String) sampleMap.get(AliquotUnit.name())))
                        {
                            if (sampleMap.get(AliquotVolume.name()) != null || sampleMap.get(AvailableAliquotVolume.name()) != null)
                            {
                                newDataMap.put(AliquotUnit.name(), Unit.unit.name());
                                aliquotUnits.setValue(Unit.unit.name());
                                aliquotCounts.put("amountWithoutMaterialOrDisplayUnits", aliquotCounts.getOrDefault("amountWithoutMaterialOrDisplayUnits", 0) + 1);
                            }
                        }
                        // for rows with an amount and a unit when there is no display unit, no conversion is done
                    }


                    if (!newDataMap.isEmpty())
                    {
                        updateStmt.addBatch();
                        batchCount.getAndIncrement();
                        SampleTimelineAuditEvent event = new SampleTimelineAuditEvent(container, "Storage amount unit conversion to base unit during upgrade script.");
                        event.setSampleId((Integer) sampleMap.get(RowId.name()));
                        event.setSampleName((String) sampleMap.get(Name.name()));
                        event.setSampleType(sampleType.getName());
                        event.setSampleTypeId(sampleType.getRowId());
                        event.setLineageUpdate(false);
                        event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(oldDataMap));
                        event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(newDataMap));
                        auditEvents.add(event);
                    }
                    if (batchCount.get() > 1000)
                    {
                        updateStmt.executeBatch();
                        AuditLogService.get().addEvents(user, auditEvents);
                        auditEvents.clear();
                        batchCount.set(0);
                    }
                });
                if (batchCount.get() > 0)
                {
                    updateStmt.executeBatch();
                    AuditLogService.get().addEvents(user, auditEvents);
                }

                LOG.info("    Sample data update counts {}", sampleCounts);
                LOG.info("    Aliquot data update counts {}", aliquotCounts);
                LOG.info("** Finished upgrade for sample type {} in folder {}", sampleType.getName(), container.getPath());
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

    }
}
