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
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.ContainerTree;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.core.login.LoginController;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class AdminController extends ViewController
{
    private static Logger _log = Logger.getLogger(AdminController.class);


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward begin() throws Exception
    {
        return new ViewForward(AdminControllerSpring.getShowAdminURL());
    }


    private Forward _renderInTemplate(HttpView view, String title) throws Exception
    {
        return _renderInTemplate(view, title, false);
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward moduleUpgrade(UpgradeStatusForm form) throws Exception
    {
        Forward f = null;
        User u = getViewContext().getUser();

        if (form.getExpress())
            ModuleLoader.getInstance().setExpress(true);

        //Make sure we are the upgrade user before upgrading...
        User upgradeUser = ModuleLoader.getInstance().setUpgradeUser(u, form.getForce());
        if (u.equals(upgradeUser))
        {
            Module module;
            String moduleName = form.getModuleName();
            //Already have a module to upgrade
            if (null != moduleName)
            {
                module = ModuleLoader.getInstance().getModule(moduleName);
                ModuleLoader.ModuleState state = ModuleLoader.ModuleState.valueOf(form.getState());
                ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                if (state.equals(ModuleLoader.ModuleState.InstallComplete))
                    ctx.upgradeComplete(form.getNewVersion());
                else //Continue calling until we're done
                    f = module.versionUpdate(ctx, getViewContext());
            }
            else
            {
                //Get next available
                module = ModuleLoader.getInstance().getNextUpgrade();
                if (null != module)
                {
                    ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                    ctx.setExpress(ModuleLoader.getInstance().getExpress());
                    //Make sure we haven't started. If so, just reshow module status again
                    f = module.versionUpdate(ctx, getViewContext());
                }
            }
        }
        else
        {
            //Make sure status doesn't force user switching..
            form.setForce(false);
        }

        if (null == f)
            f = moduleStatus(form);

        return f;
    }


    @Jpf.Action @RequiresPermission(ACL.PERM_NONE)
    protected Forward moduleStatus(UpgradeStatusForm form) throws Exception
    {
        //This is first UI at startup.  Create first admin account, if necessary.
        if (UserManager.hasNoUsers())
            HttpView.throwRedirect(LoginController.getInitialUserURL());
        requiresAdmin();

        VBox vbox = new VBox();
        vbox.addView(new ModuleStatusView());

        if (ModuleLoader.getInstance().isUpgradeRequired())
            vbox.addView(new StartUpgradingView(ModuleLoader.getInstance().getUpgradeUser(), form.getForce(), ModuleLoader.getInstance().isNewInstall()));
        else
        {
            SqlScriptRunner.stopBackgroundThread();

            ActionURL url = AdminControllerSpring.getCustomizeSiteURL();
            vbox.addView(new HtmlView("All modules are up-to-date.<br><br>" +
                    "<a href='" + url + "'><img border=0 src='" + PageFlowUtil.buttonSrc("Next") + "'></a>"));
        }

        includeView(new DialogTemplate(vbox));
        return null;
    }


    public static class UpgradeStatusForm extends ViewForm
    {
        private double oldVersion;
        private double newVersion;
        private String moduleName = null;
        private String state = ModuleLoader.ModuleState.InstallRequired.name();
        boolean force = false;
        boolean express = false;

        /**
         * Should we force current user to become the upgrader
         */
        public boolean getForce()
        {
            return force;
        }

        public void setForce(boolean force)
        {
            this.force = force;
        }

        public double getOldVersion()
        {
            return oldVersion;
        }

        public void setOldVersion(double oldVersion)
        {
            this.oldVersion = oldVersion;
        }

        public double getNewVersion()
        {
            return newVersion;
        }

        public void setNewVersion(double newVersion)
        {
            this.newVersion = newVersion;
        }

        public String getModuleName()
        {
            return moduleName;
        }

        public void setModuleName(String moduleName)
        {
            this.moduleName = moduleName;
        }

        public void setState(String state)
        {
            this.state = state;
        }

        public String getState()
        {
            return state;
        }

        public boolean getExpress()
        {
            return express;
        }

        public void setExpress(boolean express)
        {
            this.express = express;
        }
    }


    public static class ModuleStatusView extends HttpView
    {
        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            List<Module> modules = ModuleLoader.getInstance().getModules();
            out.write("<table><tr><td><b>Module</b></td><td><b>Status</b></td></tr>");
            for (Module module : modules)
            {
                ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
                out.write("<tr><td>");
                out.write(ctx.getName());
                out.write("</td><td>");
                out.write(ctx.getMessage());
                out.write("</td></tr>\n");
            }
            out.write("</table>");
        }
    }


    public static class StartUpgradingView extends HttpView
    {
        User user = null;
        boolean force = false;
        boolean newInstall = false;

        public StartUpgradingView(User currentUser, boolean force, boolean newInstall)
        {
            this.force = force;
            user = currentUser;
            this.newInstall = newInstall;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            String upgradeURL = getViewContext().getActionURL().relativeUrl("moduleUpgrade", force ? "force=1" : "");
            User upgradeUser = ModuleLoader.getInstance().getUpgradeUser();
            String action = newInstall ? "Install" : "Upgrade";
            String ing = newInstall ? "Installing" : "Upgrading";

            //Upgrade is not started
            if (null == upgradeUser || force)
            {
                out.write("<a href=\"" + upgradeURL + "&express=1" + "\"><img border=0 src='" + PageFlowUtil.buttonSrc("Express " + action) + "'></a>&nbsp;");
                out.write("<a href=\"" + upgradeURL + "\"><img border=0 src='" + PageFlowUtil.buttonSrc("Advanced " + action) + "'></a>");
            }
            //I'm already upgrading upgrade next module after showing status
            else if (getViewContext().getUser().equals(upgradeUser))
            {
                out.write("<script type=\"text/javascript\">var timeout = window.setTimeout(\"doRefresh()\", 1000);" +
                        "function doRefresh() {\n" +
                        "   window.clearTimeout(timeout);\n" +
                        "   window.location = '" + upgradeURL + "';\n" +
                        "}\n</script>");
                out.write("<p>");
                out.write(ing + "...");
                out.write("<p>This page should refresh automatically. If the page does not refresh <a href=\"");
                out.write(upgradeURL);
                out.write("\">Click Here</a>");
            }
            //Somebody else is installing/upgrading
            else
            {
                ActionURL url = getViewContext().cloneActionURL();
                url.deleteParameter("express");
                String retryUrl = url.relativeUrl("moduleStatus.view", "force=1");

                out.print("<p>");
                out.print(user.getEmail());
                out.print(" is already " + ing.toLowerCase() + ". <p>");

                out.print("Refresh this page to see " + action.toLowerCase() + " progress.<p>");
                out.print("If " + action.toLowerCase() + " was cancelled, <a href='");
                out.print(retryUrl);
                out.print("'>Try Again</a>");
            }
        }
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward dbChecker() throws Exception
    {
        ActionURL currentUrl = cloneActionURL();
        String fixRequested = currentUrl.getParameter("_fix");
        StringBuffer contentBuffer = new StringBuffer();

        if (null != fixRequested)
        {
            String sqlcheck=null;
            if (fixRequested.equalsIgnoreCase("container"))
                   sqlcheck = DbSchema.checkAllContainerCols(true);
            if (fixRequested.equalsIgnoreCase("descriptor"))
                   sqlcheck = OntologyManager.doProjectColumnCheck(true);
            contentBuffer.append(sqlcheck);
        }
        else
        {
            contentBuffer.append("\n<br/><br/>Checking Container Column References...");
            String strTemp = DbSchema.checkAllContainerCols(false);
            if (strTemp.length() > 0)
            {
                contentBuffer.append(strTemp);
                currentUrl = cloneActionURL();
                currentUrl.addParameter("_fix", "container");
                contentBuffer.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\""
                        + currentUrl + "\" >here</a> to attempt recovery .");
            }
            
            contentBuffer.append("\n<br/><br/>Checking PropertyDescriptor and DomainDescriptor consistency...");
            strTemp = OntologyManager.doProjectColumnCheck(false);
            if (strTemp.length() > 0)
            {
                contentBuffer.append(strTemp);
                currentUrl = cloneActionURL();
                currentUrl.addParameter("_fix", "descriptor");
                contentBuffer.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\""
                        + currentUrl + "\" >here</a> to attempt recovery .");
            }

            contentBuffer.append("\n<br/><br/>Checking Schema consistency with tableXML...");
            Set<DbSchema> schemas = new HashSet<DbSchema>();
            List<Module> modules = ModuleLoader.getInstance().getModules();
            String sOut=null;

             for (Module module : modules)
                 schemas.addAll(module.getSchemasToTest());

            for (DbSchema schema : schemas)
            {
                sOut = TableXmlUtils.compareXmlToMetaData(schema.getName(), false, false);
                if (null!=sOut)
                    contentBuffer.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;ERROR: Inconsistency in Schema "+ schema.getName()
                            + "<br/>"+ sOut);
            }

            contentBuffer.append("\n<br/><br/>Database Consistency checker complete");

        }
        HtmlView htmlView = new HtmlView("<table class=\"DataRegion\"><tr><td>" + contentBuffer.toString() + "</td></tr></table>");
        htmlView.setTitle("Database Consistency Checker");
        return _renderInTemplate(htmlView, "Database Consistency Checker");
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


    @Jpf.Action @RequiresSiteAdmin
    protected Forward customizeEmail(CustomEmailForm form) throws Exception
    {
        JspView<CustomEmailForm> view = new JspView<CustomEmailForm>("/org/labkey/core/admin/customizeEmail.jsp", form);

        NavTree[] navTrail = new NavTree[] {
                new NavTree("Admin Console", new ActionURL("admin", "begin", getViewContext().getContainer())),
                new NavTree("Customize Email")};

        NavTrailConfig config = new NavTrailConfig(getViewContext());

        config.setTitle("Customize Email", false);
        config.setExtraChildren(navTrail);

        return _renderInTemplate(view, "Customize Email");
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward updateCustomEmail(CustomEmailForm form) throws Exception
    {
        if (form.getTemplateClass() != null)
        {
            EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());

            template.setSubject(form.getEmailSubject());
            template.setBody(form.getEmailMessage());

            String[] errors = new String[1];
            if (template.isValid(errors))
                EmailTemplateService.get().saveEmailTemplate(template);
            else
                PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionMessage("Error", errors[0]));
        }
        return customizeEmail(form);
    }

    @Jpf.Action
    @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward folderAliases() throws Exception
    {
        JspView<ViewContext> view = new JspView<ViewContext>("/org/labkey/core/admin/folderAliases.jsp");
        return _renderInTemplate(view, "Folder Aliases: " + getViewContext().getContainer().getPath());
    }

    @Jpf.Action @RequiresPermission(ACL.PERM_ADMIN)
    protected Forward saveAliases(UpdateAliasesForm form) throws Exception
    {
        List<String> aliases = new ArrayList<String>();
        if (form.getAliases() != null)
        {
            StringTokenizer st = new StringTokenizer(form.getAliases(), "\n\r", false);
            while (st.hasMoreTokens())
            {
                String alias = st.nextToken().trim();
                if (!alias.startsWith("/"))
                {
                    alias = "/" + alias;
                }
                while (alias.endsWith("/"))
                {
                    alias = alias.substring(0, alias.lastIndexOf('/'));
                }
                aliases.add(alias);
            }
        }
        ContainerManager.saveAliasesForContainer(getContainer(), aliases);
        ActionURL url = cloneActionURL();
        url.setAction("manageFolders.view");
        return new ViewForward(url);
    }

    public static class UpdateAliasesForm extends ViewForm
    {
        private String _aliases;


        public String getAliases()
        {
            return _aliases;
        }

        public void setAliases(String aliases)
        {
            _aliases = aliases;
        }
    }


    @Jpf.Action @RequiresSiteAdmin
    protected Forward deleteCustomEmail(CustomEmailForm form) throws Exception
    {
        if (form.getTemplateClass() != null)
        {
            EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());
            template.setSubject(form.getEmailSubject());
            template.setBody(form.getEmailMessage());

            EmailTemplateService.get().deleteEmailTemplate(template);
        }
        return customizeEmail(form);
    }

    public static class CustomEmailForm extends ViewForm
    {
        private String _templateClass;
        private String _emailSubject;
        private String _emailMessage;

        public void setTemplateClass(String name){_templateClass = name;}
        public String getTemplateClass(){return _templateClass;}
        public void setEmailSubject(String subject){_emailSubject = subject;}
        public String getEmailSubject(){return _emailSubject;}
        public void setEmailMessage(String body){_emailMessage = body;}
        public String getEmailMessage(){return _emailMessage;}
    }
}
