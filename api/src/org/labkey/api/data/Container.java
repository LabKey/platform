/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.EnableRestrictedModules;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.springframework.validation.BindException;

import java.io.File;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Basic hierarchical structure for holding data within LabKey Server. Security is configured at the container level.
 * Projects, folders, and workbooks are all different types of containers.
 *
 * {@link ContainerManager} for more info
 *
 * CONSIDER: extend {@link org.labkey.api.data.Entity}
 */
public class Container implements Serializable, Comparable<Container>, SecurableResource, ContainerContext, HasPermission, Parameter.JdbcParameterValue
{
    private GUID _id;
    private Path _path;
    private Date _created;
    private int _createdBy;
    private int _rowId; //Unique for this installation

    /** Used to arbitrarily reorder siblings within a container. */
    private int _sortOrder;
    private String _description;

    private transient Module _defaultModule;

    private transient WeakReference<Container> _parent;

    public static final String DEFAULT_SUPPORT_PROJECT_PATH = ContainerManager.HOME_PROJECT_PATH + "/support";

    public enum TYPE
    {
        normal,
        workbook,
        tab;

        public static TYPE typeFromString(String s)
        {
            if (null != s)
            {
                if (s.equalsIgnoreCase("workbook")) return workbook;
                if (s.equalsIgnoreCase("tab")) return tab;
            }
            return normal;
        }

    }

    //is this container a workbook, tab or normal?
    private TYPE _type;

    // include in results from searches outside this container?
    private final boolean _searchable;

    //optional non-unique title for the container
    private String _title;

    // UNDONE: BeanFactory for Container

    protected Container(Container dirParent, String name, String id, int rowId, int sortOrder, Date created, int createdBy, boolean searchable)
    {
        _path = null == dirParent && StringUtils.isEmpty(name) ? Path.rootPath : ContainerManager.makePath(dirParent, name);
        _id = new GUID(id);
        _parent = new WeakReference<>(dirParent);
        _rowId = rowId;
        _sortOrder = sortOrder;
        _created = created;
        _createdBy = createdBy;
        _searchable = searchable;
    }


    public Container getContainer(Map context)
    {
        return this;
    }


    @NotNull
    public String getName()
    {
        return _path.getName();
    }

    /**
     * Returns the folder title for normal folders, or the name (i.e. number) for workbooks
     */
    public String getDisplayTitle(){
        return isWorkbook() ? getName() : getTitle();
    }

    /**
     * Returns the current container for normal folders, or the parent folder for workbooks
     */
    @NotNull
    public Container getSelfOrWorkbookParent()
    {
        return isWorkbook() && getParent() != null ? getParent() : this;
    }

    @NotNull
    public String getResourceName()
    {
        return _path.getName();
    }

    public Date getCreated()
    {
        return _created;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }


    public boolean isInheritedAcl()
    {
        return !(getPolicy().getResourceId().equals(getId()));
    }

    /**
     * @return the parent container, or the root container (with path "/") if called on the root
     */
    public Container getParent()
    {
        Container parent = _parent == null ? null : _parent.get();
        if (null == parent && _path.size() > 0)
        {
            parent = ContainerManager.getForPath(_path.getParent());
            _parent = new WeakReference<>(parent);
        }
        return parent;
    }

    /**
     * @return the unencoded container path ignoring "/"
     */
    public String getPath()
    {
        return _path.toString("/","");
    }


    public Path getParsedPath()
    {
        return _path;
    }


    /**
     * returned string begins and ends with "/" so you can slap it between
     * getContextPath() and action.view
     */
    public String getEncodedPath()
    {
        return _path.encode("/","/");
    }


    public String getId()
    {
        return _id.toString();
    }

    public GUID getEntityId()
    {
        return _id;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public int getSortOrder()
    {
        return _sortOrder;
    }

    public void setSortOrder(int sortOrder)
    {
        _sortOrder = sortOrder;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    /**
     * Get the project Container or null if isRoot().
     * @return The project Container or null if isRoot().
     */
    public @Nullable Container getProject()
    {
        // Root has no project
        if (isRoot())
            return null;

        Container project = this;
        while (!project.isProject())
        {
            project = project.getParent();
            if (null == project)        // deleted container?
                return null;
        }
        return project;
    }


    // Note: don't use the security policy directly unless you really have to... call hasPermission() or hasOneOf()
    // instead, to ensure proper behavior during impersonation.
    public @NotNull SecurityPolicy getPolicy()
    {
        return SecurityPolicyManager.getPolicy(this);
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(logMsg, user, perm);
    }


    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(user, perm);
    }


    public boolean hasPermission(String logMsg, @NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(logMsg, user, perm, contextualRoles);
    }


    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        if (user instanceof User && isForbiddenProject((User)user))
            return false;
        return getPolicy().hasPermission(user, perm, contextualRoles);
    }

