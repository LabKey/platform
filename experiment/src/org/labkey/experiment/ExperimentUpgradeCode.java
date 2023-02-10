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

import com.google.common.collect.Sets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;
import org.labkey.experiment.api.ExpSampleTypeImpl;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.MaterialSource;
import org.labkey.api.exp.api.SampleTypeDomainKind;
import org.labkey.experiment.api.SampleTypeServiceImpl;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    /**
     * Called from exp-22.003-20.004.sql
     */
    public static void addProvisionedSampleName(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // get all SampleTypes across all containers
            TableInfo sampleTypeTable = ExperimentServiceImpl.get().getTinfoSampleType();
            new TableSelector(sampleTypeTable, null, null).stream(MaterialSource.class)
                    .map(ExpSampleTypeImpl::new)
                    .forEach(ExperimentUpgradeCode::setSampleName);

            tx.commit();
        }
    }

    private static void setSampleName(ExpSampleTypeImpl st)
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
        if (null == kind || null == kind.getStorageSchemaName())
            return;

        DbSchema schema = kind.getSchema();
        DbScope scope = kind.getSchema().getScope();

        StorageProvisioner.get().ensureStorageTable(domain, kind, scope);
        domain = PropertyService.get().getDomain(domain.getTypeId());
        assert (null != domain && null != domain.getStorageTableName());

        SchemaTableInfo provisionedTable = schema.getTable(domain.getStorageTableName());
        if (provisionedTable == null)
        {
            LOG.error("Sample type '" + st.getName() + "' (" + st.getRowId() + ") has no provisioned table.");
            return;
        }

        ColumnInfo nameCol = provisionedTable.getColumn("name");
        if (nameCol == null)
        {
            PropertyStorageSpec nameProp = kind.getBaseProperties(domain).stream().filter(p -> "name".equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
            StorageProvisioner.get().addStorageProperties(domain, Arrays.asList(nameProp), true);
            LOG.info("Added 'name' column to sample type '" + st.getName() + "' (" + st.getRowId() + ") provisioned table.");
        }

        uniquifySampleNames(st, scope);

        fillSampleName(st, domain, scope);

        //addIndex
        Set<PropertyStorageSpec.Index> newIndices =  Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(new PropertyStorageSpec.Index(true, "name"))));
        StorageProvisioner.get().addOrDropTableIndices(domain, newIndices, true, TableChange.IndexSizeMode.Normal);
        LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") added unique constraint on 'name'");
    }

    /**
     * Samples created using xar could have manually constructed LSID, which might allow duplicate sample names
     */
    private static void uniquifySampleNames(ExpSampleTypeImpl st, DbScope scope)
    {
        String targetCpas = st.getLSID();
        String lsidPrefix = st.getMaterialLSIDPrefix();

        // check if duplicate exp.material.name exist for CpasType
        // note that db unique key is of (name, cpastype, container). "container" is in the mix due to general cpastype="Material" for specimen
        // for sample type material, name + cpastype SHOULD be unique. But exception might exist due to manually constructed LSIDs
        SQLFragment sql = new SQLFragment()
                .append("SELECT LSID, name\n")
                .append("FROM ").append(ExperimentService.get().getTinfoMaterial(), "m").append("\n")
                .append("WHERE m.name IN (").append("\n")
                .append("  SELECT name FROM ").append(ExperimentService.get().getTinfoMaterial(), "mi").append("\n")
                .append("  WHERE mi.CpasType = ? ")
                .add(targetCpas)
                .append("  GROUP BY (name) HAVING COUNT(*) > 1").append("\n")
                .append(") \n")
                .append("AND m.CpasType = ?")
                .add(targetCpas);

        TableInfo table = ExperimentService.get().getTinfoMaterial();
        @NotNull Map<String, Object>[] results = new SqlSelector(ExperimentService.get().getSchema(), sql).getMapArray();
        if (results.length > 0)
        {
            LOG.warn(results.length + " duplicate name(s) found for sample type " + st.getName());

            Set<String> existingValues = new CaseInsensitiveHashSet();
            existingValues.addAll(new TableSelector(table, Collections.singleton(table.getColumn("name")), new SimpleFilter(FieldKey.fromParts("CpasType"), targetCpas), null).getCollection(String.class));

            Set<String> newValues = new CaseInsensitiveHashSet();

            Map<String, List<String>> sampleLsids = new HashMap<>();
            for (Map<String, Object> result : results)
            {
                String name = (String) result.get("name");
                String lsid = (String) result.get("lsid");
                sampleLsids.putIfAbsent(name, new ArrayList<>());
                sampleLsids.get(name).add(lsid);
            }

            for (String sampleName : sampleLsids.keySet())
            {
                List<String> lsids = sampleLsids.get(sampleName);
                LOG.warn(lsids.size() + " samples with the name '" + sampleName + "' are found for sample type " + st.getName() + ". ");

                // prefer to keep the sample whose LSID conforms to the sample type's defined prefix
                String sampleLSIDToKeep = lsids.get(0);
                for (String lsid : lsids)
                {
                    if (lsid.startsWith(lsidPrefix))
                    {
                        sampleLSIDToKeep = lsid;
                        break;
                    }
                }

                // rename all but one samples by adding suffix _2, _3, etc
                for (String lsid : lsids)
                {
                    if (sampleLSIDToKeep.equals(lsid))
                        continue;

                    int i = 1;
                    String candidateValue;

                    do
                    {
                        i++;
                        candidateValue = sampleName + "_" + i;
                    }
                    while(newValues.contains(candidateValue) || existingValues.contains(candidateValue));

                    SQLFragment update = new SQLFragment()
                            .append("UPDATE exp.material\n")
                            .append("SET name = ?\n")
                            .add(candidateValue)
                            .append(" WHERE lsid = ?")
                            .add(lsid);

                    new SqlExecutor(scope).execute(update);
                    LOG.warn("Renamed '" + sampleName + "' of lsid '" + lsid + "' to '" + candidateValue + "'.");

                    newValues.add(candidateValue);
                }
            }
        }
    }

    // populate name on provisioned sample tables
    private static void fillSampleName(ExpSampleTypeImpl st, Domain domain, DbScope scope)
    {
        String tableName = domain.getStorageTableName();
        SQLFragment update = new SQLFragment()
                .append("UPDATE expsampleset.").append(tableName).append("\n")
                .append("SET name = i.name\n")
                .append("FROM (\n")
                .append("  SELECT m.lsid, m.name\n")
                .append("  FROM exp.material m\n")
                .append("  WHERE m.cpasType = ?\n").add(domain.getTypeURI())
                .append(") AS i\n")
                .append("WHERE i.lsid = ").append(tableName).append(".lsid");

        int count = new SqlExecutor(scope).execute(update);
        LOG.info("Sample type '" + st.getName() + "' (" + st.getRowId() + ") updated 'name' column, count=" + count);
    }

    private static Map<String, List<String>> findContainersWithAliquotRecomputes()
    {
        SQLFragment sql = new SQLFragment()
                .append("SELECT distinct cpastype, container\n")
                .append("FROM ").append(ExperimentService.get().getTinfoMaterial(), "m").append("\n")
                .append("WHERE m.recomputerollup = ?")
                .add(true);

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

    /**
     * Called from exp-23.000-23.001.sql
     */
    public static void cleanUpAliquotRecomputeFlag(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = ExperimentService.get().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            InventoryService inventoryService = InventoryService.get();
            SampleTypeService sampleTypeService = SampleTypeService.get();
            Map<String, List<String>> containerSampleTypes = findContainersWithAliquotRecomputes();
            for (String containerId : containerSampleTypes.keySet())
            {
                Container container = ContainerManager.getForId(containerId);
                if (container == null)
                    continue;

                List<String> sampleTypes = containerSampleTypes.get(containerId);
                LOG.info("** starting cleaning up exp.material.recalcFlag in folder: " + container.getPath());

                try
                {
                    for (String sampleTypeLsid : sampleTypes)
                    {
                        ExpSampleType sampleType = sampleTypeService.getSampleType(sampleTypeLsid);
                        if (sampleType == null)
                            continue;

                        int cleanedUpCount = sampleTypeService.resetRecomputeFlagForNonParents(sampleType, container);
                        if (cleanedUpCount > 0)
                            LOG.info("*** cleaned up RecomputeRollup flag for " + cleanedUpCount + " " + sampleType.getName() + " sample(s) in folder: " + container.getPath());
                        if (inventoryService != null)
                        {
                            int syncedCount = inventoryService.recomputeSampleTypeRollup(sampleType, container, false);
                            if (syncedCount > 0)
                                LOG.info("*** recalculated aliquot roll-up for " + cleanedUpCount + " " + sampleType.getName() + " sample(s) in folder: " + container.getPath());
                        }
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }

                LOG.info("** finished cleaning up exp.material.recalcFlag in folder: " + container.getPath());
            }

            transaction.commit();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

}
