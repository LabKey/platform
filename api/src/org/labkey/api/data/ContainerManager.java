/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.ConcurrentHashSet;
import org.labkey.api.data.Container.ContainerException;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.event.PropertyChange;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.view.Portal;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * This class manages a hierarchy of collections, backed by a database table called Containers.
 * Containers are named using filesystem-like paths e.g. /proteomics/comet/.  Each path
 * maps to a UID and set of permissions.  The current security scheme allows ACLs
 * to be specified explicitly on the directory or completely inherited.  ACLs are not combined.
 * <p/>
 * NOTE: we act like java.io.File().  Paths start with forward-slash, but do not end with forward-slash.
 * The root container's name is '/'.  This means that it is not always the case that
 * me.getPath() == me.getParent().getPath() + "/" + me.getName()
 *
 * The synchronization goals are to keep invalid containers from creeping into the cache. For example, once
 * a container is deleted, it should never get put back in the cache. We accomplish this by synchronizing on
 * the removal from the cache, and the database lookup/cache insertion. While a container is in the middle
 * of being deleted, it's OK for other clients to see it because FKs enforce that it's always internally
 * consistent, even if some of the data has already been deleted.
 */
public class ContainerManager
{
    private static final Logger LOG = Logger.getLogger(ContainerManager.class);
    private static final CoreSchema CORE = CoreSchema.getInstance();

    private static final String CONTAINER_PREFIX = ContainerManager.class.getName() + "/";
    private static final String CONTAINER_CHILDREN_PREFIX = ContainerManager.class.getName() + "/children/";
    private static final String CONTAINER_ALL_CHILDREN_PREFIX = ContainerManager.class.getName() + "/children/*/";
    private static final String PROJECT_LIST_ID = "Projects";

    public static final String HOME_PROJECT_PATH = "/home";

    private static final StringKeyCache<Object> CACHE = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Containers");
    private static final ReentrantLock DATABASE_QUERY_LOCK = new ReentrantLock();
    public static final String FOLDER_TYPE_PROPERTY_SET_NAME = "folderType";
    public static final String FOLDER_TYPE_PROPERTY_NAME = "name";
    public static final String FOLDER_TYPE_PROPERTY_TABTYPE_OVERRIDDEN = "ctFolderTypeOverridden";
    public static final String TABFOLDER_CHILDREN_DELETED = "tabChildrenDeleted";

    /** enum of properties you can see in property change events */
    public enum Property
    {
        Name,
        Parent,
        Policy,
        WebRoot,
        AttachmentDirectory,
        PipelineRoot,
        Title,
        Description,
        SiteRoot,
        StudyChange,
        UserFilesRoot,
        EndpointDirectory,
    }

    static Path makePath(Container parent, String name)
    {
        if (null == parent)
            return new Path(name);
        return parent.getParsedPath().append(name, true);
    }

    public static Container createMockContainer()
    {
        return new Container(null, "MockContainer", "01234567-8901-2345-6789-012345678901", 99999999, 0, new Date(), User.guest.getUserId(), true);
    }

    private static Container createRoot()
    {
        Map<String, Object> m = new HashMap<>();
        m.put("Parent", null);
        m.put("Name", "");
        Table.insert(null, CORE.getTableInfoContainers(), m);

        return getRoot();
    }


    private static int getNewChildSortOrder(Container parent)
    {
        List<Container> children = parent.getChildren();
        if (children != null)
        {
            for (Container child : children)
            {
                if (child.getSortOrder() != 0)
                {
                    // custom sorting applies: put new container at the end.
                    return children.size();
                }
            }
        }
        // we're sorted alphabetically
        return 0;
    }

    // TODO: Make private and force callers to use ensureContainer instead?
    // TODO: Handle root creation here?
    public static Container createContainer(Container parent, String name)
    {
        return createContainer(parent, name, null, null, Container.TYPE.normal, null);
    }

    public static final String WORKBOOK_DBSEQUENCE_NAME = "org.labkey.api.data.Workbooks";

    // TODO: Pass in FolderType (separate from the container type of workbook, etc) and transact it with container creation?
    public static Container createContainer(Container parent, String name, @Nullable String title, @Nullable String description, Container.TYPE type, User user)
    {
        // NOTE: Running outside a tx doesn't seem to be necessary.
//        if (CORE.getSchema().getScope().isTransactionActive())
//            throw new IllegalStateException("Transaction should not be active");

        int sortOrder;

        if (Container.TYPE.workbook == type)
        {
            //parent must be normal
            if (Container.TYPE.normal != parent.getType())
                throw new IllegalArgumentException("Parent of a workbook must be a non-workbook container!");

            sortOrder = DbSequenceManager.get(parent, WORKBOOK_DBSEQUENCE_NAME).next();

            if (name == null)
            {
                //default workbook names are simply "<sortorder>"
                name = String.valueOf(sortOrder);
            }
        }
        else
        {
            sortOrder = getNewChildSortOrder(parent);
        }

        //parent must be normal (not workbook or container tab)
        if (Container.TYPE.normal != parent.getType())
            throw new IllegalArgumentException("Parent of a container must not be a container tab or workbook!");

        StringBuilder error = new StringBuilder();
        if (!Container.isLegalName(name, parent.isRoot(), error))
            throw new IllegalArgumentException(error.toString());

        if (!Container.isLegalTitle(title, error))
            throw new IllegalArgumentException(error.toString());

        Path path = makePath(parent, name);
        SQLException sqlx = null;
        Map<String, Object> insertMap = null;

        try
        {
            HashMap<String, Object> m = new HashMap<>();
            m.put("Parent", parent.getId());
            m.put("Name", name);
            m.put("Title", title);
            m.put("SortOrder", sortOrder);
            if (null != description)
                m.put("Description", description);
            m.put("Type", type);
            insertMap = Table.insert(user, CORE.getTableInfoContainers(), m);
        }
        catch (RuntimeSQLException x)
        {
            if (!x.isConstraintException())
                throw x;
            sqlx = x.getSQLException();
        }

        _clearChildrenFromCache(parent);

        Container c = insertMap == null ? null  : getForId((String) insertMap.get("EntityId"));

        if (null == c)
        {
            if (null != sqlx)
                throw new RuntimeSQLException(sqlx);
            else
                throw new RuntimeException("Container for path '" + path + "' was not created properly.");
        }

        //workbooks inherit perms from their parent so don't create a policy if this is a workbook     TODO; and container tabs???
        if (Container.TYPE.normal == type)
            SecurityManager.setAdminOnlyPermissions(c);

        _removeFromCache(c); // seems odd, but it removes c.getProject() which clears other things from the cache

        // init the list of active modules in the Container
        c.getActiveModules(true, true, user);

        if (c.isProject())
        {
            SecurityManager.createNewProjectGroups(c);
        }
        else
        {
            //If current user is NOT a site or folder admin, or the project has been explicitly set to have
            // new subfolders inherit permissions, we'll inherit permissions (otherwise they would not be able to see the folder)
            Integer adminGroupId = null;
            if (null != c.getProject())
                adminGroupId = SecurityManager.getGroupId(c.getProject(), "Administrators", false);
            boolean isProjectAdmin = (null != adminGroupId) && user != null && user.isInGroup(adminGroupId.intValue());
            if (!isProjectAdmin && user != null && !user.hasRootAdminPermission() || SecurityManager.shouldNewSubfoldersInheritPermissions(c.getProject()))
                SecurityManager.setInheritPermissions(c);
        }

        // NOTE parent caches some info about children (e.g. hasWorkbookChildren)
        // since mutating cached objects is frowned upon, just uncache parent
        // CONSIDER: we could perhaps only uncache if the child is a workbook, but I think this reasonable
        _removeFromCache(parent);

        fireCreateContainer(c, user);
        return c;
    }

    public static void refreshFolderType(Container c, User user, BindException errors)
    {
        setFolderType(c, c.getFolderType(), user, errors, true);
    }

    public static void setFolderType(Container c, FolderType folderType, User user, BindException errors)
    {
        setFolderType(c, folderType, user, errors, false);
    }

