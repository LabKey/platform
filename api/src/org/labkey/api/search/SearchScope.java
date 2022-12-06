/*
 * Copyright (c) 2012-2019 LabKey Corporation
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
package org.labkey.api.search;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.SafeToRenderEnum;

import java.util.HashMap;
import java.util.List;

/**
 * Options for how widely or narrowly to search on the server, based on the number of containers to include.
 * User: adam
 * Date: 2/18/12
 */
public enum SearchScope implements SafeToRenderEnum
{
    All(true,true) {
        @Override
        public Container getRoot(Container c)
        {
            return ContainerManager.getRoot();
        }
    },
    Project(true, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c.getProject();
        }
    },
    ProjectAndShared(true, true) {
        @Override
        public Container getRoot(Container c) { return Project.getRoot(c); }
    },
    FolderAndSubfolders(true, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    FolderAndSubfoldersAndShared(true, true) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    Folder(false, false) {
        @Override
        public Container getRoot(Container c)
        {
            return c;
        }
    },
    FolderAndShared(false, true) {
        @Override
        public Container getRoot(Container c)
        {
            return Folder.getRoot(c);
        }
    },
    FolderAndProject(false, false) {
        @Override
        public Container getRoot(Container c)
        {
            return Folder.getRoot(c);
        }

        @Override
        protected HashMap<String, Container> _getSearchableContainers(User user, Container currentContainer)
        {
            HashMap<String, Container> containers = Folder._getSearchableContainers(user, currentContainer);

            Container project = Project.getRoot(currentContainer);
            if (project.hasPermission(user, ReadPermission.class))
                containers.put(project.getId(), project);

            return containers;
        }
    },
    FolderAndProjectAndShared(false, true) {
        @Override
        public Container getRoot(Container c)
        {
            return FolderAndProject.getRoot(c);
        }

        @Override
        protected HashMap<String, Container> _getSearchableContainers(User user, Container currentContainer)
        {
            return FolderAndProject._getSearchableContainers(user, currentContainer);
        }
    };

    private final boolean _recursive;
    private final boolean _includeShared;

    SearchScope(boolean recursive, boolean includeShared)
    {
        _recursive = recursive;
        _includeShared = includeShared;
    }

    public abstract Container getRoot(Container c);

    public boolean isRecursive()
    {
        return _recursive;
    }

    public boolean includeShared()
    {
        return _includeShared;
    }

    public HashMap<String, Container> getSearchableContainers(User user, Container currentContainer)
    {
        Container searchRoot = this.getRoot(currentContainer);
        HashMap<String, Container> containers = this.isRecursive() ?
                getRecursiveContainers(user, searchRoot, currentContainer):
                _getSearchableContainers(user, searchRoot);

        Container shared = ContainerManager.getSharedContainer();
        if (this.includeShared() && shared.hasPermission(user, ReadPermission.class))
        {
            containers.put(shared.getId(), shared);
        }

        return containers;
    }

    protected HashMap<String, Container> _getSearchableContainers(User user, Container searchRoot)
    {
        HashMap<String, Container> containers = new HashMap<>();

        if (searchRoot.hasPermission(user, ReadPermission.class))
            containers.put(searchRoot.getId(), searchRoot);

        return containers;
    }

    protected HashMap<String, Container> getRecursiveContainers(User user, Container searchRoot, Container currentContainer)
    {
        // Returns root plus all children (including workbooks & tabs) where user has read permissions
        List<Container> containers = ContainerManager.getAllChildren(searchRoot, user);
        HashMap<String, Container> containerIds = new HashMap<>(containers.size() * 2);

        for (Container c : containers)
        {
            //Read permission is already checked in the 'getAllChildren' method
            boolean searchable = (c.isSearchable() || c.equals(currentContainer)) && (c.isContainerFor(ContainerType.DataType.search) || c.shouldDisplay(user));

            if (searchable)
            {
                containerIds.put(c.getId(), c);
            }
        }

        return containerIds;
    }
}
