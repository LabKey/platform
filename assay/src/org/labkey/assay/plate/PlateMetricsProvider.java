/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate;

import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.assay.TsvAssayProvider;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlateMetricsProvider implements UsageMetricsProvider
{
    private SQLFragment plateSetPlatesSQL(TableInfo plateSetTable, TableInfo plateTable)
    {
        return new SQLFragment("SELECT ps.rowId, COUNT(p.rowId) AS plateCount FROM ")
                .append(plateSetTable, "ps")
                .append(" LEFT OUTER JOIN ").append(plateTable, "p")
                .append(" ON ps.rowId = p.plateSet")
                .append(" WHERE ps.template = ? AND ps.archived = ?")
                .append(" GROUP BY ps.rowId")
                .add(false)
                .add(false);
    }

    private SQLFragment plateSetsPlateCountBetweenSQL(TableInfo plateSetTable, TableInfo plateTable, int above, int below)
    {
        SQLFragment sql = plateSetPlatesSQL(plateSetTable, plateTable);
        SQLFragment inner = sql.append(" HAVING COUNT(p.rowId) > ? AND COUNT(p.rowId) < ?").add(above).add(below);
        return new SQLFragment("SELECT COUNT(*) FROM (").append(inner).append(") as pc");
    }

    private Long plateSetPlatesCountBetween(DbSchema schema, TableInfo plateSetTable, TableInfo plateTable, int above, int below)
    {
        return new SqlSelector(schema, plateSetsPlateCountBetweenSQL(plateSetTable, plateTable, above, below)).getObject(Long.class);
    }

    private Long plateSetPlatesCount(DbSchema schema, TableInfo plateSetTable, TableInfo plateTable, int count)
    {
        SQLFragment platesSQL = plateSetPlatesSQL(plateSetTable, plateTable)
                .append(" HAVING COUNT(p.rowId) = ?")
                .add(count);
        SQLFragment outer = new SQLFragment("SELECT COUNT(*) FROM (").append(platesSQL).append(") as pc");
        return new SqlSelector(schema, outer).getObject(Long.class);
    }

    private Long plateCount(DbSchema schema, TableInfo plateTable, boolean template, boolean archived)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(plateTable, "p")
                .append(" WHERE p.template = ? AND p.archived = ?")
                .add(template)
                .add(archived);
        return new SqlSelector(schema, sql).getObject(Long.class);
    }

    private Long plateSetCount(DbSchema schema, TableInfo plateSetTable, boolean archived)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(plateSetTable, "ps")
                .append(" WHERE archived = ? AND template = ?")
                .add(archived)
                .add(false);
        return new SqlSelector(schema, sql).getObject(Long.class);
    }

    private Long plateTypeCount(DbSchema schema, TableInfo plateTable, TableInfo plateTypeTable, int cols, int rows, boolean template)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(plateTable, "p")
                .append(" JOIN ").append(plateTypeTable, "pt")
                .append(" ON p.plateType = pt.rowId")
                .append(" WHERE p.archived = ? AND p.template = ? AND pt.columns = ? AND pt.rows = ?")
                .add(false)
                .add(template)
                .add(cols)
                .add(rows);
        return new SqlSelector(schema, sql).getObject(Long.class);
    }

    private Long plateWellGroupCount(DbSchema schema, TableInfo plateTable, TableInfo wellGroupTable, boolean template, WellGroup.Type wellGroupType)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(DISTINCT wg.plateId) FROM ")
                .append(wellGroupTable, "wg")
                .append(" INNER JOIN ").append(plateTable, "p").append(" ON p.rowId = wg.plateId ")
                .append(" WHERE p.archived = ? AND p.template = ? AND wg.typename = ?")
                .add(false)
                .add(template)
                .add(wellGroupType);
        return new SqlSelector(schema, sql).getObject(Long.class);
    }

    private List<Container> getBiologicsFolders()
    {
        return ContainerManager.getProjects()
                .stream()
                .filter(c -> "Biologics".equals(ContainerManager.getFolderTypeName(c)))
                .toList();
    }


    private List<ExpProtocol> getPlateEnabledAssayProtocols()
    {
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return Collections.emptyList();

        List<ExpProtocol> allPlateProtocols = new ArrayList<>();
        for (Container c : getBiologicsFolders())
        {
            List<ExpProtocol> plateProtocols = AssayService.get().getAssayProtocols(c).stream().filter(provider::isPlateMetadataEnabled).toList();
            allPlateProtocols.addAll(plateProtocols);
        }

        return allPlateProtocols;
    }

    private ContainerFilter getContainerFilter(Container c)
    {
        return ContainerFilter.Type.AllInProject.create(c, User.getSearchUser());
    }

    private Long getPlateBasedAssayRunsCount(List<ExpProtocol> protocols)
    {
        Long count = 0L;
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null) return count;

        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(User.getSearchUser(), protocol.getContainer(), protocol, null);
            TableInfo runsTable = assayProtocolSchema.createRunsTable(getContainerFilter(protocol.getContainer()));
            if (runsTable != null)
            {
                SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ").append(runsTable, "ar");
                count += new SqlSelector(ExperimentService.get().getSchema(), sql).getObject(Long.class);
            }
        }

        return count;
    }

    private Long getPlateBasedAssayResultsCount(List<ExpProtocol> protocols)
    {
        Long count = 0L;
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null) return count;

        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(User.getSearchUser(), protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(getContainerFilter(protocol.getContainer()), false);
            if (assayDataTable != null)
            {
                SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ").append(assayDataTable, "ad");
                count += new SqlSelector(ExperimentService.get().getSchema(), sql).getObject(Long.class);
            }
        }

        return count;
    }

    private Long getMetadataFieldsCount()
    {
        long count = 0L;

        for (Container c : getBiologicsFolders())
        {
            count += PlateManager.get().getPlateMetadataFields(c, User.getSearchUser()).size();
        }

        return count;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        var plateMetrics = new HashMap<String, Object>();
        var schema = AssayDbSchema.getInstance();
        var dbSchema = schema.getSchema();
        TableInfo plateSetTable = schema.getTableInfoPlateSet();
        TableInfo plateTable = schema.getTableInfoPlate();
        TableInfo plateTypeTable = schema.getTableInfoPlateType();
        TableInfo wellGroupTable = schema.getTableInfoWellGroup();

        // plateSets
        {
            Map<String, Long> plateSets = new HashMap<>();
            plateSets.put("archivedPlateSetCount", plateSetCount(dbSchema, plateSetTable, true));
            plateSets.put("plateSetCount", plateSetCount(dbSchema, plateSetTable, false));
            plateSets.put("primaryPlateSetCount", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type = ?").add(PlateSetType.primary)).getObject(Long.class));
            plateSets.put("assayPlateSetCount", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type = ?").add(PlateSetType.assay)).getObject(Long.class));
            plateSets.put("standAloneAssayPlateSetCount", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type = ?").add(PlateSetType.assay).append(" AND rootPlateSetId IS NULL")).getObject(Long.class));
            plateSets.put("plateSetsWithNoPlatesCount", plateSetPlatesCount(dbSchema, plateSetTable, plateTable, 0));
            plateSets.put("plateSetsWithOnePlateCount", plateSetPlatesCount(dbSchema, plateSetTable, plateTable, 1));
            plateSets.put("maximumPlatesInPlateSet", new SqlSelector(dbSchema, new SQLFragment("SELECT MAX(plateCount) FROM (").append(plateSetPlatesSQL(plateSetTable, plateTable)).append(") x")).getObject(Long.class));
            plateSets.put("plateSetsWith1To10PlatesCount", plateSetPlatesCountBetween(dbSchema, plateSetTable, plateTable, 0, 11));
            plateSets.put("plateSetsWith11to30PlatesCount", plateSetPlatesCountBetween(dbSchema, plateSetTable, plateTable, 10, 31));
            plateSets.put("plateSetsWith31to60PlatesCount", plateSetPlatesCountBetween(dbSchema, plateSetTable, plateTable, 30, 61));
            plateMetrics.put("plateSets", plateSets);
        }

        // plates
        {
            TableInfo wellTable = schema.getTableInfoWell();
            Map<String, Long> plates = new HashMap<>();
            plates.put("platesCount", plateCount(dbSchema, plateTable, false, false));
            plates.put("archivedPlatesCount", plateCount(dbSchema, plateTable, false, true));
            plates.put("distinctPlatedSamples", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM (SELECT DISTINCT sampleId FROM ").append(wellTable, "w").append(" WHERE sampleId IS NOT NULL) as ds")).getObject(Long.class));
            plates.put("12WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 4, 3, false));
            plates.put("24WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 6, 4, false));
            plates.put("48WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 8, 6, false));
            plates.put("96WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 12, 8, false));
            plates.put("384WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 24, 16, false));
            plates.put("platesWithNegativeControlGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, false, WellGroup.Type.NEGATIVE_CONTROL));
            plates.put("platesWithPositiveControlGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, false, WellGroup.Type.POSITIVE_CONTROL));
            plates.put("platesWithReplicateGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, false, WellGroup.Type.REPLICATE));
            plateMetrics.put("plates", plates);
        }

        // templates
        {
            Map<String, Long> templates = new HashMap<>();
            templates.put("plateTemplateCount", plateCount(dbSchema, plateTable, true, false));
            templates.put("archivedTemplatesCount", plateCount(dbSchema, plateTable, true, true));
            templates.put("12WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 4, 3, true));
            templates.put("24WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 6, 4, true));
            templates.put("48WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 8, 6, true));
            templates.put("96WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 12, 8, true));
            templates.put("384WellCount", plateTypeCount(dbSchema, plateTable, plateTypeTable, 24, 16, true));
            templates.put("templatesWithNegativeControlGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, true, WellGroup.Type.NEGATIVE_CONTROL));
            templates.put("templatesWithPositiveControlGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, true, WellGroup.Type.POSITIVE_CONTROL));
            templates.put("templatesWithReplicateGroups", plateWellGroupCount(dbSchema, plateTable, wellGroupTable, true, WellGroup.Type.REPLICATE));
            plateMetrics.put("templates", templates);
        }

        // assays
        {
            TableInfo hitTable = schema.getTableInfoHit();
            TableInfo filterCriteriaTable = schema.getTableInfoFilterCriteria();
            List<ExpProtocol> plateEnabledProtocols = getPlateEnabledAssayProtocols();
            plateMetrics.put("assays", Map.of(
                "hitCount", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(hitTable, "h")).getObject(Long.class),
                "plateSetsWithHits", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(DISTINCT plateSetPath) FROM ").append(hitTable, "h")).getObject(Long.class),
                "assaysWithPlateMetadataEnabled", plateEnabledProtocols.size(),
                "assayRunsCount", getPlateBasedAssayRunsCount(plateEnabledProtocols),
                "assayResultsCount", getPlateBasedAssayResultsCount(plateEnabledProtocols),
                "domainsWithFilterCriteriaConfigured", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(DISTINCT domainId) FROM ").append(filterCriteriaTable, "fc")).getObject(Long.class),
                "columnsWithFilterCriteria", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(DISTINCT propertyId) FROM ").append(filterCriteriaTable, "fc")).getObject(Long.class)
            ));
        }

        // wells
        {
            TableInfo wellTable = schema.getTableInfoWell();
            Map<String, Long> wells = new HashMap<>();
            wells.put("wellCount", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(wellTable, "w")).getObject(Long.class));
            wells.put("wellsWithSamples", new SqlSelector(dbSchema, new SQLFragment("SELECT COUNT(*) FROM ").append(wellTable, "w").append(" WHERE sampleId IS NOT NULL")).getObject(Long.class));
            plateMetrics.put("wells", wells);
        }

        plateMetrics.put("metadata", Map.of(
            "fieldsCount", getMetadataFieldsCount()
        ));

        return Map.of("plates", plateMetrics);
    }
}
