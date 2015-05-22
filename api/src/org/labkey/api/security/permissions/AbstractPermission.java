/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.api.security.permissions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.UserPrincipal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
* User: Dave
* Date: Apr 20, 2009
* Time: 10:16:50 AM
*/

/**
 * Abstract base class for permissions.
 */
public abstract class AbstractPermission implements Permission
{
    private final String _name;
    private final String _description;
    private final Module _sourceModule;

    protected AbstractPermission(@NotNull String name, @NotNull String description)
    {
        this(name, description, ModuleLoader.getInstance().getCoreModule());
    }

    protected AbstractPermission(@NotNull String name, @NotNull String description, @NotNull Class<? extends Module> sourceModuleClass)
    {
        this(name, description, ModuleLoader.getInstance().getModule(sourceModuleClass));
    }

    private AbstractPermission(@NotNull String name, @NotNull String description, @NotNull Module sourceModule)
    {
        _name = name;
        _description = description;
        _sourceModule = sourceModule;
    }

    @NotNull
    public String getName()
    {
        return _name;
    }

    @NotNull
    public String getDescription()
    {
        return _description;
    }

    @NotNull
    public Module getSourceModule()
    {
        return _sourceModule;
    }

    @NotNull
    public String getUniqueName()
    {
        return this.getClass().getName();
    }

    @NotNull
    public Set<Class<? extends Permission>> getPermissions()
    {
        HashSet<Class<? extends Permission>> perms = new HashSet<>();
        perms.add(this.getClass());
        return perms;
    }

    public void addPermission(@NotNull Class<? extends Permission> perm)
    {
        throw new UnsupportedOperationException();
    }

    public boolean isAssignable()
    {
        //for now, we will not allow admins to grant
        //users/groups individual permissions.
        //instead, admins assign users/groups to roles
        return false;
    }

    public int getSortOrder()
    {
        return 0;
    }

    /**
     * Permissions are essentially singletons. One permission is
     * equal to another if they are of the same class.
     * @param obj The object to compare
     * @return true if equal, false if not
     */
    @Override
    public boolean equals(Object obj)
    {
        if(null == obj)
            return false;
        return this.getClass().equals(obj.getClass());
    }

    @Override
    public int hashCode()
    {
        return this.getClass().hashCode();
    }

    @NotNull
    public Set<UserPrincipal> getExcludedPrincipals()
    {
        return Collections.emptySet();
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return true;
    }

    @Nullable
    @Override
    public Object getJdbcParameterValue()
    {
        return getUniqueName();
    }

    @NotNull
    @Override
    public JdbcType getJdbcParameterType()
    {
        return JdbcType.VARCHAR;
    }
}