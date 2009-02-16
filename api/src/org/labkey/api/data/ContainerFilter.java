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
public interface ContainerFilter
{
    /**
     * @return null if no filtering should be done, otherwise the set of valid container ids
     */
    public Collection<String> getIds(Container currentContainer, User user);

    /**
     * @return true if this filter should be exposed in the UI from query
     */
    public boolean isPublicFilter();
    
    public String name();

    public enum Filters implements ContainerFilter
    {
        CURRENT()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                return Collections.singleton(currentContainer.getId());
            }

            public String toString()
            {
                return "Current Folder";
            }

            public boolean isPublicFilter()
            {
                return true;
            }
        },

        CURRENT_AND_SUBFOLDERS()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                Set<Container> containers = new HashSet<Container>(ContainerManager.getAllChildren(currentContainer, user, ACL.PERM_READ));
                containers.add(currentContainer);
                return toIds(containers);
            }

            public String toString()
            {
                return "Current Folder and Subfolders";
            }

            public boolean isPublicFilter()
            {
                return true;
            }
        },

        CURRENT_PLUS_PROJECT_AND_SHARED()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                List<Container> containers = new ArrayList<Container>();
                containers.add(currentContainer);
                Container project = currentContainer.getProject();
                if (project != null && project.hasPermission(user, ACL.PERM_READ))
                {
                    containers.add(project);
                }
                Container shared = ContainerManager.getSharedContainer();
                if (shared.hasPermission(user, ACL.PERM_READ))
                {
                    containers.add(shared);
                }
                return toIds(containers);
            }

            public boolean isPublicFilter()
            {
                return false;
            }
        },

        ALL_IN_PROJECT()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                Container project = currentContainer.isProject() ? currentContainer : currentContainer.getProject();
                if (project == null)
                {
                    // Don't allow anything
                    return Collections.emptySet();
                }
                Set<Container> containers = new HashSet<Container>(ContainerManager.getAllChildren(project, user, ACL.PERM_READ));
                containers.add(project);
                return toIds(containers);
            }

            public boolean isPublicFilter()
            {
                return false;
            }
        },

        ALL_IN_SITE()
        {
            public Collection<String> getIds(Container currentContainer, User user)
            {
                if (user.isAdministrator())
                {
                    // Don't bother filtering, the user can see everything
                    return null;
                }
                return ContainerManager.getIds(user, ACL.PERM_READ);
            }

            public boolean isPublicFilter()
            {
                return false;
            }
        };

        Set<String> toIds(Collection<Container> containers)
        {
            Set<String> ids = new HashSet<String>();
            for (Container container : containers)
            {
                ids.add(container.getId());
            }
            return ids;
        }
    }

    public class CurrentPlusExtras implements ContainerFilter
    {
        private Container[] _extraContainers;

        public CurrentPlusExtras(Container... extraContainers)
        {
            _extraContainers = extraContainers;
        }

        public Collection<String> getIds(Container currentContainer, User user)
        {
            Set<Container> containers = new HashSet<Container>();
            containers.add(currentContainer);
            for (Container extraContainer : _extraContainers)
            {
                if (extraContainer.hasPermission(user, ACL.PERM_READ))
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

        public boolean isPublicFilter()
        {
            return false;
        }

        public String name()
        {
            return getClass().getSimpleName();
        }
    }

    public static final ContainerFilter EVERYTHING = new ContainerFilter()
    {
        public Collection<String> getIds(Container currentContainer, User user)
        {
            return null;
        }

        public boolean isPublicFilter()
        {
            return false;
        }

        public String name()
        {
            return getClass().getSimpleName();
        }
    };

}