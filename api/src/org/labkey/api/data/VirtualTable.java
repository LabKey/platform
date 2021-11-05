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

package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.AbstractContainerFilterable;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * A {@link org.labkey.api.data.TableInfo} implementation that is not backed directly by a real table in the database,
 * but instead knows how to generate its own SQL, like a dynamically generated SQL VIEW.
 */

public class VirtualTable<SchemaType extends UserSchema> extends AbstractContainerFilterable
{
    protected final SchemaType _userSchema;

    /**
     *
     * @param userSchema If a subclass of VirtualTable has a UserSchema member, do not pass null here.
     *                  Pass it into the typed constructor so it is available via getUserSchema(),
     *                  and likely eliminate the redundant private member from the subclass.
     */
    public VirtualTable(DbSchema schema, String name, @Nullable SchemaType userSchema)
    {
        this(schema, name, userSchema, null);
    }

    public VirtualTable(DbSchema schema, String name, @Nullable SchemaType userSchema, ContainerFilter cf)
    {
        super(schema, name);
        _userSchema = userSchema;
        setName(name);
        _setContainerFilter(cf);
    }


    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        var us = getUserSchema();
        if (null == us)
            return false;
        boolean result = perm.equals(ReadPermission.class) && us.getContainer().hasPermission(user, perm);
        SecurityLogger.log("VirtualTable.hasPermission " + getName(), user, null, result);
        return result;
    }

    @Override
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

    @Override
    protected ContainerFilter getDefaultContainerFilter()
    {
        if (null != _userSchema)
            return _userSchema.getDefaultContainerFilter();
        return super.getDefaultContainerFilter();
    }
}