    private static void setFolderType(Container c, FolderType folderType, User user, BindException errors, boolean forceSet)
    {
        FolderType oldType = c.getFolderType();

        if (!forceSet && folderType.equals(oldType))
            return;

        List<String> errorStrings = new ArrayList<>();

        if (!c.isProject() && folderType.isProjectOnlyType())
            errorStrings.add("Cannot set a subfolder to " + folderType.getName() + " because it is a project-only folder type.");

        // Check for any containers that need to be moved into container tabs
        if (errorStrings.isEmpty() && folderType.hasContainerTabs())
        {
            List<Container> childTabFoldersNonMatchingTypes = new ArrayList<>();
            List<Container> containersBecomingTabs = findAndCheckContainersMatchingTabs(c, folderType, childTabFoldersNonMatchingTypes, errorStrings);

            if (errorStrings.isEmpty())
            {
                if (!containersBecomingTabs.isEmpty())
                {
                    // Make containers tab container; Folder tab will find them by name
                    try (DbScope.Transaction transaction = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
                    {
                        for (Container container : containersBecomingTabs)
                            updateType(container, Container.TYPE.tab.toString(), user);

                        transaction.commit();
                    }
                }

                // Check these and change type unless they were overridden explicitly
                for (Container container : childTabFoldersNonMatchingTypes)
                {
                    if (!isContainerTabTypeOverridden(container))
                    {
                        FolderTab newTab = folderType.findTab(container.getName());
                        assert null != newTab;          // There must be a tab because it caused the container to get into childTabFoldersNonMatchingTypes
                        FolderType newType = newTab.getFolderType();
                        if (null == newType)
                            newType = FolderType.NONE;      // default to NONE
                        setFolderType(container, newType, user, errors);
                    }
                }
            }
        }

        if (errorStrings.isEmpty())
        {
            oldType.unconfigureContainer(c, user);
            PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, FOLDER_TYPE_PROPERTY_SET_NAME, true);
            props.put(FOLDER_TYPE_PROPERTY_NAME, folderType.getName());

            if (c.isContainerTab())
            {
                Boolean containerTabTypeOverridden = false;
                FolderTab tab = c.getParent().getFolderType().findTab(c.getName());
                if (null != tab && !folderType.equals(tab.getFolderType()))
                    containerTabTypeOverridden = true;
                props.put(FOLDER_TYPE_PROPERTY_TABTYPE_OVERRIDDEN, containerTabTypeOverridden.toString());
            }
            props.save();

            folderType.configureContainer(c, user);         // Configure new only after folder type has been changed

            // TODO: Not needed? I don't think we've changed the container's state.
            _removeFromCache(c);
        }
        else
        {
            for (String errorString : errorStrings)
                errors.reject(SpringActionController.ERROR_MSG, errorString);
        }
    }

    public static void checkContainerValidity(Container c) throws ContainerException
    {
        // Check container for validity; in rare cases user may have changed their custom folderType.xml and caused
        // duplicate subfolders (same name) to exist
        // Get list of child containers that are not container tabs, but match container tabs; these are bad
        FolderType folderType = ContainerManager.getFolderType(c);
        List<String> errorStrings = new ArrayList<>();
        List<Container> childTabFoldersNonMatchingTypes = new ArrayList<>();
        List<Container> containersMatchingTabs = ContainerManager.findAndCheckContainersMatchingTabs(c, folderType, childTabFoldersNonMatchingTypes, errorStrings);
        if (!containersMatchingTabs.isEmpty())
        {
            throw new Container.ContainerException("Folder " + c.getPath() +
                   " has a subfolder with the same name as a container tab folder, which is an invalid state." +
                   " This may have been caused by changing the folder type's tabs after this folder was set to its folder type." +
                   " An administrator should either delete the offending subfolder or change the folder's folder type.\n");
        }
    }

