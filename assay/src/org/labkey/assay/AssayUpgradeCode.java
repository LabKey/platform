/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.assay;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.Lsid;
import org.labkey.api.module.ModuleContext;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssayUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(AssayUpgradeCode.class);

    /**
     * Called from assay-25.000-25.001.sql
     * Migrate replicate well groups to be represented via the "Replicate Group" column.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void migrateReplicateGroups(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();

        // - For all "Standard" plates that have a "REPLICATE" well group:
        //   - If the plate does not contain a "SAMPLE" zone, then create one
        //   - For each "REPLICATE" well group:
        //     - Get all the wells that are in the group
        //     - Add those wells to the "SAMPLE" zone. This will be done in the assay.WellGroupPositions table

        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            long numPlatesWithReplicates;
            {
                SQLFragment plateRowIdsSql = new SQLFragment("""
                    SELECT DISTINCT P.RowId
                    FROM assay.Plate AS P
                    INNER JOIN assay.WellGroup AS WG ON WG.PlateId = P.RowId
                    WHERE P.AssayType = ? AND WG.TypeName = ?
                """)
                .add("Standard")   // TsvPlateLayoutHandler.TYPE
                .add("REPLICATE"); // WellGroup.Type.REPLICATE

                numPlatesWithReplicates = new SqlSelector(scope, plateRowIdsSql).getRowCount();
            }

            if (numPlatesWithReplicates > 0)
            {
                ensureSampleZoneWellGroups(scope);

                // Insert missing well positions into the "SAMPLE" well groups where name is NULL
                {
                    SQLFragment sql = new SQLFragment("""
                        INSERT INTO assay.WellGroupPositions (wellGroupId, wellId)
                        SELECT sampleWG.RowId AS wellGroupId, replicateWGP.wellId
                        FROM assay.WellGroup AS replicateWG
                        INNER JOIN assay.WellGroupPositions AS replicateWGP ON replicateWG.RowId = replicateWGP.wellGroupId
                        INNER JOIN assay.WellGroup AS sampleWG ON sampleWG.PlateId = replicateWG.PlateId
                        INNER JOIN assay.Plate AS P ON P.RowId = replicateWG.PlateId
                        LEFT JOIN assay.WellGroupPositions AS sampleWGP ON sampleWG.RowId = sampleWGP.wellGroupId AND replicateWGP.wellId = sampleWGP.wellId
                        WHERE P.AssayType = ? AND replicateWG.TypeName = ? AND sampleWG.TypeName = ? AND sampleWG.Name IS NULL AND sampleWGP.wellId IS NULL
                    """)
                    .add("Standard")  // TsvPlateLayoutHandler.TYPE
                    .add("REPLICATE") // WellGroup.Type.REPLICATE;
                    .add("SAMPLE");   // WellGroup.Type.SAMPLE;

                    new SqlExecutor(scope).execute(sql);
                }
            }

            // Display the ReplicateGroup column by default on each plate set where WellGroup is currently displayed
            {
                SQLFragment sql = new SQLFragment("""
                    INSERT INTO assay.PlateSetProperty (PlateSetId, FieldKey)
                    SELECT DISTINCT PSP.PlateSetId, ? AS FieldKey
                    FROM assay.PlateSetProperty AS PSP WHERE PSP.FieldKey = ?
                """)
                .add("ReplicateGroup") // WellTable.Column.ReplicateGroup
                .add("WellGroup");     // WellTable.Column.WellGroup

                new SqlExecutor(scope).execute(sql);
            }

            tx.commit();
        }
    }

    private static void ensureSampleZoneWellGroups(DbScope scope) throws Exception
    {
        List<List<?>> sampleZoneRowValues = new ArrayList<>();

        // Determine the set of plates that do not yet have a "SAMPLE" well group that does not have a name (a.k.a. "Sample zone").
        SQLFragment needSampleZoneSql = new SQLFragment("""
            SELECT DISTINCT P.RowId, P.Container, WG.Template
            FROM assay.Plate AS P
            INNER JOIN assay.WellGroup AS WG ON WG.PlateId = P.RowId
            WHERE P.AssayType = ? AND WG.TypeName = ?
            AND P.RowId NOT IN (
                SELECT PP.RowId
                FROM assay.Plate AS PP
                INNER JOIN assay.WellGroup AS WGG ON WGG.PlateId = PP.RowId
                WHERE PP.AssayType = ? AND WGG.TypeName = ? AND WGG.Name IS NULL
            )
            ORDER BY P.RowId
        """)
        .add("Standard")  // TsvPlateLayoutHandler.TYPE
        .add("REPLICATE") // WellGroup.Type.REPLICATE;
        .add("Standard")  // TsvPlateLayoutHandler.TYPE
        .add("SAMPLE");   // WellGroup.Type.SAMPLE;

        try (ResultSet rs = new SqlSelector(scope, needSampleZoneSql).getResultSet())
        {
            Map<String, Container> containers = new HashMap<>();
            while (rs.next())
            {
                int plateRowId = rs.getInt("RowId");
                String containerId = rs.getString("Container");
                boolean template = rs.getBoolean("Template");

                Container container = containers.computeIfAbsent(containerId, ContainerManager::getForId);
                if (container == null)
                {
                    // This is never expected to occur due to schema constraint of foreign key assay.WellGroup.Container -> core.Containers
                    throw new IllegalStateException(String.format("Unable to resolve container with entityId \"%s\" for plate rowId (%d).", containerId, plateRowId));
                }

                Lsid lsid = PlateManager.get().getLsid(WellGroup.class, container);
                sampleZoneRowValues.add(List.of(plateRowId, lsid.toString(), container.getEntityId(), template, "SAMPLE")); // WellGroup.Type.SAMPLE;
            }
        }

        if (!sampleZoneRowValues.isEmpty())
        {
            String insertSql = "INSERT INTO assay.WellGroup (PlateId, LSID, Container, Template, TypeName) VALUES (?, ?, ?, ?, ?)";
            Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, sampleZoneRowValues);
        }

        _log.info("Inserted {} new \"sample zone\" well groups.", sampleZoneRowValues.size());
    }
}
