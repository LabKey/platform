/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.admin;

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang.StringUtils;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class AdminController extends ViewController
{
    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward begin() throws Exception
    {
        return new ViewForward(AdminControllerSpring.getShowAdminURL());
    }


    private Forward _renderInTemplate(HttpView view, String title, boolean navTrailEndsAtProject) throws Exception
    {
        NavTrailConfig trailConfig = new NavTrailConfig(getViewContext());
        if (title != null)
            trailConfig.setTitle(title);
        if (!"showAdmin".equals(getActionURL().getAction()))
            trailConfig.setExtraChildren(new NavTree("Admin Console", AdminControllerSpring.getShowAdminURL()));

        HomeTemplate template = new HomeTemplate(getViewContext(), getContainer(), view, trailConfig);
        includeView(template);
        return null;
    }


    private Forward _renderInDialogTemplate(HttpView view, String title) throws Exception
    {
        DialogTemplate template = new DialogTemplate(view);
        template.setTitle(title);
        includeView(template);
        return null;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward manageFolders(ManageFoldersForm form) throws Exception
    {
        if (getContainer().isRoot())
            HttpView.throwNotFound();
        
        JspView v = FormPage.getView(AdminController.class, form, "manageFolders.jsp");

        return _renderInTemplate(v, "Manage Folders", true);
    }

    public static class FolderReorderForm extends FormData
    {
        private String _order;
        private boolean _resetToAlphabetical;

        public String getOrder()
        {
            return _order;
        }

        public void setOrder(String order)
        {
            _order = order;
        }

        public boolean isResetToAlphabetical()
        {
            return _resetToAlphabetical;
        }

        public void setResetToAlphabetical(boolean resetToAlphabetical)
        {
            _resetToAlphabetical = resetToAlphabetical;
        }
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward reorderFolders(FolderReorderForm form) throws Exception
    {
        if ("POST".equalsIgnoreCase(getRequest().getMethod()))
        {
            Container parent = getContainer().isRoot() ? getContainer() : getContainer().getParent();
            if (form.isResetToAlphabetical())
                ContainerManager.setChildOrderToAlphabetical(parent);
            else if (form.getOrder() != null)
            {
                List<Container> children = parent.getChildren();
                String[] order = form.getOrder().split(";");
                Map<String,Container> nameToContainer = new HashMap<String, Container>();
                for (Container child : children)
                    nameToContainer.put(child.getName(), child);
                List<Container> sorted = new ArrayList<Container>(children.size());
                for (String childName : order)
                {
                    Container child = nameToContainer.get(childName);
                    sorted.add(child);
                }
                ContainerManager.setChildOrder(parent, sorted);
            }
            if (getContainer().isRoot())
                return new ViewForward(getActionURL().relativeUrl("showAdmin", null));
            else
                return new ViewForward(getActionURL().relativeUrl("manageFolders", null));
        }
        JspView<ViewContext> v = new JspView<ViewContext>("/org/labkey/core/admin/reorderFolders.jsp");
        return _renderInTemplate(v, "Reorder " + (getContainer().isRoot() || getContainer().getParent().isRoot() ? "Projects" : "Folders"), true);
    }

    /**
     * Shows appropriate UI for renaming, moving, deleting, and creating folders & projects.  These actions
     * share a lot of common code, so use a single action class with an "action" parameter.
     */
    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward modifyFolder(ManageFoldersForm form) throws Exception
    {
        Container c = getContainer();

        JspView<ManageFoldersForm> view = new JspView<ManageFoldersForm>("/org/labkey/core/admin/modifyFolder.jsp", form);

        String containerDescription;

        if ("create".equals(form.getAction()))
            containerDescription = (c.isRoot() ? "Project" : "Folder");
        else
            containerDescription = (c.isProject() ? "Project" : "Folder");

        String title = toProperCase(form.getAction()) + " " + containerDescription;

        if ("delete".equals(form.getAction()))
            return _renderInDialogTemplate(view, title);
        else
            return _renderInTemplate(view, title, true);
    }


    private static String toProperCase(String s)
    {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }


    public static class MoveContainerTree extends ContainerTree
    {
        private Container ignore;

        public MoveContainerTree(String rootPath, User user, int perm, ActionURL url)
        {
            super(rootPath, user, perm, url);
        }

        public void setIgnore(Container c)
        {
            ignore = c;
        }

        @Override
        protected boolean renderChildren(StringBuilder html, MultiMap<Container, Container> mm, Container parent, int level)
        {
            if (!parent.equals(ignore))
                return super.renderChildren(html, mm, parent, level);
            else
                return false;
        }
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward moveFolder(ManageFoldersForm form) throws Exception
    {
        Container c = ContainerManager.getForPath(form.getFolder());  // Folder to move
        if (null == c)
            HttpView.throwNotFound("Folder does not exist");
        if (c.isRoot())
            return errorForward("move", "Error: Can't move the root folder.", c.getPath());

        Container newParent = form.getContainer();
        if (!newParent.hasPermission(getUser(), ACL.PERM_ADMIN))
            HttpView.throwUnauthorized();

        if (newParent.hasChild(c.getName()))
            return errorForward("move", "Error: The selected folder already has a folder with that name.  Please select a different location (or Cancel).", c.getPath());

        Container oldProject = c.getProject();
        Container newProject = newParent.isRoot() ? c : newParent.getProject();
        if (!oldProject.getId().equals(newProject.getId()) && !form.isConfirmed())
        {
            HttpView v = new JspView<ManageFoldersForm>("/org/labkey/core/admin/confirmProjectMove.jsp", form);

            return includeView(new DialogTemplate(v));
        }

        ContainerManager.move(c, newParent);

        if (form.isAddAlias())
        {
            String[] originalAliases = ContainerManager.getAliasesForContainer(c);
            List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
            newAliases.add(c.getPath());
            ContainerManager.saveAliasesForContainer(c, newAliases);
        }

        c = ContainerManager.getForId(c.getId());                     // Reload container to populate new location
        return new ViewForward("admin", "manageFolders", c.getPath());
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward renameFolder(ManageFoldersForm form) throws SQLException, ServletException, URISyntaxException
    {
        Container c = getContainer();
        String folderName = form.getName();
        StringBuffer error = new StringBuffer();

        if (Container.isLegalName(folderName, error))
        {
            if (c.getParent().hasChild(folderName))
                error.append("The parent folder already has a folder with this name.");
            else
            {
                ContainerManager.rename(c, folderName);
                if (form.isAddAlias())
                {
                    String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                    List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
                    newAliases.add(c.getPath());
                    ContainerManager.saveAliasesForContainer(c, newAliases);
                }
                c = ContainerManager.getForId(c.getId());  // Reload container to populate new name
                return new ViewForward("admin", "manageFolders", c.getPath());
            }
        }

        return errorForward("rename", "Error: " + error + "  Please enter a different folder name (or Cancel).");
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward createFolder(ManageFoldersForm form)
            throws IOException, ServletException, SQLException, URISyntaxException
    {
        Container parent = getContainer();
        String folderName = form.getName();
        StringBuffer error = new StringBuffer();

        if (Container.isLegalName(folderName, error))
        {
            if (parent.hasChild(folderName))
                error.append("The parent folder already has a folder with this name.");
            else
            {
                Container c = ContainerManager.createContainer(parent, folderName);
                String folderType = form.getFolderType();
                assert null != folderType;
                FolderType type = ModuleLoader.getInstance().getFolderType(folderType);
                c.setFolderType(type);


                ActionURL next;
                if (c.isProject())
                {
                    SecurityManager.createNewProjectGroups(c);
                    next = new ActionURL("Security", "project", c);
                }
                else
                {
                    //If current user is NOT a site or folder admin, we'll inherit permissions (otherwise they would not be able to see the folder)
                    Integer adminGroupId = null;
                    if (null != c.getProject())
                        adminGroupId = SecurityManager.getGroupId(c.getProject(), "Administrators", false);
                    boolean isProjectAdmin = (null != adminGroupId) && getUser().isInGroup(adminGroupId.intValue());
                    if (!isProjectAdmin && !getUser().isAdministrator())
                        SecurityManager.setInheritPermissions(c);

                    if (type.equals(FolderType.NONE))
                        next = new ActionURL("admin", "customize", c);
                    else
                        next = new ActionURL("Security", "container", c);
                }
                next.addParameter("wizard", Boolean.TRUE.toString());

                return new ViewForward(next);
            }
        }

        return errorForward("create", "Error: " + error + "  Please enter a different folder name (or Cancel).");
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward deleteFolder(ManageFoldersForm form) throws SQLException, ServletException, URISyntaxException
    {
        Container c = getContainer();

        if (!isPost())
            return new ViewForward("admin", "manageFolder", c);

        // Must be site admin to delete a project
        if (c.isProject())
            requiresGlobalAdmin();

        if (form.getRecurse())
        {
            ContainerManager.deleteAll(c, getUser());
        }
        else
        {
            if (c.getChildren().isEmpty())
                ContainerManager.delete(c, getUser());
            else
                throw new IllegalStateException("This container has children");  // UI should prevent this case
        }

        // If we just deleted a project then redirect to the home page, otherwise back to managing the project folders
        if (c.isProject())
            return new ViewForward(AppProps.getInstance().getHomePageActionURL());
        else
            return new ViewForward("admin", "manageFolders", c.getParent());
    }


    private Forward errorForward(String action, String error)
    {
        return errorForward(action, error, null);
    }


    // Forward back to modifyFolder action with appropriate error.  Used for SQL Exceptions (e.g., constraint
    // violations when renaming, creating, or moving a folder somewhere that already has a folder of that name)
    private Forward errorForward(String action, String error, String extraPath)
    {
        ActionURL currentUrl = cloneActionURL();
        currentUrl.setAction("modifyFolder");
        currentUrl.addParameter("action", action);
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", error));
        currentUrl.deleteParameter("x");
        currentUrl.deleteParameter("y");

        if (null != extraPath)
            currentUrl.setExtraPath(extraPath);

        return new ViewForward(currentUrl, false);
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward customize(UpdateFolderForm form) throws Exception
    {
        Container c = getContainer();
        if (c.isRoot())
            HttpView.throwNotFound();
            
        JspView<UpdateFolderForm> view = new JspView<UpdateFolderForm>("/org/labkey/core/admin/customizeFolder.jsp", form);
        NavTrailConfig config = new NavTrailConfig(getViewContext()).setTitle("Customize folder " + c.getPath());
        HttpView template = new HomeTemplate(getViewContext(), c, view, config);
        includeView(template);
        return null;
    }


    @Jpf.Action(validationErrorForward = @Jpf.Forward(path = "customize.do", name = "customize"))
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward updateFolder(UpdateFolderForm form) throws Exception
    {
        Container c = getContainer();
        if (c.isRoot())
            HttpView.throwNotFound();
        
        String[] modules = form.getActiveModules();
        Set<Module> activeModules = new HashSet<Module>();
        for (String moduleName : modules)
        {
            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                activeModules.add(module);
        }

        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
        {
            c.setFolderType(FolderType.NONE, activeModules);
            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
            c.setDefaultModule(defaultModule);
        }
        else
        {
            FolderType folderType= ModuleLoader.getInstance().getFolderType(form.getFolderType());
            c.setFolderType(folderType, activeModules);
        }

        ActionURL url;
        if (form.isWizard())
        {
            url = new ActionURL("Security", "container", c);
            url.addParameter("wizard", Boolean.TRUE.toString());
        }
        else
            url = c.getFolderType().getStartURL(c, getUser());

        return new ViewForward(url);
    }

    public static class UpdateFolderForm extends ViewForm
    {
        private String[] activeModules;
        private String defaultModule;
        private String folderType;
        private boolean wizard;

        public String[] getActiveModules()
        {
            return activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            this.activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            this.defaultModule = defaultModule;
        }

        public void reset(ActionMapping mapping, HttpServletRequest request)
        {
            activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        }

        public ActionErrors validate(ActionMapping arg0, HttpServletRequest arg1)
        {
            boolean fEmpty = true;
            for(String module : activeModules)
            {
                if(module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if(fEmpty && "None".equals(getFolderType()))
            {
                ActionErrors actionErrors = new ActionErrors();
                String error = "Error: Please select at least one tab to display.";
                actionErrors.add("tabs", new ActionMessage("Error", error));
                return actionErrors;
            }
            return null;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isWizard()
        {
            return wizard;
        }

        public void setWizard(boolean wizard)
        {
            this.wizard = wizard;
        }
    }


    public static class ManageFoldersForm extends ViewForm
    {
        private String name;
        private String folder;
        private String action;
        private String folderType;
        private boolean showAll;
        private boolean confirmed = false;
        private boolean addAlias;
        private boolean recurse = false;


        public void reset(ActionMapping actionMapping, HttpServletRequest request)
        {
            super.reset(actionMapping, request);
            addAlias = false;
        }

        public boolean isShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean showAll)
        {
            this.showAll = showAll;
        }

        public String getAction()
        {
            return action;
        }

        public void setAction(String action)
        {
            this.action = action;
        }

        public String getFolder()
        {
            return folder;
        }

        public void setFolder(String folder)
        {
            this.folder = folder;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getProjectName()
        {
            String extraPath = getContainer().getPath();

            int i = extraPath.indexOf("/", 1);

            if (-1 == i)
                return extraPath;
            else
                return extraPath.substring(0, i);
        }

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isAddAlias()
        {
            return addAlias;
        }

        public void setAddAlias(boolean addAlias)
        {
            this.addAlias = addAlias;
        }

        public boolean getRecurse()
        {
            return recurse;
        }

        public void setRecurse(boolean recurse)
        {
            this.recurse = recurse;
        }
    }
}
