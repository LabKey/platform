/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.*;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Cache;
import org.labkey.api.module.ModuleLoader;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private static Logger _log = Logger.getLogger(ContainerManager.class);
    private static CoreSchema core = CoreSchema.getInstance();

    private static final String _containerPrefix = ContainerManager.class.getName() + "/";
    private static final String _containerChildrenPrefix = ContainerManager.class.getName() + "/children/";
    private static final String _containerAllChildrenPrefix = ContainerManager.class.getName() + "/children/*/";
    private static final String PROJECT_LIST_ID = "Projects";
    public static final String HOME_PROJECT_PATH = "/home";
    public static final String CONTAINER_AUDIT_EVENT = "ContainerAuditEvent";

    private static final Cache _cache = new Cache(Integer.MAX_VALUE, Cache.DAY, "containers");

    // enum of properties you can see in property change events
    public enum Property
    {
        Name,
        Parent,
        Policy,
        WebRoot,
        AttachmentDirectory,
        PipelineRoot
    }

    private static Cache getCache()
    {
        return _cache;
    }


    static Path makePath(Container parent, String name)
    {
        if (null == parent)
            return new Path(name);
        return parent.getParsedPath().append(name, true);
    }


    public static String getParentPath(String path)
    {
        path = removeTrailing(path, '/');

        if ("".equals(path))
            return null;    // Parent of root is null.

        int slash = path.lastIndexOf('/');

        //for the case where container name does not include preceding slash
        if(slash == -1)
            return "";

        return path.substring(0, slash);
    }


    private static String removeTrailing(String s, char c)
    {
        int i = s.length() - 1;

        //noinspection StatementWithEmptyBody
        for (; i >= 0 && c == s.charAt(i); i--)
            ;

        return s.substring(0, i + 1);
    }


    private static Container createRoot()
    {
        try
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("Parent", null);
            m.put("Name", "");
            Table.insert(null, core.getTableInfoContainers(), m);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

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
        return createContainer(parent, name, null, false);
    }

    public static Container createContainer(Container parent, String name, String description)
    {
        return createContainer(parent, name, description, false);
    }
    
    private static Container createContainer(Container parent, String name, String description, boolean workbook)
    {
        if (core.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

        StringBuffer error = new StringBuffer();
        if (!Container.isLegalName(name, error))
            throw new IllegalArgumentException(error.toString());

        Path path = makePath(parent, name);
        SQLException sqlx = null;

        try
        {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("Parent", parent.getId());
            m.put("Name", name);
            m.put("SortOrder", getNewChildSortOrder(parent));
            if (null != description)
                m.put("Description", description);
            m.put("Workbook", workbook);
            Table.insert(null, core.getTableInfoContainers(), m);
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isConstraintException(x))
                throw new RuntimeSQLException(x);
            sqlx = x;
        }

        _clearChildrenFromCache(parent);
        
        Container c = getForPath(path);
        if (null == c && null != sqlx)
            throw new RuntimeSQLException(sqlx);

        //workbooks inherit perms from their parent so don't create a policy if this is a workbook
        if (!workbook)
            SecurityManager.setAdminOnlyPermissions(c);
        
        _removeFromCache(c); // seems odd, but it removes c.getProject() which clears other things from the cache

        fireCreateContainer(c);
        return c;
    }


    public static Container ensureContainer(String path)
    {
        return ensureContainer(Path.parse(path));
    }


    public static Container ensureContainer(Path path)
    {
        if (core.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

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
                c = ensureContainer(parentPath);
                if (null == c)
                    return null;
                c = createContainer(c, path.getName());
            }
        }
        return c;
    }


    public static Container ensureContainer(Container parent, String name)
    {
        if (core.getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("Transaction should not be active");

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

    public static Container createWorkbook(Container parent, String name, String description)
    {
        //parent must not be a workbook
        if (parent.isWorkbook())
            throw new IllegalArgumentException("Parent of a workbook must be a non-workbook container!");
        return createContainer(parent, name, description, true);
    }


    private static final String SHARED_CONTAINER_PATH = "/Shared";

    @NotNull
    public static Container getSharedContainer()
    {
        Container c = getForPath(SHARED_CONTAINER_PATH);
        if (null == c)
            c = bootstrapContainer(SHARED_CONTAINER_PATH,
                    RoleManager.getRole(SiteAdminRole.class),
                    RoleManager.getRole(ReaderRole.class),
                    RoleManager.getRole(NoPermissionsRole.class));
        return c;
    }


    private static final String LOSTANDFOUND_CONTAINER_NAME = "_LostAndFound";

    public static Container getLostAndFoundContainer()                                 
    {
        Container shared = getSharedContainer();
        return ensureContainer(shared, LOSTANDFOUND_CONTAINER_NAME);
    }


    public static List<Container> getChildren(Container parent)
    {
        return new ArrayList<Container>(getChildrenMap(parent).values());
    }
    

    public static List<Container> getChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        List<Container> children = new ArrayList<Container>();
        for (Container child : getChildrenMap(parent).values())
            if (child.hasPermission(u, perm))
                children.add(child);

        return children;
    }


    public static Set<Container> getAllChildren(Container parent, User u)
    {
        return getAllChildren(parent, u, ReadPermission.class);
    }
    

    public static Set<Container> getAllChildren(Container parent, User u, Class<? extends Permission> perm)
    {
        Set<Container> result = new HashSet<Container>();
        Container[] containers = getAllChildren(parent);
        for (Container container : containers)
        {
            if (container.hasPermission(u, perm))
            {
                result.add(container);
            }
        }

        return result;
    }
    

    // Returns true only if user has the specified permission in the entire container tree starting at root
    public static boolean hasTreePermission(Container root, User u,  Class<? extends Permission> perm)
    {
        Container[] all = getAllChildren(root);

        for (Container c : all)
            if (!c.hasPermission(u, perm))
                return false;

        return true;
    }


    static String[] emptyStringArray = new String[0];

    public static synchronized Map<String, Container> getChildrenMap(Container parent)
    {
        String[] childIds = (String[])getCache().get(_containerChildrenPrefix + parent.getId());
        if (null != childIds)
        {
            if (childIds == emptyStringArray)
                return Collections.emptyMap();
            Map<String,Container> ret = new TreeMap<String,Container>(String.CASE_INSENSITIVE_ORDER);
            for (String id : childIds)
            {
                Container c = ContainerManager.getForId(id);
                if (null != c)
                    ret.put(c.getName(), c);
            }
            assert null != (ret = Collections.unmodifiableMap(ret));
            return ret;
        }
        
        try
        {
            Container[] children = Table.executeQuery(core.getSchema(),
                    "SELECT * FROM " + core.getTableInfoContainers() + " WHERE Parent = ? ORDER BY SortOrder, LOWER(Name)",
                    new Object[]{parent.getId()},
                    Container.class);
            if (children.length == 0)
            {
                getCache().put(_containerChildrenPrefix + parent.getId(), emptyStringArray);
                return Collections.emptyMap();
            }
            Map<String,Container> ret = new TreeMap<String,Container>(String.CASE_INSENSITIVE_ORDER);
            childIds = new String[children.length];
            for (int i=0 ; i<children.length ; i++)
            {
                Container c = children[i];
                childIds[i] = c.getId();
                _addToCache(c);
                ret.put(c.getName(),c);
            }
            getCache().put(_containerChildrenPrefix + parent.getId(), childIds);
            return ret;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static Container getForRowId(String idString)
    {
        try
        {
            // Postgres 8.3 doesn't like it if we use a string as
            // an int parameter, so parse it first
            Integer id = Integer.parseInt(idString);
            Container[] ret = Table.executeQuery(
                    core.getSchema(),
                    "SELECT * FROM " + core.getTableInfoContainers() + " WHERE RowId = ?",
                    new Object[]{id}, Container.class);
            if (ret == null || ret.length == 0)
                return null;
            return ret[0];
        }
        catch(NumberFormatException nfe)
        {
            // That's certainly not going to match anything
            return null;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static synchronized Container getForId(String id)
    {
        Container d = _getFromCacheId(id);
        if (null != d)
            return d;

        //if the input string is not a GUID, just return null,
        //so that we don't get a SQLException when the database
        //tries to convert it to a unique identifier.
        if(null != id && !GUID.isGUID(id))
            return null;

        try
        {
            Container[] ret = Table.executeQuery(
                    core.getSchema(),
                    "SELECT * FROM " + core.getTableInfoContainers() + " WHERE EntityId = ?",
                    new Object[]{id}, Container.class);
            if (ret == null || ret.length == 0)
                return null;
            return _addToCache(ret[0]);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public static synchronized Container getChild(Container c, String name)
    {
        Path path = c.getParsedPath().append(name);
        
        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        Map<String,Container> map = ContainerManager.getChildrenMap(c);
        return map.get(name);
    }


    public static synchronized Container getForPath(String path)
    {
        Path p = Path.parse(path);
        return getForPath(p);
    }
    

    public static synchronized Container getForPath(Path path)
    {
        Container d = _getFromCachePath(path);
        if (null != d)
            return d;

        try
        {
            if (path.equals(Path.rootPath))
            {
                // Special case for ROOT.  Never return null -- either database error or corrupt database
                Container[] ret = Table.executeQuery(core.getSchema(),
                        "SELECT * FROM " + core.getTableInfoContainers() + " WHERE Parent IS NULL",
                        null, Container.class);

                if (null == ret || ret.length == 0)
                    throw new RootContainerException("Root container does not exist");

                if (ret.length > 1)
                    throw new RootContainerException("More than one root container was found");

                if (null == ret[0])
                    throw new RootContainerException("Root container is NULL");

                _addToCache(ret[0]);
                return ret[0];
            }
            else
            {
                Path parent = path.getParent();
                String name = path.getName();
                Container dirParent = getForPath(parent);

                if (null == dirParent)
                    return null;

                Map<String,Container> map = ContainerManager.getChildrenMap(dirParent);
                return map.get(name);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    @SuppressWarnings({"serial"})
    public static class RootContainerException extends RuntimeException
    {
        private RootContainerException(String message)
        {
            super(message);
        }
    }


    public static Container getRoot()
    {
        return getForPath("/");
    }


    public static void saveAliasesForContainer(Container container, List<String> aliases) throws SQLException
    {
        core.getSchema().getScope().beginTransaction();

        try
        {
            SQLFragment deleteSQL = new SQLFragment();
            deleteSQL.append("DELETE FROM ");
            deleteSQL.append(core.getTableInfoContainerAliases());
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
            Table.execute(core.getSchema(), deleteSQL);

            Set<String> caseInsensitiveAliases = new CaseInsensitiveHashSet(aliases);

            for (String alias : caseInsensitiveAliases)
            {
                SQLFragment insertSQL = new SQLFragment();
                insertSQL.append("INSERT INTO ");
                insertSQL.append(core.getTableInfoContainerAliases());
                insertSQL.append(" (Path, ContainerId) VALUES (?, ?)");
                insertSQL.add(alias);
                insertSQL.add(container.getId());
                Table.execute(core.getSchema(), insertSQL);
            }

            core.getSchema().getScope().commitTransaction();
        }
        finally
        {
            core.getSchema().getScope().closeConnection();
        }
    }


    // Used for attaching system resources (favorite icon, logo) to the root container
    public static class ContainerParent implements AttachmentParent
    {
        Container _c;

        public ContainerParent(Container c)
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

        public void setAttachments(Collection<Attachment> attachments)
        {
        }
    }

    // Used for attaching system resources (favorite icon, logo) to the root container
    public static class RootContainer extends ContainerParent
    {
        private RootContainer(Container c)
        {
            super(c);
        }

        public static RootContainer get()
        {
            Container root = getRoot();

            if (null == root)
                return null;
            else
                return new RootContainer(root);
        }
    }


    public static NavTree getProjectList(ViewContext context)
    {
        if (context.getUser().isAdministrator())
            return getProjectListForAdmin(context);
        else
            return getProjectListForUser(context);
    }


    public static Container getHomeContainer()
    {
        //TODO: Allow people to set the home container?
        return getForPath(HOME_PROJECT_PATH);
    }

    public static List<Container> getProjects()
    {
        return ContainerManager.getChildren(ContainerManager.getRoot());
    }

    private static NavTree getProjectListForAdmin(ViewContext context)
    {
        // CONSIDER: use UserManager.getAdmin() here for user
        NavTree navTree = (NavTree) NavTreeManager.getFromCache(PROJECT_LIST_ID, context);
        if (null != navTree)
            return navTree;

        NavTree list = new NavTree("Projects");
        List<Container> projects = ContainerManager.getProjects();

        for (Container project : projects)
        {
            ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(project);

            if (project.equals(getHomeContainer()))
                list.addChild(0, new NavTree("Home", startURL));
            else
                list.addChild(project.getName(), startURL);
        }
        list.setId(PROJECT_LIST_ID);
        NavTreeManager.cacheTree(list, context.getUser());
        return list;
    }


    private static NavTree getProjectListForUser(ViewContext context)
    {
        NavTree tree = (NavTree) NavTreeManager.getFromCache(PROJECT_LIST_ID, context);
        if (null != tree)
            return tree;
        User user = context.getUser();
        try
        {
            NavTree list = new NavTree("Projects");
            // Use a tree set so that we only add each container once, and they're sorted correctly
            Set<Container> containerSet = new TreeSet<Container>();

            String[] ids;

            // If user is being impersonated within a project then only that project should appear in the list
            if (user.isImpersonated() && null != user.getImpersonationProject())
            {
                ids = new String[]{user.getImpersonationProject().getId()};
            }
            else
            {
                ids = Table.executeArray(
                    core.getSchema(),
                    "SELECT DISTINCT Container\n" +
                            "FROM " + core.getTableInfoPrincipals() + " P INNER JOIN " + core.getTableInfoMembers() + " M ON P.UserId = M.GroupId\n" +
                            "WHERE M.UserId = ?",
                    new Object[]{user.getUserId()},
                    String.class);
            }

            for (String id : ids)
            {
                if (id == null)
                    continue;
                Container c = ContainerManager.getForId(id);
                if (null == c || !c.isProject() || !c.shouldDisplay())
                    continue;
                containerSet.add(c);
            }

            // find all projects that have public permissions to root directory
            Container root = ContainerManager.getRoot();
            ids = Table.executeArray(
                    core.getSchema(),
                    "SELECT EntityId\n" +
                            "FROM " + core.getTableInfoContainers() + "\n" +
                            "WHERE Parent = ? ORDER BY SortOrder, LOWER(Name)",
                    new Object[]{root.getId()},
                    String.class);

            for (String id : ids)
            {
                Container c = ContainerManager.getForId(id);
                if (null == c || !c.shouldDisplay())
                    continue;
                //ensure that user has permissions on container, and that container is not already in nav tree set
                if (c.hasPermission(user, ReadPermission.class))
                {
                    containerSet.add(c);
                }
            }
            
            for (Container c : containerSet)
            {
                String name = c.equals(getHomeContainer()) ? "Home" : c.getName();
                list.addChild(name, PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c));
            }

            list.setId(PROJECT_LIST_ID);
            NavTreeManager.cacheTree(list, user);
            return list;
        }
        catch (SQLException x)
        {
            _log.error(x);
            return null;
        }
    }


    public static NavTree getFolderListForUser(Container project, ViewContext viewContext)
    {
        Container c = viewContext.getContainer();

        NavTree tree = (NavTree) NavTreeManager.getFromCache(project.getId(), viewContext);
        if (null != tree)
            return tree;
        User user = viewContext.getUser();
        String projectId = project.getId();

        Container[] folders = ContainerManager.getAllChildren(project);

        Arrays.sort(folders);

        Set<Container> containersInTree = new HashSet<Container>();

        Map<String, NavTree> m = new HashMap<String, NavTree>();
        for (Container f : folders)
        {
            if (f.isWorkbook())
                continue;
            
            Set<Class<? extends Permission>> perms = f.getPolicy().getPermissions(user);
            boolean skip = (perms.size() == 0 || (!f.shouldDisplay()));
            //Always put the project and current container in...
            if (skip && !f.equals(project) && !f.equals(c))
                continue;

            //HACK to make home link consistent...
            String name = f.getName();
            if (name.equals("home") && f.equals(getHomeContainer()))
                name = "Home";

            NavTree t = new NavTree(name);
            if (perms.size() > 0)
            {
                ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(f);
                t.second = (url.getEncodedLocalURIString());
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

    public static Set<Container> containersToRoot(Container child)
    {
        Set<Container> containersOnPath = new HashSet<Container>();
        Container current = child;
        while (current != null && !current.isRoot())
        {
            containersOnPath.add(current);
            current = current.getParent();
        }

        return containersOnPath;
    }


    // Move a container to another part of the container tree.  Careful: this method DOES NOT prevent you from orphaning
    // an entire tree (e.g., by setting a container's parent to one of its children); the UI in AdminController does this.
    //
    // NOTE: Beware side-effect of changing ACLs and GROUPS if a container changes projects
    //
    // @return true if project has changed (should probably redirect to security page)
    public static boolean move(Container c, Container newParent)
    {
        if (c.isRoot())
            throw new IllegalArgumentException("can't move root container");

        if (c.getParent().getId().equals(newParent.getId()))
            return false;

        Container oldParent = c.getParent();
        Container oldProject = c.getProject();
        Container newProject = newParent.isRoot() ? c : newParent.getProject();

        boolean changedProjects = !oldProject.getId().equals(newProject.getId());

        try
        {
            core.getSchema().getScope().beginTransaction();

            Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoContainers() + " SET Parent=? WHERE EntityId=?", new Object[]{newParent.getId(), c.getId()});

            // this could be done in the trigger, but I prefer to put it in the transaction
            if (changedProjects)
                SecurityManager.changeProject(c, oldProject, newProject);

            core.getSchema().getScope().commitTransaction();

            _clearCache();  // Clear the entire cache, since containers cache their full paths
            Container newContainer = getForId(c.getId());
            fireMoveContainer(newContainer, oldParent);
        }
        catch (SQLException e)
        {
            core.getSchema().getScope().rollbackTransaction();
            _log.error(e);
        }

        return changedProjects;
    }


    // Rename a container in the table.  Will fail if the new name already exists in the parent container.
    // Lock the class to ensure the old version of this container doesn't sneak into the cache after clearing
    public static synchronized void rename(Container c, String name)
    {
        name = StringUtils.trimToNull(name);
        if (null == name)
            throw new NullPointerException();
        try
        {
            String oldName = c.getName();
            if (oldName.equals(name))
                return;
            Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoContainers() + " SET Name=? WHERE EntityId=?", new Object[]{name, c.getId()});
            _clearCache();  // Clear the entire cache, since containers cache their full paths
            //Get new version since name has changed.
            c = getForId(c.getId());
            fireRenameContainer(c, oldName);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }
    }

    public static void setPublishBit(Container c, Boolean bit) throws SQLException
    {
        Table.execute(core.getSchema(), "UPDATE " + core.getTableInfoContainers() + " SET CaBIGPublished = ? WHERE EntityId = ?", new Object[]{bit, c.getId()});
    }

    public static void setChildOrderToAlphabetical(Container parent)
    {
        setChildOrder(parent.getChildren(), true);
    }

    public static void setChildOrder(Container parent, List<Container> orderedChildren)
    {
        for (Container child : orderedChildren)
        {
            if (!child.getParent().equals(parent))
                throw new IllegalArgumentException(parent.getPath() + " is not parent of " + child.getPath());
        }
        setChildOrder(orderedChildren, false);
    }

    private static synchronized void setChildOrder(List<Container> siblings, boolean resetToAlphabetical)
    {
        DbSchema schema = core.getSchema();
        try
        {
            schema.getScope().beginTransaction();
            for (int index = 0; index < siblings.size(); index++)
            {
                Container current = siblings.get(index);
                Table.execute(schema, "UPDATE " + core.getTableInfoContainers() + " SET SortOrder = ? WHERE EntityId = ?",
                        new Object[]{resetToAlphabetical ? 0 : index, current.getId()});
            }
            schema.getScope().commitTransaction();
            _clearCache();  // Clear the entire cache, since container lists are cached in order
        }
        catch (SQLException e)
        {
            _log.error(e);
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (schema.getScope().isTransactionActive())
                schema.getScope().rollbackTransaction();
        }
    }

    // Delete a container from the database.
    // Lock the class to ensure the old version of this container doesn't sneak into the cache after clearing
    public static boolean delete(Container c, User user)
    {
        ResultSet rs = null;

        try
        {
            // check to ensure no children exist
            rs = Table.executeQuery(core.getSchema(), "SELECT EntityId FROM " + core.getTableInfoContainers() +
                    " WHERE Parent = ?", new Object[]{c.getId()});
            if (rs.next())
            {
                _removeFromCache(c);
                return false;
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
           ResultSetUtil.close(rs);
        }

        List<Throwable> errors = fireDeleteContainer(c, user);

        if (errors.size() != 0)
        {
            Throwable first = errors.get(0);
            if (first instanceof RuntimeException)
                throw (RuntimeException)first;
            else
               throw new RuntimeException(first);
        }

        try
        {
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoContainerAliases() + " WHERE ContainerId=?", new Object[]{c.getId()});
            Table.execute(core.getSchema(), "DELETE FROM " + core.getTableInfoContainers() + " WHERE EntityId=?", new Object[]{c.getId()});
            // now that the container is actually gone, delete all ACLs (better to have an ACL w/o object than object w/o ACL)
            SecurityManager.removeAll(c);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            _removeFromCache(c);
        }
        return true;
    }


    public static void deleteAll(Container root, User user) throws UnauthorizedException
    {
        if (!hasTreePermission(root, user, DeletePermission.class))
            throw new UnauthorizedException("You don't have delete permissions to all folders");

        LinkedHashSet<Container> depthFirst = getAllChildrenDepthFirst(root);
        depthFirst.add(root);

        for (Container c : depthFirst)
            delete(c, user);
    }


    private static LinkedHashSet<Container> getAllChildrenDepthFirst(Container c)
    {
        LinkedHashSet<Container> set = new LinkedHashSet<Container>();
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

    
    private static synchronized Container[] _getAllChildrenFromCache(Container c)
    {
        return (Container[]) getCache().get(_containerAllChildrenPrefix + c.getId());
    }

    private static void _addAllChildrenToCache(Container c, Container[] children)
    {
        assert Thread.holdsLock(ContainerManager.class) : "Any insertion into the cache must be synchronized at a " +
                "higher level so that we ensure that the container to be inserted still exists and hasn't been deleted";
        getCache().put(_containerAllChildrenPrefix + c.getId(), children);
    }


    private static synchronized Container _getFromCacheId(String id)
    {
        return (Container) getCache().get(_containerPrefix + id);
    }


    private static synchronized Container _getFromCachePath(Path path)
    {
        return (Container) getCache().get(_containerPrefix + toString(path));
    }


    // UNDONE: use Path directly instead of toString()
    private static String toString(Container c)
    {
        return StringUtils.strip(c.getPath(),"/").toLowerCase();
    }
    private static String toString(Path p)
    {
        return StringUtils.strip(p.toString(),"/").toLowerCase();
    }


    private static Container _addToCache(Container c)
    {
        assert Thread.holdsLock(ContainerManager.class) : "Any insertion into the cache must be synchronized at a " +
                "higher level so that we ensure that the container to be inserted still exists and hasn't been deleted";
        getCache().put(_containerPrefix + toString(c), c);
        getCache().put(_containerPrefix + c.getId(), c);
        return c;
    }


    private static synchronized void _clearChildrenFromCache(Container c)
    {
        Cache cache = getCache();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (cache)
        {
            cache.remove(_containerChildrenPrefix + c.getId());
        }

        // UNDONE: NavTreeManager should register a ContainerListener
        Container project = c.getProject();
        NavTreeManager.uncacheTree(PROJECT_LIST_ID);
        if (project != null)
            NavTreeManager.uncacheTree(project.getId());
    }


    private static synchronized void _removeFromCache(Container c)
    {
        Container project = c.getProject();
        Container parent = c.getParent();

        Cache cache = getCache();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (cache)
        {
            cache.remove(_containerPrefix + toString(c));
            cache.remove(_containerPrefix + c.getId());
            cache.remove(_containerChildrenPrefix + c.getId());

            if (null != parent)
                cache.remove(_containerChildrenPrefix + parent.getId());

            // blow away the all children caches
            cache.removeUsingPrefix(_containerChildrenPrefix);
        }

        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheTree(PROJECT_LIST_ID);
        if (project != null)
            NavTreeManager.uncacheTree(project.getId());
    }


    private static synchronized void _clearCache()
    {
        getCache().clear();

        // UNDONE: NavTreeManager should register a ContainerListener
        NavTreeManager.uncacheAll();
    }


    public static void notifyContainerChange(String id)
    {
        Container c = getForId(id);
        if (null != c)
        {
            _removeFromCache(c);
            ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, Property.Policy, null, null);
            firePropertyChangeEvent(evt);
        }
    }


    /** including root node */
    public static synchronized Container[] getAllChildren(Container root)
    {
        Container[] allChildren = _getAllChildrenFromCache(root);
        if (allChildren != null)
            return allChildren.clone(); // don't let callers modify the array in the cache

        LinkedHashSet<Container> containerList = getAllChildrenDepthFirst(root);
        containerList.add(root);

        allChildren = containerList.toArray(new Container[containerList.size()]);
        _addAllChildrenToCache(root, allChildren);
        return allChildren.clone(); // don't let callers modify the array in the cache
    }


    public static long getContainerCount()
    {
        try
        {
            return Table.rowCount(core.getTableInfoContainers());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    // Retrieve entire container hierarchy
    public static MultiMap<Container, Container> getContainerTree()
    {
        MultiMap<Container, Container> mm = new MultiHashMap<Container, Container>();

        ResultSet rs = null;
        try
        {
            // Get all containers and parents
            rs = Table.executeQuery(core.getSchema(), "SELECT Parent, EntityId FROM " + core.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC", null);

            while (rs.next())
            {
                String parentId = rs.getString(1);
                Container parent = (parentId != null ? getForId(parentId) : null);
                Container child = getForId(rs.getString(2));

                if (null != child)
                    mm.put(parent, child);
            }

            for (Object key : mm.keySet())
            {
                List<Container> siblings = new ArrayList<Container>(mm.get(key));
                Collections.sort(siblings);
            }
        }
        catch (SQLException x)
        {
            _log.error("getContainerTree: ", x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        return mm;
    }

    /**
     * Returns a branch of the container tree including only the root and its descendants
     * @param root The root container
     * @return MultiMap of containers including root and its descendants
     */
    public static MultiMap<Container, Container> getContainerTree(Container root)
    {
        //build a multimap of only the container ids
        MultiMap<String, String> mmIds = new MultiHashMap<String, String>();

        ResultSet rs = null;
        try
        {
            // Get all containers and parents
            rs = Table.executeQuery(core.getSchema(), "SELECT Parent, EntityId FROM " + core.getTableInfoContainers() + " ORDER BY SortOrder, LOWER(Name) ASC", null);

            while (rs.next())
            {
                mmIds.put(rs.getString(1), rs.getString(2));
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }

        //now find the root and build a MultiMap of it and its descendants
        MultiMap<Container,Container> mm = new MultiHashMap<Container,Container>();
        mm.put(null, root);
        addChildren(root, mmIds, mm);
        for (Object key : mm.keySet())
        {
            List<Container> siblings = new ArrayList<Container>(mm.get(key));
            Collections.sort(siblings);
        }
        return mm;
    }

    private static void addChildren(Container c, MultiMap<String,String> mmIds, MultiMap<Container,Container> mm)
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


    @Deprecated
    public static Set<Container> getContainerSet(MultiMap<Container, Container> mm, User user, int perm)
    {
        //noinspection unchecked
        Collection<Container> containers = mm.values();
        if (null == containers)
            return new HashSet<Container>();

        Set<Container> set = new HashSet<Container>(containers.size());

        for (Container c : containers)
        {
            if (c.hasPermission(user, perm))
                set.add(c);
        }

        return set;
    }


    public static Set<Container> getContainerSet(MultiMap<Container, Container> mm, User user, Class<? extends Permission> perm)
    {
        //noinspection unchecked
        Collection<Container> containers = mm.values();
        if (null == containers)
            return new HashSet<Container>();

        Set<Container> set = new HashSet<Container>(containers.size());

        for (Container c : containers)
        {
            if (c.hasPermission(user, perm))
                set.add(c);
        }

        return set;
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

        List<String> ids = new ArrayList<String>(containers.size());

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

        void containerCreated(Container c);

        void containerDeleted(Container c, User user);
    }


    public static class ContainerPropertyChangeEvent extends PropertyChangeEvent
    {
        public final Property property;
        public final Container container;
        
        public ContainerPropertyChangeEvent(Container c, Property p, Object oldValue, Object newValue)
        {
            super(c, p.name(), oldValue, newValue);
            container = c;
            property = p;
        }
    }


    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<ContainerListener> _listeners = new CopyOnWriteArrayList<ContainerListener>();
    private static final List<ContainerListener> _laterListeners = new CopyOnWriteArrayList<ContainerListener>();

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
        List<ContainerListener> combined = new ArrayList<ContainerListener>(_listeners.size() + _laterListeners.size());
        combined.addAll(_listeners);
        combined.addAll(_laterListeners);

        return combined;
    }


    protected static void fireCreateContainer(Container c)
    {
        List<ContainerListener> list = getListeners();
        for (ContainerListener cl : list)
            try
            {
                cl.containerCreated(c);
            }
            catch (Throwable t)
            {
                _log.error("fireCreateContainer", t);
            }
    }


    protected static List<Throwable> fireDeleteContainer(Container c, User user)
    {
        List<ContainerListener> list = getListeners();
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ContainerListener l : list)
        {
            try
            {
                l.containerDeleted(c, user);
            }
            catch (Throwable t)
            {
                _log.error("fireDeleteContainer", t);
                errors.add(t);
            }
        }
        return errors;
    }


    protected static void fireRenameContainer(Container c, String oldValue)
    {
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, Property.Name, oldValue, c.getName());
        firePropertyChangeEvent(evt);
    }


    protected static void fireMoveContainer(Container c, Container oldParent)
    {
        ContainerPropertyChangeEvent evt = new ContainerPropertyChangeEvent(c, Property.Parent, oldParent, c.getParent());
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
                _log.error("firePropertyChangeEvent", t);
            }
        }
    }


    public static Container getDefaultSupportContainer()
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
        try
        {
            return Table.executeArray(core.getSchema(), "SELECT Path FROM " + core.getTableInfoContainerAliases() + " WHERE ContainerId = ? ORDER BY LOWER(Path)", new Object[] { c.getId() }, String.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static Container getForPathAlias(String path)
    {
        try
        {
            Container[] ret = Table.executeQuery(core.getSchema(),
                    "SELECT * FROM " + core.getTableInfoContainers() + " c, " + core.getTableInfoContainerAliases() + " ca WHERE ca.ContainerId = c.EntityId AND LOWER(ca.path) = LOWER(?)",
                    new Object[]{path}, Container.class);
            if (null == ret || ret.length == 0)
                return null;
            return ret[0];
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
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
                _log.debug("Creating new container for path '" + path + "'");
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
                policyCount = Table.executeSingleton(core.getSchema(),
                    "SELECT COUNT(*) FROM " + core.getTableInfoPolicies() + " WHERE ResourceId = ?",
                    new Object[]{c.getId()}, Integer.class);
            }

            if (newContainer || 0 == policyCount.intValue())
            {
                _log.debug("Setting permissions for '" + path + "'");
                MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), adminRole);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupUsers), userRole);
                policy.addRoleAssignment(SecurityManager.getGroup(Group.groupGuests), guestRole);
                SecurityManager.savePolicy(policy);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return c;
    }


    public static class TestCase extends junit.framework.TestCase implements ContainerListener
    {
        Map<Path,Container> _containers = new HashMap<Path,Container>();
        Container _testRoot = null;        

        public TestCase()
        {
            super();
        }


        public TestCase(String name)
        {
            super(name);
        }


        @Override
        protected void setUp() throws Exception
        {
            if (null == _testRoot)
            {
                Container junit = ContainerManager.ensureContainer("/Shared/_junit");
                _testRoot = ContainerManager.ensureContainer(junit, "ContainerManager$TestCase-" + GUID.makeGUID());
                ContainerManager.addContainerListener(this);
            }
        }


        @Override
        protected void tearDown() throws Exception
        {
            ContainerManager.removeContainerListener(this);
            if (null != _testRoot)
                ContainerManager.deleteAll(_testRoot, TestContext.get().getUser());
        }


        public void testCreateDeleteContainers() throws Exception
        {
            int count = 20;
            Random random = new Random();
            MultiMap<String, String> mm = new MultiHashMap<String, String>();

            for (int i = 1; i <= count; i++)
            {
                int parentId = random.nextInt(i);
                String parentName = 0 == parentId ? _testRoot.getName() : String.valueOf(parentId);
                String childName = String.valueOf(i);
                mm.put(parentName, childName);
            }

            logNode(mm, _testRoot.getName(), 0);
            createContainers(mm, _testRoot.getName(), _testRoot);
            assertEquals(count, _containers.size());
            cleanUpChildren(mm, _testRoot.getName(), _testRoot);
            assertEquals(0, _containers.size());
        }


        public void testCache() throws Exception
        {
            assertEquals(0, _containers.size());
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());

            Container one = ContainerManager.createContainer(_testRoot, "one");
            assertEquals(1, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, ContainerManager.getChildren(one).size());

            Container oneA = ContainerManager.createContainer(one,"A");
            assertEquals(2, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(1, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneA).size());

            Container oneB = ContainerManager.createContainer(one,"B");
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneB).size());

            Container deleteme = ContainerManager.createContainer(one,"deleteme");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(deleteme).size());

            ContainerManager.delete(deleteme, TestContext.get().getUser());
            assertEquals(3, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(2, ContainerManager.getChildren(one).size());

            Container oneC = ContainerManager.createContainer(one,"C");
            assertEquals(4, _containers.size());
            assertEquals(1, ContainerManager.getChildren(_testRoot).size());
            assertEquals(3, ContainerManager.getChildren(one).size());
            assertEquals(0, ContainerManager.getChildren(oneC).size());

            ContainerManager.delete(oneC, TestContext.get().getUser());
            ContainerManager.delete(oneB, TestContext.get().getUser());
            assertEquals(1, ContainerManager.getChildren(one).size());

            ContainerManager.delete(oneA, TestContext.get().getUser());
            assertEquals(0, ContainerManager.getChildren(one).size());

            ContainerManager.delete(one, TestContext.get().getUser());
            assertEquals(0, ContainerManager.getChildren(_testRoot).size());
            assertEquals(0, _containers.size());
        }


        private static void createContainers(MultiMap<String, String> mm, String name, Container parent)
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


        private static void cleanUpChildren(MultiMap<String, String> mm, String name, Container parent)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                Container child = getForPath(makePath(parent, childName));
                cleanUpChildren(mm, childName, child);
                assertTrue(ContainerManager.delete(child, null));
            }
        }


        private static void logNode(MultiMap<String, String> mm, String name, int offset)
        {
            Collection<String> nodes = mm.get(name);

            if (null == nodes)
                return;

            for (String childName : nodes)
            {
                _log.debug(StringUtils.repeat("   ", offset) + childName);
                logNode(mm, childName, offset + 1);
            }
        }


        // ContainerListener
        public void propertyChange(PropertyChangeEvent evt)
        {
        }


        public void containerCreated(Container c)
        {
            if (null == _testRoot || !c.getParsedPath().startsWith(_testRoot.getParsedPath()))
                return;
            _containers.put(c.getParsedPath(), c);
        }


        public void containerDeleted(Container c, User user)
        {
            _containers.remove(c.getParsedPath());
        }

        
        public static Test suite()
        {
            return new TestSuite(TestCase.class);
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
            // _ts, createdby, cabigpublished
            String description = rs.getString("Description");
            boolean workbook = rs.getBoolean("Workbook");

            Container dirParent = null;
            if (null != parentId)
                dirParent = getForId(parentId);

            d = new Container(dirParent, name, id, rowId, sortOrder, created);
            d.setDescription(description);
            d.setWorkbook(workbook);
            return d;
        }

        public Container[] handleArray(ResultSet rs) throws SQLException
        {
            ArrayList<Container> list = new ArrayList<Container>();
            while (rs.next())
            {
                list.add(handle(rs));
            }
            return list.toArray(new Container[list.size()]);
        }
    }
}
