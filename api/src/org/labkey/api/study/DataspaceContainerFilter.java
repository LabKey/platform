/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.api.study;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.GUID;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by matthew on 2/11/14.
 */
public class DataspaceContainerFilter extends ContainerFilter.AllInProject
{
    public static final String SHARED_STUDY_CONTAINER_FILTER_KEY = "sharedStudyContainerFilter.";

    private final List<GUID> _containerIds;

    // ideally this would always be set true, but it needs to be tested for each type
    // we really care about optimizing datasets
    private final boolean _allowOptimizePermissionsCheck;
    private final boolean _includeProject;

    public DataspaceContainerFilter(User user, Study sharedStudy)
    {
        this(user, sharedStudy.getContainer().getProject());
    }

    public DataspaceContainerFilter(User user, Container project)
    {
        super(user);
        List<GUID> containerIds = null;
        ViewContext context = HttpView.hasCurrentView() ? HttpView.currentContext() : null;
        if (project != null && context != null)
        {
            Object o = context.getSession().getAttribute(SHARED_STUDY_CONTAINER_FILTER_KEY + project.getRowId());
            if (o instanceof List)
                containerIds = (List) o;
        }

        _containerIds = null==containerIds ? null : new ArrayList<>(containerIds);
        _allowOptimizePermissionsCheck = false;
        _includeProject = false;
    }

    public DataspaceContainerFilter(User user, List<GUID> containerIds)
    {
        super(user);
        _containerIds = null==containerIds ? null : new ArrayList<>(containerIds);
        _allowOptimizePermissionsCheck = false;
        _includeProject = false;
    }

    private DataspaceContainerFilter(DataspaceContainerFilter src, boolean allowOptimize, boolean includeProject)
    {
        super(src._user);
        _containerIds = src._containerIds;
        _allowOptimizePermissionsCheck = allowOptimize;
        _includeProject = includeProject;
    }


    // This only works for provisioned, project scoped tables, e.g. datasets
    public DataspaceContainerFilter getCanOptimizeDatasetContainerFilter()
    {
        return new DataspaceContainerFilter(this,true,_includeProject);
    }


    // Only usefor for some metadata/design tables, e.g. participantgroups
    public DataspaceContainerFilter getIncludeProjectDatasetContainerFilter()
    {
        return new DataspaceContainerFilter(this,_allowOptimizePermissionsCheck,true);
    }


    public boolean isSubsetOfStudies()
    {
        return _containerIds != null && !_containerIds.isEmpty();
    }

    @Override
    public boolean includeWorkbooks()
    {
        return false;
    }

    @Override
    public boolean useCTE()
    {
        return true;
    }

    @Override
    public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
    {
        HashSet<GUID> allowedContainers = new HashSet<>();
        if (_containerIds != null && !_containerIds.isEmpty())
        {
            for (GUID guid : _containerIds)
            {
                Container c = ContainerManager.getForId(guid);
                if (null != c && !c.isWorkbook() && c.hasPermission(_user, perm, roles))
                    allowedContainers.add(guid);
            }
        }
        else
        {
            allowedContainers.addAll(super.getIds(currentContainer, perm, roles));
        }
        Container project = currentContainer.getProject();
        if (_includeProject && null != project &&  project.hasPermission(_user, perm, roles))
            allowedContainers.add(project.getEntityId());

        if (_allowOptimizePermissionsCheck)
        {
            Set<GUID> studyContainers = null;
            if (currentContainer.isProject())
            {
                // only if it is a project do we use the studiesCache
                studyContainers = studiesCache.get(currentContainer.getId());
            }
            else
            {
                // see if the current container is a study
                Study study = StudyService.get().getStudy(currentContainer);
                if (study != null)
                    studyContainers = Collections.singleton(currentContainer.getEntityId());
            }

            if (null == studyContainers)
            {
                studyContainers = Collections.emptySet();
            }

            // OPTIMIZATION: intersect the containers with permissions with the actual list of studies
            // OPTIMIZATION: check if the list is a superset of all studies (e.g. no containers are filtered)
            allowedContainers.retainAll(studyContainers);

            if (allowedContainers.containsAll(studyContainers))
                return null;
        }

        return allowedContainers;
    }



    /*
     CONSIDER: if there were a caching version of StudyService.get().getAllStudies(project)
     we could do away with this cache
    */
    private static final StringKeyCache<Set<GUID>> studiesCache = CacheManager.getBlockingStringKeyCache(CacheManager.UNLIMITED, CacheManager.HOUR, "Dataspace study cache", new CacheLoader<String,Set<GUID>>(){
        @Override
        public Set<GUID> load(String key, @Nullable Object argument)
        {
            Container project = ContainerManager.getForId(key);
            if (null == project || !project.isProject())
                return null;
            HashSet<GUID> ret = new HashSet<>();
            for (Study s : StudyService.get().getAllStudies(project))
                ret.add(s.getContainer().getEntityId());
            return Collections.unmodifiableSet(ret);
        }
    });

    static
    {
        ContainerManager.addContainerListener(new ContainerManager.ContainerListener(){
            @Override
            public void containerCreated(Container c, User user)
            {
                if (!c.isRoot())
                    studiesCache.remove(c.getProject().getId());
            }

            @Override
            public void containerDeleted(Container c, User user)
            {
                if (!c.isRoot())
                    studiesCache.remove(c.getProject().getId());
            }

            @Override
            public void containerMoved(Container c, Container oldParent, User user)
            {
                if (!c.isRoot())
                    studiesCache.remove(c.getProject().getId());
                if (!oldParent.isRoot())
                    studiesCache.remove(oldParent.getProject().getId());
            }

            @NotNull
            @Override
            public Collection<String> canMove(Container c, Container newParent, User user)
            {
                return Collections.emptyList();
            }

            @Override
            public void propertyChange(PropertyChangeEvent evt)
            {
                if (!(evt instanceof ContainerManager.ContainerPropertyChangeEvent))
                    return;
                ContainerManager.ContainerPropertyChangeEvent event = (ContainerManager.ContainerPropertyChangeEvent) evt;

                if (!event.container.isRoot())
                    studiesCache.remove(event.container.getProject().getId());
            }
        });
    }

}
