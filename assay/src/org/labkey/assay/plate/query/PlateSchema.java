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

package org.labkey.assay.plate.query;

import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.util.List;
import java.util.Set;

public class PlateSchema extends SimpleUserSchema
{
    public static final String SCHEMA_NAME = "plate";
    private static final String DESCRIPTION = "Contains data about defined plates";

    private static final Set<String> AVAILABLE_TABLES = new CaseInsensitiveTreeSet(List.of(
        HitTable.NAME,
        PlateTable.NAME,
        PlateSetTable.NAME,
        PlateTypeTable.NAME,
        WellTable.NAME,
        WellGroupTable.NAME
    ));

    public PlateSchema(User user, Container container)
    {
        super(SCHEMA_NAME, DESCRIPTION, user, container, AssayDbSchema.getInstance().getSchema(),
                null, AVAILABLE_TABLES, null);
    }

    @Override
    public Set<String> getTableNames()
    {
        return AVAILABLE_TABLES;
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (PlateTable.NAME.equalsIgnoreCase(name))
            return new PlateTable(this, cf).init();
        if (WellTable.NAME.equalsIgnoreCase(name))
            return new WellTable(this, cf).init();
        if (PlateSetTable.NAME.equalsIgnoreCase(name))
            return new PlateSetTable(this, cf).init();
        if (PlateTypeTable.NAME.equalsIgnoreCase(name))
            return new PlateTypeTable(this, cf).init();
        if (HitTable.NAME.equalsIgnoreCase(name))
            return new HitTable(this, cf, false).init();
        if (WellGroupTable.NAME.equalsIgnoreCase(name))
            return new WellGroupTable(this, cf).init();
        if (WellTable.WELL_PROPERTIES_TABLE.equalsIgnoreCase(name))
        {
            Domain domain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUser());
            if (domain != null)
                return new WellTable.WellPropertiesTable(domain,this, cf);
        }

        return null;
    }

    static public void register(Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module)
        {
            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new PlateSchema(schema.getUser(), schema.getContainer());
            }

            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // because assays are always available
                return true;
            }
        });
    }
}
