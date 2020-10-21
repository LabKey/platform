/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.UserSchema;


/* This is a shim class to let Query interact with APIs that require a TableInfo (like new BaseColumnInfo()) */
public class SQLTableInfo extends AbstractTableInfo implements ContainerFilterable
{
    private ContainerFilter _containerFilter;

    public SQLTableInfo(DbSchema schema, String name)
    {
        super(schema, name);
    }

    @Override
    protected boolean isCaseSensitive()
    {
        return true;
    }

    @Override
    @NotNull
    public SQLFragment getFromSQL()
    {
        throw new IllegalStateException();
    }

    @Override
    public UserSchema getUserSchema()
    {
        return null;
    }

    @Override
    public ContainerFilter getContainerFilter()
    {
        return _containerFilter;
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
    }

    @Override
    public boolean hasDefaultContainerFilter()
    {
        return false;
    }
}

