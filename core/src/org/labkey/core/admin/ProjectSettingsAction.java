/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.TermsOfUseException;
import org.labkey.api.view.ThemeFont;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.WebTheme;
import org.labkey.api.view.WebThemeManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Nov 24, 2009
 */

@ActionNames("projectSettings, lookAndFeelSettings")
@RequiresPermissionClass(AdminPermission.class)
public class ProjectSettingsAction extends FormViewAction<AdminController.ProjectSettingsForm>
{
    public void checkPermissions() throws TermsOfUseException, UnauthorizedException
    {
        super.checkPermissions();

        if (getViewContext().getContainer().isRoot() && !getViewContext().getUser().isAdministrator())
            throw new UnauthorizedException();
    }

    public void validateCommand(AdminController.ProjectSettingsForm form, Errors errors)
    {
        if (form.isFilesTab())
        {
            if (!form.isPipelineRootForm() && !form.isDisableFileSharing() && !form.hasSiteDefaultRoot())
            {
                String root = StringUtils.trimToNull(form.getFolderRootPath());
                if (root != null)
                {
                    File f = new File(root);
                    if (!f.exists() || !f.isDirectory())
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "File root '" + root + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                    }
                }
                else
                    errors.reject(SpringActionController.ERROR_MSG, "A Project specified file root cannot be blank, to disable file sharing for this project, select the disable option.");
            }
        }
    }

    public ModelAndView getView(AdminController.ProjectSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        return new ProjectSettingsTabStrip(form, reshow, errors);
    }

    public boolean handlePost(AdminController.ProjectSettingsForm form, BindException errors) throws Exception
    {
        Container c = getViewContext().getContainer();

        if (form.isResourcesTab())
            return handleResourcesPost(c, errors);
        else if (form.isMenuTab())
            return handleMenuPost(c, form, errors);
        else if (form.isFilesTab())
            return handleFilesPost(c, form, errors);
        else
            return handlePropertiesPost(c, form, errors);
    }

    private boolean handlePropertiesPost(Container c, AdminController.ProjectSettingsForm form, BindException errors)
    {
        WriteableLookAndFeelProperties props = LookAndFeelProperties.getWriteableInstance(c);

        try
        {
            if (form.getThemeName() != null)
            {
                WebTheme theme = WebThemeManager.getTheme(form.getThemeName());
                if (theme != null)
                {
                    props.setThemeName(theme.getFriendlyName());
                }
                ThemeFont themeFont = ThemeFont.getThemeFont(form.getThemeFont());
                if (themeFont != null)
                {
                    props.setThemeFont(themeFont.getFriendlyName());
                }
            }
        }
        catch (IllegalArgumentException e)
        {
        }

        if (form.getShouldInherit() != SecurityManager.shouldNewSubfoldersInheritPermissions(c))
        {
            SecurityManager.setNewSubfoldersInheritPermissions(c, getViewContext().getUser(), form.getShouldInherit());
        }

        try
        {
            // this will throw an InvalidEmailException for invalid email addresses
            ValidEmail email = new ValidEmail(form.getSystemEmailAddress());
            props.setSystemEmailAddress(email);
        }
        catch (ValidEmail.InvalidEmailException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Invalid System Email Address: ["
                    + e.getBadEmail() + "]. Please enter a valid email address.");
            return false;
        }

        props.setCompanyName(form.getCompanyName());
        props.setSystemDescription(form.getSystemDescription());
        props.setLogoHref(form.getLogoHref());
        props.setSystemShortName(form.getSystemShortName());
        props.setNavigationBarWidth(form.getNavigationBarWidth());
        props.setReportAProblemPath(form.getReportAProblemPath());

        if (!StringUtils.isBlank(form.getSupportEmail()))
        {
            try
            {
                // this will throw an InvalidEmailException for invalid email addresses
                ValidEmail email = new ValidEmail(form.getSupportEmail());
                props.setSupportEmail(email.toString());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid Support Email Address: ["
                        + e.getBadEmail() + "]. Please enter a valid email address.");
                return false;
            }
        }
        else
        {
            props.setSupportEmail(null);
        }

        FolderDisplayMode folderDisplayMode = FolderDisplayMode.fromString(form.getFolderDisplayMode());
        props.setFolderDisplayMode(folderDisplayMode);
        props.setHelpMenuEnabled(form.isEnableHelpMenu());
        props.save();

        //write an audit log event
        props.writeAuditLogEvent(getViewContext().getUser(), props.getOldProperties());

        // Bump the look & feel revision so browsers retrieve the new theme stylesheet
        WriteableAppProps.incrementLookAndFeelRevisionAndSave();

        return true;
    }

    private boolean handleResourcesPost(Container c, BindException errors)
    {
        Map<String, MultipartFile> fileMap = getFileMap();

        MultipartFile logoFile = fileMap.get("logoImage");
        if (logoFile != null && !logoFile.isEmpty())
        {
            try
            {
                handleLogoFile(logoFile, c);
            }
            catch (Exception e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return false;
            }
        }

        MultipartFile iconFile = fileMap.get("iconImage");
        if (logoFile != null && !iconFile.isEmpty())
        {
            try
            {
                handleIconFile(iconFile, c);
            }
            catch (Exception e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return false;
            }
        }

        MultipartFile customStylesheetFile = fileMap.get("customStylesheet");
        if (customStylesheetFile != null && !customStylesheetFile.isEmpty())
        {
            try
            {
                handleCustomStylesheetFile(customStylesheetFile, c);
            }
            catch (Exception e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                return false;
            }
        }

        // TODO: write an audit log event
        //props.writeAuditLogEvent(getViewContext().getUser(), props.getOldProperties());

        // Bump the look & feel revision so browsers retrieve the new logo, custom stylesheet, etc.
        WriteableAppProps.incrementLookAndFeelRevisionAndSave();

        return true;
    }

    private boolean handleMenuPost(Container c, AdminController.ProjectSettingsForm form, BindException errors)
    {
        ContainerManager.setMenuEnabled(c, getViewContext().getUser(), form.isEnableMenuBar());
        return true;
    }

    private boolean handleFilesPost(Container c, AdminController.ProjectSettingsForm form, BindException errors) throws Exception
    {
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);

        if (service != null)
        {
            if (form.isPipelineRootForm())
                return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
            else
            {
                FolderManagementAction.setFileRootFromForm(getViewContext(), form);
            }
        }
        return true;
    }

    public ActionURL getSuccessURL(AdminController.ProjectSettingsForm form)
    {
        Container c = getViewContext().getContainer();
        if (form.isResourcesTab())
            return new AdminController.AdminUrlsImpl().getLookAndFeelResourcesURL(c);
        else if (form.isMenuTab())
            return new AdminController.AdminUrlsImpl().getProjectSettingsMenuURL(c);
        else if (form.isFilesTab())
        {
            String param = form.isPipelineRootForm() ? "piperootSet" : "rootSet";
            return new AdminController.AdminUrlsImpl().getProjectSettingsFileURL(c).addParameter(param, true);
        }
        else
            return new AdminController.AdminUrlsImpl().getProjectSettingsURL(c);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        setHelpTopic(new HelpTopic("customizeLook"));

        Container c = getViewContext().getContainer();

        if (c.isRoot())
            root.addChild("Admin Console", AdminController.getShowAdminURL()).addChild("Look and Feel Settings");
        else
            root.addChild("Project Settings");
        return root;
    }

    private void handleLogoFile(MultipartFile file, Container c) throws ServletException, SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        // Set the name to something we'll recognize as a logo file
        String uploadedFileName = file.getOriginalFilename();
        int index = uploadedFileName.lastIndexOf(".");
        if (index == -1)
        {
            throw new ServletException("No file extension on the uploaded image");
        }

        ContainerManager.ContainerParent parent = new ContainerManager.ContainerParent(c);
        // Get rid of any existing logo
        AdminController.deleteExistingLogo(c, user);

        AttachmentFile renamed = new SpringAttachmentFile(file);
        renamed.setFilename(AttachmentCache.LOGO_FILE_NAME_PREFIX + uploadedFileName.substring(index));
        AttachmentService.get().addAttachments(parent, Collections.<AttachmentFile>singletonList(renamed), user);
        AttachmentCache.clearLogoCache();
    }


    private void handleIconFile(MultipartFile file, Container c) throws SQLException, IOException, ServletException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        if (!file.getOriginalFilename().toLowerCase().endsWith(".ico"))
        {
            throw new ServletException("FavIcon must be a .ico file");
        }

        AdminController.deleteExistingFavicon(c, user);

        ContainerManager.ContainerParent parent = new ContainerManager.ContainerParent(c);
        AttachmentFile renamed = new SpringAttachmentFile(file);
        renamed.setFilename(AttachmentCache.FAVICON_FILE_NAME);
        AttachmentService.get().addAttachments(parent, Collections.<AttachmentFile>singletonList(renamed), user);
        AttachmentCache.clearFavIconCache();
    }

    private void handleCustomStylesheetFile(MultipartFile file, Container c) throws SQLException, IOException, ServletException, AttachmentService.DuplicateFilenameException
    {
        User user = getViewContext().getUser();

        AdminController.deleteExistingCustomStylesheet(c, user);

        ContainerManager.ContainerParent parent = new ContainerManager.ContainerParent(c);
        AttachmentFile renamed = new SpringAttachmentFile(file);
        renamed.setFilename(AttachmentCache.STYLESHEET_FILE_NAME);
        AttachmentService.get().addAttachments(parent, Collections.<AttachmentFile>singletonList(renamed), user);

        // Don't need to clear cache -- lookAndFeelRevision gets checked on retrieval
    }

    private static class ProjectSettingsTabStrip extends TabStripView
    {
        private AdminController.ProjectSettingsForm _form;
        private BindException _errors;
        private boolean _reshow;

        private ProjectSettingsTabStrip(AdminController.ProjectSettingsForm form, boolean reshow, BindException errors)
        {
            _form = form;
            _errors = errors;
            _reshow = reshow;
        }

        public List<NavTree> getTabList()
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getProjectSettingsURL(getViewContext().getContainer());
            List<NavTree> tabs = new ArrayList<NavTree>(2);

            tabs.add(new TabInfo("Properties", "properties", url));
            tabs.add(new TabInfo("Resources", "resources", url));
            if (!ContainerManager.getRoot().equals(getViewContext().getContainer()))
            {
                tabs.add(new TabInfo("Menu Bar", "menubar", url));
                tabs.add(new TabInfo("Files", "files", url));
            }
            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            Container c = getViewContext().getContainer();

            if (c.isRoot() || c.isProject())
            {
                if ("resources".equals(tabId))
                {
                    LookAndFeelResourcesBean bean = new LookAndFeelResourcesBean(c);
                    return new JspView<LookAndFeelResourcesBean>("/org/labkey/core/admin/lookAndFeelResources.jsp", bean, _errors);
                }
                else if ("properties".equals(tabId))
                {
                    LookAndFeelPropertiesBean bean = new LookAndFeelPropertiesBean(c, _form.getThemeName());
                    return new JspView<LookAndFeelPropertiesBean>("/org/labkey/core/admin/lookAndFeelProperties.jsp", bean, _errors);
                }
                else if ("menubar".equals(tabId))
                {
                    if (c.isRoot())
                        throw new NotFoundException("Menu bar must be configured for each project separately.");
                    WebPartView v = new JspView<Object>(AdminController.class, "editMenuBar.jsp", null);
                    v.setView("menubar", new VBox());
                    //TODO: propagate ClientDependencies
                    Portal.populatePortalView(getViewContext(), Portal.DEFAULT_PORTAL_PAGE_ID, v, true);

                    return v;
                }
                else if ("files".equals(tabId))
                {
                    if (c.isRoot())
                        throw new NotFoundException("Files must be configured for each project separately.");

                    if (!_reshow || _form.isPipelineRootForm())
                    {
                        try
                        {
                            FolderManagementAction.setConfirmMessage(getViewContext(), _form);
                        }
                        catch (IllegalArgumentException e)
                        {
                            _errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                        }
                    }
                    VBox box = new VBox();
                    box.addView(new JspView<AdminController.ProjectSettingsForm>("/org/labkey/core/admin/view/filesProjectSettings.jsp", _form, _errors));

                    // only site admins can configure the pipeline root
                    if (getViewContext().getUser().isAdministrator())
                    {
                        box.addView(new HttpView() {
                            protected void renderInternal(Object model, PrintWriter out) throws Exception {
                                WebPartView.startTitleFrame(out, "Configure Data Processing Pipeline");
                            }
                        });

                        SetupForm form = SetupForm.init(c);
                        form.setShowAdditionalOptionsLink(true);
                        form.setErrors(_errors);
                        PipeRoot pipeRoot = SetupForm.getPipelineRoot(c);

                        if (pipeRoot != null)
                        {
                            for (String errorMessage : pipeRoot.validate())
                                _errors.addError(new LabkeyError(errorMessage));
                        }
                        box.addView(PipelineService.get().getSetupView(form));
                        box.addView(new HttpView() {
                            protected void renderInternal(Object model, PrintWriter out) throws Exception {
                                WebPartView.endTitleFrame(out);
                            }
                        });
                    }

                    // add the folder tree view to show all folders and file content settings for this project
                    box.addView(new JspView<AdminController.ProjectSettingsForm>("/org/labkey/core/admin/view/filesProjectSettingsSummary.jsp", _form, _errors));
                    
                    return box;
                }
                else
                    throw new NotFoundException("Unknown tab id");
            }
            else
            {
                throw new NotFoundException("Can only be called for root or project.");
            }
        }
    }

    private static abstract class LookAndFeelBean
    {
        public String helpLink = new HelpTopic("customizeLook").getSimpleLinkHtml("more info...");
    }

    public static class LookAndFeelResourcesBean extends LookAndFeelBean
    {
        public Attachment customLogo;
        public Attachment customFavIcon;
        public Attachment customStylesheet;

        private LookAndFeelResourcesBean(Container c)
        {
            customLogo = AttachmentCache.lookupLogoAttachment(c);
            customFavIcon = AttachmentCache.lookupFavIconAttachment(new ContainerManager.ContainerParent(c));
            customStylesheet = AttachmentCache.lookupCustomStylesheetAttachment(new ContainerManager.ContainerParent(c));
        }
    }

    public static class LookAndFeelPropertiesBean extends LookAndFeelBean
    {
        public Collection<WebTheme> themes = WebThemeManager.getWebThemes();
        public List<ThemeFont> themeFonts = ThemeFont.getThemeFonts();
        public ThemeFont currentThemeFont;
        public WebTheme currentTheme;
        public Attachment customLogo;
        public Attachment customFavIcon;
        public Attachment customStylesheet;
        public WebTheme newTheme = null;

        private LookAndFeelPropertiesBean(Container c, String newThemeName)
        {
            customLogo = AttachmentCache.lookupLogoAttachment(c);
            customFavIcon = AttachmentCache.lookupFavIconAttachment(new ContainerManager.ContainerParent(c));
            currentTheme = WebThemeManager.getTheme(c);
            currentThemeFont = ThemeFont.getThemeFont(c);
            customStylesheet = AttachmentCache.lookupCustomStylesheetAttachment(new ContainerManager.ContainerParent(c));

            //if new color scheme defined, get new theme name from url
            if (newThemeName != null)
                newTheme = WebThemeManager.getTheme(newThemeName);
        }
    }
}
