/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.AbstractContainerFilterable;
import org.labkey.api.query.UserSchema;

/**
 * A {@link org.labkey.api.data.TableInfo} implementation that is not backed directly by a real table in the database,
 * but instead knows how to generate its own SQL, like a dynamically generated SQL VIEW.
 */

public class VirtualTable<SchemaType extends UserSchema> extends AbstractContainerFilterable
{
    protected final SchemaType _userSchema;

    public VirtualTable(DbSchema schema, String name, @Nullable SchemaType userSchema)
    {
        super(schema, name);
        _userSchema = userSchema;
        setName(name);
    }

    public VirtualTable(DbSchema schema, String name)
    {
        this(schema, name, null);
    }

    @NotNull
    public SQLFragment getFromSQL()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPublic()
    {
        return false;
    }

    @Override
    public SchemaType getUserSchema()
    {
        return _userSchema;
    }

    @Override
    protected void _setContainerFilter(ContainerFilter filter)
    {
        _containerFilter = filter;
    }
}
