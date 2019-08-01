/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents which set of containers should be included when querying for data. In general, the code will
 * default to showing data from just the current container, but alternative ContainerFilters can resolve items
 * in the /Shared project, in parent containers, or a variety of other scoping locations.
 * User: jeckels
 * Date: Nov 3, 2008
 */
public abstract class ContainerFilter
{
    /**
     * Users of ContainerFilter should use getSQLFragment() or createFilterClause(), not build up their own SQL using
     * the IDs.
     * @return null if no filtering should be done, otherwise the set of valid container ids
     */
    @Nullable
    public abstract Collection<GUID> getIds(Container currentContainer);

    /**
     * @return The set of container types to be included based on their parent container's id
     */
    public Set<String> getIncludedChildTypes()
    {
        return Collections.singleton(WorkbookContainerType.NAME);
    }

    public boolean useCTE()
    {
        return false;
    }

    /**
     * May return null if the ContainerFilter has no corresponding ContainerFilter.Type.
     */
    @Nullable
    public abstract Type getType();


    public String getDefaultCacheKey(Container c)
    {
        return getClass().getName() + "/" + c.getId();
    }

    // return a string such that a.getCacheKey().equals(b.getCacheKey()) => a.equals(b)
    // This is purposefully abstract, to force the implementer to consider if getDefaultCacheKey() is appropriate
    abstract public String getCacheKey(Container c);

    /**
     * If we can't find the name, we default to CURRENT
     */
    @NotNull
    public static ContainerFilter getContainerFilterByName(@Nullable String name, @NotNull User user)
    {
        Type type = Type.Current;
        try
        {
            if (name != null)
            {
                type = Type.valueOf(name);
            }
        }
        catch (IllegalArgumentException e)
        {
            // Revert to Current
        }
        return type.create(user);
    }

    /**
     * The standard ContainerFilter SQL includes data from workbooks if the parent is already in the list via a join.
     * Therefore, we can filter out any workbooks from the list so that we don't need to pass as many Ids in the SQL.
     * This is important for servers that have lots and lots of workbooks, like the O'Connor server which has more than
     * 10,000.
     */
    protected Collection<Container> removeDuplicatedContainers(Collection<Container> containers)
    {
        Set<Container> result = new HashSet<>(containers.size());
        for (Container c : containers)
        {
            if (!c.isDuplicatedInContainerFilter())
            {
                result.add(c);
            }
        }
        return result;
    }

