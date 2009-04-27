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

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.apache.log4j.Logger;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    //global map from role name to Role instance
    private static final Map<String, Role> _roleMap = new ConcurrentHashMap<String,Role>();

    //register all core roles
    static
    {
        registerRole(new SiteAdminRole());
    }

    public static Role getRole(String name)
    {
        Role role = _roleMap.get(name);
        if(null == role)
            Logger.getLogger(RoleManager.class).warn("Could not resolve the role " + name + "! The role may no longer exist, or may not yet be registered.");
        return role;
    }

    public static void registerRole(Role role)
    {
        _roleMap.put(role.getUniqueName(), role);
    }

}