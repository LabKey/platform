/*
 * Copyright (c) 2026 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateStorageService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.assay.AssayModule;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PlateStorageServiceImpl implements PlateStorageService
{
    @Override
    public boolean isAvailable(@NotNull Container container)
    {
        return container.hasActiveModuleByName(AssayModule.NAME);
    }

    @Override
    public @NotNull Map<Long, StoragePlate> getStoragePlates(@NotNull Collection<Long> plateRowIds, @NotNull Container container, @NotNull User user)
    {
        Map<Long, StoragePlate> plates = new HashMap<>();
        if (plateRowIds.isEmpty())
            return plates;

        AssayDbSchema schema = AssayDbSchema.getInstance();
        // Read permission only, not folder scope: callers scope for themselves, and a lookup filter would make a
        // readable plate in a sibling or child folder indistinguishable from an unreadable one
        ContainerFilter cf = ContainerFilter.Type.AllFolders.create(container, user);

        // A projection query rather than PlateCache.getPlate per id: that loader runs populatePlate, which
        // materializes every well, well group and custom field only for these few scalars to be read off it.
        SQLFragment sql = new SQLFragment()
                .append("SELECT p.RowId, p.PlateId, p.Name, p.Barcode, p.Container, p.Template, p.Archived,\n")
                .append("       p.PlateSet AS PlateSetId, ps.Name AS PlateSetName, ps.Type AS PlateSetType,\n")
                .append("       pt.Description AS PlateTypeName\n")
                .append("FROM ").append(schema.getTableInfoPlate(), "p").append("\n")
                .append("LEFT JOIN ").append(schema.getTableInfoPlateSet(), "ps").append(" ON ps.RowId = p.PlateSet\n")
                .append("LEFT JOIN ").append(schema.getTableInfoPlateType(), "pt").append(" ON pt.RowId = p.PlateType\n")
                .append("WHERE ").append(cf.getSQLFragment(schema.getSchema(), new SQLFragment("p.Container"))).append("\n")
                .append("AND p.RowId ").appendInClause(plateRowIds, schema.getSchema().getSqlDialect());

        new SqlSelector(schema.getSchema(), sql).forEach(rs -> {
            long rowId = rs.getLong(PlateTable.Column.RowId.name());
            long plateSet = rs.getLong("PlateSetId");
            Long plateSetId = rs.wasNull() ? null : plateSet;

            plates.put(rowId, new StoragePlate(
                    rowId,
                    rs.getString(PlateTable.Column.PlateId.name()),
                    rs.getString(PlateTable.Column.Name.name()),
                    rs.getString(PlateTable.Column.Barcode.name()),
                    new GUID(rs.getString(PlateTable.Column.Container.name())),
                    rs.getBoolean(PlateTable.Column.Template.name()),
                    rs.getBoolean(PlateTable.Column.Archived.name()),
                    PlateSetType.assay.name().equalsIgnoreCase(rs.getString("PlateSetType")),
                    plateSetId,
                    rs.getString("PlateSetName"),
                    rs.getString("PlateTypeName")
            ));
        });

        return plates;
    }

    @Override
    public @NotNull Collection<Long> getExistingPlateRowIds(@NotNull Collection<Long> plateRowIds)
    {
        if (plateRowIds.isEmpty())
            return Collections.emptyList();

        TableInfo table = AssayDbSchema.getInstance().getTableInfoPlate();
        return new TableSelector(table, Collections.singleton(PlateTable.Column.RowId.name()),
                new SimpleFilter(FieldKey.fromParts(PlateTable.Column.RowId.name()), plateRowIds, CompareType.IN), null)
                .getCollection(Long.class);
    }
}
