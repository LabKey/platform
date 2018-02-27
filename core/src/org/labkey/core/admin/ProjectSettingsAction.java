/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceAttachmentParent;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.DateParsingMode;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.ThemeFont;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * User: klum
 * Date: Nov 24, 2009
 */

@ActionNames("projectSettings, lookAndFeelSettings")
@RequiresPermission(AdminPermission.class)
public class ProjectSettingsAction extends FormViewAction<AdminController.ProjectSettingsForm>
{
    public void validateCommand(AdminController.ProjectSettingsForm form, Errors errors)
    {
        if (form.isFilesTab())
        {
            if (!form.isPipelineRootForm() && !form.isDisableFileSharing() && !form.hasSiteDefaultRoot() && !form.isCloudFileRoot())
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
            else if (form.isCloudFileRoot())
            {
                AdminController.validateCloudFileRoot(form, getContainer(), errors);
            }
        }
    }

    public ModelAndView getView(AdminController.ProjectSettingsForm form, boolean reshow, BindException errors) throws Exception
    {
        Container c = getContainer();
        validateContainer(c);

        return new ProjectSettingsTabStrip(form, reshow, errors);
    }

    private void validateContainer(Container c)
    {
        if (!c.isRoot() && !c.isProject())
            throw new NotFoundException("Valid only from root or project");
    }

    public boolean handlePost(AdminController.ProjectSettingsForm form, BindException errors) throws Exception
    {
        Container c = getContainer();
        validateContainer(c);

        if (form.isResourcesTab())
            return handleResourcesPost(c, errors);
        else if (form.isFilesTab())
            return handleFilesPost(form, errors);
        else
            return handlePropertiesPost(c, form, errors);
    }

    private boolean handlePropertiesPost(Container c, AdminController.ProjectSettingsForm form, BindException errors)
    {
        WriteableLookAndFeelProperties props = LookAndFeelProperties.getWriteableInstance(c);
        boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);

        try
        {
            if (form.getThemeName() == null)
            {
                if (!c.isRoot())
                    props.clearThemeName();
            }
            else
            {
                props.setThemeName(form.getThemeName());

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
            SecurityManager.setNewSubfoldersInheritPermissions(c, getUser(), form.getShouldInherit());
        }

        // a few properties on this page should be restricted to operational permissions (i.e. site admin)
        if (hasAdminOpsPerm)
        {
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

            if (!props.isValidUrl(form.getCustomLogin()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid login URL.  Should be in the form <module>-<name>.");
                return false;
            }
            props.setCustomLogin(form.getCustomLogin());

            String welcomeUrl = StringUtils.trimToNull(form.getCustomWelcome());
            if ("/".equals(welcomeUrl) || AppProps.getInstance().getContextPath().equalsIgnoreCase(welcomeUrl))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid welcome URL.  The url cannot equal '/' or the contextPath (" + AppProps.getInstance().getContextPath() + ")");
                return false;
            }
            props.setCustomWelcome(welcomeUrl);
        }

        props.setCompanyName(form.getCompanyName());
        props.setSystemDescription(form.getSystemDescription());
        props.setLogoHref(form.getLogoHref());
        props.setSystemShortName(form.getSystemShortName());
        props.setReportAProblemPath(form.getReportAProblemPath());

        if (!isBlank(form.getSupportEmail()))
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
        props.setDiscussionEnabled(form.isEnableDiscussion());

        DateParsingMode dateParsingMode = DateParsingMode.fromString(form.getDateParsingMode());
        props.setDateParsingMode(dateParsingMode);

        if (!saveFolderSettings(c, form, props, getUser(), errors))
            return false;

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

    private boolean handleFilesPost(AdminController.ProjectSettingsForm form, BindException errors) throws Exception
    {
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        if (service != null)
        {
            if (form.isPipelineRootForm())
                return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
            else
            {
                AdminController.setFileRootFromForm(getViewContext(), form);
            }
        }

        // Cloud settings
        AdminController.setEnabledCloudStores(getViewContext(), form, errors);

        return true;
    }

    public ActionURL getSuccessURL(AdminController.ProjectSettingsForm form)
    {
        Container c = getContainer();
        if (form.isResourcesTab())
            return new AdminController.AdminUrlsImpl().getLookAndFeelResourcesURL(c);
        else if (form.isMenuTab())
            return new AdminController.AdminUrlsImpl().getProjectSettingsMenuURL(c);  // Delete... I don't think we ever get here
        else if (form.isFilesTab())
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getProjectSettingsFileURL(c);
            if (form.isPipelineRootForm())
            {
                url.addParameter("piperootSet", true);
            }
            else
            {
                if (form.isFileRootChanged())
                    url.addParameter("rootSet", true);
                if (form.isEnabledCloudStoresChanged())
                    url.addParameter("cloudChanged", true);
            }
            return url;
        }
        else
            return new AdminController.AdminUrlsImpl().getProjectSettingsURL(c);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        setHelpTopic(new HelpTopic("customizeLook"));

        Container c = getContainer();

        if (c.isRoot())
            root.addChild("Admin Console", AdminController.getShowAdminURL()).addChild("Look and Feel Settings");
        else
            root.addChild("Project Settings");
        return root;
    }