    public boolean hasPermissions(@NotNull User user, @NotNull Set<Class<? extends Permission>> permissions)
    {
        if (isForbiddenProject(user))
            return false;
        return getPolicy().hasPermissions(user, permissions);
    }

    public boolean hasOneOf(@NotNull User user, @NotNull Collection<Class<? extends Permission>> perms)
    {
        return !isForbiddenProject(user) && getPolicy().hasOneOf(user, perms, null);
    }

    @SafeVarargs
    public final boolean hasOneOf(@NotNull User user, @NotNull Class<? extends Permission>... perms)
    {
        return hasOneOf(user, Arrays.asList(perms));
    }


    public boolean isForbiddenProject(User user)
    {
        if (null != user)
        {
            @Nullable Container impersonationProject = user.getImpersonationProject();

            // Root is never forbidden (site admin case), otherwise, impersonation project must match current project
            if (null != impersonationProject && !impersonationProject.equals(getProject()))
                return true;
        }

        return false;
    }


    public boolean isProject()
    {
        return _path.size() == 1;
    }


    public boolean isRoot()
    {
        return _path.size() == 0;
    }


    public boolean shouldDisplay(User user)
    {
        if (isWorkbookOrTab())
            return false;

        String name = _path.getName();
        if (name.length() == 0)
            return true; // Um, I guess we should display it?
        char c = name.charAt(0);
        if (c == '_' || c == '.')
        {
            return user != null && (user.hasRootAdminPermission() || hasPermission(user, AdminPermission.class));
        }
        else
        {
            return true;
        }
    }


    public boolean isWorkbook()
    {
        return _type == TYPE.workbook;
    }

    public boolean isWorkbookOrTab()
    {
        return TYPE.workbook == _type || TYPE.tab == _type;
    }

    Boolean hasWorkbookChildren = null;

    public synchronized boolean hasWorkbookChildren()
    {
        if (null == hasWorkbookChildren)
        {
            hasWorkbookChildren = false;
            for (Container ch : getChildren())
            {
                if (ch.isWorkbook())
                {
                    hasWorkbookChildren = true;
                    break;
                }
            }
        }
        return hasWorkbookChildren;
    }

    public boolean isSearchable()
    {
        return _searchable;
    }

    /**
     * Returns true if possibleAncestor is a parent of this container,
     * or a parent-of-a-parent, etc.
     */
    public boolean hasAncestor(Container possibleAncestor)
    {
        if (isRoot() || getParent() == null)
            return false;
        if (getParent().equals(possibleAncestor))
            return true;
        return getParent().hasAncestor(possibleAncestor);
    }

    public Container getChild(String folderName)
    {
        return ContainerManager.getChild(this,folderName);
    }

    public boolean hasChild(String folderName)
    {
        return getChild(folderName) != null;
    }

    public boolean hasChildren()
    {
        return getChildren().size() > 0;
    }

    public List<Container> getChildren()
    {
        return ContainerManager.getChildren(this);
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        List<SecurableResource> ret = new ArrayList<>();

        //add all sub-containers the user is allowed to read
        ret.addAll(ContainerManager.getChildren(this, user, ReadPermission.class));

        //add resources from study
        StudyService sts = StudyService.get();
        if (null != sts)
            ret.addAll(sts.getSecurableResources(this, user));

        //add report descriptors
        //this seems much more cumbersome than it should be
        for (Report report : ReportService.get().getReports(user, this))
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(report.getDescriptor());
            if (policy.hasPermission(user, AdminPermission.class))
                ret.add(report.getDescriptor());
        }

        //add pipeline root
        PipeRoot root = PipelineService.get().findPipelineRoot(this);
        if (null != root)
        {
            SecurityPolicy policy = SecurityPolicyManager.getPolicy(root);
            if (policy.hasPermission(user, AdminPermission.class))
                ret.add(root);
        }

        if (isRoot())
        {
            SearchService ss = SearchService.get();

            if (null != ss)
            {
                ret.addAll(ss.getSecurableResources(user));
            }
        }