    public static List<Container> findAndCheckContainersMatchingTabs(Container c, FolderType folderType,
                                                                     List<Container> childTabFoldersNonMatchingTypes, List<String> errorStrings)
    {
        List<Container> containersMatchingTabs = new ArrayList<>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (folderTab.getTabType() == FolderTab.TAB_TYPE.Container)
            {
                for (Container child : c.getChildren())
                {
                    if (child.getName().equalsIgnoreCase(folderTab.getName()))
                    {
                        if (!child.getFolderType().getName().equalsIgnoreCase(folderTab.getFolderTypeName()))
                        {
                            if (child.isContainerTab())
                                childTabFoldersNonMatchingTypes.add(child);     // Tab type doesn't match child tab folder
                            else
                                errorStrings.add("Child folder " + child.getName() +
                                    " matches container tab, but folder type " + child.getFolderType().getName() + " doesn't match tab's folder type " +
                                    folderTab.getFolderTypeName() + ".");
                        }

                        int childCount = child.getChildren().size();
                        if (childCount > 0)
                        {
                            errorStrings.add("Child folder " + child.getName() +
                                    " matches container tab, but cannot be converted to a tab folder because it has " + childCount + " children.");
                        }

                        if (child.isWorkbook())
                        {
                            errorStrings.add("Child folder " + child.getName() +
                                    " matches container tab, but cannot be converted to a tab folder because it is a workbook.");
                        }

                        if (!child.isContainerTab())
                            containersMatchingTabs.add(child);

                        break;  // we found name match; can't be another
                    }
                }
            }
        }
        return containersMatchingTabs;
    }

    private static final Set<Container> containersWithBadFolderTypes = new ConcurrentHashSet<>();

    @NotNull
    public static FolderType getFolderType(Container c)
    {
        String name = getFolderTypeName(c);
        FolderType folderType;

        if (null != name)
        {
            folderType = FolderTypeManager.get().getFolderType(name);

            if (null == folderType)
            {
                // If we're upgrading then folder types won't be defined yet... don't warn in that case.
                if (!ModuleLoader.getInstance().isUpgradeInProgress() &&
                    !ModuleLoader.getInstance().isUpgradeRequired() &&
                    !containersWithBadFolderTypes.contains(c))
                {
                    LOG.warn("No such folder type " + name + " for folder " + c.toString());
                    containersWithBadFolderTypes.add(c);
                }

                folderType = FolderType.NONE;
            }
        }
        else
            folderType = FolderType.NONE;

        return folderType;
    }

    /**
     * Most code should call getFolderType() instead.
     * Useful for finding the name of the folder type BEFORE startup is complete, so the FolderType itself
     * may not be available.
     */
    @Nullable
    public static String getFolderTypeName(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, FOLDER_TYPE_PROPERTY_SET_NAME);
        return props.get(FOLDER_TYPE_PROPERTY_NAME);
    }

    @NotNull
    public static Map<String, Integer> getFolderTypeNameContainerCounts(Container root)
    {
        Map<String, Integer> nameCounts = new TreeMap<>();
        for (Container c : getAllChildren(root))
        {
            Integer count = nameCounts.get(c.getFolderType().getName());
            if (null == count)
            {
                count = new Integer(0);
            }
            nameCounts.put(c.getFolderType().getName(), ++count);
        }
        return nameCounts;
    }

    public static boolean isContainerTabTypeThisOrChildrenOverridden(Container c)
    {
        if (isContainerTabTypeOverridden(c))
            return true;
        if (c.getFolderType().hasContainerTabs())
        {
            for (Container child : c.getChildren())
            {
                if (child.isContainerTab() && isContainerTabTypeOverridden(child))
                    return true;
            }
        }
        return false;
    }

    public static boolean isContainerTabTypeOverridden(Container c)
    {
        Map<String, String> props = PropertyManager.getProperties(c, FOLDER_TYPE_PROPERTY_SET_NAME);
        String overridden = props.get(FOLDER_TYPE_PROPERTY_TABTYPE_OVERRIDDEN);
        return (null != overridden) && overridden.equalsIgnoreCase("true");
    }

    private static void setContainerTabDeleted(Container c, String tabName, String folderTypeName)
    {
        // Add prop in this category <tabName, folderTypeName>
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, TABFOLDER_CHILDREN_DELETED, true);
        props.put(getDeletedTabKey(tabName, folderTypeName), "true");
        props.save();
    }

    public static void clearContainerTabDeleted(Container c, String tabName, String folderTypeName)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(c, TABFOLDER_CHILDREN_DELETED, true);
        String key = getDeletedTabKey(tabName, folderTypeName);
        if (props.containsKey(key))
        {
            props.remove(key);
            props.save();
        }
    }

    public static boolean hasContainerTabBeenDeleted(Container c, String tabName, String folderTypeName)
    {
        // We keep arbitrary number of deleted children tabs using suffix 0, 1, 2....
        Map<String, String> props = PropertyManager.getProperties(c, ContainerManager.TABFOLDER_CHILDREN_DELETED);
        return props.containsKey(getDeletedTabKey(tabName, folderTypeName));
    }

    private static String getDeletedTabKey(String tabName, String folderTypeName)
    {
        return tabName + "-TABDELETED-FOLDER-" + folderTypeName;
    }

    public static Container ensureContainer(String path)
    {
        return ensureContainer(Path.parse(path), null);
    }

    public static Container ensureContainer(Path path)
    {
        return ensureContainer(path, null);
    }

    public static Container ensureContainer(Path path, User user)
    {
        // NOTE: Running outside a tx doesn't seem to be necessary.
//        if (CORE.getSchema().getScope().isTransactionActive())
//            throw new IllegalStateException("Transaction should not be active");

        Container c = null;

        try
        {
            c = getForPath(path);
        }
        catch (RootContainerException e)
        {
            // Ignore this -- root doesn't exist yet
        }

        if (null == c)
        {
            if (0 == path.size())
                c = createRoot();
            else
            {
                Path parentPath = path.getParent();
                c = ensureContainer(parentPath, user);
                if (null == c)
                    return null;
                c = createContainer(c, path.getName(), null, null, Container.TYPE.normal, user);
            }
        }
        return c;
    }


    public static Container ensureContainer(Container parent, String name)
    {
        // NOTE: Running outside a tx doesn't seem to be necessary.
//        if (CORE.getSchema().getScope().isTransactionActive())
//            throw new IllegalStateException("Transaction should not be active");

        Container c = null;

        try
        {
            c = getForPath(makePath(parent,name));
        }
        catch (RootContainerException e)
        {
            // Ignore this -- root doesn't exist yet
        }

        if (null == c)
        {
            c = createContainer(parent, name);
        }
        return c;
    }

    public static void updateDescription(Container container, String description, User user)
            throws ValidationException
    {
        ColumnValidators.validate(CORE.getTableInfoContainers().getColumn("Title"), null, 1, description);

        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Description=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema()).execute(sql, description, container.getRowId());
        
        String oldValue = container.getDescription();
        _removeFromCache(container);
        container = getForRowId(container.getRowId());
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(container, user, Property.Description, oldValue, description);
        firePropertyChangeEvent(evt);
    }

    public static void updateSearchable(Container container, boolean searchable, User user)
    {
        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Searchable=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema()).execute(sql, searchable, container.getRowId());

        _removeFromCache(container);
    }

    public static void updateType(Container container, String newType, User user)
    {
        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Type=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema()).execute(sql, newType, container.getRowId());

        _removeFromCache(container);
    }

    public static void updateTitle(Container container, String title, User user)
            throws ValidationException
    {
        ColumnValidators.validate(CORE.getTableInfoContainers().getColumn("Title"), null, 1, title);

        //For some reason there is no primary key defined on core.containers
        //so we can't use Table.update here
        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(CORE.getTableInfoContainers());
        sql.append(" SET Title=? WHERE RowID=?");
        new SqlExecutor(CORE.getSchema()).execute(sql, title, container.getRowId());

        _removeFromCache(container);
        String oldValue = container.getTitle();
        container = getForRowId(container.getRowId());
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(container, user, Property.Title, oldValue, title);
        firePropertyChangeEvent(evt);
    }

    public static final String SHARED_CONTAINER_PATH = "/Shared";

    @NotNull
    public static Container getSharedContainer()
    {
        return ensureContainer(SHARED_CONTAINER_PATH);
    }

    public static List<Container> getChildren(Container parent)
    {
        return new ArrayList<>(getChildrenMap(parent).values());
    }


    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        return getChildren(parent, u, perm, null, true);
    }

    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm, Set<Role> roles)
    {
        return getChildren(parent, u, perm, roles, true);
    }

    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm, boolean includeWorkbooksAndTabs)
    {
        return getChildren(parent, u, perm, null, includeWorkbooksAndTabs);
    }

    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm, Set<Role> roles, boolean includeWorkbooksAndTabs)
    {
        List<Container> children = new ArrayList<>();
        for (Container child : getChildrenMap(parent).values())
            if (child.hasPermission(u, perm, roles) && (includeWorkbooksAndTabs || !child.isWorkbookOrTab()))
                children.add(child);

        return children;
    }

    public static List<Container> getAllChildren(Container parent, User u)
    {
        return getAllChildren(parent, u, ReadPermission.class, null, true);
    }

    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        return getAllChildren(parent, u, perm, null, true);
    }

    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm, Set<Role> roles)
    {
        return getAllChildren(parent, u, perm, roles, true);
    }

    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm, boolean includeWorkbooksAndTabs)
    {
        return getAllChildren(parent, u, perm, null, includeWorkbooksAndTabs);
    }
    
    public static List<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm, Set<Role> roles, boolean includeWorkbooksAndTabs)
    {
        Set<Container> allChildren = getAllChildren(parent);
        List<Container> result = new ArrayList<>(allChildren.size());

        for (Container container : allChildren)
        {
            if ((includeWorkbooksAndTabs || !container.isWorkbookOrTab()) && container.hasPermission(u, perm, roles))
            {
                result.add(container);
            }
        }

        return result;
    }

    // Returns the next available child container name based on the baseName
    public static String getAvailableChildContainerName(Container c, String baseName)
    {
        List<Container> children = getChildren(c);
        Map<String, Container> folders = new HashMap<>(children.size() * 2);
        for (Container child : children)
            folders.put(child.getName(), child);

        String availableContainerName = baseName;
        int i = 1;
        while (folders.containsKey(availableContainerName))
        {
            availableContainerName = baseName + " " + i++;
        }

        return availableContainerName;
    }

    // Returns true only if user has the specified permission in the entire container tree starting at root
    public static boolean hasTreePermission(Container root, User u,  Class<? extends Permission> perm)
    {
        for (Container c : getAllChildren(root))
            if (!c.hasPermission(u, perm))
                return false;

        return true;
    }


    private static Map<String, Container> getChildrenMap(Container parent)
    {
        if (parent.getType() != Container.TYPE.normal)
        {
            // Optimization to avoid database query (important because some installs have tens of thousands of
            // workbooks) when the container is a workbook, which is not allowed to have children
            return Collections.emptyMap();
        }

        List<String> childIds = (List<String>) CACHE.get(CONTAINER_CHILDREN_PREFIX + parent.getId());
        if (null == childIds)
        {
            try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
            {
                List<Container> children = new SqlSelector(CORE.getSchema(),
                        "SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE Parent = ? ORDER BY SortOrder, LOWER(Name)",
                        parent.getId()).getArrayList(Container.class);

                childIds = new ArrayList<>(children.size());
                for (Container c : children)
                {
                    childIds.add(c.getId());
                    _addToCache(c);
                }
                childIds = Collections.unmodifiableList(childIds);
                CACHE.put(CONTAINER_CHILDREN_PREFIX + parent.getId(), childIds);
                // No database changes to commit, but need to decrement the transaction counter
                t.commit();
            }
        }

        if (childIds.isEmpty())
            return Collections.emptyMap();

        // Use a LinkedHashMap to preserve the order defined by the user - they're not necessarily alphabetical
        Map<String, Container> ret = new LinkedHashMap<>();
        for (String id : childIds)
        {
            Container c = ContainerManager.getForId(id);
            if (null != c)
                ret.put(c.getName(), c);
        }
        return Collections.unmodifiableMap(ret);
    }


    public static Container getForRowId(int id)
    {
        Selector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE RowId = ?", id));
        return selector.getObject(Container.class);
    }

    public static Container getForId(@NotNull GUID id)
    {
        return getForId(id.toString());
    }


    public static Container getForId(String id)
    {
        Container d = getFromCacheId(id);
        if (null != d)
            return d;

        //if the input string is not a GUID, just return null,
        //so that we don't get a SQLException when the database
        //tries to convert it to a unique identifier.
        if (null != id && !GUID.isGUID(id))
            return null;

        try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
        {
            Container result = new SqlSelector(
                    CORE.getSchema(),
                    "SELECT * FROM " + CORE.getTableInfoContainers() + " WHERE EntityId = ?",
                    id).getObject(Container.class);
            if (result != null)
            {
                result = _addToCache(result);
            }
            // No database changes to commit, but need to decrement the counter
            t.commit();

            return result;
        }
    }


    public static Container getChild(Container c, String name)
    {
        Path path = c.getParsedPath().append(name);

        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        Map<String, Container> map = ContainerManager.getChildrenMap(c);
        return map.get(name);
    }


    public static Container getForPath(String path)
    {
        Path p = Path.parse(path);
        return getForPath(p);
    }


    public static Container getForPath(Path path)
    {
        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        // Special case for ROOT -- we want to throw instead of returning null
        if (path.equals(Path.rootPath))
        {
            try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
            {
                TableInfo tinfo = CORE.getTableInfoContainers();

                // Unusual, but possible -- if cache loader hits an exception it can end up caching null
                if (null == tinfo)
                    throw new RootContainerException("Container table could not be retrieved from the cache");

                // This might be called at bootstrap, before schemas have been created
                if (tinfo.getTableType() == DatabaseTableType.NOT_IN_DB)
                    throw new RootContainerException("Container table has not been created");

                Container result = new SqlSelector(CORE.getSchema(),"SELECT * FROM " + tinfo + " WHERE Parent IS NULL").getObject(Container.class);

                if (result == null)
                    throw new RootContainerException("Root container does not exist");

                _addToCache(result);
                // No database changes to commit, but need to decrement the counter
                t.commit();
                return result;
            }
        }
        else
        {
            Path parent = path.getParent();
            String name = path.getName();
            Container dirParent = getForPath(parent);

            if (null == dirParent)
                return null;

            Map<String, Container> map = ContainerManager.getChildrenMap(dirParent);
            return map.get(name);
        }
    }


    @SuppressWarnings({"serial"})
    public static class RootContainerException extends RuntimeException
    {
        private RootContainerException(String message, Throwable cause)
        {
            super(message, cause);
        }

        private RootContainerException(String message)
        {
            super(message);
        }
    }


    public static Container getRoot()
    {
        try
        {
            return getForPath("/");
        }
        catch (IllegalStateException e)
        {
            throw new RootContainerException("Root container can't be retrieved", e);
        }
    }


    public static void saveAliasesForContainer(Container container, List<String> aliases)
    {
        try (DbScope.Transaction transaction = CORE.getSchema().getScope().ensureTransaction())
        {
            SQLFragment deleteSQL = new SQLFragment();
            deleteSQL.append("DELETE FROM ");
            deleteSQL.append(CORE.getTableInfoContainerAliases());
            deleteSQL.append(" WHERE ContainerId = ? ");
            deleteSQL.add(container.getId());
            if (!aliases.isEmpty())
            {
                deleteSQL.append(" OR LOWER(Path) IN (");
                String separator = "";
                for (String alias : aliases)
                {
                    deleteSQL.append(separator);
                    separator = ", ";
                    deleteSQL.append("LOWER(?)");
                    deleteSQL.add(alias);
                }
                deleteSQL.append(")");
            }
            new SqlExecutor(CORE.getSchema()).execute(deleteSQL);

            Set<String> caseInsensitiveAliases = new CaseInsensitiveHashSet(aliases);

            for (String alias : caseInsensitiveAliases)
            {
                SQLFragment insertSQL = new SQLFragment();
                insertSQL.append("INSERT INTO ");
                insertSQL.append(CORE.getTableInfoContainerAliases());
                insertSQL.append(" (Path, ContainerId) VALUES (?, ?)");
                insertSQL.add(alias);
                insertSQL.add(container.getId());
                new SqlExecutor(CORE.getSchema()).execute(insertSQL);
            }

            transaction.commit();
        }
    }


    // Abstract base class used for attaching system resources (favorite icons, logos, stylesheets, sso auth logos) to folders and projects
    public static abstract class ContainerParent implements AttachmentParent
    {
        private final Container _c;

        protected ContainerParent(Container c)
        {
            _c = c;
        }

        public String getEntityId()
        {
            return _c.getId();
        }

        public String getContainerId()
        {
            return _c.getId();
        }

        public Container getContainer()
        {
            return _c;
        }
    }


    public static Container getHomeContainer()
    {
        return getForPath(HOME_PROJECT_PATH);
    }

    public static List<Container> getProjects()
    {
        return ContainerManager.getChildren(ContainerManager.getRoot());
    }


    public static NavTree getProjectList(ViewContext context, boolean includeChildren)
    {
        User user = context.getUser();
        Container currentProject = context.getContainer().getProject();
        String projectNavTreeId = PROJECT_LIST_ID;
        if (currentProject != null)
            projectNavTreeId += currentProject.getId();

        NavTree navTree = (NavTree) NavTreeManager.getFromCache(projectNavTreeId, context);
        if (null != navTree)
            return navTree;

        NavTree list = new NavTree("Projects");
        List<Container> projects = ContainerManager.getProjects();

        for (Container project : projects)
        {
            boolean shouldDisplay = project.shouldDisplay(user) && project.hasPermission("getProjectList()", user, ReadPermission.class);
            boolean includeCurrentProject = includeChildren && currentProject != null && currentProject.equals(project);

            if (shouldDisplay || includeCurrentProject)
            {
                ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(project);

                if (includeChildren)
                    list.addChild(ContainerManager.getFolderListForUser(project, context));
                else if (project.equals(getHomeContainer()))
                    list.addChild(new NavTree("Home", startURL));
                else
                    list.addChild(project.getTitle(), startURL);
            }
        }

        list.setId(projectNavTreeId);
        NavTreeManager.cacheTree(list, context.getUser());

        return list;
    }


    public static NavTree getFolderListForUser(Container project, ViewContext viewContext)
    {
        Container c = viewContext.getContainer();

        NavTree tree = (NavTree) NavTreeManager.getFromCache(project.getId(), viewContext);
        if (null != tree)
            return tree;

        try
        {
            assert SecurityLogger.indent("getFolderListForUser()");

            User user = viewContext.getUser();
            String projectId = project.getId();

            List<Container> folders = new ArrayList<>(ContainerManager.getAllChildren(project));

            Collections.sort(folders);

            Set<Container> containersInTree = new HashSet<>();

            Map<String, NavTree> m = new HashMap<>();
            for (Container f : folders)
            {
                if (f.isWorkbookOrTab())
                    continue;

                SecurityPolicy policy = f.getPolicy();
                boolean skip = (!policy.hasPermission(user, ReadPermission.class) || (!f.shouldDisplay(user)) || (!f.hasPermission(user, ReadPermission.class)));

                //Always put the project and current container in...
                if (skip && !f.equals(project) && !f.equals(c))
                    continue;

                //HACK to make home link consistent...
                String name = f.getTitle();
                if (name.equals("home") && f.equals(getHomeContainer()))
                    name = "Home";

                NavTree t = new NavTree(name);
                if (policy.hasPermission(user, ReadPermission.class))
                {
                    ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(f);
                    t.setHref(url.getEncodedLocalURIString());
                }
                containersInTree.add(f);
                m.put(f.getId(), t);
            }

            //Ensure parents of any accessible folder are in the tree. If not add them with no link.
            for (Container treeContainer : containersInTree)
            {
                if (!treeContainer.equals(project) && !containersInTree.contains(treeContainer.getParent()))
                {
                    Set<Container> containersToRoot = containersToRoot(treeContainer);
                    //Possible will be added more than once, if several children are accessible, but that's OK...
                    for (Container missing : containersToRoot)
                        if (!m.containsKey(missing.getId()))
                        {
                            NavTree noLinkTree = new NavTree(missing.getName());
                            m.put(missing.getId(), noLinkTree);
                        }
                }
            }

            for (Container f : folders)
            {
                if (f.getId().equals(projectId))
                    continue;

                NavTree child = m.get(f.getId());
                if (null == child)
                    continue;

                NavTree parent = m.get(f.getParent().getId());
                assert null != parent; //This should not happen anymore, we assure all parents are in tree.
                if (null != parent)
                    parent.addChild(child);
            }

            NavTree projectTree = m.get(projectId);

            projectTree.setId(project.getId());

            NavTreeManager.cacheTree(projectTree, user);
            return projectTree;
        }
        finally
        {
            assert SecurityLogger.outdent();
        }
    }


    public static Set<Container> containersToRoot(Container child)
    {
        Set<Container> containersOnPath = new HashSet<>();
        Container current = child;
        while (current != null && !current.isRoot())
        {
            containersOnPath.add(current);
            current = current.getParent();
        }

        return containersOnPath;
    }

    /**
     * Provides a sorted list of containers from the root to the child container provided.
     * It does not include the root node.
     * @param child Container from which the search is sourced.
     * @return List sorted in order of distance from root.
     */
    public static List<Container> containersToRootList(Container child)
    {
        List<Container> containers = new ArrayList<>();
        Container current = child;
        while (current != null && !current.isRoot())
        {
            containers.add(current);
            current = current.getParent();
        }

        Collections.reverse(containers);
        return containers;
    }


    // Move a container to another part of the container tree.  Careful: this method DOES NOT prevent you from orphaning
    // an entire tree (e.g., by setting a container's parent to one of its children); the UI in AdminController does this.
    //
    // NOTE: Beware side-effect of changing ACLs and GROUPS if a container changes projects
    //
    // @return true if project has changed (should probably redirect to security page)
    public static boolean move(Container c, final Container newParent, User user) throws ValidationException
    {
        if (c.isRoot())
            throw new IllegalArgumentException("can't move root container");

        if (!isRenameable(c))
        {
            throw new IllegalArgumentException("Can't move container " + c.getPath());
        }

        List<String> errors = new ArrayList<>();
        for (ContainerListener listener : getListeners())
        {
            try
            {
                errors.addAll(listener.canMove(c, newParent, user));
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(null, new IllegalStateException(listener.getClass().getName() + ".canMove() threw an exception or violated @NotNull contract"));
            }
        }
        if (!errors.isEmpty())
        {
            ValidationException exception = new ValidationException();
            for (String error : errors)
            {
                exception.addError(new SimpleValidationError(error));
            }
            throw exception;
        }

        if (c.getParent().getId().equals(newParent.getId()))
            return false;

        Container oldParent = c.getParent();
        Container oldProject = c.getProject();
        Container newProject = newParent.isRoot() ? c : newParent.getProject();

        boolean changedProjects = !oldProject.getId().equals(newProject.getId());

        // Synchronize the transaction, but not the listeners -- see #9901
        try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
        {
            new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoContainers() + " SET Parent = ? WHERE EntityId = ?", newParent.getId(), c.getId());

            // Refresh the container directly from the database so the container reflects the new parent, isProject(), etc.
            c = ContainerManager.getForRowId(c.getRowId());

            // this could be done in the trigger, but I prefer to put it in the transaction
            if (changedProjects)
                SecurityManager.changeProject(c, oldProject, newProject);

            clearCache();

            try
            {
                ExperimentService.get().moveContainer(c, oldParent, newParent);
            }
            catch (ExperimentException e)
            {
                throw new RuntimeException(e);
            }

            // Clear after the commit has propagated the state to other threads and transactions
            // Do this in a commit task in case we've joined another existing DbScope.Transaction instead of starting our own
            t.addCommitTask(() ->
            {
                clearCache();
                getChildrenMap(newParent); // reload the cache
            }, DbScope.CommitTaskOption.POSTCOMMIT);

            t.commit();
        }

        Container newContainer = getForId(c.getId());
        fireMoveContainer(newContainer, oldParent, user);

        return changedProjects;
    }


    // Rename a container in the table.  Will fail if the new name already exists in the parent container.
    // Lock the class to ensure the old version of this container doesn't sneak into the cache after clearing
    public static void rename(Container c, User user, String name)
    {
        if (!isRenameable(c))
        {
            throw new IllegalArgumentException("Cannot rename container " + c.getPath());
        }

        name = StringUtils.trimToNull(name);
        if (null == name)
            throw new NullPointerException();
        String oldName = c.getName();
        if (oldName.equals(name))
            return;

        try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
        {
            new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoContainers() + " SET Name=? WHERE EntityId=?", name, c.getId());
            clearCache();  // Clear the entire cache, since containers cache their full paths
            //Get new version since name has changed.
            c = getForId(c.getId());
            fireRenameContainer(c, user, oldName);
            // Clear again after the commit has propagated the state to other threads and transactions
            // Do this in a commit task in case we've joined another existing DbScope.Transaction instead of starting our own
            t.addCommitTask(ContainerManager::clearCache, DbScope.CommitTaskOption.POSTCOMMIT);
            t.commit();
        }
    }

    public static void setChildOrderToAlphabetical(Container parent)
    {
        setChildOrder(parent.getChildren(), true);
    }

    public static void setChildOrder(Container parent, List<Container> orderedChildren) throws ContainerException
    {
        for (Container child : orderedChildren)
        {
            if (child == null || child.getParent() == null || !child.getParent().equals(parent)) // #13481
                throw new ContainerException("Invalid parent container of " + (child == null ? "null child container" : child.getPath()));
        }
        setChildOrder(orderedChildren, false);
    }

    private static void setChildOrder(List<Container> siblings, boolean resetToAlphabetical)
    {
        try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction(DATABASE_QUERY_LOCK))
        {
            for (int index = 0; index < siblings.size(); index++)
            {
                Container current = siblings.get(index);
                new SqlExecutor(CORE.getSchema()).execute("UPDATE " + CORE.getTableInfoContainers() + " SET SortOrder = ? WHERE EntityId = ?",
                        resetToAlphabetical ? 0 : index, current.getId());
            }
            // Clear after the commit has propagated the state to other threads and transactions
            // Do this in a commit task in case we've joined another existing DbScope.Transaction instead of starting our own
            t.addCommitTask(ContainerManager::clearCache, DbScope.CommitTaskOption.POSTCOMMIT);

            t.commit();
        }
    }


    private static Map<String,Integer> deletingContainers = Collections.synchronizedMap(new HashMap<String,Integer>());

    // Delete a container from the database
    public static boolean delete(final Container c, User user)
    {
        if (!isDeletable(c))
        {
            throw new IllegalArgumentException("Cannot delete container: " + c.getPath());
        }

        if (deletingContainers.containsKey(c.getId()))
        {
            throw new IllegalArgumentException("Container is already being deleted: " + c.getPath());
        }

        LOG.debug("Starting container delete for " + c.getContainerNoun(true) + " " + c.getPath());

        try (DbScope.Transaction t = CORE.getSchema().getScope().ensureTransaction())
        {
            deletingContainers.put(c.getId(), user.getUserId());

            // Verify that no children exist
            Selector sel = new TableSelector(CORE.getTableInfoContainers(), new SimpleFilter(FieldKey.fromParts("Parent"), c), null);

            if (sel.exists())
            {
                _removeFromCache(c);
                t.commit();
                return false;
            }

            if (c.isContainerTab())
            {
                // Need to remove portal page, too; container name is page's pageId and in container's parent container
                Portal.PortalPage page = Portal.getPortalPage(c.getParent(), c.getName());
                if (null != page)               // Be safe
                    Portal.deletePage(page);

                // Tell parent
                setContainerTabDeleted(c.getParent(), c.getName(), c.getParent().getFolderType().getName());
            }

            fireDeleteContainer(c, user);

            SqlExecutor sqlExecutor = new SqlExecutor(CORE.getSchema());
            sqlExecutor.execute("DELETE FROM " + CORE.getTableInfoContainerAliases() + " WHERE ContainerId=?", c.getId());
            sqlExecutor.execute("DELETE FROM " + CORE.getTableInfoContainers() + " WHERE EntityId=?", c.getId());
            // now that the container is actually gone, delete all ACLs (better to have an ACL w/o object than object w/o ACL)
            SecurityPolicyManager.removeAll(c);
            // and delete all container-based sequences
            DbSequenceManager.deleteAll(c);

            // After we've committed the transaction, be sure that we remove this container from the cache
            // See https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=17015
            t.addCommitTask(() ->
            {
                // Be sure that we've waited until any threads that might be populating the cache have finished
                // before we guarantee that we've removed this now-deleted container
                DATABASE_QUERY_LOCK.lock();
                try
                {
                    _removeFromCache(c);
                }
                finally
                {
                    DATABASE_QUERY_LOCK.unlock();
                }
            }, DbScope.CommitTaskOption.POSTCOMMIT);
            addAuditEvent(user, c, c.getContainerNoun(true) + " " + c.getPath() + " was deleted");
            t.commit();
        }
        finally
        {
            deletingContainers.remove(c.getId());
        }
        LOG.debug("Completed container delete for " + c.getContainerNoun(true) + " " + c.getPath());
        return true;
    }

    public static boolean isDeletable(Container c)
    {
        return !isSystemContainer(c);
    }

    public static boolean isRenameable(Container c)
    {
        return !isSystemContainer(c);
    }

    public static boolean isSystemContainer(Container c)
    {
         return c.equals(getRoot()) || c.equals(getHomeContainer()) || c.equals(getSharedContainer());
    }

    public static boolean exists(Container c)
    {
        return null != getForId(c.getEntityId());
    }

    public static void deleteAll(Container root, User user) throws UnauthorizedException
    {
        if (!hasTreePermission(root, user, DeletePermission.class))
            throw new UnauthorizedException("You don't have delete permissions to all folders");

        LOG.debug("Starting container (and children) delete for " + root.getContainerNoun(true) + " " + root.getPath());
        Set<Container> depthFirst = getAllChildrenDepthFirst(root);
        depthFirst.add(root);

        for (Container c : depthFirst)
            delete(c, user);

        LOG.debug("Completed container (and children) delete for " + root.getContainerNoun(true) + " " + root.getPath());
    }

    private static void addAuditEvent(User user, Container c, String comment)
    {
        if (user != null)
        {
            AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, c.getId(), comment);
            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());

            AuditLogService.get().addEvent(user, event);
        }
    }

    private static Set<Container> getAllChildrenDepthFirst(Container c)
    {
        Set<Container> set = new LinkedHashSet<>();
        getAllChildrenDepthFirst(c, set);
        return set;
    }


    private static void getAllChildrenDepthFirst(Container c, Collection<Container> list)
    {
        for (Container child : c.getChildren())
        {
            getAllChildrenDepthFirst(child, list);
            list.add(child);
        }
    }


    private static Set<Container> _getAllChildrenFromCache(Container c)
    {
        return (Set<Container>) CACHE.get(CONTAINER_ALL_CHILDREN_PREFIX + c.getId());
    }

    private static void _addAllChildrenToCache(Container c, Set<Container> children)
    {
        CACHE.put(CONTAINER_ALL_CHILDREN_PREFIX + c.getId(), children);
    }



    public static Container getFromCacheId(String id)
    {
        return (Container) CACHE.get(CONTAINER_PREFIX + id);
    }


    private static Container _getFromCachePath(Path path)
    {
        return (Container) CACHE.get(CONTAINER_PREFIX + toString(path));
    }


    // UNDONE: use Path directly instead of toString()
    private static String toString(Container c)
    {
        return toString(c.getParsedPath());
    }

    private static String toString(Path p)
    {
        return StringUtils.strip(p.toString(), "/").toLowerCase();
    }


    private static Container _addToCache(Container c)
    {
        assert DATABASE_QUERY_LOCK.isHeldByCurrentThread() : "Any cache modifications must be synchronized at a " +
                "higher level so that we ensure that the container to be inserted still exists and hasn't been deleted";
        CACHE.put(CONTAINER_PREFIX + toString(c), c);
        CACHE.put(CONTAINER_PREFIX + c.getId(), c);
        return c;
    }


    private static void _clearChildrenFromCache(Container c)
    {
        CACHE.remove(CONTAINER_CHILDREN_PREFIX + c.getId());
        navTreeManageUncache(c);
    }


    private static void _removeFromCache(Container c)
    {
        Container project = c.getProject();
        Container parent = c.getParent();

        CACHE.remove(CONTAINER_PREFIX + toString(c));
        CACHE.remove(CONTAINER_PREFIX + c.getId());
        CACHE.remove(CONTAINER_CHILDREN_PREFIX + c.getId());

        if (null != parent)
            CACHE.remove(CONTAINER_CHILDREN_PREFIX + parent.getId());

        // blow away the all children caches
        CACHE.removeUsingPrefix(CONTAINER_CHILDREN_PREFIX);

        navTreeManageUncache(c);
    }


    public static void clearCache()
    {
        CACHE.clear();

        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheAll();
    }


    private static void navTreeManageUncache(Container c)
    {
        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheTree(PROJECT_LIST_ID);
        NavTreeManager.uncacheTree(getRoot().getId());

        Container project = c.getProject();
        if (project != null)
        {
            NavTreeManager.uncacheTree(project.getId());
            NavTreeManager.uncacheTree(PROJECT_LIST_ID + project.getId());
        }
    }


    public static void notifyContainerChange(String id)
    {
        notifyContainerChange(id, Property.Policy);
    }

    public static void notifyContainerChange(String id, Property prop)
    {
        Container c = getForId(id);
        if (null != c)
        {
            _removeFromCache(c);
            c = getForId(id);  // load a fresh container since the original might be stale.
            if (null != c)
            {
                ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, prop, null, null);
                firePropertyChangeEvent(evt);
            }
        }
    }


    /** including root node */
    public static Set<Container> getAllChildren(Container root)
    {
        Set<Container> children = _getAllChildrenFromCache(root);
        if (children != null)
            return children;

        children = getAllChildrenDepthFirst(root);
        children.add(root);

        children = Collections.unmodifiableSet(children); // don't let callers modify the cached copy
        _addAllChildrenToCache(root, children);
        return children;
    }

    /**
     * Return all children of the root node, including root node, which have the given active module
     */
    @NotNull
    public static Set<Container> getAllChildrenWithModule(@NotNull Container root, @NotNull Module module)
    {
        Set<Container> children = new HashSet<>();
        for (Container candidate : getAllChildren(root))
        {
            if (candidate.getActiveModules().contains(module))
                children.add(candidate);
        }
        return Collections.unmodifiableSet(children);
    }

    public static long getContainerCount()
    {
        return new TableSelector(CORE.getTableInfoContainers()).getRowCount();
    }


    /** Retrieve entire container hierarchy */
    public static MultiValuedMap<Container, Container> getContainerTree()
    {
        final MultiValuedMap<Container, Container> mm = new ArrayListValuedHashMap<>();

        // Get all containers and parents
        SqlSelector selector = new SqlSelector(CORE.getSchema(), "SELECT Parent, EntityId FROM " + CORE.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC");

        selector.forEach(rs -> {
            String parentId = rs.getString(1);
            Container parent = (parentId != null ? getForId(parentId) : null);
            Container child = getForId(rs.getString(2));

            if (null != child)
                mm.put(parent, child);
        });

        return mm;
    }

    /**
     * Returns a branch of the container tree including only the root and its descendants
     * @param root The root container
     * @return MultiMap of containers including root and its descendants
     */
    public static MultiValuedMap<Container, Container> getContainerTree(Container root)
    {
        //build a multimap of only the container ids
        final MultiValuedMap<String, String> mmIds = new ArrayListValuedHashMap<>();

        // Get all containers and parents
        Selector selector = new SqlSelector(CORE.getSchema(), "SELECT Parent, EntityId FROM " + CORE.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC");

        selector.forEach(rs -> mmIds.put(rs.getString(1), rs.getString(2)));

        //now find the root and build a MultiMap of it and its descendants
        MultiValuedMap<Container, Container> mm = new ArrayListValuedHashMap<>();
        mm.put(null, root);
        addChildren(root, mmIds, mm);
        for (Container key : mm.keySet())
        {
            List<Container> siblings = new ArrayList<>(mm.get(key));
            Collections.sort(siblings);
        }
        return mm;
    }

    private static void addChildren(Container c, MultiValuedMap<String, String> mmIds, MultiValuedMap<Container, Container> mm)
    {
        Collection<String> childIds = mmIds.get(c.getId());
        if (null != childIds)
        {
            for (String childId : childIds)
            {
                Container child = getForId(childId);
                if (null != child)
                {
                    mm.put(c, child);
                    addChildren(child, mmIds, mm);
                }
            }
        }
    }

    public static Set<Container> getContainerSet(MultiValuedMap<Container, Container> mm, User user, Class<? extends Permission> perm)
    {
        Collection<Container> containers = mm.values();
        if (null == containers)
            return new HashSet<>();

        return containers
            .stream()
            .filter(c -> c.hasPermission(user, perm))
            .collect(Collectors.toSet());
    }


    public static String getIdsAsCsvList(Set<Container> containers)
    {
        if (0 == containers.size())
            return "(NULL)";    // WHERE x IN (NULL) should match no rows

        StringBuilder csvList = new StringBuilder("(");

        for (Container container : containers)
            csvList.append("'").append(container.getId()).append("',");

        // Replace last comma with ending paren
        csvList.replace(csvList.length() - 1, csvList.length(), ")");

        return csvList.toString();
    }


    public static List<String> getIds(User user, Class<? extends Permission> perm)
    {
        Set<Container> containers = getContainerSet(getContainerTree(), user, perm);

        List<String> ids = new ArrayList<>(containers.size());

        for (Container c : containers)
            ids.add(c.getId());

        return ids;
    }


    //
    // ContainerListener
    //

    public interface ContainerListener extends PropertyChangeListener
    {
        enum Order {First, Last}

        /** Called after a new container has been created */
        void containerCreated(Container c, User user);

        /** Called immediately prior to deleting the row from core.containers */
        void containerDeleted(Container c, User user);

        /** Called after the container has been moved to its new parent */
        void containerMoved(Container c, Container oldParent, User user);

        /**
         * Called prior to moving a container, to find out if there are any issues that would prevent a successful move
         * @return a list of errors that should prevent the move from happening, if any
         */
        @NotNull
        Collection<String> canMove(Container c, Container newParent, User user);

        @Override
        void propertyChange(PropertyChangeEvent evt);
    }

    public static abstract class AbstractContainerListener implements ContainerListener
    {
        @Override
        public void containerCreated(Container c, User user)
        {}

        @Override
        public void containerDeleted(Container c, User user)
        {}

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {}

        @NotNull
        @Override
        public Collection<String> canMove(Container c, Container newParent, User user)
        {
            return Collections.emptyList();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {}
    }


    public static class ContainerPropertyChangeEvent extends PropertyChangeEvent implements PropertyChange<Property, Object>
    {
        public final Property property;
        public final Container container;
        public User user;
        
        public ContainerPropertyChangeEvent(Container c, @Nullable User user, Property p, Object oldValue, Object newValue)
        {
            super(c, p.name(), oldValue, newValue);
            container = c;
            this.user = user;
            property = p;
        }

        public ContainerPropertyChangeEvent(Container c, Property p, Object oldValue, Object newValue)
        {
            this(c, null, p, oldValue, newValue);
        }

        @Override
        public Property getProperty()
        {
            return property;
        }
    }


    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<ContainerListener> _listeners = new CopyOnWriteArrayList<>();
    private static final List<ContainerListener> _laterListeners = new CopyOnWriteArrayList<>();

    // These listeners are executed in the order they are registered, before the "Last" listeners
    public static void addContainerListener(ContainerListener listener)
    {
        addContainerListener(listener, ContainerListener.Order.First);
    }


    // Explicitly request "Last" ordering via this method.  "Last" listeners execute after all "First" listeners.
    public static void addContainerListener(ContainerListener listener, ContainerListener.Order order)
    {
        if (ContainerListener.Order.First == order)
            _listeners.add(listener);
        else
            _laterListeners.add(listener);
    }


    public static void removeContainerListener(ContainerListener listener)
    {
        _listeners.remove(listener);
        _laterListeners.remove(listener);
    }


    private static List<ContainerListener> getListeners()
    {
        List<ContainerListener> combined = new ArrayList<>(_listeners.size() + _laterListeners.size());
        combined.addAll(_listeners);
        combined.addAll(_laterListeners);

        return combined;
    }


    private static List<ContainerListener> getListenersReversed()
    {
        List<ContainerListener> combined = new LinkedList<>();

        // Copy to guarantee consistency between .listIterator() and .size()
        List<ContainerListener> copy = new ArrayList<>(_listeners);
        ListIterator<ContainerListener> iter = copy.listIterator(copy.size());

        // Iterate in reverse
        while(iter.hasPrevious())
            combined.add(iter.previous());

        // Copy to guarantee consistency between .listIterator() and .size()
        // Add elements from the laterList in reverse order so that Core is fired last
        List<ContainerListener> laterCopy = new ArrayList<>(_laterListeners);
        ListIterator<ContainerListener> laterIter = laterCopy.listIterator(laterCopy.size());

        // Iterate in reverse
        while(laterIter.hasPrevious())
            combined.add(laterIter.previous());

        return combined;
    }


    protected static void fireCreateContainer(Container c, User user)
    {
        List<ContainerListener> list = getListeners();

        for (ContainerListener cl : list)
        {
            try
            {
                cl.containerCreated(c, user);
            }
            catch (Throwable t)
            {
                LOG.error("fireCreateContainer for " + cl.getClass().getName(), t);
            }
        }
    }


    protected static void fireDeleteContainer(Container c, User user)
    {
        List<ContainerListener> list = getListenersReversed();

        for (ContainerListener l : list)
        {
            try
            {
                l.containerDeleted(c, user);
            }
            catch (RuntimeException e)
            {
                LOG.error("fireDeleteContainer for " + l.getClass().getName(), e);

                // Fail fast (first Throwable aborts iteration), #17560
                throw e;
            }
        }
    }


    protected static void fireRenameContainer(Container c, User user, String oldValue)
    {
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, user, Property.Name, oldValue, c.getName());
        firePropertyChangeEvent(evt);
    }


    protected static void fireMoveContainer(Container c, Container oldParent, User user)
    {
        List<ContainerListener> list = getListeners();

        for (ContainerListener cl : list)
        {
            // While we would ideally transact the full container move, that will likely cause long-blocking
            // queries and/or deadlocks. For now, at least transact each separate move handler independently
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                cl.containerMoved(c, oldParent, user);
                transaction.commit();
            }
        }
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, user, Property.Parent, oldParent, c.getParent());
        firePropertyChangeEvent(evt);
    }


    public static void firePropertyChangeEvent(ContainerPropertyChangeEvent evt)
    {
        List<ContainerListener> list = getListeners();
        for (ContainerListener l : list)
        {
            try
            {
                l.propertyChange(evt);
            }
            catch (Throwable t)
            {
                LOG.error("firePropertyChangeEvent for " + l.getClass().getName(), t);
            }
        }
    }


    public static Container createDefaultSupportContainer()
    {
        // create a "support" container. Admins can do anything,
        // Users can read/write, Guests can read.
        return bootstrapContainer(Container.DEFAULT_SUPPORT_PROJECT_PATH,
                RoleManager.getRole(SiteAdminRole.class),
                RoleManager.getRole(AuthorRole.class),
                RoleManager.getRole(ReaderRole.class));
    }

    public static String[] getAliasesForContainer(Container c)
    {
        return new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT Path FROM " + CORE.getTableInfoContainerAliases() + " WHERE ContainerId = ? ORDER BY LOWER(Path)", c.getId())).getArray(String.class);
    }

    @Nullable
    public static Container resolveContainerPathAlias(String path)
    {
        return resolveContainerPathAlias(path, false);
    }

    @Nullable
    private static Container resolveContainerPathAlias(String path, boolean top)
    {
        // Simple case -- resolve directly (sans alias)
        Container aliased = getForPath(path);
        if (aliased != null)
            return aliased;

        // Simple case -- directly resolve from database
        aliased = getForPathAlias(path);
        if (aliased != null)
            return aliased;

        // At the leaf and the container was not found
        if (top)
            return null;

        List<String> splits = Arrays.asList(path.split("/"));
        String subPath = "";
        for (int i=0; i < splits.size()-1; i++) // minus 1 due to leaving off last container
        {
            if (splits.get(i).length() > 0)
                subPath += "/" + splits.get(i);
        }

        aliased = resolveContainerPathAlias(subPath, false);

        if (aliased == null)
            return null;

        String leafPath = aliased.getPath() + "/" + splits.get(splits.size()-1);
        return resolveContainerPathAlias(leafPath, true);
    }

    @Nullable
    private static Container getForPathAlias(String path)
    {
        Container[] ret = new SqlSelector(CORE.getSchema(),
                "SELECT * FROM " + CORE.getTableInfoContainers() + " c, " + CORE.getTableInfoContainerAliases() + " ca WHERE ca.ContainerId = c.EntityId AND LOWER(ca.path) = LOWER(?)",
                path).getArray(Container.class);

        return ret.length == 0 ? null : ret[0];
    }

    /**
     * If a container at the given path does not exist create one
     * and set permissions. If the container does exist, permissions
     * are only set if there is no explicit ACL for the container.
     * This prevents us from resetting permissions if all users
     * are dropped.
     */
    @NotNull
    public static Container bootstrapContainer(String path, Role adminRole, Role userRole, Role guestRole)
    {
        Container c = null;

        try
        {
            c = getForPath(path);
        }
        catch (RootContainerException e)
        {
            // Ignore this -- root doesn't exist yet
        }
        boolean newContainer = false;

        if (c == null)
        {
            LOG.debug("Creating new container for path '" + path + "'");
            newContainer = true;
            c = ensureContainer(path);
        }

        if (c == null)
        {
            throw new IllegalStateException("Unable to ensure container for path '" + path + "'");
        }

        // Only set permissions if there are no explicit permissions
        // set for this object or we just created it
        Integer policyCount = null;
        if (!newContainer)
        {
            policyCount = new SqlSelector(CORE.getSchema(),
                "SELECT COUNT(*) FROM " + CORE.getTableInfoPolicies() + " WHERE ResourceId = ?",
                c.getId()).getObject(Integer.class);
        }

        if (newContainer || 0 == policyCount.intValue())
        {
            LOG.debug("Setting permissions for '" + path + "'");
            MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), adminRole);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupUsers), userRole);
            policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), guestRole);
            SecurityPolicyManager.savePolicy(policy);
        }

        return c;
    }

    /**
     * @param container the container being created. May be null if we haven't actually created it yet
     * @param parent the parent of the container being created. Used in case the container doesn't actually exist yet.
     * @return the list of standard steps and any extra ones based on the container's FolderType
     */
    public static List<NavTree> getCreateContainerWizardSteps(@Nullable Container container, @NotNull Container parent)
    {
        List<NavTree> navTrail = new ArrayList<>();

        boolean isProject = parent.isRoot();

        navTrail.add(new NavTree(isProject ? "Create Project" : "Create Folder"));
        navTrail.add(new NavTree("Users / Permissions"));
        if(isProject)
            navTrail.add(new NavTree("Project Settings"));
        if(container != null)
            navTrail.addAll(container.getFolderType().getExtraSetupSteps(container));
        return navTrail;
    }

    @TestTimeout(120) @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert implements ContainerListener
    {
        Map<Path, Container> _containers = new HashMap<>();
        Container _testRoot = null;

        @Before
        public void setUp() throws Exception
        {
            if (null == _testRoot)
            {
                Container junit = JunitUtil.getTestContainer();
                _testRoot = ContainerManager.ensureContainer(junit, "ContainerManager$TestCase-" + GUID.makeGUID());
                ContainerManager.addContainerListener(this);
            }
        }


        @After
        public void tearDown() throws Exception
        {
            ContainerManager.removeContainerListener(this);
            if (null != _testRoot)
                ContainerManager.deleteAll(_testRoot, TestContext.get().getUser());
        }


        @Test
        public void testImproperFolderNamesBlocked() throws Exception
        {
            String[] badnames = {"", "f\\o", "f/o", "f\\\\o", "foo;", "@foo", "foo" + '\u001F', '\u0000' + "foo", "fo" + '\u007F' + "o", "" + '\u009F'};

            for(String name: badnames)
            {
                try
                {
                    Container c = createContainer(_testRoot, name);
                    try
                    {
                        assertTrue(delete(c, TestContext.get().getUser()));
                    }
                    catch(Exception e){}
                    fail("Should have thrown illegal argument when trying to create container with name: " + name);
                }
                catch(IllegalArgumentException e)
                {
                        //Do nothing, this is expected
                }
            }
        }


        @Test
        public void testCreateDeleteContainers() throws Exception
        {
            int count = 20;
            Random random = new Random();
            MultiValuedMap<String, String> mm = new ArrayListValuedHashMap<>();

            for (int i = 1; i <= count; i++)
            {
                int parentId = random.nextInt(i);
                String parentName = 0 == parentId ? _testRoot.getName() : String.valueOf(parentId);
                String childName = String.valueOf(i);
                mm.put(parentName, childName);
            }

            logNode(mm, _testRoot.getName(), 0);
            for(int i=0; i<2; i++) //do this twice to make sure the containers were *really* deleted
            {
                createContainers(mm, _testRoot.getName(), _testRoot);
                assertEquals(count, _containers.size());
                cleanUpChildren(mm, _testRoot.getName(), _testRoot);
                assertEquals(0, _containers.size());
            }
        }


        @Test
        public void testCache() throws Exception
        {
            assertEquals(0, _containers.size());
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());

            Container one = ContainerManager.createContainer(_testRoot, "one");
            assertEquals(1, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, ContainerManager.getChildren(one).size());

            Container oneA = ContainerManager.createContainer(one, "A");
            assertEquals(2, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(1, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneA).size());

            Container oneB = ContainerManager.createContainer(one, "B");
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneB).size());

            Container deleteme = ContainerManager.createContainer(one, "deleteme");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(deleteme).size());

            assertTrue(ContainerManager.delete(deleteme, TestContext.get().getUser()));
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());

            Container oneC = ContainerManager.createContainer(one, "C");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneC).size());

            assertTrue(ContainerManager.delete(oneC, TestContext.get().getUser()));
            assertTrue(ContainerManager.delete(oneB, TestContext.get().getUser()));
            assertEquals(1, ContainerManager.getChildren(one).size());

            assertTrue(ContainerManager.delete(oneA, TestContext.get().getUser()));
            assertEquals(0, ContainerManager.getChildren(one).size());

            assertTrue(ContainerManager.delete(one, TestContext.get().getUser()));
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, _containers.size());
        }


        @Test
        public void testFolderType()
        {
            // Test all folder types
            List<FolderType> folderTypes = new ArrayList<>(FolderTypeManager.get().getAllFolderTypes());
            for (FolderType folderType : folderTypes)
            {
                if (!folderType.isProjectOnlyType())     // Dataspace can't be subfolder
                    testOneFolderType(folderType);
            }
        }

        private void testOneFolderType(FolderType folderType)
        {
            Container newFolder = createContainer(_testRoot, "folderTypeTest");
            FolderType ft = newFolder.getFolderType();
            assertEquals(ft, FolderType.NONE);

            Container newFolderFromCache = getForId(newFolder.getId());
            assertNotNull(newFolderFromCache);
            assertEquals(newFolderFromCache.getFolderType(), FolderType.NONE);
            newFolder.setFolderType(folderType, TestContext.get().getUser());

            newFolderFromCache = getForId(newFolder.getId());
            assertNotNull(newFolderFromCache);
            assertEquals(newFolderFromCache.getFolderType().getName(), folderType.getName());
            assertEquals(newFolderFromCache.getFolderType().getDescription(), folderType.getDescription());

            deleteAll(newFolder, TestContext.get().getUser());          // There might be subfolders because of container tabs
            Container deletedContainer = getForId(newFolder.getId());

            if (deletedContainer != null)
            {
                fail("Expected container with Id " + newFolder.getId() + " to be deleted, but found " + deletedContainer + ". Folder type was " + folderType);
            }
        }

        private static void createContainers(MultiValuedMap<String, String> mm, String name, Container parent)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                Container child = ContainerManager.createContainer(parent, childName);
                createContainers(mm, childName, child);
            }
        }


        private static void cleanUpChildren(MultiValuedMap<String, String> mm, String name, Container parent)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                Container child = getForPath(makePath(parent, childName));
                cleanUpChildren(mm, childName, child);
                assertTrue(ContainerManager.delete(child, TestContext.get().getUser()));
            }
        }


        private static void logNode(MultiValuedMap<String, String> mm, String name, int offset)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                LOG.debug(StringUtils.repeat("   ", offset) + childName);
                logNode(mm, childName, offset + 1);
            }
        }


        // ContainerListener
        public void propertyChange(PropertyChangeEvent evt)
        {
        }


        public void containerCreated(Container c, User user)
        {
            if (null == _testRoot || !c.getParsedPath().startsWith(_testRoot.getParsedPath()))
                return;
            _containers.put(c.getParsedPath(), c);
        }


        public void containerDeleted(Container c, User user)
        {
            _containers.remove(c.getParsedPath());
        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
        }

        @NotNull
        @Override
        public Collection<String> canMove(Container c, Container newParent, User user)
        {
            return Collections.emptyList();
        }
    }


    static
    {
        ObjectFactory.Registry.register(Container.class, new ContainerFactory());
    }


    public static class ContainerFactory implements ObjectFactory<Container>
    {
        public Container fromMap(Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Container fromMap(Container bean, Map<String, ?> m)
        {
            throw new UnsupportedOperationException();
        }

        public Map<String, Object> toMap(Container bean, Map<String, Object> m)
        {
            throw new UnsupportedOperationException();
        }

        public Container handle(ResultSet rs) throws SQLException
        {
            String id;
            Container d;
            String parentId = rs.getString("Parent");
            String name = rs.getString("Name");
            id = rs.getString("EntityId");
            int rowId = rs.getInt("RowId");
            int sortOrder = rs.getInt("SortOrder");
            Date created = rs.getTimestamp("Created");
            int createdBy = rs.getInt("CreatedBy");
            // _ts
            String description = rs.getString("Description");
            String type = rs.getString("Type");
            String title = rs.getString("Title");
            boolean searchable = rs.getBoolean("Searchable");

            Container dirParent = null;
            if (null != parentId)
                dirParent = getForId(parentId);

            d = new Container(dirParent, name, id, rowId, sortOrder, created, createdBy, searchable);
            d.setDescription(description);
            d.setType(type);
            d.setTitle(title);
            return d;
        }

        @Override
        public ArrayList<Container> handleArrayList(ResultSet rs) throws SQLException
        {
            ArrayList<Container> list = new ArrayList<>();
            while (rs.next())
            {
                list.add(handle(rs));
            }
            return list;
        }

        @Override
        public Container[] handleArray(ResultSet rs) throws SQLException
        {
            ArrayList<Container> list = handleArrayList(rs);
            return list.toArray(new Container[list.size()]);
        }
    }



    static final ContainerService _instance = new ContainerServiceImpl();

    public static ContainerService getContainerService()
    {
        return _instance;
    }

    private static class ContainerServiceImpl implements ContainerService
    {
        @Override
        public Container getForId(GUID id)
        {
            return ContainerManager.getForId(id);
        }

        @Override
        public Container getForId(String id)
        {
            return ContainerManager.getForId(id);
        }

        @Override
        public Container getForPath(Path path)
        {
            return ContainerManager.getForPath(path);
        }

        @Override
        public Container getForPath(String path)
        {
            return ContainerManager.getForPath(path);
        }
    }

    public static Container createFakeContainer(@Nullable String name, @Nullable Container parent)
    {
        return new Container(parent, name, GUID.makeGUID(), 1, 0, new Date(), 0, false);
    }
}
