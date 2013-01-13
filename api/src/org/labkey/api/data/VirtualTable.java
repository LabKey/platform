/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
import org.labkey.api.query.UserSchema;

public class VirtualTable<SchemaType extends UserSchema> extends AbstractTableInfo
{
    protected final SchemaType _userSchema;

    public VirtualTable(DbSchema schema, @Nullable SchemaType userSchema)
    {
        super(schema);
        _userSchema = userSchema;
    }

    public VirtualTable(DbSchema schema)
    {
        this(schema, null);
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
}