        return ret;
    }

    /**
     * Finds a securable resource within this container or child containers with the same id
     * as the given resource id.
     * @param resourceId The resource id to find
     * @param user The current user (searches only resources that user can see)
     * @return The resource or null if not found
     */
    @Nullable
    public SecurableResource findSecurableResource(String resourceId, User user)
    {
        if (null == resourceId)
            return null;

        if (getResourceId().equals(resourceId))
            return this;

        //recurse down all non-container resources
        SecurableResource resource = findSecurableResourceInContainer(resourceId, user, this);
        if (null != resource)
            return resource;

        //recurse down child containers
        for(Container child : getChildren())
        {
            //only look in child containers where the user has read perm
            if (child.hasPermission(user, ReadPermission.class))
            {
                resource = child.findSecurableResource(resourceId, user);

                if (null != resource)
                    return resource;
            }
        }

        return null;
    }

    protected SecurableResource findSecurableResourceInContainer(String resourceId, User user, SecurableResource parent)
    {
        for (SecurableResource child : parent.getChildResources(user))
        {
            if (child instanceof Container)
                continue;

            if (child.getResourceId().equals(resourceId))
                return child;

            SecurableResource resource = findSecurableResourceInContainer(resourceId, user, child);

            if (null != resource)
                return resource;
        }

        return null;
    }


    public String toString()
    {
        return getClass().getName() + "@" + System.identityHashCode(this) + " " + _path + " " + _id;
    }


    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Container container = (Container) o;

        return _id == null ? container._id == null : _id.equals(container._id);
    }


    public int hashCode()
    {
        return _id.hashCode();
    }


    public static boolean isLegalName(String name, boolean isProject, StringBuilder error)
    {
        if (StringUtils.isBlank(name))
        {
            error.append("Blank names are not allowed.");
            return false;
        }

        if (name.length() > 255)
        {
            error.append("Folder name must be shorter than 255 characters");
            return false;
        }

        if (!FileUtil.isLegalName(name))
        {
            error.append("Folder name must be a legal filename and not contain one of '/', '\\', ':', '?', '<', '>', '*', '|', '\"', '^'");
            return false;
        }

        if (name.contains(";"))
        {
            error.append("Semicolons are not allowed in folder names.");
            return false;
        }

        if (name.startsWith("@"))
        {
            error.append("Folder name may not begin with '@'.");
            return false;
        }

        if (StringUtils.endsWithIgnoreCase(name, ".view") || StringUtils.endsWithIgnoreCase(name, ".api") || StringUtils.endsWithIgnoreCase(name, ".post"))
        {
            error.append("Folder name should not end with '.view', '.api', or '.post'.");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in folder names.");
                return false;
            }
        }

        if (isProject && !isLegalProjectName(name))
        {
            error.append("Project name can't be \"").append(name).append("\".");
            return false;
        }

        return true;
    }

    // Any names that we register as special servlets won't work as project names
    private static final Set<String> ILLEGAL_PROJECT_NAMES = Sets.newCaseInsensitiveHashSet("cas", "_rstudio", "_webdav", "filecontent");

    // Check for illegal project names
    public static boolean isLegalProjectName(String name)
    {
        return !ILLEGAL_PROJECT_NAMES.contains(name);
    }

    public static boolean isLegalTitle(String name, StringBuilder error)
    {
        if (StringUtils.isBlank(name))
        {
            return true;  //titles can be blank
        }

        if (name.length() > 1000)
        {
            error.append("Title must be shorter than 1000 characters");
            return false;
        }

        //Don't allow ISOControl characters as they are not handled well by the databases
        for( int i = 0; i < name.length(); ++i)
        {
            if (Character.isISOControl(name.charAt(i)))
            {
                error.append("Non-printable characters are not allowed in titles.");
                return false;
            }
        }

        return true;
    }

    public @NotNull ActionURL getStartURL(User user)
    {
        FolderType ft = getFolderType();
        if (!FolderType.NONE.equals(ft))
            return ft.getStartURL(this, user);

        Module module = getDefaultModule(user);
        if (module != null)
        {
            ActionURL helper = module.getTabURL(this, user);
            if (helper != null)
                return helper;
        }

        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(this);
    }

    public Module getDefaultModule()
    {
        return getDefaultModule(null);
    }

    public Module getDefaultModule(@Nullable User user)
    {
        if (isRoot())
            return null;

        if (_defaultModule == null)
        {
            Map props = PropertyManager.getProperties(this, "defaultModules");
            String defaultModuleName = (String) props.get("name");

            boolean initRequired = false;
            if (null == defaultModuleName || null == ModuleLoader.getInstance().getModule(defaultModuleName))
            {
                defaultModuleName = "Core";
                initRequired = true;
            }
            Module defaultModule = ModuleLoader.getInstance().getModule(defaultModuleName);

            //set default module
            if (initRequired)
                setDefaultModule(defaultModule);

            //ensure that default module is included in active module set
            //should be there already if it's not portal, but if it is core, we have to add it for upgrade
            if (defaultModuleName.compareToIgnoreCase("Core") == 0)
            {
                Set<Module> modules = new HashSet<>(getActiveModules(user));
                if (!modules.contains(defaultModule))
                {
                    modules.add(defaultModule);
                    setActiveModules(modules, user);
                }
            }

            _defaultModule = defaultModule;
        }
        return _defaultModule;
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules)
    {
        BindException errors = new BindException(new Object(), "dummy");
        setFolderType(folderType, ensureModules, errors);
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules, User user)
    {
        BindException errors = new BindException(new Object(), "dummy");
        setFolderType(folderType, ensureModules, user, errors);
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules, BindException errors)
    {
        setFolderType(folderType, ensureModules,ModuleLoader.getInstance().getUpgradeUser(), errors);
    }

    public void setFolderType(FolderType folderType, Set<Module> ensureModules, User user, BindException errors)
    {
        setFolderType(folderType, user, errors);
        if (!errors.hasErrors())
        {
            // Check modules explicitly requested (folderType's modules already checked by setFolderType
            if (!hasEnableRestrictedModules(user))
            {
                for (Module module : ensureModules)
                {
                    if (module.getRequireSitePermission())
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "Modules not enabled because module '" + module.getName() + "' is restricted and you do not have the necessary permission to enable it.");
                    }
                }
            }

            if (!errors.hasErrors())
            {
                Set<Module> modules = new HashSet<>(folderType.getActiveModules());
                modules.addAll(ensureModules);
                setActiveModules(modules, user);
            }
        }
    }

    public void setFolderType(FolderType folderType, User user)
    {
        BindException errors = new BindException(new Object(), "dummy");
        setFolderType(folderType, user, errors);
    }

    public void setFolderType(FolderType folderType, User user, BindException errors)
    {
        if (hasRestrictedModule(folderType) && !hasEnableRestrictedModules(user))
        {
            errors.reject(SpringActionController.ERROR_MSG, "Folder type '" + folderType.getName() + "' not set because it requires a restricted module for which you do not have permission.");
        }
        else
        {
            ContainerManager.setFolderType(this, folderType, user, errors);

            if (!errors.hasErrors())
            {
                if (isWorkbook())
                    appendWorkbookModulesToParent(new HashSet<Module>(), user);
            }
        }
    }

    public Set<Module> getRequiredModules()
    {
        Set<Module> requiredModules = new HashSet<>();
        requiredModules.addAll(getFolderType().getActiveModules());
        requiredModules.add(ModuleLoader.getInstance().getModule("API"));
        requiredModules.add(ModuleLoader.getInstance().getModule("Internal"));

        for(Container child: getChildren())
        {
            if(child.isWorkbook())
            {
                requiredModules.addAll(child.getFolderType().getActiveModules());
            }
        }

        return requiredModules;
    }

    @NotNull
    public FolderType getFolderType()
    {
        return ContainerManager.getFolderType(this);
    }

    /**
     * Sets the default module for a "mixed" type folder. We try not to create
     * these any more. Instead each folder is "owned" by a module
     */
    public void setDefaultModule(Module module)
    {
        if (module == null)
            return;
        PropertyMap props = PropertyManager.getWritableProperties(this, "defaultModules", true);
        props.put("name", module.getName());

        props.save();
        ContainerManager.notifyContainerChange(getId());
        _defaultModule = null;
    }

    public void setActiveModules(Set<Module> modules)
    {
        setActiveModules(modules, null);
    }

    public void setActiveModules(Set<Module> modules, @Nullable User user)
    {
        if (isWorkbook())
        {
            appendWorkbookModulesToParent(modules, user);
            return;
        }

        boolean userHasEnableRestrictedModules = hasEnableRestrictedModules(user);
        PropertyMap props = PropertyManager.getWritableProperties(this, "activeModules", true);
        props.clear();
        for (Module module : modules)
        {
            if (null != module && userCanAccessModule(module, userHasEnableRestrictedModules))
                props.put(module.getName(), Boolean.TRUE.toString());
        }

        for (Module module : getRequiredModules())
        {
            if (null != module && userCanAccessModule(module, userHasEnableRestrictedModules))
                props.put(module.getName(), Boolean.TRUE.toString());
        }

        props.save();
        ContainerManager.notifyContainerChange(getId());
    }

    public void appendWorkbookModulesToParent()
    {
        appendWorkbookModulesToParent(new HashSet<Module>());
    }

    public void appendWorkbookModulesToParent(Set<Module> newModules)
    {
        appendWorkbookModulesToParent(newModules, null);
    }

    public void appendWorkbookModulesToParent(Set<Module> newModules, @Nullable User user)
    {
        if (!isWorkbook())
            return;

        boolean isChanged = false;
        Set<Module> existingModules = new HashSet<>();
        existingModules.addAll(getParent().getActiveModules(false, false, user));

        boolean userHasEnableRestrictedModules = hasEnableRestrictedModules(user);
        for (Module m : newModules)
        {
            if (!existingModules.contains(m) && userCanAccessModule(m, userHasEnableRestrictedModules))
            {
                isChanged = true;
                existingModules.add(m);
            }
        }
        for (Module m : getFolderType().getActiveModules())
        {
            if (!existingModules.contains(m) && userCanAccessModule(m, userHasEnableRestrictedModules))
            {
                isChanged = true;
                existingModules.add(m);
            }
        }

        if (isChanged)
        {
            getParent().setActiveModules(existingModules, user);
        }
    }

    // UNDONE: (MAB) getActiveModules() and setActiveModules()
    // UNDONE: these don't feel like they belong on this class
    // UNDONE: move to ModuleLoader?
    public Set<Module> getActiveModules()
    {
        return getActiveModules(false, true);
    }

    public Set<Module> getActiveModules(boolean init)
    {
        return getActiveModules(init, true);
    }

    public Set<Module> getActiveModules(boolean init, boolean includeDepencendies)
    {
        return getActiveModules(init, includeDepencendies, null);
    }

    /** @return all modules that are active in the container, ordered based on module dependencies as implemented in {@link ModuleLoader#orderModules(Collection)} */
    public Set<Module> getActiveModules(@Nullable User user)
    {
        return getActiveModules(false, true, user);
    }

    /** @return all modules that are active in the container, ordered based on module dependencies as implemented in {@link ModuleLoader#orderModules(Collection)} */
    public Set<Module> getActiveModules(boolean init, boolean includeDependencies, @Nullable User user)
    {
        if(isWorkbook())
        {
            if(init)
                appendWorkbookModulesToParent(new HashSet<>(), user);

            return getParent().getActiveModules(init, includeDependencies, user);
        }

        //Short-circuit for root module
        if (isRoot())
        {
            return Collections.emptySet();
        }

        Map<String, String> props = PropertyManager.getProperties(this, "activeModules");
        //get set of all modules
        List<Module> allModules = ModuleLoader.getInstance().getModules();

        //get active web parts for this container
        List<Portal.WebPart> activeWebparts = Portal.getParts(this);

        boolean userHasEnableRestrictedModules = hasEnableRestrictedModules(user);

        // store active modules, checking first that the container still exists -- junit test creates and deletes
        // containers quickly and this check helps keep the search indexer from creating orphaned property sets.
        if (props.isEmpty() && init && null != ContainerManager.getForId(getId()))
        {
            //initialize properties cache
            PropertyMap propsWritable = PropertyManager.getWritableProperties(this, "activeModules", true);
            props = propsWritable;

            if (isProject())
            {
                // first time in this project: initialize active modules now, based on the active webparts
                Map<String, Module> mapWebPartModule = new HashMap<>();
                //get set of all web parts for all modules
                for (Module module : allModules)
                {
                    for (WebPartFactory factory : module.getWebPartFactories())
                        mapWebPartModule.put(factory.getName(), module);
                }

                //get active modules based on which web parts are active
                for (Portal.WebPart activeWebPart : activeWebparts)
                {
                    if (!"forward".equals(activeWebPart.getLocation()))
                    {
                        //get module associated with this web part & add to props
                        Module activeModule = mapWebPartModule.get(activeWebPart.getName());
                        if (activeModule != null && userCanAccessModule(activeModule, userHasEnableRestrictedModules))
                            propsWritable.put(activeModule.getName(), Boolean.TRUE.toString());
                    }
                }

                // enable 'default' tabs:
                for (Module module : allModules)
                {
                    if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT &&
                        userCanAccessModule(module, userHasEnableRestrictedModules))
                    {
                        propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
            }
            else
            {
                //if this is a subfolder, set active modules to inherit from parent
                Set<Module> parentModules = getParent().getActiveModules(false, false, user);
                for (Module module : parentModules)
                {
                    if (userCanAccessModule(module, userHasEnableRestrictedModules))
                    {
                        //set the default module for the subfolder to be the default module of the parent.
                        Module parentDefault = getParent().getDefaultModule(user);
                        if (module.equals(parentDefault))
                            setDefaultModule(module);

                        propsWritable.put(module.getName(), Boolean.TRUE.toString());
                    }
                }
            }
            propsWritable.save();
        }

        Set<Module> modules = new HashSet<>();
        // add all modules found in user preferences:
        for (String moduleName : props.keySet())
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                modules.add(module);
        }

        // ensure all modules for folder type are added (may have been added after save)
        if (!getFolderType().equals(FolderType.NONE))
        {
            for (Module module : getFolderType().getActiveModules())
            {
                // check for null, since there's no guarantee that a third-party folder type has all its
                // active modules installed on this system (so nulls may end up in the list- bug 6757):
                // Don't restrict based on userHasEnableRestrictedModules, since user is already accessing folder
                if (module != null)
                    modules.add(module);
            }
        }

        // add all 'always display' modules, remove all 'never display' modules:
        for (Module module : allModules)
        {
            if (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_NEVER)
                modules.remove(module);
        }

        Set<Module> activeModules;
        if (includeDependencies)
        {
            Set<Module> withDependencies = new HashSet<>();
            for (Module m : modules)
            {
                withDependencies.add(m);
                Set<Module> dependencies = m.getResolvedModuleDependencies();
                for (Module dependent : dependencies)
//                    if (userCanAccessModule(dependent, userHasEnableRestrictedModules))           // TODO: check dependencies
                        withDependencies.add(dependent);
            }

            //Issue 24850: add modules associated with assays that have an active definition
            //note: on server startup, this can be called before AssayService is registered
            //we also need to defer until after the initial setup, to make sure the expected schemas exist.
            if (ModuleLoader.getInstance().isStartupComplete() && AssayService.get() != null)
            {
                List<ExpProtocol> activeProtocols = AssayService.get().getAssayProtocols(this);
                for (ExpProtocol p : activeProtocols)
                {
                    AssayProvider ap = AssayService.get().getProvider(p);
                    if (ap != null && !ap.getRequiredModules().isEmpty())
                    {
                        withDependencies.addAll(ap.getRequiredModules());
                    }
                }
            }

            activeModules = withDependencies;
        }
        else
        {
            activeModules = modules;
        }

        return Collections.unmodifiableSet(new LinkedHashSet<>(ModuleLoader.getInstance().orderModules(activeModules)));
    }

    public boolean isDescendant(Container container)
    {
        if (null == container)
            return false;

        Container cur = getParent();
        while (null != cur)
        {
            if (cur.equals(container))
                return true;
            cur = cur.getParent();
        }
        return false;
    }

    public Map<String, Set<String>> getModuleDependencyMap()
    {
        Map<String, Set<String>> dependencies = new HashMap<>();

        for (Module m : ModuleLoader.getInstance().getModules())
        {
            for (Module dm : m.getResolvedModuleDependencies())
            {
                String name = dm.getName(); //modules can declare a dependency using the wrong case, so we normalize
                dependencies.putIfAbsent(name, new HashSet<>());

                dependencies.get(name).add(m.getName());
            }
        }

        return dependencies;
    }

    /**
     * Searches descendants of this container recursively until it finds
     * one that has a name matching the name provided. Search is done
     * breadth-first (optimize for immediate child), and name matching is
     * case-insensitive.
     * @param name The name to find
     * @return Matching Container or null if not found.
     */
    @Nullable
    public Container findDescendant(String name)
    {
        return findDescendant(this, name);
    }

    private Container findDescendant(Container parent, String name)
    {
        for (Container child : parent.getChildren())
        {
            if (child.getName().equalsIgnoreCase(name))
                return child;
        }

        Container ret = null;
        for (Container child : parent.getChildren())
        {
            ret = findDescendant(child, name);
            if (null == ret)
                return ret;
        }
        return null;
    }

    public Map<String, Object> toJSON(User user)
    {
        return toJSON(user, true);
    }

    public Map<String, Object> toJSON(User user, boolean includePermissions)
    {
        Map<String, Object> containerProps = new HashMap<>();
        Container parent = getParent();
        containerProps.put("name", getName());
        containerProps.put("path", getPath());
        containerProps.put("parentPath", parent==null ? null : parent.getPath());

        if (this.hasPermission(user, ReadPermission.class))
        {
            containerProps.put("startUrl", getStartURL(user));
            containerProps.put("iconHref", getIconHref());
            containerProps.put("id", getId());
            containerProps.put("sortOrder", getSortOrder());
            if (includePermissions)
            {
                containerProps.put("userPermissions", getPolicy().getPermsAsOldBitMask(user));
                containerProps.put("effectivePermissions", getPolicy().getPermissionNames(user));
            }
            if (null != getDescription())
                containerProps.put("description", getDescription());
            containerProps.put("isWorkbook", isWorkbook());
            containerProps.put("isContainerTab", isContainerTab());
            containerProps.put("type", getContainerNoun());
            JSONArray activeModuleNames = new JSONArray();
            Set<Module> activeModules = getActiveModules(user);
            for (Module module : activeModules)
            {
                activeModuleNames.put(module.getName());
            }
            containerProps.put("activeModules", activeModuleNames);
            containerProps.put("folderType", getFolderType().getName());
            containerProps.put("hasRestrictedActiveModule", hasRestrictedActiveModule(activeModules));
            containerProps.put("parentId", parent==null ? null : parent.getId());

            if (null != getTitle())
                containerProps.put("title", getTitle());
        }
        else
        {
            // Issue 21207: backward compatibility for Skyline on 14.2
            containerProps.put("userPermissions", 0);
            containerProps.put("activeModules", new HashSet<Module>());
            containerProps.put("folderType", "");
        }

        LookAndFeelProperties props = LookAndFeelProperties.getInstance(this);
        JSONObject formats = new JSONObject();
        formats.put("dateFormat", DateUtil.getDateFormatString(this));
        formats.put("dateTimeFormat", props.getDefaultDateTimeFormat());
        formats.put("numberFormat", props.getDefaultNumberFormat());
        containerProps.put("formats", formats);

        return containerProps;
    }

    public boolean isContainerTab()
    {
        return _type == TYPE.tab;
    }

    public TYPE getType()
    {
        return _type;
    }

    @JsonIgnore
    public void setType(TYPE type)
    {
        _type = type;
    }

    public void setType(String typeString)
    {
        _type = TYPE.typeFromString(typeString);
    }

    public static class ContainerException extends Exception
    {
        public ContainerException(String message)
        {
            super(message);
        }

        public ContainerException(String message, Throwable t)
        {
            super(message, t);
        }
    }

    private int compareSiblings(Container c1, Container c2)
    {
        int result = c1.getSortOrder() - c2.getSortOrder();
        if (result != 0)
            return result;
        return c1.getName().compareToIgnoreCase(c2.getName());
    }

    // returns in order from the root (e.g. /project/folder/)
    private List<Container> getPathAsList()
    {
        List<Container> containerList = new ArrayList<>();
        Container current = this;
        while (!current.isRoot())
        {
            containerList.add(current);
            current = current.getParent();
        }
        Collections.reverse(containerList);
        return containerList;
    }

    public int compareTo(@NotNull Container other)
    {
        // Container returns itself as a parent if it's root, so we need to special case that
        if (isRoot())
        {
            if (other.isRoot())
                return 0;
            else
                return -1;
        }
        if (other.isRoot())
        {
            return 1;
        }

        // Special case siblings which is common
        if (getParent().equals(other.getParent()))
        {
            return compareSiblings(this, other);
        }

        List<Container> myPath = getPathAsList();
        List<Container> otherPath = other.getPathAsList();
        for (int i=0; i<Math.min(myPath.size(), otherPath.size()); i++)
        {
            Container myContainer = myPath.get(i);
            Container otherContainer = otherPath.get(i);
            if (myContainer.equals(otherContainer))
                continue;
            return compareSiblings(myContainer, otherContainer);
        }

        // They're equal up to the end, but one is longer. E.g. /a/b/c vs /a/b
        return myPath.size() - otherPath.size();
    }

    @NotNull
    public String getResourceId()
    {
        return _id.toString();
    }

    @NotNull
    public String getResourceDescription()
    {
        return "The folder " + getPath();
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    @NotNull
    public Container getResourceContainer()
    {
        return this;
    }

    public SecurableResource getParentResource()
    {
        SecurableResource parent = getParent();
        return this.equals(getParent()) ? null : parent;
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }

    /**
     * Returns the non-unique title for this Container, or the Container's name if a title is not set
     * @return the title
     */
    public @NotNull String getTitle()
    {
        return null != _title ? _title : getName();
    }

    public boolean isTitleFieldSet()
    {
        return null != _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getContainerNoun()
    {
        return getContainerNoun(false);
    }

    public String getContainerNoun(boolean titleCase)
    {
        String noun = isProject() ? "project" : isWorkbook() ? "workbook" : "folder";
        if(titleCase)
        {
            return noun.substring(0, 1).toUpperCase() + noun.substring(1);
        }

        return noun;
    }

    public List<FolderTab> getDefaultTabs()
    {
        // Filter out any container tabs whose containers have been deleted
        FolderType folderType = getFolderType();
        List<FolderTab> folderTabs = new ArrayList<>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (!folderTab.isContainerTab() || null != ContainerManager.getChild(this, folderTab.getName()))
            {
                folderTabs.add(folderTab);
            }
        }
        return folderTabs;
    }

    public List<FolderTab> getDeletedTabFolders(String newFolderType)
    {
        if (null == newFolderType)
            throw new IllegalStateException("Folder type not found.");

        // Get container tabs whose containers have been deleted
        FolderType folderType = FolderTypeManager.get().getFolderType(newFolderType);
        if (null == folderType)
            throw new IllegalStateException("Folder type not found.");
        List<FolderTab> folderTabs = new ArrayList<>();
        for (FolderTab folderTab : folderType.getDefaultTabs())
        {
            if (folderTab.isContainerTab() && null == ContainerManager.getChild(this, folderTab.getName()) &&
                    ContainerManager.hasContainerTabBeenDeleted(this, folderTab.getName(), newFolderType))
            {
                folderTabs.add(folderTab);
            }
        }
        return folderTabs;
    }

    public boolean hasEnableRestrictedModules(@Nullable User user)
    {
        boolean userHasEnableRestrictedModules = false;
        if (null != user && hasPermission(user, EnableRestrictedModules.class))
            userHasEnableRestrictedModules = true;
        return userHasEnableRestrictedModules;
    }

    public boolean hasRestrictedActiveModule(Set<Module> activeModules)
    {
        for (Module module : activeModules)
            if (module.getRequireSitePermission())
                return true;
        return false;
    }

    public boolean isDataspace()
    {
        return StudyService.DATASPACE_FOLDERTYPE_NAME.equalsIgnoreCase(getFolderType().getName());
    }

    public boolean hasActiveModuleByName(@NotNull String moduleName)
    {
        for (Module module : getActiveModules())
        {
            if (moduleName.equalsIgnoreCase(module.getName()))
                return true;
        }

        return false;
    }

    public static boolean userCanAccessModule(Module module, boolean userHasEnableRestrictedModules)
    {
        return userHasEnableRestrictedModules || !module.getRequireSitePermission();
    }

    public static boolean hasRestrictedModule(FolderType folderType)
    {
        for (Module module : folderType.getActiveModules())
            if (module.getRequireSitePermission())
                return true;
        return false;
    }

    static final Map<String,String> iconPathToHref = Collections.synchronizedMap(new HashMap<String,String>());

    public String getIconHref()
    {
        FolderType ft = getFolderType();
        String iconPath = ft.getFolderIconPath();
        String url = iconPathToHref.get(iconPath);
        if (null == url)
        {
            WebdavResource dir = WebdavService.get().getRootResolver().lookup(Path.parse(iconPath));
            File iconFile = null;
            if (null != dir)
                iconFile = dir.getFile();
            if (!NetworkDrive.exists(iconFile))
            {
                Logger.getLogger(Container.class).warn("Could not find specified icon: "+iconPath);
                iconPath = FolderType.NONE.getFolderIconPath();
            }
            if (!iconPath.startsWith("/"))
                iconPath = "/" + iconPath;
            url = AppProps.getInstance().getContextPath() + iconPath;
            iconPathToHref.put(iconPath, url);
        }
        return url;
    }

    @Nullable
    @Override
    public Object getJdbcParameterValue()
    {
        return getId();
    }

    @NotNull
    @Override
    public JdbcType getJdbcParameterType()
    {
        return JdbcType.VARCHAR;
    }
}
