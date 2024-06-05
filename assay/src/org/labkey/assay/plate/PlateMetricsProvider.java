package org.labkey.assay.plate;

import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateSetType;
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
        return new SQLFragment("SELECT ps.name, COUNT(p.rowid) FROM ")
                .append(plateSetTable, "ps")
                .append(" LEFT OUTER JOIN ").append(plateTable, "p")
                .append(" ON ps.rowid = p.plateset")
                .append(" GROUP BY ps.rowId");
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

    private Long plateTypeCount(DbSchema schema, TableInfo plateTable, TableInfo plateTypeTable, int cols, int rows)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ")
                .append(plateTable, "p")
                .append(" JOIN ").append(plateTypeTable, "pt")
                .append(" ON p.platetype = pt.rowid")
                .append(" WHERE pt.columns = ? AND pt.rows = ?")
                .add(cols)
                .add(rows);
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
        if (provider == null) return Collections.emptyList();
        var containers = getBiologicsFolders();
        List<ExpProtocol> allPlateProtocols = new ArrayList<>();

        for (Container c : containers)
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
        if (!AssayPlateMetadataService.isExperimentalAppPlateEnabled())
            return Map.of("plates", new HashMap<String, Object>());

        var plateMetrics = new HashMap<String, Object>();
        var schema = AssayDbSchema.getInstance();
        TableInfo plateSetTable = schema.getTableInfoPlateSet();
        TableInfo plateTable = schema.getTableInfoPlate();
        Long plateSetCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps")).getObject(Long.class);
        Long primaryPlateSetCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type =?").add(PlateSetType.primary)).getObject(Long.class);
        Long assayPlateSetCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type =?").add(PlateSetType.assay)).getObject(Long.class);
        Long standAlonePlateSetCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(plateSetTable, "ps").append(" WHERE type =?").add(PlateSetType.assay).append(" AND rootplatesetid IS NULL")).getObject(Long.class);
        SQLFragment plateSetNoPlates = plateSetPlatesSQL(plateSetTable, plateTable).append(" HAVING COUNT(p.rowId) = ?").add(0);
        Long plateSetNoPlatesCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM (").append(plateSetNoPlates).append(") as np")).getObject(Long.class);

        plateMetrics.put("plateSets", Map.of(
                "plateSetCount", plateSetCount,
                "primaryPlateSetCount", primaryPlateSetCount,
                "assayPlateSetCount", assayPlateSetCount,
                "standAloneAssayPlateSetCount", standAlonePlateSetCount,
                "plateSetsWithNoPlatesCount", plateSetNoPlatesCount,
                "plateSetsWith1To10PlatesCount", plateSetPlatesCountBetween(schema.getSchema(), plateSetTable, plateTable, 0, 11),
                "plateSetsWith11to30PlatesCount", plateSetPlatesCountBetween(schema.getSchema(), plateSetTable, plateTable, 10, 31),
                "plateSetsWith31to60PlatesCount", plateSetPlatesCountBetween(schema.getSchema(), plateSetTable, plateTable, 30, 61)
        ));

        Long platesCount = new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(plateTable, "p")).getObject(Long.class);
        TableInfo plateTypeTable = schema.getTableInfoPlateType();
        TableInfo wellTable = schema.getTableInfoWell();
        plateMetrics.put("plates", Map.of(
                "platesCount", platesCount,
                "distinctPlatedSamples", new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM (SELECT DISTINCT sampleId FROM ").append(wellTable, "w").append(" WHERE sampleId IS NOT NULL) as ds")).getObject(Long.class),
                "12WellCount", plateTypeCount(schema.getSchema(), plateTable, plateTypeTable, 4, 3),
                "24WellCount", plateTypeCount(schema.getSchema(), plateTable, plateTypeTable, 6, 4),
                "48WellCount", plateTypeCount(schema.getSchema(), plateTable, plateTypeTable, 8, 6),
                "96WellCount", plateTypeCount(schema.getSchema(), plateTable, plateTypeTable, 12, 8),
                "384WellCount", plateTypeCount(schema.getSchema(), plateTable, plateTypeTable, 24, 16)
        ));

        TableInfo hitTable = schema.getTableInfoHit();
        List<ExpProtocol> plateEnabledProtocols = getPlateEnabledAssayProtocols();
        plateMetrics.put("assays", Map.of(
                "hitCount", new SqlSelector(schema.getSchema(), new SQLFragment("SELECT COUNT(*) FROM ").append(hitTable, "h")).getObject(Long.class),
                "assaysWithPlateMetadataEnabled", plateEnabledProtocols.size(),
                "assayRunsCount", getPlateBasedAssayRunsCount(plateEnabledProtocols),
                "assayResultsCount", getPlateBasedAssayResultsCount(plateEnabledProtocols)
        ));

        plateMetrics.put("metadata", Map.of(
                "fieldsCount", getMetadataFieldsCount()
        ));

        return Map.of("plates", plateMetrics);
    }
}
