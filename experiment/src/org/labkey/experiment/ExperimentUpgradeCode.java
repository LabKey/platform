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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.experiment.api.ClosureQueryHelper;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.qc.DataState;
import org.labkey.api.qc.SampleStatusService;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.experiment.api.SampleTypeServiceImpl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 12/27/16
 */
public class ExperimentUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogManager.getLogger(ExperimentUpgradeCode.class);

    private record ProvisionedSampleTypeContext(DbScope scope, Domain domain, SampleTypeDomainKind kind, SchemaTableInfo provisionedTable) {}

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
            // e.g. SpecimenSampleTypeDomainKind is not provisioned
            LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned storage schema.");
            return null;
        }

        DbSchema schema = kind.getSchema();
        DbScope scope = kind.getSchema().getScope();

        StorageProvisioner.get().ensureStorageTable(domain, kind, scope);
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert (null != domain && null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned table.");
            return null;
        }

        return new ProvisionedSampleTypeContext(scope, domain, kind, provisionedTable);
    }

    /**
     * Called from exp-23.000-23.001.sql
     */
    public static void cleanUpAliquotRecomputeFlag(ModuleContext context)
    {
        // do nothing.
        // exp.material.recomputerollup dropped in exp-23.004-23.005.sql
    }

    private static Map<String, List<String>> findContainersWithAliquot()
    {
        SQLFragment sql = new SQLFragment()
                .append("SELECT DISTINCT cpastype, container\n")
                .append("FROM ").append(ExperimentService.get().getTinfoMaterial(), "m").append("\n")
                .append("WHERE m.AliquotCount IS NOT NULL AND m.AliquotCount <> 0");

        @NotNull Map<String, Object>[] results = new SqlSelector(ExperimentService.get().getSchema(), sql).getMapArray();
        if (results.length > 0)
        {
            Map<String, List<String>> containerSampleTypes = new HashMap<>();
            for (Map<String, Object> result : results)
            {
                String container = (String) result.get("container");
                String cpastype = (String) result.get("cpastype");
                containerSampleTypes.putIfAbsent(container, new ArrayList<>());
                containerSampleTypes.get(container).add(cpastype);
            }
            return containerSampleTypes;
        }

        return Collections.emptyMap();
    }

    private static List<Integer> getRootLsidsWithAvailableAliquots(String sampleTypeLsid, Container container)
    {
        List<Integer> availableSampleStates = new ArrayList<>();

        if (SampleStatusService.get().supportsSampleStatus())
        {
            for (DataState state : SampleStatusService.get().getAllProjectStates(container))
            {
                if (ExpSchema.SampleStateType.Available.name().equals(state.getStateType()))
                    availableSampleStates.add(state.getRowId());
            }
        }

        TableInfo tableInfo = ExperimentService.get().getTinfoMaterial();

        SQLFragment inner = new SQLFragment("SELECT DISTINCT rootmateriallsid FROM ").append(tableInfo, "ali")
                .append(" WHERE ali.cpastype = ").appendValue(sampleTypeLsid)
                .append(" AND ali.rootmateriallsid <> ali.lsid ")
                .append(" AND ali.container = ").appendValue(container)
                .append(" AND ali.SampleState ").appendInClause(availableSampleStates, tableInfo.getSqlDialect());

        SQLFragment sql = new SQLFragment("SELECT rowid FROM exp.material root WHERE root.lsid IN (")
                .append(inner)
                .append(")");

        return new SqlSelector(tableInfo.getSchema(), sql).getArrayList(Integer.class);
    }

    private static List<Integer> getRootSampleIdsWithAliquotVolume(String sampleTypeLsid, Container container)
    {
        TableInfo tableInfo = ExperimentService.get().getTinfoMaterial();

        SQLFragment sql = new SQLFragment("SELECT root.rowId FROM ").append(tableInfo, "root")
                .append(" WHERE root.cpastype = ").appendValue(sampleTypeLsid)
                .append(" AND aliquotedfromlsid IS NULL ")
                .append(" AND aliquotVolume > 0 ")
                .append(" AND root.container = ").appendValue(container);

        return new SqlSelector(tableInfo.getSchema(), sql).getArrayList(Integer.class);
    }

    private static int recomputeSampleTypeAvailableAliquotRollup(ExpSampleType sampleType, Container container) throws SQLException
    {
        List<Integer> rootSamplesWithAvailableAliquot = getRootLsidsWithAvailableAliquots(sampleType.getLSID(), container);
        if (rootSamplesWithAvailableAliquot.isEmpty())
            return 0;

        List<Integer> rootSamplesWithAliquotVolume = getRootSampleIdsWithAliquotVolume(sampleType.getLSID(), container);

        Set<Integer> s1 = new HashSet<>(rootSamplesWithAvailableAliquot);
        Set<Integer> s2 = new HashSet<>(rootSamplesWithAliquotVolume);
        s1.retainAll(s2);
        List<Integer> rootSamplesWithAvailableAliquotVolume = new ArrayList<>(s1);

        // Exposed as "public" only for upgrade. When removing this code make this signature "private".
        // Issue 49150: Additionally, remove the "useRootMaterialLSID" flag and all of it's related logic as it
        // is only necessary for this upgrade path.
        return SampleTypeServiceImpl.get().recomputeSamplesRollup(
            Collections.emptyList(),
            rootSamplesWithAvailableAliquot,
            rootSamplesWithAvailableAliquotVolume,
            sampleType.getMetricUnit(),
            container,
            true
        );
    }

    /**
     * Called from exp-23.007-23.008.sql
     */
    public static void recomputeAliquotAvailableAmount(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // find sample types
            SampleTypeService sampleTypeService = SampleTypeService.get();
            Map<String, List<String>> containerSampleTypes = findContainersWithAliquot();
            for (String containerId : containerSampleTypes.keySet())
            {
                Container container = ContainerManager.getForId(containerId);
                if (container == null)
                    continue;

                List<String> sampleTypes = containerSampleTypes.get(containerId);
                LOG.info("** starting recalculating exp.material.aliquotAvailableCount/Volume in folder: " + container.getPath());

                for (String sampleTypeLsid : sampleTypes)
                {
                    ExpSampleType sampleType = sampleTypeService.getSampleType(sampleTypeLsid);
                    if (sampleType == null)
                        continue;

                    int syncedCount = recomputeSampleTypeAvailableAliquotRollup(sampleType, container);
                    if (syncedCount > 0)
                        LOG.info("*** recalculated aliquotAvailableCount/Volume for " + syncedCount + " " + sampleType.getName() + " sample(s) in folder: " + container.getPath());
                }

                LOG.info("** finished cleaning up exp.material.aliquotAvailableCount/Volume in folder: " + container.getPath());
            }

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void addRowIdColumn(ExpSampleTypeImpl st)
    {
        ProvisionedSampleTypeContext provisionedContext = getProvisionedSampleTypeContext(st);
        if (provisionedContext == null)
            return;

        int count = addRowIdColumn(provisionedContext.domain, provisionedContext.scope);
        LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") added 'rowId' column, count=" + count);
    }

    private static int addRowIdColumn(@NotNull Domain domain, @NotNull DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment table = new SQLFragment(SampleTypeDomainKind.PROVISIONED_SCHEMA_NAME).append(".").append(tableName);

        SQLFragment addColumn;
        SQLFragment update;
        SQLFragment notNull;
        if (scope.getSqlDialect().isSqlServer())
        {
            addColumn = new SQLFragment("ALTER TABLE ").append(table).append(" ADD rowid INTEGER NULL").appendEOS().append("\n");

            update = new SQLFragment("UPDATE Materialized SET Materialized.rowid = Material.rowid\n")
                    .append("FROM ").append(table).append(" Materialized\n")
                    .append("INNER JOIN exp.material Material ON Materialized.lsid = Material.lsid").appendEOS().append("\n");

            notNull = new SQLFragment("ALTER TABLE ").append(table).append(" ALTER COLUMN rowid INT NOT NULL").appendEOS().append("\n");
        }
        else
        {
            addColumn = new SQLFragment("ALTER TABLE ").append(table).append(" ADD COLUMN rowid INTEGER").appendEOS().append("\n");

            update = new SQLFragment("UPDATE ")
                    .append(table).append(" AS st\n")
                    .append("SET rowid = (SELECT rowid FROM exp.material expmat WHERE st.lsid = expmat.lsid)").appendEOS().append("\n");

            notNull = new SQLFragment("ALTER TABLE ").append(table).append(" ALTER COLUMN rowid SET NOT NULL").appendEOS().append("\n");
        }

        SQLFragment createIndex = new SQLFragment("CREATE INDEX ").append(tableName).append("_rowid ")
                .append("ON ").append(table).append(" (rowid)").appendEOS().append("\n");

        new SqlExecutor(scope).execute(addColumn);
        int count = new SqlExecutor(scope).execute(update);
        new SqlExecutor(scope).execute(notNull);
        new SqlExecutor(scope).execute(createIndex);

        return count;
    }

    /**
     * Called from exp-23.011-23.012.sql
     */
    public static void addRowIdToMaterializedSampleTypes(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // get all SampleTypes across all containers
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            new TableSelector(sampleTypeTable, null, null).stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .forEach(ExperimentUpgradeCode::addRowIdColumn);

            tx.commit();
        }
    }

    /**
     * Called from exp-24.001-24.002.sql
     */
    public static void populateMaterialAncestors(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbSchema schema = ExperimentService.get().getSchema();

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(
            container -> {
                LOG.info("Adding rows to exp.materialAncestors from samples in container " + container.getPath());
                SampleTypeService.get().getSampleTypes(container, null, false).forEach(
                    sampleType -> {
                        LOG.debug("   Adding rows from samples in sampleType " + sampleType.getName() + " in container " + container.getPath());
                        SQLFragment from = new SQLFragment(" FROM exp.material WHERE container = ?").add(container.getEntityId())
                                .append(" AND materialSourceId = ?").add(sampleType.getRowId());
                        SQLFragment sql = ClosureQueryHelper.selectAndInsertSql(schema.getSqlDialect(), from, null, "INSERT INTO exp.materialAncestors (RowId, AncestorRowId, AncestorTypeId) ");
                        new SqlExecutor(schema.getScope()).execute(sql);
                    }
                );
            }
        );
    }

    /**
     * Called from exp-24.001-24.002.sql
     */
    public static void populateDataAncestors(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbSchema schema = ExperimentService.get().getSchema();

        ContainerManager.getAllChildren(ContainerManager.getRoot()).forEach(
            container -> {
                LOG.info("Adding rows to exp.dataAncestors from data objects in container " + container.getPath());
                ExperimentService.get().getDataClasses(container, null, false).forEach(
                        dataClass -> {
                            LOG.debug("    Adding rows to exp.dataAncestors from data class " + dataClass.getName() + " in container " + container.getPath());
                            SQLFragment from = new SQLFragment(" FROM exp.data WHERE container = ?").add(container.getEntityId())
                                    .append(" AND classId = ?").add(dataClass.getRowId());
                            SQLFragment sql = ClosureQueryHelper.selectAndInsertSql(schema.getSqlDialect(), from, null,
                                    "INSERT INTO exp.dataAncestors (RowId, AncestorRowId, AncestorTypeId) ");
                            new SqlExecutor(schema.getScope()).execute(sql);
                        }
                );
            }
        );
    }
}
