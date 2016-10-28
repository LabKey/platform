/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: 3/27/12
 * Time: 4:45 PM
 */

// Caches the list of groups in each project plus the site group list
public class ProjectAndSiteGroupsCache
{
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final BlockingCache<Container, Collection<Integer>> CACHE = CacheManager.getBlockingCache(1000, CacheManager.DAY, "Project Groups", null);

    private static final CacheLoader<Container, Collection<Integer>> GROUP_LIST_LOADER = new CacheLoader<Container, Collection<Integer>>()
    {
        @Override
        public Collection<Integer> load(Container c, Object argument)
        {
            String containerClause = c.isRoot() ? "IS NULL" : "= ?";

            SQLFragment sql = new SQLFragment(
                "SELECT UserId FROM " + CORE.getTableInfoPrincipals() + "\n" +
                    "WHERE Type = '" + PrincipalType.GROUP.getTypeChar() + "' AND Container " + containerClause + "\n" +
                    "ORDER BY LOWER(Name)");  // Force case-insensitve order for consistency

            if (!c.isRoot())
                sql.add(c);

            return Collections.unmodifiableCollection(new SqlSelector(CORE.getSchema(), sql).getCollection(Integer.class));
        }
    };


    static @NotNull List<Group> getProjectGroups(Container project, boolean includeSiteGroups)
    {
        Collection<Integer> projectGroups = CACHE.get(project, includeSiteGroups, GROUP_LIST_LOADER);
        ArrayList<Group> groups;

        if (includeSiteGroups)
        {
            Collection<Integer> siteGroupIds = getSiteGroupIds();
            groups = new ArrayList<>(projectGroups.size() + siteGroupIds.size());
            addAll(groups, siteGroupIds);
        }
        else
        {
            groups = new ArrayList<>(projectGroups.size());
        }

        addAll(groups, projectGroups);

        return Collections.unmodifiableList(groups);
    }


    static @NotNull List<Group> getSiteGroups()
    {
        Collection<Integer> siteGroupIds = getSiteGroupIds();
        ArrayList<Group> groups = new ArrayList<>(siteGroupIds.size());
        addAll(groups, siteGroupIds);

        return Collections.unmodifiableList(groups);
    }


    private static void addAll(List<Group> groups, Collection<Integer> ids)
    {
        for (Integer id : ids)
        {
            Group group = SecurityManager.getGroup(id);

            if (null != group)
                groups.add(group);
        }
    }


    private static @NotNull Collection<Integer> getSiteGroupIds()
    {
        return CACHE.get(ContainerManager.getRoot(), null, GROUP_LIST_LOADER);
    }


    static void uncache(Container c)
    {
        if (null != c)
            CACHE.remove(c);
        else
            CACHE.remove(ContainerManager.getRoot());
    }
}