    /** Create a FilterClause that restricts based on the containers that meet the filter */
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn, Container container)
    {
        return new ContainerClause(schema, containerFilterColumn, this, container);
    }

    /** Create a FilterClause that restricts based on the containers that meet the filter and user that meets the permission*/
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn, Container container, Class<? extends Permission> permission, Set<Role> roles)
    {
        return new ContainerClause(schema, containerFilterColumn, this, container, permission, roles);
    }


    /** Create an expression for a WHERE clause */
    public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Container container, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        ColumnInfo columnInfo = columnMap.get(containerColumnFieldKey);
        SQLFragment sql;
        if (columnInfo != null)
        {
            sql = new SQLFragment(columnInfo.getSelectName());
        }
        else
        {
            sql = new SQLFragment(containerColumnFieldKey.toString());
        }
        return getSQLFragment(schema, sql, container);
    }

    /** Create an expression for a WHERE clause */
    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container)
    {
        return getSQLFragment(schema, containerColumnSQL, container, true);
    }

    /**
     * Create an expression for a WHERE clause
     * Generally parameters are preferred, but can cause perf problems in certain cases
     * @param allowNulls - if looking at ALL rows, whether to allow nulls in the Container column
     */
    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, boolean allowNulls)
    {
        SecurityLogger.indent("ContainerFilter");
        Collection<GUID> ids = getIds(container);
        SecurityLogger.outdent();
        return getSQLFragment(schema, container, containerColumnSQL, ids, allowNulls, getIncludedChildTypes());
    }

    // instances of ContainerFilterWithUser will call this getSQLFragment after GetIds with a specific permission to check against the user
    protected SQLFragment getSQLFragment(DbSchema schema, Container container, SQLFragment containerColumnSQL, Collection<GUID> ids, boolean allowNulls, Set<String> includedTypes)
    {
        SQLFragment f = _getSQLFragment(schema, container, containerColumnSQL, ids, allowNulls, includedTypes);
        if (_log.isTraceEnabled())
        {
            SQLFragment comment = new SQLFragment(f);
            comment.appendComment(toString(), schema.getSqlDialect());
            f = comment;
        }
        return f;
    }

    protected SQLFragment _getSQLFragment(DbSchema schema, Container container, SQLFragment containerColumnSQL, Collection<GUID> ids, boolean allowNulls, @NotNull Set<String> includedChildTypes)
    {
        if (ids == null)
        {
            if (allowNulls)
            {
                return new SQLFragment("1 = 1");
            }
            else
            {
                SQLFragment result = new SQLFragment(containerColumnSQL);
                result.append(" IS NOT NULL");
                return result;
            }
        }

        if (ids.isEmpty())
        {
            return new SQLFragment("1 = 0");
        }

        if (ids.size() == 1)
        {
            Container first = ContainerManager.getForId(ids.iterator().next());
            if (null == first)
                return new SQLFragment("1 = 0");
            if (includedChildTypes.isEmpty() || !first.hasChildrenOfAnyType(includedChildTypes))
                return new SQLFragment(containerColumnSQL).append("=").append(first);
        }

        SQLFragment list = new SQLFragment();
        String comma = "";
        boolean verbose = AppProps.getInstance().isDevMode() && ids.size() <= 3;
        for (GUID containerId : ids)
        {
            list.append(comma);
            list.append("(");
            if (verbose)
            {
                Container c = ContainerManager.getForId(containerId);
                if (null != c)
                    list.append(c);
                else
                    list.append("'").append(containerId.toString()).append("'");
            }
            else
            {
                list.append("'").append(containerId.toString()).append("'");
            }
            list.append(")");
            comma = ", ";
        }

        SQLFragment select = new SQLFragment();

        if (!includedChildTypes.isEmpty())
        {
            select.append("SELECT c.EntityId FROM ");
            select.append(CoreSchema.getInstance().getTableInfoContainers(), "c");
            // Need to add cast to make Postgres happy
            select.append(" INNER JOIN (SELECT CAST(Id AS ");
            select.append(schema.getSqlDialect().getGuidType());
            select.append(") AS Id FROM (VALUES ");
            select.append(list);
            select.append(") as _containerids_ (Id) ");
            // Filter based on the container's ID, or the container is a child of the ID and of type workbook
            select.append(") x ON c.EntityId = x.Id OR (c.Parent = x.Id AND c.Type IN ('");
            select.append(StringUtils.join(includedChildTypes, "','"));
            select.append("') )");
        }
        else if (ids.size() < 10 || ! useCTE())
        {
            SQLFragment result = new SQLFragment(containerColumnSQL);
            result.append(" IN (").append(list).append(")");
            return result;
        }
        else
        {
            select.append ("SELECT EntityId FROM (VALUES ");
            select.append(list);
            select.append(") AS _containerids_ (EntityId)");
        }

        if (useCTE())
        {
            SQLFragment result = new SQLFragment(containerColumnSQL);
            String shortName = null != this.getType() ? this.getType().name() : this.getClass().getSimpleName();
            String cteKey = this.getClass().getName()+":"+ container.getId();
            String token = result.addCommonTableExpression(cteKey, "cte" + shortName + System.identityHashCode(this), select);
            result.append(" IN (SELECT EntityId");
            result.append(" FROM ").append(token);
            result.append(")");
            return result;
        }
        else
        {
            SQLFragment result = new SQLFragment(containerColumnSQL);
            result.append(" IN (");
            result.append(select);
            result.append(")");
            return result;
        }
    }


    public enum Type
    {
        Current("Current folder")
        {
            public ContainerFilter create(User user)
            {
                return CURRENT;
            }
        },
        CurrentWithUser("Current folder with permissions applied to user")
        {
            public ContainerFilter create(User user)
            {
                return new ContainerFilterWithUser(user);
            }
        },
        CurrentAndFirstChildren("Current folder and first children that are not workbooks")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndFirstChildren(user);
            }
        },
        CurrentAndSubfolders("Current folder and subfolders")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndSubfolders(user);
            }
        },
        CurrentAndSiblings("Current folder and siblings")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndSiblings(user);
            }
        },
        CurrentOrParentAndWorkbooks("Current folder and/or parent if the current folder is a workbook, plus all workbooks in this series")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentOrParentAndWorkbooks(user);
            }
        },
        CurrentPlusProject("Current folder and project")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentPlusProject(user);
            }
        },
        CurrentAndParents("Current folder and parent folders")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentAndParents(user);
            }
        },
        Project("Project folder")
        {
            public ContainerFilter create(User user)
            {
                return new Project(user);
            }
        },
        CurrentPlusProjectAndShared("Current folder, project, and Shared project")
        {
            public ContainerFilter create(User user)
            {
                return new CurrentPlusProjectAndShared(user);
            }
        },
        AssayLocation("Current folder, project, and Shared project")
        {
            public ContainerFilter create(User user)
            {
                return new AssayLocation(user);
            }
        },
        WorkbookAndParent("Current workbook and parent")
        {
            public ContainerFilter create(User user)
            {
                return new WorkbookAndParent(user);
            }
        },
        StudyAndSourceStudy("Current study and its source/parent study")
        {
            public ContainerFilter create(User user)
            {
                return new StudyAndSourceStudy(user, false);
            }
        },
        AllFolders("All folders")
        {
            public ContainerFilter create(User user)
            {
                return new AllFolders(user);
            }
        };


        private final String _description;

        private Type(String description)
        {
            _description = description;
        }

        @Override
        public String toString()
        {
            return _description;
        }

        public abstract ContainerFilter create(User user);
    }

    public static final ContainerFilter CURRENT = new ContainerFilter()
    {
        @Override
        public String getCacheKey(Container c)
        {
            return "CURRENT/" + c.getEntityId();
        }

        public Collection<GUID> getIds(Container currentContainer)
        {
            return Collections.singleton(currentContainer.getEntityId());
        }

        @Override
        public String toString()
        {
            return "Current Folder";
        }

        public Type getType()
        {
            return Type.Current;
        }
    };

    /* TODO ContainerFilter -- Consolidate with InternalNoContainerFilter
    /** Use this with extreme caution - it doesn't check permissions */
    public static final ContainerFilter EVERYTHING = new ContainerFilter()
    {
        @Override
        public String getCacheKey(Container c)
        {
            return "EVERYTHING";
        }
        public Collection<GUID> getIds(Container currentContainer)
        {
            return null;
        }

        public Type getType()
        {
            return null;
        }
    };

    public static class ContainerFilterWithUser extends ContainerFilter
    {
        protected final User _user;

        public ContainerFilterWithUser(User user)
        {
            _user = user;
        }

        @Override
        public String getCacheKey(Container c)
        {
            return super.getDefaultCacheKey(c) + "/" + _user.getUserId() + "/";
        }

        public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Container container, Class<? extends Permission> permission, Set<Role> roles)
        {
            return getSQLFragment(schema, new SQLFragment(containerColumnFieldKey.toString()), container, permission, roles, true);
        }

        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            SecurityLogger.indent("ContainerFilter");
            Collection<GUID> ids = getIds(container, permission, roles);
            SecurityLogger.outdent();
            return getSQLFragment(schema, container, containerColumnSQL, ids, allowNulls, getIncludedChildTypes());
        }

        // each ContainerFilterWithUser subclass should override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();
            if (currentContainer.hasPermission(_user, permission, roles))
            {
                result.add(currentContainer.getEntityId());
            }
            return result;
        }

        // If a permission is not explicitly passed, then use ReadPermission by default.  Otherwise, subclasses
        // of ContainerFilterWithUser should override getIds method above that takes a permission.
        public Collection<GUID> getIds(Container currentContainer)
        {
            return getIds(currentContainer, ReadPermission.class, null);
        }

        public Type getType()
        {
            return Type.CurrentWithUser;
        }
    }

    public static class SimpleContainerFilter extends ContainerFilter
    {
        private final Collection<GUID> _ids;

        public SimpleContainerFilter(Collection<Container> containers)
        {
            _ids = toIds(containers);
        }

        @Override
        public String getCacheKey(Container c)
        {
            // container is ignored
            return getClass().getName() + "/" + StringUtils.join(_ids, ";");
        }

        public Collection<GUID> getIds(Container currentContainer)
        {
            return _ids;
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class SimpleContainerFilterWithUser extends ContainerFilterWithUser
    {
        private final Collection<GUID> _ids;

        public SimpleContainerFilterWithUser(User user, Container c)
        {
            this(user, Collections.singleton(c));
        }

        public SimpleContainerFilterWithUser(User user, Collection<Container> containers)
        {
            super(user);
            _ids = toIds(containers);
        }

        @Override
        public String getCacheKey(Container c)
        {
            // container is ignored
            return getClass().getName() + "/" + _user.getUserId() + "/" + StringUtils.join(_ids, ";");
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
        {
            Set<GUID> result;
            result = _ids.stream()
                    .map(ContainerManager::getForId)
                    .filter(Objects::nonNull)
                    .filter(c -> c.hasPermission(_user, permission, roles))
                    .map(Container::getEntityId)
                    .collect(Collectors.toSet());
            return result;
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentPlusExtras extends ContainerFilterWithUser
    {
        private final Collection<Container> _extraContainers;

        public CurrentPlusExtras(User user, Container... extraContainers)
        {
            this(user, Arrays.asList(extraContainers));
        }

        public CurrentPlusExtras(User user, Collection<Container> extraContainers)
        {
            super(user);

            //Note: dont force upstream code to consider this
            _extraContainers = new ArrayList<>(extraContainers);
            _extraContainers.removeIf(c -> c.getContainerType().isDuplicatedInContainerFilter());
        }

        @Override
        public String getCacheKey(Container c)
        {
            StringBuilder sb = new StringBuilder(super.getCacheKey(c));
            _extraContainers.stream().map(Container::getEntityId).forEach(id -> sb.append(id).append("/"));
            return sb.toString();
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            for (Container extraContainer : _extraContainers)
            {
                if (extraContainer.hasPermission(_user, perm, roles))
                {
                    containers.add(extraContainer);
                }
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentAndFirstChildren extends ContainerFilterWithUser
    {
        public CurrentAndFirstChildren(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = new HashSet<>();
            for(Container c : ContainerManager.getChildren(currentContainer, _user, perm, roles))
            {
                if (c.isInFolderNav() && c.hasPermission(_user, perm, roles))
                {
                    containers.add(c);
                }
            }
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndFirstChildren;
        }
    }

    public static class CurrentAndSubfolders extends ContainerFilterWithUser
    {
        public CurrentAndSubfolders(User user)
        {
            super(user);
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            if (_user.hasRootAdminPermission() && container.isRoot())
                return new SQLFragment("1 = 1");
            return super.getSQLFragment(schema,containerColumnSQL, container, permission, roles, allowNulls);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            List<Container> containers = new ArrayList<>(removeDuplicatedContainers(ContainerManager.getAllChildren(currentContainer, _user, perm, roles)));
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndSubfolders;
        }
    }


    public static class CurrentPlusProject extends ContainerFilterWithUser
    {
        public CurrentPlusProject(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, perm, roles))
            {
                containers.add(project);
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentPlusProject;
        }
    }

    public static class CurrentAndParents extends ContainerFilterWithUser
    {
        public CurrentAndParents(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = new HashSet<>();
            do
            {
                if (currentContainer.hasPermission(_user, perm, roles))
                {
                    containers.add(currentContainer);
                }
                currentContainer = currentContainer.getParent();
            }
            while (currentContainer != null && !currentContainer.isRoot());
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentAndParents;
        }
    }

    public static class AssayLocation extends ContainerFilterWithUser
    {
        public AssayLocation(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = currentContainer.getContainersFor(ContainerType.DataType.protocol);
            return containers.stream()
                    .filter(container -> container.hasPermission(_user, perm, roles))
                    .map(Container::getEntityId)
                    .collect(Collectors.toList());
        }

        @Override
        public Type getType()
        {
            return Type.AssayLocation;
        }
    }

    public static class WorkbookAndParent extends ContainerFilterWithUser
    {
        public WorkbookAndParent(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm, roles))
            {
                result.add(currentContainer.getEntityId());
            }
            if (currentContainer.isWorkbook() && currentContainer.getParent().hasPermission(_user, perm, roles))
            {
                result.add(currentContainer.getParent().getEntityId());
            }
            return result;
        }

        @Override
        public Type getType()
        {
            return Type.WorkbookAndParent;
        }
    }

    public static class CurrentOrParentAndWorkbooks extends ContainerFilterWithUser
    {
        public CurrentOrParentAndWorkbooks(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();

            if (currentContainer.isWorkbook())
            {
                if (currentContainer.getParent().hasPermission(_user, perm, roles))
                    result.add(currentContainer.getParent().getEntityId());
            }
            else
            {
                if (currentContainer.hasPermission(_user, perm, roles))
                    result.add(currentContainer.getEntityId());
            }

            return result;
        }

        @Override
        public Type getType()
        {
            return Type.CurrentOrParentAndWorkbooks;
        }
    }

    public static class CurrentAndSiblings extends ContainerFilterWithUser
    {
        public CurrentAndSiblings(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();

            if (currentContainer.isRoot() && currentContainer.hasPermission(_user, perm, roles))
                result.add(currentContainer.getEntityId());  //if not root, we will add the current container below

            Container parent = currentContainer.getParent();
            if(parent != null)
            {
                for(Container c : parent.getChildren())
                {
                    if (c.hasPermission(_user, perm, roles))
                    {
                        result.add(c.getEntityId());
                    }
                }
            }

            return result;
        }

        @Override
        public Type getType()
        {
            return Type.CurrentAndSiblings;
        }
    }

    public static class StudyAndSourceStudy extends ContainerFilterWithUser
    {
        private boolean _skipPermissionChecks;

        public StudyAndSourceStudy(User user, boolean skipPermissionChecks)
        {
            super(user);
            _skipPermissionChecks = skipPermissionChecks;
        }

        @Override
        public String getCacheKey(Container c)
        {
            return super.getCacheKey(c) + _skipPermissionChecks;
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();
            if (_skipPermissionChecks || currentContainer.hasPermission(_user, perm, roles))
            {
                result.add(currentContainer.getEntityId());
            }

            Study study = null;
            StudyService svc = StudyService.get();
            if (svc != null)
                study = svc.getStudy(currentContainer);

            if (study != null && study.isAncillaryStudy())
            {
                Study sourceStudy = study.getSourceStudy();
                if (sourceStudy != null && (_skipPermissionChecks || sourceStudy.getContainer().hasPermission(_user, perm, roles)))
                {
                    result.add(sourceStudy.getContainer().getEntityId());
                }
            }
            return result;
        }

        @Override
        public Type getType()
        {
            return Type.StudyAndSourceStudy;
        }
    }

    public static class Project extends ContainerFilterWithUser
    {
        public Project(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
        {
            Container project = currentContainer.getProject();
            if (null == project || !project.hasPermission(_user, permission, roles))
                return Collections.emptyList();
            return Collections.singleton(project.getEntityId());
        }

        public Type getType()
        {
            return Type.Project;
        }
    }


    public static class CurrentPlusProjectAndShared extends ContainerFilterWithUser
    {
        public CurrentPlusProjectAndShared(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Set<Container> containers = new HashSet<>();
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            Container project = currentContainer.getProject();
            if (project != null && project.hasPermission(_user, perm, roles))
            {
                containers.add(project);
            }
            Container shared = ContainerManager.getSharedContainer();
            if (shared.hasPermission(_user, perm, roles))
            {
                containers.add(shared);
            }
            return toIds(containers);
        }

        public Type getType()
        {
            return Type.CurrentPlusProjectAndShared;
        }
    }

    public static class AllInProject extends ContainerFilterWithUser
    {
        public AllInProject(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            Container project = currentContainer.isProject() ? currentContainer : currentContainer.getProject();
            if (project == null)
            {
                // Don't allow anything
                return Collections.emptySet();
            }
            Set<Container> containers = new HashSet<>(removeDuplicatedContainers(ContainerManager.getAllChildren(project, _user, perm, roles)));
            if (project.hasPermission(_user, perm, roles))
                containers.add(project);
            return toIds(containers);
        }

        public Type getType()
        {
            return null;
        }
    }

    public static class AllFolders extends ContainerFilterWithUser
    {
        public AllFolders(User user)
        {
            super(user);
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            if (_user.hasRootAdminPermission())
            {
                // Don't bother filtering, the user can see everything
                return null;
            }
            List<Container> containers = ContainerManager.getAllChildren(ContainerManager.getRoot(), _user, perm, roles);
            Set<GUID> ids = containers.stream()
                    .filter(c -> !c.isDuplicatedInContainerFilter())
                    .map(Container::getEntityId)
                    .collect(Collectors.toSet());
            if (ContainerManager.getRoot().hasPermission(_user, perm, roles))
            {
                ids.add(ContainerManager.getRoot().getEntityId());
            }
            return ids;
        }

        @Override
        public Type getType()
        {
            return Type.AllFolders;
        }
    }


    public static class InternalNoContainerFilter extends ContainerFilterWithUser
    {
        public InternalNoContainerFilter(User user)
        {
            super(user);
        }

        @Override
        public String getCacheKey(Container c)
        {
            return "EVERYTHING";
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Container container, Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, boolean allowNulls)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public Collection<GUID> getIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            throw new IllegalStateException();
        }

        public Type getType()
        {
            return Type.AllFolders;
        }
    }


    public static Set<GUID> toIds(Collection<Container> containers)
    {
        Set<GUID> ids = new HashSet<>();
        for (Container container : containers)
        {
            ids.add(container.getEntityId());
        }
        return ids;
    }

    public static class ContainerClause extends SimpleFilter.FilterClause
    {
        private final DbSchema _schema;
        private final FieldKey _fieldKey;
        private final ContainerFilter _filter;
        private final Container _container;
        private final Class<? extends Permission> _permission;
        private final Set<Role> _roles;

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter, Container container)
        {
            this(schema, fieldKey, filter, container, null, null);
        }

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter, Container container, Class<? extends Permission> permission,
                               Set<Role> roles)
        {
            _schema = schema;
            _fieldKey = fieldKey;
            _filter = filter;
            _container = container;
            _permission = (permission != null) ? permission : ReadPermission.class;
            _roles = roles;
        }

        @Override
        public List<FieldKey> getFieldKeys()
        {
            return Collections.singletonList(_fieldKey);
        }

        @Override
        public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
        {
            if (_filter instanceof ContainerFilterWithUser)
            {
                ContainerFilterWithUser filter = (ContainerFilterWithUser) _filter;
                return filter.getSQLFragment(_schema, _fieldKey, _container, _permission, _roles);
            }
            return _filter.getSQLFragment(_schema, _fieldKey, _container, columnMap);
        }
    }


    static final Logger _log = Logger.getLogger(ContainerFilter.class);

    // helper so that ContainerFilter logging can be traced using one logger class
    public static void logSetContainerFilter(ContainerFilter cf, String... parts)
    {
        if (!_log.isDebugEnabled())
            return;
        _log.debug("setContainerFilter( " + StringUtils.join(parts, " ") + ", " + String.valueOf(cf) + " )");
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testCacheKey()
        {
            Container home = ContainerManager.getHomeContainer();
            Container root = ContainerManager.getRoot();
            Container shared = ContainerManager.getSharedContainer();
            Container test = JunitUtil.getTestContainer();
            User user = TestContext.get().getUser();

            assertNotEquals(home, test);

            assertEquals(CURRENT.getCacheKey(home), CURRENT.getCacheKey(home));
            assertNotEquals(CURRENT.getCacheKey(home), CURRENT.getCacheKey(test));

            // SimpleContainerFilter ignores argument to getCacheKey() in container
            assertEquals(new SimpleContainerFilterWithUser(user, test).getCacheKey(home), new SimpleContainerFilterWithUser(user, test).getCacheKey(test));
            assertNotEquals(new SimpleContainerFilterWithUser(user, home).getCacheKey(home), new SimpleContainerFilterWithUser(user, test).getCacheKey(home));

            assertEquals(EVERYTHING.getCacheKey(null), new InternalNoContainerFilter(null).getCacheKey(null));
            assertEquals(EVERYTHING.getCacheKey(null), new InternalNoContainerFilter(user).getCacheKey(home));

            assertEquals(new CurrentPlusExtras(user, shared).getCacheKey(home), new CurrentPlusExtras(user, shared).getCacheKey(home));
            assertNotEquals(new CurrentPlusExtras(user, shared).getCacheKey(home), new CurrentPlusExtras(user, shared).getCacheKey(test));
            assertNotEquals(new CurrentPlusExtras(user, shared).getCacheKey(home), new CurrentPlusExtras(user, test).getCacheKey(home));
            assertNotEquals(new CurrentPlusExtras(user, shared).getCacheKey(home), new CurrentPlusExtras(user, home).getCacheKey(shared));

            for (var type : Type.values())
            {
                assertEquals(type.name(), type.create(user).getCacheKey(home), type.create(user).getCacheKey(home));
                assertNotEquals(type.name(), type.create(user).getCacheKey(home), type.create(user).getCacheKey(shared));
            }

            for (var outer : Type.values())
            {
                String outerKey = outer.create(user).getCacheKey(home);
                for (var inner : Type.values())
                {
                    if (outer == inner)
                        continue;
                    assertNotEquals(outerKey, inner.create(user).getCacheKey(home));
                }
            }
        }
    }
}
