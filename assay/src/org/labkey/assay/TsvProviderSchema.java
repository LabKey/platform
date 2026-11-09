/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProviderSchema;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.assay.plate.TsvPlateLayoutHandler;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateSetTable;
import org.labkey.assay.plate.query.PlateTypeTable;
import org.labkey.assay.query.AssayDbSchema;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TsvProviderSchema extends AssayProviderSchema
{
    public static final String PLATE_TEMPLATE_TABLE = "PlateTemplate";

    public TsvProviderSchema(User user, Container container, TsvAssayProvider provider, @Nullable Container targetStudy)
    {
        super(user, container, provider, targetStudy);
    }

    @Override
    public Set<String> getTableNames()
    {
        return Collections.singleton(PLATE_TEMPLATE_TABLE);
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (name.equalsIgnoreCase(PLATE_TEMPLATE_TABLE))
        {
            return new PlateTemplateTable(this, cf).init();
        }
        return super.createTable(name, cf);
    }

    private static class PlateTemplateTable extends SimpleUserSchema.SimpleTable<TsvProviderSchema>
    {
        public PlateTemplateTable(TsvProviderSchema schema, ContainerFilter cf)
        {
            super(schema, AssayDbSchema.getInstance().getTableInfoPlate(), cf);
            setName(PLATE_TEMPLATE_TABLE);
            setTitleColumn("Name");

            addCondition(new SimpleFilter(FieldKey.fromParts("AssayType"), TsvPlateLayoutHandler.TYPE));
        }

        @Override
        public void addColumns()
        {
            super.addColumns();

            // Remove the "RowId" field so the "Lsid" is considered the primary key
            removeColumn(getColumn(FieldKey.fromParts("RowId")));
            getMutableColumnOrThrow("Lsid").setKeyField(true);

            getMutableColumnOrThrow("PlateType").setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(PlateSchema.SCHEMA_NAME).table(PlateTypeTable.NAME));
            getMutableColumnOrThrow("PlateSet").setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).schema(PlateSchema.SCHEMA_NAME).table(PlateSetTable.NAME));
        }

        @Override
        public List<FieldKey> getDefaultVisibleColumns()
        {
            return List.of(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("Type"),
                FieldKey.fromParts("Rows"),
                FieldKey.fromParts("Columns")
            );
        }
    }
}
