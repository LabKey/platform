/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.UserPrincipal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;

/**
 * Abstract base class for roles
 * User: Dave
 * Date: Apr 22, 2009
 */
public abstract class AbstractRole implements Role
{
    private final String _name;
    private final String _description;
    private final Module _sourceModule;
    private Set<Class<? extends Permission>> _permissions = Collections.unmodifiableSet(new HashSet<Class<? extends Permission>>());
    private final Set<UserPrincipal> _excludedPrincipals = new HashSet<>();

    @SafeVarargs
    protected AbstractRole(String name, String description, Class<? extends Permission>... perms)
    {
        this(name, description, ModuleLoader.getInstance().getCoreModule(), perms);
    }

    @SafeVarargs
    protected AbstractRole(String name, String description, Class<? extends Module> sourceModuleClass, Class<? extends Permission>... perms)
    {
        this(name, description, ModuleLoader.getInstance().getModule(sourceModuleClass), perms);
    }

    @SafeVarargs
    private AbstractRole(String name, String description, Module sourceModule, Class<? extends Permission>... perms)
    {
        _name = name;
        _description = description;
        _sourceModule = sourceModule;

        if (null != perms && perms.length > 0)
        {
            Set<Class<? extends Permission>> p = new HashSet<>();
            p.addAll(Arrays.asList(perms));
            _permissions = Collections.unmodifiableSet(p);
        }
    }

    @NotNull
    public String getUniqueName()
    {
        return this.getClass().getName();
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
    public synchronized Set<Class<? extends Permission>> getPermissions()
    {
        return Collections.unmodifiableSet(_permissions);
    }

    public synchronized void addPermission(@NotNull Class<? extends Permission> perm)
    {
        Set<Class<? extends Permission>> p = new HashSet<>();
        p.addAll(_permissions);
        p.add(perm);
        _permissions = Collections.unmodifiableSet(p);
    }

    @NotNull
    public Module getSourceModule()
    {
        return _sourceModule;
    }

    public boolean isAssignable()
    {
        return true;
    }

    @Override
    public int hashCode()
    {
        return this.getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if(null == obj)
            return false;
        return this.getClass().equals(obj.getClass());
    }

    @Override
    public String toString()
    {
        return getUniqueName();
    }

    @NotNull
    public Set<UserPrincipal> getExcludedPrincipals()
    {
        return _excludedPrincipals;
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && !((Container)resource).isRoot();
    }

    protected void addExcludedPrincipal(UserPrincipal principal)
    {
        _excludedPrincipals.add(principal);
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