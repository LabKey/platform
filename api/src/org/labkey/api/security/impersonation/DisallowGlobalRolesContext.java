/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.security.impersonation;

/**
 * Created by adam on 10/30/2015.
 */

import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;

/**
 * A "not impersonating" context that disallows all global roles (i.e., Site Admin and Developer)
 */
public class DisallowGlobalRolesContext extends NotImpersonatingContext
{
    private static final DisallowGlobalRolesContext INSTANCE = new DisallowGlobalRolesContext();

    public static DisallowGlobalRolesContext get()
    {
        return INSTANCE;
    }

    private DisallowGlobalRolesContext()
    {
    }

    @Override
    public boolean isAllowedGlobalRoles()
    {
        return false;
    }

    @Override
    public String getNavTreeCacheKey()
    {
        return "DisallowGlobalRoles";
    }

    @Override
    public int[] getGroups(User user)
    {
        int[] groups = super.getGroups(user);
        return ArrayUtils.removeElements(groups, Group.groupAdministrators, Group.groupDevelopers);
    }
}
