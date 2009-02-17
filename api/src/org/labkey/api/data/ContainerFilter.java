/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

import java.util.*;

/**
 * User: jeckels
 * Date: Nov 3, 2008
 */
public abstract class ContainerFilter
{
    /**
     * @return null if no filtering should be done, otherwise the set of valid container ids
     */
    public abstract Collection<String> getIds(Container currentContainer);

    public String name()
    {
        return getClass().getSimpleName();
    }

    /**
     * If we can't find the name, we default to CURRENT
     */
    public static ContainerFilter getContainerFilterByName(String name, User user)
    {
        if ("CurrentAndSubfolders".equals(name))
            return new CurrentAndSubfolders(user);

        return CURRENT;
    }

    public static Collection<ContainerFilter> getPublicFilters(User user)
    {
        List<ContainerFilter> filters = new ArrayList<ContainerFilter>();
        filters.add(CURRENT);
        filters.add(new CurrentAndSubfolders(user));

        return filters;
    }

    public static final ContainerFilter CURRENT = new ContainerFilter()
    {
        public Collection<String> getIds(Container currentContainer)
        {
            return Collections.singleton(currentContainer.getId());
        }

        @Override
        public String toString()
        {
            return "Current Folder";
        }

        @Override
        public String name()
        {
            return "CURRENT";
        }
    };

    public static final ContainerFilter EVERYTHING = new ContainerFilter()
    {
        public Collection<String> getIds(Container currentContainer)
        {
            return null;
        }
    };

    private static abstract class ContainerFilterWithUser extends ContainerFilter
    {
        protected final User _user;

        public ContainerFilterWithUser(User user)
        {
            _user = user;
        }
    }

    public static class CurrentPlusExtras extends ContainerFilterWithUser
    {
        private final Container[] _extraContainers;

        public CurrentPlusExtras(User user, Container... extraContainers)
        {
            super(user);
            _extraContainers = extraContainers;
        }

        public Collection<String> getIds(Container currentContainer)
        {
            Set<Container> containers = new HashSet<Container>();
            containers.add(currentContainer);
            for (Container extraContainer : _extraContainers)
            {
                if (extraContainer.hasPermission(_user, ACL.PERM_READ))
                {
                    containers.add(extraContainer);
                }
            }
            return toIds(containers);
        }

        private Set<String> toIds(Collection<Container> containers)
        {
            Set<String> ids = new HashSet<String>();
            for (Container container : containers)
            {
                ids.add(container.getId());
            }
            return ids;
        }
    }

    public static class CurrentAndSubfolders extends ContainerFilterWithUser
    {
        public CurrentAndSubfolders(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            Set<Container> containers = new HashSet<Container>(ContainerManager.getAllChildren(currentContainer, _user, ACL.PERM_READ));
            containers.add(currentContainer);
            return toIds(containers);
        }

        public String toString()
        {
            return "Current Folder and Subfolders";
        }
    }

    public static class CurrentPlusProject extends ContainerFilterWithUser
    {
        public CurrentPlusProject(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            Set<Container> containers = new HashSet<Container>();
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, ACL.PERM_READ))
            {
                containers.add(project);
            }
            return toIds(containers);
        }
    }

    public static class CurrentPlusProjectAndShared extends ContainerFilterWithUser
    {
        public CurrentPlusProjectAndShared(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            Set<Container> containers = new HashSet<Container>();
            containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, ACL.PERM_READ))
            {
                containers.add(project);
            }
            Container shared = ContainerManager.getSharedContainer();
            if (shared.hasPermission(_user, ACL.PERM_READ))
            {
                containers.add(shared);
            }
            return toIds(containers);
        }

    }

    public static class AllInProject extends ContainerFilterWithUser
    {
        public AllInProject(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            Container project = currentContainer.isProject() ? currentContainer : currentContainer.getProject();
            if (project == null)
            {
                // Don't allow anything
                return Collections.emptySet();
            }
            Set<Container> containers = new HashSet<Container>(ContainerManager.getAllChildren(project, _user, ACL.PERM_READ));
            containers.add(project);
            return toIds(containers);
        }
    }

    public static class AllInSite extends ContainerFilterWithUser
    {
        public AllInSite(User user)
        {
            super(user);
        }

        public Collection<String> getIds(Container currentContainer)
        {
            if (_user.isAdministrator())
            {
                // Don't bother filtering, the user can see everything
                return null;
            }
            return ContainerManager.getIds(_user, ACL.PERM_READ);
        }

    }

    private static Set<String> toIds(Collection<Container> containers)
    {
        Set<String> ids = new HashSet<String>();
        for (Container container : containers)
        {
            ids.add(container.getId());
        }
        return ids;
    }

}