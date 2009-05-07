/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.security.permissions.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/*
* User: Dave
* Date: Apr 22, 2009
* Time: 2:09:39 PM
*/

/**
 * Global role manager
 */
public class RoleManager
{
    public static final Set<Class<? extends Permission>> BasicPermissions = new HashSet<Class<? extends Permission>>();
    static
    {
        BasicPermissions.add(ReadPermission.class);
        BasicPermissions.add(InsertPermission.class);
        BasicPermissions.add(UpdatePermission.class);
        BasicPermissions.add(DeletePermission.class);
    }

    //global map from role name to Role instance
    private static final Map<String, Role> _nameToRoleMap = new ConcurrentHashMap<String,Role>();
    private static final Map<Class<? extends Role>, Role> _classToRoleMap = new ConcurrentHashMap<Class<? extends Role>, Role>();
    private static final List<Role> _roles = new CopyOnWriteArrayList<Role>();

    //register all core roles
    static
    {
        registerRole(new SiteAdminRole());
        registerRole(new ProjectAdminRole());
        registerRole(new FolderAdminRole());
        registerRole(new EditorRole());
        registerRole(new AuthorRole());
        registerRole(new ReaderRole());
        registerRole(new RestrictedReaderRole());
        registerRole(new SubmitterRole());
        registerRole(new NoPermissionsRole());
        registerRole(new OwnerRole());
    }

    public static Role getRole(String name)
    {
        Role role = _nameToRoleMap.get(name);
        if(null == role)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the role " + name + "! The role may no longer exist, or may not yet be registered.");
        return role;
    }

    public static Role getRole(Class<? extends Role> clazz)
    {
        Role role = _classToRoleMap.get(clazz);
        if(null == role)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the role " + clazz.getName() + "! Did you forget to register the role with RoleManager.register()?");
        return role;
    }

    public static Permission getPermission(Class<? extends Permission> clazz)
    {
        Permission perm = (Permission)_classToRoleMap.get(clazz);
        if(null == perm)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the permission " + clazz.getName() + "! If this is not part of a role, you must register it separately with RoleManager.register().");
        return perm;
    }

    public static List<Role> getAllRoles()
    {
        return _roles;
    }

    public static void registerRole(Role role)
    {
        _nameToRoleMap.put(role.getUniqueName(), role);
        _classToRoleMap.put(role.getClass(), role);
        _roles.add(role);

        //register all exposed permissions in the name and class maps
        for(Class<? extends Permission> permClass : role.getPermissions())
        {
            try
            {
                Permission perm = permClass.newInstance();
                _nameToRoleMap.put(perm.getUniqueName(), perm);
                _classToRoleMap.put(perm.getClass(), perm);
            }
            catch(InstantiationException e) {}
            catch (IllegalAccessException e) {}
        }
    }

}