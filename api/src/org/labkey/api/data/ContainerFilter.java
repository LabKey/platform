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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.ExprColumn;
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
import org.labkey.api.writer.ContainerUser;

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

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Represents which set of containers should be included when querying for data. In general, the code will
 * default to showing data from just the current container, but alternative ContainerFilters can resolve items
 * in the /Shared project, in parent containers, or a variety of other scoping locations.
 * User: jeckels
 * Date: Nov 3, 2008
 */
public abstract class ContainerFilter
{
    final protected Container _container;
    final protected User _user;

    protected ContainerFilter(Container container, User user)
    {
        _container = container;
        _user = user;
    }

    /**
     * Users of ContainerFilter should use getSQLFragment() or createFilterClause(), not build up their own SQL using
     * the IDs.
     *
     * @return null if no filtering should be done, otherwise the set of valid container ids
     */
    @Nullable
    public abstract Collection<GUID> getIds();

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


    public String getDefaultCacheKey(Container c, User user)
    {
        return getClass().getName() + "/" + (null == c ? "-" : c.getId()) + "/" + (null == user ? "-" : user.getUserId());
    }

    // return a string such that a.getCacheKey().equals(b.getCacheKey()) => a.equals(b)
    // This is purposefully abstract, to force the implementer to consider if getDefaultCacheKey() is appropriate
    public abstract String getCacheKey();

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (null==obj)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContainerFilter other = (ContainerFilter)obj;
        return Objects.equals(_user, other._user) && Objects.equals(_container,other._container);
    }

    /* return null if not matched, if passed directly to getTable() this means use schema default */
    @Nullable
    public static ContainerFilter.Type getType(@Nullable String name)
    {
        try
        {
            return isBlank(name) ? null : Type.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    /**
     * If we can't find the name, we default to CURRENT
     */
    @NotNull
    public static ContainerFilter getContainerFilterByName(@Nullable String name, Container container, @NotNull User user)
    {
        Type type = getType(name);
        if (null == type)
            type = Type.Current;
        return type.create(container, user);
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
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn)
    {
        return new ContainerClause(schema, containerFilterColumn, this);
    }

    /** Create a FilterClause that restricts based on the containers that meet the filter and user that meets the permission*/
    public SimpleFilter.FilterClause createFilterClause(DbSchema schema, FieldKey containerFilterColumn, Class<? extends Permission> permission, Set<Role> roles)
    {
        return new ContainerClause(schema, containerFilterColumn, this, permission, roles);
    }


    /** Create an expression for a WHERE clause */
    public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        ColumnInfo columnInfo = columnMap.get(containerColumnFieldKey);
        SQLFragment sql;
        if (columnInfo != null)
        {
            // NOTE: we really should know the tableAlias here, but we don't, so caller has to guarantee that the columninfo is unambigious
            SQLFragment value = columnInfo.getValueSql(ExprColumn.STR_TABLE_ALIAS);
            sql = new SQLFragment(value.getSQL().replace(ExprColumn.STR_TABLE_ALIAS+".", ""), value.getParams());
        }
        else
        {
            sql = new SQLFragment(containerColumnFieldKey.toString());
        }
        return getSQLFragment(schema, sql);
    }

    /** Create an expression for a WHERE clause */
    @Deprecated
    public final SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Container container)
    {
        assert null==_container || null==container || _container.equals(container);
        return getSQLFragment(schema, containerColumnSQL, true);
    }

    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL)
    {
        return getSQLFragment(schema, containerColumnSQL, true);
    }

    /**
     * Create an expression for a WHERE clause
     * Generally parameters are preferred, but can cause perf problems in certain cases
     * @param allowNulls - if looking at ALL rows, whether to allow nulls in the Container column
     */
    public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, boolean allowNulls)
    {
        SecurityLogger.indent("ContainerFilter");
        Collection<GUID> ids = getIds();
        SecurityLogger.outdent();
        return getSQLFragment(schema, _container, containerColumnSQL, ids, allowNulls, getIncludedChildTypes());
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

        // Issue 39891: Biologics: slow page loads for sample/assay and assay/results
        // When including child containers by type, check if any of the containers
        // have children that match the types. If there are no children that match
        // the set of included types, we can simplify the query to a simple IN clause
        // instead of joining to core.Containers and filtering by container type.
        final Set<String> finalIncludedChildTypes = includedChildTypes;
        List<Container> containers = ids.stream()
                .map(ContainerManager::getForId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        boolean hasNoSpecialChildren = includedChildTypes.isEmpty() ||
                containers.stream().noneMatch(c -> c.hasChildrenOfAnyType(finalIncludedChildTypes));

        if (hasNoSpecialChildren)
        {
            if (containers.isEmpty())
            {
                return new SQLFragment("1 = 0");
            }
            else
            {
                // For optimization, turn off join to core.containers to filter by container type
                includedChildTypes = Collections.emptySet();
            }
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

    public interface Factory
    {
        ContainerFilter create(Container c, User u);
    }

    public enum Type implements Factory
    {
        Current("Current folder")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentContainerFilter(c);
                    }
                },
        CurrentWithUser("Current folder with permissions applied to user")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new ContainerFilterWithPermission(c, user);
                    }
                },
        CurrentAndFirstChildren("Current folder and first children that are not workbooks")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentAndFirstChildren(c, user);
                    }
                },
        CurrentAndSubfolders("Current folder and subfolders")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentAndSubfolders(c, user);
                    }
                },
        CurrentAndSubfoldersPlusShared("Current folder, subfolders, and Shared project")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentAndSubfoldersPlusShared(c, user);
                    }
                },
        CurrentAndSiblings("Current folder and siblings")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentAndSiblings(c, user);
                    }
                },
        CurrentOrParentAndWorkbooks("Current folder and/or parent if the current folder is a workbook, plus all workbooks in this series")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentOrParentAndWorkbooks(c, user);
                    }
                },
        CurrentPlusProject("Current folder and project")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentPlusProject(c, user);
                    }
                },
        CurrentAndParents("Current folder and parent folders")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentAndParents(c, user);
                    }
                },
        Project("Project folder")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new Project(c, user);
                    }
                },
        CurrentPlusProjectAndShared("Current folder, project, and Shared project")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new CurrentPlusProjectAndShared(c, user);
                    }
                },
        AssayLocation("Current folder, project, and Shared project")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new AssayLocation(c, user);
                    }
                },
        WorkbookAndParent("Current workbook and parent")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new WorkbookAndParent(c, user);
                    }
                },
        StudyAndSourceStudy("Current study and its source/parent study")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new StudyAndSourceStudy(c, user, false);
                    }
                },
        AllFolders("All folders")
                {
                    @Override
                    public ContainerFilter create(Container c, User user)
                    {
                        return new AllFolders(user);
                    }
                },
        AllInProject("All folders in current project")
                {
                    @Override
                    public ContainerFilter create(Container container, User user)
                    {
                        return new AllInProject(container, user);
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

        @Override
        public abstract ContainerFilter create(Container container, User user);

        public ContainerFilter create(ContainerUser cu)
        {
            return create(cu.getContainer(), cu.getUser());
        }
    }

    // short for ContainerFilter.Type.Current.create(container, null)
    public static ContainerFilter current(Container c)
    {
        return new CurrentContainerFilter(c);
    }

    public static class CurrentContainerFilter extends ContainerFilter
    {
        CurrentContainerFilter(Container c)
        {
            // CurrentContainerFilter does not validate permission
            super(c,null);
            Objects.requireNonNull(c);
        }

        @Override
        public String getCacheKey()
        {
            return "CURRENT/" + _container.getEntityId();
        }

        @Override
        public Collection<GUID> getIds()
        {
            return Collections.singleton(_container.getEntityId());
        }

        @Override
        public String toString()
        {
            return "Current Folder";
        }

        @Override
        public Type getType()
        {
            return Type.Current;
        }
    };

    /* TODO ContainerFilter -- Consolidate with InternalNoContainerFilter
    /** Use this with extreme caution - it doesn't check permissions */
    public static final ContainerFilter EVERYTHING = new InternalNoContainerFilter();

    public static class ContainerFilterWithPermission extends ContainerFilter
    {
        public ContainerFilterWithPermission(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public String getCacheKey()
        {
            return getDefaultCacheKey(_container, _user);
        }

        public final SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Class<? extends Permission> permission, Set<Role> roles)
        {
            return getSQLFragment(schema, new SQLFragment(containerColumnFieldKey.toString()), permission, roles, true);
        }

        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            SecurityLogger.indent("ContainerFilter");
            Collection<GUID> ids;
            if (permission == ReadPermission.class && null == roles)
                ids = getIds();
            else
                 ids = generateIds(_container, permission, roles);
            SecurityLogger.outdent();
            return getSQLFragment(schema, _container, containerColumnSQL, ids, allowNulls, getIncludedChildTypes());
        }

        /** return null means return all rows (1=1),  empty collection means return no rows (1=0) */
        @Nullable
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
        {
            Set<GUID> result = new HashSet<>();
            if (currentContainer.hasPermission(_user, permission, roles))
            {
                result.add(currentContainer.getEntityId());
            }
            return result;
        }

        Collection<GUID> _cached = null;

        // If a permission is not explicitly passed, then use ReadPermission by default.  Otherwise, subclasses
        // of ContainerFilterWithUser should override generateIds method above that takes a permission.
        @Override
        public final Collection<GUID> getIds()
        {
            if (null != _container)
            {
                if (null == _cached)
                    _cached = generateIds(_container, ReadPermission.class, null);
                return _cached;
            }
            return generateIds(_container, ReadPermission.class, null);
        }

        @Override
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
            super(null, null);
            _ids = toIds(containers);
        }

        @Override
        public String getCacheKey()
        {
            // container is ignored
            return getClass().getName() + "/" + StringUtils.join(_ids, ";");
        }

        @Override
        public Collection<GUID> getIds()
        {
            return _ids;
        }

        @Override
        public Type getType()
        {
            return null;
        }
    }

    public static class SimpleContainerFilterWithUser extends ContainerFilterWithPermission
    {
        private final Collection<GUID> _ids;

        public SimpleContainerFilterWithUser(User user, Container c)
        {
            this(user, Collections.singleton(c));
        }

        public SimpleContainerFilterWithUser(User user, Collection<Container> containers)
        {
            super(null, user);
            _ids = toIds(containers);
        }

        @Override
        public String getCacheKey()
        {
            // container is ignored
            return getClass().getName() + "/" + _user.getUserId() + "/" + StringUtils.join(_ids, ";");
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
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

        @Override
        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentPlusExtras extends ContainerFilterWithPermission
    {
        private final Collection<Container> _extraContainers;

        public CurrentPlusExtras(Container current, User user, Container... extraContainers)
        {
            this(current, user, Arrays.asList(extraContainers));
        }
        public CurrentPlusExtras(Container current, User user, Collection<Container> extraContainers)
        {
            super(current, user);

            //Note: dont force upstream code to consider this
            _extraContainers = new ArrayList<>(extraContainers);
            _extraContainers.removeIf(c -> c.getContainerType().isDuplicatedInContainerFilter());
        }

        @Override
        public String getCacheKey()
        {
            StringBuilder sb = new StringBuilder(super.getCacheKey());
            _extraContainers.stream().map(Container::getRowId).forEach(id -> sb.append(id).append("/"));
            return sb.toString();
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return null;
        }
    }

    public static class CurrentAndFirstChildren extends ContainerFilterWithPermission
    {
        CurrentAndFirstChildren(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return Type.CurrentAndFirstChildren;
        }
    }

    public static class CurrentAndSubfolders extends ContainerFilterWithPermission
    {
        CurrentAndSubfolders(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            if (_user.hasRootAdminPermission() && _container.isRoot())
                return new SQLFragment("1 = 1");
            return super.getSQLFragment(schema, containerColumnSQL, permission, roles, allowNulls);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

            List<Container> containers = new ArrayList<>(removeDuplicatedContainers(ContainerManager.getAllChildren(currentContainer, _user, perm, roles)));
            if (currentContainer.hasPermission(_user, perm, roles))
                containers.add(currentContainer);
            return toIds(containers);
        }

        @Override
        public Type getType()
        {
            return Type.CurrentAndSubfolders;
        }
    }


    public static class CurrentPlusProject extends ContainerFilterWithPermission
    {
        CurrentPlusProject(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return Type.CurrentPlusProject;
        }
    }

    public static class CurrentAndParents extends ContainerFilterWithPermission
    {
        public CurrentAndParents(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return Type.CurrentAndParents;
        }
    }

    public static class AssayLocation extends ContainerFilterWithPermission
    {
        public AssayLocation(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

    public static class WorkbookAndParent extends ContainerFilterWithPermission
    {
        public WorkbookAndParent(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

    public static class CurrentOrParentAndChildrenOfType extends ContainerFilterWithPermission
    {
        Set<String> _includedChildTypes;

        public CurrentOrParentAndChildrenOfType(Container c, User user, Set<String> includedChildTypes)
        {
            super(c, user);
            _includedChildTypes = includedChildTypes;
        }

        @Override
        public Set<String> getIncludedChildTypes()
        {
            return _includedChildTypes;
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

            Set<GUID> result = new HashSet<>();

            if (_includedChildTypes.contains(currentContainer.getType()))
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
            return null;
        }
    }

    public static class CurrentOrParentAndWorkbooks extends CurrentOrParentAndChildrenOfType
    {
        public CurrentOrParentAndWorkbooks(Container c, User user)
        {
            super(c, user, Collections.singleton(WorkbookContainerType.NAME));
        }

        @Override
        public Type getType()
        {
            return Type.CurrentOrParentAndWorkbooks;
        }
    }

    public static class CurrentAndSiblings extends ContainerFilterWithPermission
    {
        CurrentAndSiblings(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

    public static class StudyAndSourceStudy extends ContainerFilterWithPermission
    {
        private boolean _skipPermissionChecks;

        public StudyAndSourceStudy(Container c, User user, boolean skipPermissionChecks)
        {
            super(c, user);
            _skipPermissionChecks = skipPermissionChecks;
        }

        @Override
        public String getCacheKey()
        {
            return super.getCacheKey() + _skipPermissionChecks;
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

    public static class Project extends ContainerFilterWithPermission
    {
        public Project(Container c, User user)
        {
            super(null==c?null:c.isRoot()?c:c.getProject(), user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> permission, Set<Role> roles)
        {
            Container project = currentContainer.getProject();
            if (null == project || !project.hasPermission(_user, permission, roles))
                return Collections.emptyList();
            return Collections.singleton(project.getEntityId());
        }

        @Override
        public Type getType()
        {
            return Type.Project;
        }
    }


    public static class CurrentPlusProjectAndShared extends ContainerFilterWithPermission
    {
        public CurrentPlusProjectAndShared(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return Type.CurrentPlusProjectAndShared;
        }
    }


    public static class CurrentAndSubfoldersPlusShared extends CurrentAndSubfolders
    {
        public CurrentAndSubfoldersPlusShared(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public @Nullable Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            var containers = super.generateIds(currentContainer, perm, roles);
            var shared = ContainerManager.getSharedContainer();

            if (shared.hasPermission(_user, perm, roles))
                containers.add(shared.getEntityId());

            return containers;
        }

        @Override
        public Type getType()
        {
            return Type.CurrentAndSubfoldersPlusShared;
        }
    }


    public static class AllInProject extends ContainerFilterWithPermission
    {
        public AllInProject(Container c, User user)
        {
            super(c, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            assert null == _container || _container.equals(currentContainer);

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

        @Override
        public Type getType()
        {
            return Type.AllInProject;
        }
    }

    public static class AllFolders extends ContainerFilterWithPermission
    {
        public AllFolders(User user)
        {
            super(null, user);
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
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


    public static class InternalNoContainerFilter extends ContainerFilterWithPermission
    {
        public InternalNoContainerFilter()
        {
            super(null, null);
        }

        @Override
        public String getCacheKey()
        {
            return "EVERYTHING";
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, FieldKey containerColumnFieldKey, Map<FieldKey, ? extends ColumnInfo> columnMap)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, boolean allowNulls)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public SQLFragment getSQLFragment(DbSchema schema, SQLFragment containerColumnSQL, Class<? extends Permission> permission, Set<Role> roles, boolean allowNulls)
        {
            return new SQLFragment("1=1");
        }

        @Override
        public Collection<GUID> generateIds(Container currentContainer, Class<? extends Permission> perm, Set<Role> roles)
        {
            return null;
        }

        @Override
        public Type getType()
        {
            return null;
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
        private final Class<? extends Permission> _permission;
        private final Set<Role> _roles;

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter)
        {
            this(schema, fieldKey, filter, null, null);
        }

        public ContainerClause(DbSchema schema, FieldKey fieldKey, ContainerFilter filter, Class<? extends Permission> permission, Set<Role> roles)
        {
            _schema = schema;
            _fieldKey = fieldKey;
            _filter = filter;
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
            if (_filter instanceof ContainerFilterWithPermission)
            {
                ContainerFilterWithPermission filter = (ContainerFilterWithPermission) _filter;
                return filter.getSQLFragment(_schema, _fieldKey, _permission, _roles);
            }
            return _filter.getSQLFragment(_schema, _fieldKey, columnMap);
        }
    }


    static final Logger _log = LogManager.getLogger(ContainerFilter.class);

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
            Container shared = ContainerManager.getSharedContainer();
            Container test = JunitUtil.getTestContainer();
            User user = TestContext.get().getUser();

            assertNotEquals(home, test);

            assertEquals(current(home).getCacheKey(), current(home).getCacheKey());
            assertNotEquals(current(home).getCacheKey(), current(test).getCacheKey());

            assertEquals(EVERYTHING.getCacheKey(), new InternalNoContainerFilter().getCacheKey());

            assertEquals(new CurrentPlusExtras(home, user, shared).getCacheKey(), new CurrentPlusExtras(home, user, shared).getCacheKey());
            assertNotEquals(new CurrentPlusExtras(home, user, shared).getCacheKey(), new CurrentPlusExtras(test, user, shared).getCacheKey());
            assertNotEquals(new CurrentPlusExtras(home, user, shared).getCacheKey(), new CurrentPlusExtras(home, user, test).getCacheKey());
            assertNotEquals(new CurrentPlusExtras(home, user, shared).getCacheKey(), new CurrentPlusExtras(shared, user, home).getCacheKey());

            for (var type : Type.values())
            {
                assertEquals(type.name(), type.create(home, user).getCacheKey(), type.create(home, user).getCacheKey());
                if (type == Type.AllFolders)
                    assertEquals(type.name(), type.create(home, user).getCacheKey(), type.create(shared, user).getCacheKey());
                else
                    assertNotEquals(type.name(), type.create(home, user).getCacheKey(), type.create(shared, user).getCacheKey());
            }

            for (var outer : Type.values())
            {
                String outerKey = outer.create(home, user).getCacheKey();
                for (var inner : Type.values())
                {
                    if (outer == inner)
                        continue;
                    assertNotEquals(outerKey, inner.create(home, user).getCacheKey());
                }
            }
        }
    }
}
