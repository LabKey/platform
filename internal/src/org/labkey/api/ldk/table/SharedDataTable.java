/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;

import java.util.Arrays;

/**
 * A TableInfo that will always show rows from the current folder plus the shared container
 *
 * If alwaysReverToParent = true, then when queried from a workbook, this will always filter as though the parent is the source
 */
public class SharedDataTable<SchemaType extends UserSchema> extends SimpleUserSchema.SimpleTable<SchemaType>
{
    private boolean _alwaysRevertToParent = false;

    public SharedDataTable(SchemaType schema, TableInfo table, boolean alwaysRevertToParent)
    {
        super(schema, table);
        applyContainerFilter(getContainerFilter());
        _alwaysRevertToParent = alwaysRevertToParent;
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

    @NotNull
    @Override
    public ContainerFilter getContainerFilter()
    {
        Container[] arr = getUserSchema().getContainer().isWorkbook() ? new Container[]{ContainerManager.getSharedContainer(), getUserSchema().getContainer().getParent()} : new Container[]{ContainerManager.getSharedContainer()};
        return new ContainerFilter.CurrentPlusExtras(getUserSchema().getUser(), arr);
    }
}