    private void handleLogoFile(MultipartFile file, Container c) throws ServletException, SQLException, IOException
    {
        User user = getUser();

        // Set the name to something we'll recognize as a logo file
        String uploadedFileName = file.getOriginalFilename();
        int index = uploadedFileName.lastIndexOf(".");
        if (index == -1)
        {
            throw new ServletException("No file extension on the uploaded image");
        }

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        // Get rid of any existing logo
        AdminController.deleteExistingLogo(c, user);

        AttachmentFile renamed = new SpringAttachmentFile(file, AttachmentCache.LOGO_FILE_NAME_PREFIX + uploadedFileName.substring(index));
        AttachmentService.get().addAttachments(parent, Collections.singletonList(renamed), user);
        AttachmentCache.clearLogoCache();
    }


    private void handleIconFile(MultipartFile file, Container c) throws SQLException, IOException, ServletException
    {
        User user = getUser();

        if (!file.getOriginalFilename().toLowerCase().endsWith(".ico"))
        {
            throw new ServletException("FavIcon must be a .ico file");
        }

        AdminController.deleteExistingFavicon(c, user);

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentFile renamed = new SpringAttachmentFile(file, AttachmentCache.FAVICON_FILE_NAME);
        AttachmentService.get().addAttachments(parent, Collections.singletonList(renamed), user);
        AttachmentCache.clearFavIconCache();
    }

    private void handleCustomStylesheetFile(MultipartFile file, Container c) throws SQLException, IOException, ServletException
    {
        User user = getUser();

        AdminController.deleteExistingCustomStylesheet(c, user);

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentFile renamed = new SpringAttachmentFile(file, AttachmentCache.STYLESHEET_FILE_NAME);
        AttachmentService.get().addAttachments(parent, Collections.singletonList(renamed), user);

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
            List<NavTree> tabs = new ArrayList<>(2);

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

            // Should have validated this in the action
            assert c.isRoot() || c.isProject();

            switch (tabId)
            {
                case "resources":
                {
                    LookAndFeelResourcesBean bean = new LookAndFeelResourcesBean(c);
                    return new JspView<>("/org/labkey/core/admin/lookAndFeelResources.jsp", bean, _errors);
                }
                case "properties":
                {
                    return new LookAndFeelView(c, _form.getThemeName(), _errors);
                }
                case "menubar":
                    if (c.isRoot())
                        return getErrorView("Menu bar must be configured for each project separately.");

                    WebPartView v = new JspView<>("/org/labkey/core/admin/editMenuBar.jsp", null);
                    v.setView("menubar", new VBox());
                    Portal.populatePortalView(getViewContext(), Portal.DEFAULT_PORTAL_PAGE_ID, v, false, true, true, false);

                    return v;
                case "files":
                    if (c.isRoot())
                        return getErrorView("Files must be configured for each project separately.");

                    if (!_reshow || _form.isPipelineRootForm())
                    {
                        try
                        {
                            AdminController.setFormAndConfirmMessage(getViewContext(), _form);
                        }
                        catch (IllegalArgumentException e)
                        {
                            _errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                        }
                    }
                    VBox box = new VBox();
                    JspView view = new JspView<>("/org/labkey/core/admin/view/filesProjectSettings.jsp", _form, _errors);
                    String title = "Configure File Root";
                    if (CloudStoreService.get() != null)
                        title += " And Enable Cloud Stores";
                    view.setTitle(title);
                    box.addView(view);

                    // only site admins (i.e. AdminOperationsPermission) can configure the pipeline root
                    if (c.hasPermission(getViewContext().getUser(), AdminOperationsPermission.class))
                    {
                        SetupForm form = SetupForm.init(c);
                        form.setShowAdditionalOptionsLink(true);
                        form.setErrors(_errors);
                        PipeRoot pipeRoot = SetupForm.getPipelineRoot(c);

                        if (pipeRoot != null)
                        {
                            for (String errorMessage : pipeRoot.validate())
                                _errors.addError(new LabKeyError(errorMessage));
                        }
                        JspView pipelineView = (JspView) PipelineService.get().getSetupView(form);
                        pipelineView.setTitle("Configure Data Processing Pipeline");
                        box.addView(pipelineView);
                    }

                    return box;
                default:
                    return getErrorView("Unknown tab id");
            }
        }

