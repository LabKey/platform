/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by matthew on 2/11/14.
 */
public class DataspaceContainerFilter extends ContainerFilter.AllInProject
{
    private final List<GUID> _containerIds;

    public DataspaceContainerFilter(User user)
    {
        super(user);
        _containerIds = null;
    }

    public DataspaceContainerFilter(User user, List<GUID> containerIds)
    {
        super(user);

        _containerIds = containerIds;
    }

    @Override
    public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
    {
        if (_containerIds != null && !_containerIds.isEmpty())
        {
            List<GUID> containers = new ArrayList<>(_containerIds.size());
            for (GUID guid : _containerIds)
            {
                Container c = ContainerManager.getForId(guid);
                if (!c.isWorkbook() && c.hasPermission(_user, perm, roles))
                    containers.add(guid);
            }
            return containers;
        }
        else
        {
            return super.getIds(currentContainer, perm, roles);
        }
    }

}
