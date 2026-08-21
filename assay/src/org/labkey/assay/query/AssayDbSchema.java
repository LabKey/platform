/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.assay.query;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;

public class AssayDbSchema
{
    private static final AssayDbSchema INSTANCE = new AssayDbSchema();
    private static final String SCHEMA_NAME = "assay";

    public static AssayDbSchema getInstance()
    {
        return INSTANCE;
    }

    private AssayDbSchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    public TableInfo getTableInfoPlate()
    {
        return getSchema().getTable("Plate");
    }

    public TableInfo getTableInfoWellGroup()
    {
        return getSchema().getTable("WellGroup");
    }

    public TableInfo getTableInfoWell()
    {
        return getSchema().getTable("Well");
    }

    public TableInfo getTableInfoWellGroupPositions()
    {
        return getSchema().getTable("WellGroupPositions");
    }

    public TableInfo getTableInfoPlateSetProperty()
    {
        return getSchema().getTable("PlateSetProperty");
    }

    public TableInfo getTableInfoPlateSet()
    {
        return getSchema().getTable("PlateSet");
    }

    public TableInfo getTableInfoPlateSetEdge()
    {
        return getSchema().getTable("PlateSetEdge");
    }

    public TableInfo getTableInfoPlateType()
    {
        return getSchema().getTable("PlateType");
    }

    public TableInfo getTableInfoHit()
    {
        return getSchema().getTable("Hit");
    }

    public TableInfo getTableInfoFilterCriteria()
    {
        return getSchema().getTable("FilterCriteria");
    }
}