        private HtmlView getErrorView(String msg)
        {
            return new HtmlView("<span class=\"labkey-error\">" + msg + "</span>");
        }
    }

    public static class LookAndFeelView extends JspView<LookAndFeelPropertiesBean>
    {
        public LookAndFeelView(Container c, @Nullable String newThemeName, BindException errors)
        {
            super("/org/labkey/core/admin/lookAndFeelProperties.jsp", getBean(c, newThemeName), errors);
        }

        private static LookAndFeelPropertiesBean getBean(Container c, @Nullable String newThemeName)
        {
            return new LookAndFeelPropertiesBean(c, newThemeName);
        }
    }

    private static abstract class LookAndFeelBean
    {
        public final String helpLink = new HelpTopic("customizeLook").getSimpleLinkHtml("more info...");
        public final String welcomeLink = new HelpTopic("customizeLook").getSimpleLinkHtml("more info...");
        public final String customColumnRestrictionHelpLink = new HelpTopic("chartTrouble").getSimpleLinkHtml("more info...");
    }

    public static class LookAndFeelResourcesBean extends LookAndFeelBean
    {
        public final Attachment customLogo;
        public final Attachment customFavIcon;
        public final Attachment customStylesheet;

        private LookAndFeelResourcesBean(Container c)
        {
            customLogo = AttachmentCache.lookupLogoAttachment(c);
            customFavIcon = AttachmentCache.lookupFavIconAttachment(new LookAndFeelResourceAttachmentParent(c));
            customStylesheet = AttachmentCache.lookupCustomStylesheetAttachment(new LookAndFeelResourceAttachmentParent(c));
        }
    }

    public static class LookAndFeelPropertiesBean extends LookAndFeelBean
    {
        public final Collection<WebTheme> themes = WebThemeManager.getWebThemes();
        public final List<ThemeFont> themeFonts = ThemeFont.getThemeFonts();
        public final ThemeFont currentThemeFont;
        public final WebTheme currentTheme;
        public final @Nullable WebTheme newTheme;

        private LookAndFeelPropertiesBean(Container c, @Nullable String newThemeName)
        {
            currentTheme = WebThemeManager.getTheme(c);
            currentThemeFont = ThemeFont.getThemeFont(c);

            //if new color scheme defined, get new theme name from url
            newTheme = newThemeName != null ? WebThemeManager.getTheme(newThemeName) : null;
        }
    }


    // Validate and populate the folder settings; save & log all changes
    public static boolean saveFolderSettings(Container c, AdminController.SettingsForm form, WriteableFolderLookAndFeelProperties props, User user, BindException errors)
    {
        String defaultDateFormat = StringUtils.trimToNull(form.getDefaultDateFormat());
        if (null == defaultDateFormat)
        {
            props.clearDefaultDateFormat();
        }
        else
        {
            try
            {
                props.setDefaultDateFormat(defaultDateFormat);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid date format: " + e.getMessage());
                return false;
            }
        }

        String defaultDateTimeFormat = StringUtils.trimToNull(form.getDefaultDateTimeFormat());
        if (null == defaultDateTimeFormat)
        {
            props.clearDefaultDateTimeFormat();
        }
        else
        {
            try
            {
                props.setDefaultDateTimeFormat(defaultDateTimeFormat);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid date time format: " + e.getMessage());
                return false;
            }
        }

        String defaultNumberFormat = StringUtils.trimToNull(form.getDefaultNumberFormat());
        if (null == defaultNumberFormat)
        {
            props.clearDefaultNumberFormat();
        }
        else
        {
            try
            {
                props.setDefaultNumberFormat(defaultNumberFormat);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Invalid number format: " + e.getMessage());
                return false;
            }
        }

        try
        {
            props.setRestrictedColumnsEnabled(form.areRestrictedColumnsEnabled());
        }
        catch (IllegalArgumentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Invalid restricted columns flag: " + e.getMessage());
            return false;
        }

        props.save();

        //write an audit log event
        props.writeAuditLogEvent(c, user);

        return true;
    }
}
