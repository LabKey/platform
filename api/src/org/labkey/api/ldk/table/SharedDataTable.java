/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;

/**
 * A TableInfo that will always show rows from the current folder plus the shared container
 */
public class SharedDataTable<SchemaType extends UserSchema> extends SimpleUserSchema.SimpleTable<SchemaType>
{
    public SharedDataTable(SchemaType schema, TableInfo table)
    {
        super(schema, table, null);
    }

    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    public SharedDataTable<SchemaType> init()
    {
        return (SharedDataTable<SchemaType>)super.init();
    }

    @Override
    protected ContainerFilter getDefaultContainerFilter()
    {
        return new ContainerFilter.CurrentPlusExtras(getUserSchema().getContainer(), getUserSchema().getUser(), ContainerManager.getSharedContainer(), getUserSchema().getContainer().getContainerFor(ContainerType.DataType.sharedSchemaOwner));
    }
}
