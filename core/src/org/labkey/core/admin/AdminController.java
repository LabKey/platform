/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.JSONObject;
import org.junit.Test;
import org.labkey.api.Constants;
import org.labkey.api.action.*;
import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceAttachmentParent;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheStats;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.Container.ContainerException;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.files.FileContentService;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.message.settings.MessageConfigService.ConfigTypeProvider;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.*;
import org.labkey.api.util.SystemMaintenance.SystemMaintenanceProperties;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.*;
import org.labkey.api.view.FolderManagement.FolderManagementViewAction;
import org.labkey.api.view.FolderManagement.FolderManagementViewPostAction;
import org.labkey.api.view.template.EmptyView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PageConfig.Template;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.core.CoreModule;
import org.labkey.core.admin.ProjectSettingsAction.LookAndFeelView;
import org.labkey.core.admin.miniprofiler.MiniProfilerController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.security.SecurityController;
import org.labkey.data.xml.TablesDocument;
import org.labkey.folder.xml.FolderDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.labkey.api.settings.AdminConsole.SettingsLinkType.Configuration;
import static org.labkey.api.settings.AdminConsole.SettingsLinkType.Diagnostics;
import static org.labkey.api.view.FolderManagement.EVERY_CONTAINER;
import static org.labkey.api.view.FolderManagement.FOLDERS_AND_PROJECTS;
import static org.labkey.api.view.FolderManagement.FOLDERS_ONLY;
import static org.labkey.api.view.FolderManagement.NOT_ROOT;
import static org.labkey.api.view.FolderManagement.addTab;

/**
 * User: Karl Lum
 * Date: Feb 27, 2008
 */
public class AdminController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
            AdminController.class,
            FilesSiteSettingsAction.class,
            FileListAction.class,
            ProjectSettingsAction.class);

    private static final Logger LOG = Logger.getLogger(AdminController.class);
    private static final Logger CLIENT_LOG = Logger.getLogger(LogAction.class);
    private static final String HEAP_MEMORY_KEY = "Total Heap Memory";

    private static long _errorMark = 0;

    public static void registerAdminConsoleLinks()
    {
        Container root = ContainerManager.getRoot();

        // Configuration
        AdminConsole.addLink(Configuration, "authentication", PageFlowUtil.urlProvider(LoginUrls.class).getConfigureURL());
        AdminConsole.addLink(Configuration, "email customization", new ActionURL(CustomizeEmailAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "experimental features", new ActionURL(ExperimentalFeaturesAction.class, root), AdminOperationsPermission.class);
        // TODO move to FileContentModule
        if (ModuleLoader.getInstance().hasModule("FileContent"))
            AdminConsole.addLink(Configuration, "files", new ActionURL(FilesSiteSettingsAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Configuration, "folder types", new ActionURL(FolderTypesAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "look and feel settings", new AdminUrlsImpl().getProjectSettingsURL(root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "missing value indicators", new AdminUrlsImpl().getMissingValuesURL(root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "profiler", new ActionURL(MiniProfilerController.ManageAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "project display order", new ActionURL(ReorderFoldersAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "short urls", new ActionURL(ShortURLAdminAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "site settings", new AdminUrlsImpl().getCustomizeSiteURL());
        AdminConsole.addLink(Configuration, "system maintenance", new ActionURL(ConfigureSystemMaintenanceAction.class, root));

/*
        // Management
        // note these should match (link and permissions) with SiteAdminMenu.getNavTree()
        AdminConsole.addLink(Management, "site admins", PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(root, "Administrators"), AdminOperationsPermission.class);
        AdminConsole.addLink(Management, "site developers", PageFlowUtil.urlProvider(SecurityUrls.class).getManageGroupURL(root, "Developers"), AdminOperationsPermission.class);
        AdminConsole.addLink(Management, "site users", PageFlowUtil.urlProvider(UserUrls.class).getSiteUsersURL(), UserManagementPermission.class);
        AdminConsole.addLink(Management, "site groups", PageFlowUtil.urlProvider(SecurityUrls.class).getSiteGroupsURL(root, null), UserManagementPermission.class);
        AdminConsole.addLink(Management, "site permissions", PageFlowUtil.urlProvider(SecurityUrls.class).getPermissionsURL(root), UserManagementPermission.class);
*/

        // Diagnostics
        AdminConsole.addLink(Diagnostics, "actions", new ActionURL(ActionsAction.class, root));
        AdminConsole.addLink(Diagnostics, "attachments", new ActionURL(AttachmentsAction.class, root));
        AdminConsole.addLink(Diagnostics, "caches", new ActionURL(CachesAction.class, root));
        AdminConsole.addLink(Diagnostics, "check database", new ActionURL(DbCheckerAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "credits", new ActionURL(CreditsAction.class, root));
        AdminConsole.addLink(Diagnostics, "dump heap", new ActionURL(DumpHeapAction.class, root));
        AdminConsole.addLink(Diagnostics, "environment variables", new ActionURL(EnvironmentVariablesAction.class, root));
        AdminConsole.addLink(Diagnostics, "memory usage", new ActionURL(MemTrackerAction.class, root));
        AdminConsole.addLink(Diagnostics, "queries", getQueriesURL(null));
        AdminConsole.addLink(Diagnostics, "reset site errors", new ActionURL(ResetErrorMarkAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Diagnostics, "running threads", new ActionURL(ShowThreadsAction.class, root));
        AdminConsole.addLink(Diagnostics, "site validation", new ActionURL(SiteValidationAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Diagnostics, "sql scripts", new ActionURL(SqlScriptController.ScriptsAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "system properties", new ActionURL(SystemPropertiesAction.class, root));
        AdminConsole.addLink(Diagnostics, "test email configuration", new ActionURL(EmailTestAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "view all site errors since reset", new ActionURL(ShowErrorsSinceMarkAction.class, root));
        AdminConsole.addLink(Diagnostics, "view all site errors", new ActionURL(ShowAllErrorsAction.class, root));
        AdminConsole.addLink(Diagnostics, "view primary site log file", new ActionURL(ShowPrimaryLogAction.class, root));
    }

    public static void registerFolderManagementTabs()
    {
        addTab("Folder Tree", "folderTree", NOT_ROOT, ManageFoldersAction.class);
        addTab("Folder Type", "folderType", NOT_ROOT, FolderTypeAction.class);
        addTab("Missing Values", "mvIndicators", EVERY_CONTAINER, MissingValuesAction.class);
        addTab("Module Properties", "props", c -> {
            if (!c.isRoot())
            {
                // Show module properties tab only if a module w/ properties to set is present for current folder
                for (Module m : c.getActiveModules())
                    if (!m.getModuleProperties().isEmpty())
                        return true;
            }

            return false;
        }, ModulePropertiesAction.class);
        addTab("Concepts", "concepts", c -> {
            // Show Concepts tab only if the experiment module is enabled in this container
            return c.getActiveModules().contains(ModuleLoader.getInstance().getModule("Experiment"));
        }, AdminController.ConceptsAction.class);
        addTab("Notifications", "messages", NOT_ROOT, NotificationsAction.class);
        addTab("Export", "export", NOT_ROOT, ExportFolderAction.class);
        addTab("Import", "import", NOT_ROOT, ImportFolderAction.class);
        addTab("Files", "files", FOLDERS_AND_PROJECTS, FileRootsAction.class);
        addTab("Formats", "settings", FOLDERS_ONLY, FolderSettingsAction.class);
        addTab("Information", "info", NOT_ROOT, FolderInformationAction.class);
    }

    public AdminController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            return getShowAdminURL();
        }
    }

    public NavTree appendAdminNavTrail(NavTree root, String childTitle, Class<? extends Controller> action)
    {
        return appendAdminNavTrail(root, childTitle, action, getContainer());
    }

    public static NavTree appendAdminNavTrail(NavTree root, String childTitle, Class<? extends Controller> action, Container container)
    {
        if (container.isRoot())
            root.addChild("Admin Console", getShowAdminURL());

        if (null == action)
            root.addChild(childTitle);
        else
            root.addChild(childTitle, new ActionURL(action, container));

        return root;
    }


    public static ActionURL getShowAdminURL()
    {
        return new ActionURL(ShowAdminAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class ShowAdminAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/admin.jsp", new AdminBean(getUser()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            URLHelper returnUrl = getViewContext().getActionURL().getReturnURL();
            if (null != returnUrl)
                root.addChild("Return to Project", returnUrl);
            root.addChild("Admin Console");
            getPageConfig().setHelpTopic(new HelpTopic("siteManagement"));
            return root;
        }
    }


    public static class AdminBean
    {
        public final List<Module> modules;
        public String javaVersion = System.getProperty("java.version");
        public String javaHome = System.getProperty("java.home");
        public String userName = System.getProperty("user.name");
        public String userHomeDir = System.getProperty("user.home");
        public String webappDir = ModuleLoader.getServletContext().getRealPath("");
        public String workingDir = new File("file").getAbsoluteFile().getParent();
        public String osName = System.getProperty("os.name");
        public String mode = AppProps.getInstance().isDevMode() ? "Development" : "Production";
        public String asserts = "disabled";
        public String serverGuid = AppProps.getInstance().getServerGUID();
        public String servletContainer = ModuleLoader.getServletContext().getServerInfo();
        public DbScope scope = CoreSchema.getInstance().getSchema().getScope();
        public List<Pair<String, Long>> active = UserManager.getRecentUsers(System.currentTimeMillis() - DateUtils.MILLIS_PER_HOUR);
        public String userEmail;

        private AdminBean(User user)
        {
            //noinspection ConstantConditions,AssertWithSideEffects
            assert null != (asserts = "enabled");
            userEmail = user.getEmail();
            modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        }
    }


    @RequiresSiteAdmin
    public class ShowModuleErrors extends SimpleViewAction
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Module Errors", this.getClass());
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/core/admin/moduleErrors.jsp");
        }
    }


    public static class AdminUrlsImpl implements AdminUrls
    {
        @Override
        public ActionURL getModuleErrorsURL(Container container)
        {
            return new ActionURL(ShowModuleErrors.class, container);
        }

        @Override
        public ActionURL getAdminConsoleURL()
        {
            return getShowAdminURL();
        }

        @Override
        public ActionURL getModuleStatusURL(URLHelper returnURL)
        {
            return AdminController.getModuleStatusURL(returnURL);
        }

        @Override
        public ActionURL getCustomizeSiteURL()
        {
            return new ActionURL(CustomizeSiteAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getCustomizeSiteURL(boolean upgradeInProgress)
        {
            ActionURL url = getCustomizeSiteURL();

            if (upgradeInProgress)
                url.addParameter("upgradeInProgress", "1");

            return url;
        }

        @Override
        public ActionURL getExperimentalFeaturesURL()
        {
            return new ActionURL(ExperimentalFeaturesAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getProjectSettingsURL(Container c)
        {
            return new ActionURL(ProjectSettingsAction.class, LookAndFeelProperties.getSettingsContainer(c));
        }

        public ActionURL getLookAndFeelResourcesURL(Container c)
        {
            ActionURL url = getProjectSettingsURL(c);
            url.addParameter("tabId", "resources");
            return url;
        }

        @Override
        public ActionURL getProjectSettingsMenuURL(Container c)
        {
            ActionURL url = getProjectSettingsURL(c);
            url.addParameter("tabId", "menubar");
            return url;
        }

        @Override
        public ActionURL getProjectSettingsFileURL(Container c)
        {
            ActionURL url = getProjectSettingsURL(c);
            url.addParameter("tabId", "files");
            return url;
        }

        @Override
        public ActionURL getCustomizeEmailURL(@NotNull Container c, @Nullable Class<? extends EmailTemplate> selectedTemplate, @Nullable URLHelper returnURL)
        {
            return getCustomizeEmailURL(c, selectedTemplate == null ? null : selectedTemplate.getName(), returnURL);
        }

        public ActionURL getCustomizeEmailURL(@NotNull Container c, @Nullable String selectedTemplate, @Nullable URLHelper returnURL)
        {
            ActionURL url = new ActionURL(CustomizeEmailAction.class, c);
            if (selectedTemplate != null)
            {
                url.addParameter("templateClass", selectedTemplate);
            }
            if (returnURL != null)
            {
                url.addParameter(ActionURL.Param.returnUrl, returnURL.toString());
            }
            return url;
        }

        public ActionURL getResetLookAndFeelPropertiesURL(Container c)
        {
            return new ActionURL(ResetPropertiesAction.class, c);
        }

        @Override
        public ActionURL getMaintenanceURL(URLHelper returnURL)
        {
            ActionURL url = new ActionURL(MaintenanceAction.class, ContainerManager.getRoot());
            if (returnURL != null)
                url.addParameter(ActionURL.Param.returnUrl, returnURL.toString());
            return url;
        }

        @Override
        public ActionURL getManageFoldersURL(Container c)
        {
            return getFolderManagementURL(ManageFoldersAction.class, c, "folderTree");
        }

        @Override
        public ActionURL getExportFolderURL(Container c)
        {
            return getFolderManagementURL(ExportFolderAction.class, c, "export");
        }

        @Override
        public ActionURL getImportFolderURL(Container c)
        {
            return getFolderManagementURL(ImportFolderAction.class, c, "import");
        }

        @Override
        public ActionURL getCreateProjectURL(@Nullable ActionURL returnURL)
        {
            return getCreateFolderURL(ContainerManager.getRoot(), returnURL);
        }

        @Override
        public ActionURL getCreateFolderURL(Container c, @Nullable ActionURL returnURL)
        {
            ActionURL result = new ActionURL(CreateFolderAction.class, c);
            if (returnURL != null)
            {
                result.addParameter(ActionURL.Param.returnUrl, returnURL.toString());
            }
            return result;
        }

        public ActionURL getSetFolderPermissionsURL(Container c)
        {
            return new ActionURL(SetFolderPermissionsAction.class, c);
        }

        @Override
        public NavTree appendAdminNavTrail(NavTree root, String childTitle, @Nullable ActionURL childURL)
        {
            root.addChild("Admin Console", getAdminConsoleURL().setFragment("links") );

            if (null != childURL)
                root.addChild(childTitle, childURL);
            else
                root.addChild(childTitle);

            return root;
        }

        @Override
        public ActionURL getFileRootsURL(Container c)
        {
            return getFolderManagementURL(FileRootsAction.class, c, "files");
        }

        @Override
        public ActionURL getFolderSettingsURL(Container c)
        {
            return getFolderManagementURL(FolderSettingsAction.class, c, "settings");
        }

        @Override
        public ActionURL getNotificationsURL(Container c)
        {
            return getFolderManagementURL(NotificationsAction.class, c, "messages");
        }

        @Override
        public ActionURL getModulePropertiesURL(Container c)
        {
            return getFolderManagementURL(ModulePropertiesAction.class, c, "props");
        }

        @Override
        public ActionURL getMissingValuesURL(Container c)
        {
            return getFolderManagementURL(MissingValuesAction.class, c, "mvIndicators");
        }

        public ActionURL getInitialFolderSettingsURL(Container c)
        {
            return new ActionURL(SetInitialFolderSettingsAction.class, c);
        }

        @Override
        public ActionURL getMemTrackerURL()
        {
            return new ActionURL(MemTrackerAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getFilesSiteSettingsURL(boolean upgrade)
        {
            ActionURL url = new ActionURL(FilesSiteSettingsAction.class, ContainerManager.getRoot());

            if (upgrade)
                url.addParameter("upgrade", true);

            return url;
        }

        @Override
        public ActionURL getSessionLoggingURL()
        {
            return new ActionURL(SessionLoggingAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getTrackedAllocationsViewerURL()
        {
            return new ActionURL(TrackedAllocationsViewerAction.class, ContainerManager.getRoot());
        }

        public static ActionURL getFolderManagementURL(Class<? extends Controller> actionClass, Container c, String tabId)
        {
            return new ActionURL(actionClass, c).addParameter("tabId", tabId);
        }
    }

    public static class MaintenanceBean
    {
        public String content;
        public ActionURL loginURL;
    }

    /**
     * During upgrade, startup, or maintenance mode, the user will be redirected to
     * MaintenanceAction and only admin users will be allowed to log into the server.
     * The maintenance.jsp page checks startup is complete or adminOnly mode is turned off
     * and will redirect to the returnURL or the loginURL.
     *
     * See Issue 18758 for more information.
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public class MaintenanceAction extends SimpleViewAction<ReturnUrlForm>
    {
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            if (!getUser().isInSiteAdminGroup())
            {
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            getPageConfig().setTemplate(Template.Dialog);

            boolean upgradeInProgress = ModuleLoader.getInstance().isUpgradeInProgress();
            boolean startupInProgress = ModuleLoader.getInstance().isStartupInProgress();
            boolean maintenanceMode = AppProps.getInstance().isUserRequestedAdminOnlyMode();

            String title = "Maintenance in progress";
            String content = "This site is currently undergoing maintenance, only site admins may login at this time.";
            if (upgradeInProgress)
            {
                title = "Upgrade in progress";
                content = "Upgrade in progress: only site admins may login at this time. Your browser will be redirected when startup is complete.";
            }
            else if (startupInProgress)
            {
                title = "Startup in progress";
                content = "Startup in progress: only site admins may login at this time. Your browser will be redirected when startup is complete.";
            }
            else if (maintenanceMode)
            {
                WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
                if (null != wikiService)
                {
                    content =  wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage());
                }
            }

            if (content == null)
                content = title;

            ActionURL loginURL = null;
            if (getUser().isGuest())
            {
                URLHelper returnURL = form.getReturnURLHelper();
                if (returnURL != null)
                    loginURL = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL(ContainerManager.getRoot(), returnURL);
                else
                    loginURL = PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL();
            }

            MaintenanceBean bean = new MaintenanceBean();
            bean.content = content;
            bean.loginURL = loginURL;

            JspView view = new JspView<>("/org/labkey/core/admin/maintenance.jsp", bean, errors);
            view.setTitle(title);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    /**
     * Similar to SqlScriptController.GetModuleStatusAction except that Guest is
     * allowed to check that the startup is complete.
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public class StartupStatusAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            JSONObject result = new JSONObject();
            result.put("startupComplete", ModuleLoader.getInstance().isStartupComplete());
            result.put("adminOnly",  AppProps.getInstance().isUserRequestedAdminOnlyMode());

            return new ApiSimpleResponse(result);
        }
    }

    @RequiresSiteAdmin
    @IgnoresTermsOfUse
    public class GetPendingRequestCountAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            JSONObject result = new JSONObject();
            result.put("pendingRequestCount", ViewServlet.getPendingRequestCount() - 1 /* Exclude this request */);

            return new ApiSimpleResponse(result);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetModulesAction extends ApiAction<GetModulesForm>
    {
        public ApiResponse execute(GetModulesForm form, BindException errors) throws Exception
        {
            Container c = ContainerManager.getForPath(getContainer().getPath());

            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Map<String, Object>> qinfos = new ArrayList<>();

            FolderType folderType = c.getFolderType();
            List<Module> allModules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            allModules.sort(Comparator.comparing(module -> module.getTabName(getViewContext()), String.CASE_INSENSITIVE_ORDER));

            //note: this has been altered to use Container.getRequiredModules() instead of FolderType
            //this is b/c a parent container must consider child workbooks when determining the set of requiredModules
            Set<Module> requiredModules = c.getRequiredModules(); //folderType.getActiveModules() != null ? folderType.getActiveModules() : new HashSet<Module>();
            Set<Module> activeModules = c.getActiveModules(getUser());

            for (Module m : allModules)
            {
                Map<String, Object> qinfo = new HashMap<>();

                qinfo.put("name", m.getName());
                qinfo.put("required", requiredModules.contains(m));
                qinfo.put("active", activeModules.contains(m) || requiredModules.contains(m));
                qinfo.put("enabled", (m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE ||
                    m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT) && !requiredModules.contains(m));
                qinfo.put("tabName", m.getTabName(getViewContext()));
                qinfo.put("requireSitePermission", m.getRequireSitePermission());
                qinfos.add(qinfo);
            }

            response.put("modules", qinfos);
            response.put("folderType", folderType.getName());

            return response;
        }
    }

    public static class GetModulesForm
    {

    }


    @RequiresNoPermission
    public class ContainerIdAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
//            getPageConfig().setTemplate(Template.None);
            HtmlView v = getContainerInfoView(c, getUser());
            v.setTitle("Container details: " + StringUtils.defaultIfEmpty(c.getName(),"/"));
            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static HtmlView getContainerInfoView(Container c, User currentUser)
    {
        User createdBy = UserManager.getUser(c.getCreatedBy());
        Map<String, Object> propValueMap = new LinkedHashMap<>();
        propValueMap.put("Path", PageFlowUtil.filter(c.getPath()));
        propValueMap.put("Name", PageFlowUtil.filter(c.getName()));
        propValueMap.put("Displayed Title", PageFlowUtil.filter(c.getTitle()));
        propValueMap.put("EntityId", c.getId());
        propValueMap.put("RowId", c.getRowId());
        propValueMap.put("Created", PageFlowUtil.filter(DateUtil.formatDateTime(c, c.getCreated())));
        propValueMap.put("Created By", (createdBy != null ? PageFlowUtil.filter(createdBy.getDisplayName(currentUser)) : "<" + c.getCreatedBy() + ">"));
        propValueMap.put("Folder Type", PageFlowUtil.filter(c.getFolderType().getName()));
        propValueMap.put("Description", PageFlowUtil.filter(c.getDescription()));

        return new HtmlView(PageFlowUtil.getDataRegionHtmlForPropertyObjects(propValueMap));
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade  // This action is invoked by HttpsUtil.checkSslRedirectConfiguration(), often while upgrade is in progress
    public class GuidAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            response.getWriter().write(GUID.makeGUID());
        }
    }

    /**
     * Action that preforms a basic check to see if the configured db is available
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class BaseHealthCheckAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            if (!ModuleLoader.getInstance().isStartupComplete())
                return new ApiSimpleResponse("healthy", false);

            Map<String, Object> healthValues = new HashMap<>();
            //Hold overall status
            Map<String, Boolean> overallStatus = new HashMap<>();

            healthValues.put("Overall", overallStatus);
            healthValues.put("DbConnectionStatus", dbConnectionHealth(overallStatus));
            additionalHealthChecks(overallStatus, healthValues);

            Collection<Boolean> statusValues = overallStatus.values();
            Boolean healthy = statusValues != null;

            for (Boolean healthStat : statusValues)
            {
                healthy = healthy && healthStat;
            }

            if (getUser().hasRootAdminPermission())
            {
                healthValues.put("healthy", healthy);
                return new ApiSimpleResponse(healthValues);
            }
            else
            {
                if (!healthy)
                {
                    createResponseWriter().writeAndCloseError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server isn't ready yet");
                    return null;
                }

                return new ApiSimpleResponse("healthy", healthy);
            }
        }

        private Map<String, Boolean> dbConnectionHealth(Map<String, Boolean> overallStatus)
        {
            Map<String, Boolean> healthValues = new HashMap<>();
            Boolean allConnected = true;
            for (DbScope dbScope : DbScope.getDbScopes())
            {
                Boolean dbConnected;
                try (Connection conn = dbScope.getConnection())
                {
                    dbConnected = conn != null;
                }
                catch (SQLException e)
                {
                    dbConnected = false;
                }

                healthValues.put(dbScope.getDatabaseName(), dbConnected);
                allConnected &= dbConnected;
            }

            overallStatus.put("AllDBsConnected", allConnected);
            return healthValues;
        }

        /**
         * Add additional health checks to the response
         * @param overallStatus json map showing boolean health indicators for server health categories
         * @param healthValues Map expanded health per category
         */
        protected void additionalHealthChecks(Map<String, Boolean> overallStatus, Map<String, Object> healthValues)
        {
            //No additional health checks since this is the base
        }
    }

    /**
     * Preform the base DB HealthCheck plus some additional checks
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class HealthCheckAction extends BaseHealthCheckAction
    {
        @Override
        protected void additionalHealthChecks(Map<String, Boolean> overallStatus, Map<String, Object> healthValues)
        {
            //Check if initial user is set
            healthValues.put("Users", userHealth(overallStatus));
        }

        /**
         * Check user health status (particularly, is at least one user registered)
         * @param overallStatus Map for server. This method will add flag indicating if a user has been registered
         * @return a Map containing expanded user health status
         */
        private Map<String, Object> userHealth(Map<String, Boolean> overallStatus)
        {
            Map<String, Object> userHealth = new HashMap<>();
            ZonedDateTime now = ZonedDateTime.now();
            try
            {
                int userCount = UserManager.getUserCount(Date.from(now.toInstant()));
                userHealth.put("RegisteredUsers", userCount);
                overallStatus.put("HasUsers", userCount > 0);
            }
            catch (SQLException e)
            {
                LOG.error("HealthCheck: can't get user count", e);
                overallStatus.put("HasUsers", false);  //TODO: not sure if this is best option...
            }
            return userHealth;
        }
    }

    // No security checks... anyone (even guests) can view the credits page
    @RequiresNoPermission
    public class CreditsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            VBox views = new VBox();
            List<Module> modules = new ArrayList<>(ModuleLoader.getInstance().getModules());

            // DefaultModule and CoreModule compareTo() implementations claim to cooperate to put Core first in the sort order... but it doesn't work
            modules.sort((m1, m2) ->
            {
                if (m1.getName().equalsIgnoreCase(m2.getName()))
                    return 0;
                else if (CoreModule.CORE_MODULE_NAME.equalsIgnoreCase(m1.getName()))
                    return -1;
                else if (CoreModule.CORE_MODULE_NAME.equalsIgnoreCase(m2.getName()))
                    return 1;
                else
                    return m1.getName().compareToIgnoreCase(m2.getName());
            });

            String jarRegEx = "^([\\w-\\.]+\\.jar)\\|";

            addCreditsViews(views, modules, "jars.txt", "JAR", "webapp", null, Module::getJarFilenames, jarRegEx);

            views.addView(new CreditsView("/core/resources/credits/tomcat_jars.txt", getCreditsFile(ModuleLoader.getInstance().getCoreModule(), "tomcat_jars.txt"), getTomcatJars(), "Tomcat JAR", "/external/lib/tomcat directory", null, jarRegEx));

            addCreditsViews(views, modules, "scripts.txt", "Script, Icon and Font");
            addCreditsViews(views, modules, "source.txt", "Java Source Code");
            addCreditsViews(views, modules, "executables.txt", "Executable");

            if (AppProps.getInstance().isDevMode() || MothershipReport.usedInstaller())
                addCreditsViews(views, modules, "installer.txt", "Executable", null, "the Graphical Windows Installer for ", null, null);

            return views;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Credits", this.getClass());
        }
    }


    private void addCreditsViews(VBox views, List<Module> modules, String creditsFile, String fileType) throws IOException
    {
        addCreditsViews(views, modules, creditsFile, fileType, null, null, null, null);
    }


    private void addCreditsViews(VBox views, List<Module> modules, String creditsFile, String fileType, @Nullable String foundWhere, @Nullable String descriptionPrefix, @Nullable Function<Module, Collection<String>> filenameProvider, @Nullable String wikiSourceSearchPattern) throws IOException
    {
        for (Module module : modules)
        {
            String wikiSource = getCreditsFile(module, creditsFile);

            Collection<String> filenames = null;

            if (null != filenameProvider)
                filenames = filenameProvider.apply(module);

            if (null != wikiSource || (null != filenames && !filenames.isEmpty()))
            {
                HttpView moduleJS = new CreditsView(creditsFile, wikiSource, filenames, fileType, foundWhere, (null != descriptionPrefix ? descriptionPrefix : "") + "the " + module.getName() + " Module", wikiSourceSearchPattern);
                views.addView(moduleJS);
            }
        }
    }


    private static class CreditsView extends WebPartView
    {
        private final String WIKI_LINE_SEP = "\r\n\r\n";
        private String _html;

        CreditsView(String creditsFilename, @Nullable String wikiSource, @Nullable Collection<String> filenames, String fileType, String foundWhere, String component, String wikiSourceSearchPattern)
        {
            super(fileType + " Files Distributed with " + (null == component ? "LabKey" : component));

            // If both wikiSource and filenames are null there can't be a problem.
            // trims/empty check allow for problem reporting if one is null but not the other.
            if (null != filenames)
                wikiSource = StringUtils.trimToEmpty(wikiSource) + getErrors(wikiSource, creditsFilename, filenames, fileType, foundWhere, wikiSourceSearchPattern);

            if (StringUtils.isNotEmpty(wikiSource))
            {
                WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
                if (null != wikiService)
                {
                    String html = wikiService.getFormattedHtml(WikiRendererType.RADEOX, wikiSource);
                    _html = "<style type=\"text/css\">\ntr.table-odd td { background-color: #EEEEEE; }</style>\n" + html;
                }
                else
                    _html = "<p class='labkey-error'>NO WIKI SERVICE AVAILABLE!</p>";
            }
        }


        private String getErrors(String wikiSource, String creditsFilename, Collection<String> filenames, String fileType, String foundWhere, String wikiSourceSearchPattern)
        {
            Set<String> documentedFilenames = new CaseInsensitiveTreeSet();

            if (null != wikiSource)
            {
                Pattern p = Pattern.compile(wikiSourceSearchPattern, Pattern.MULTILINE);
                Matcher m = p.matcher(wikiSource);

                while(m.find())
                {
                    String found = m.group(1);
                    documentedFilenames.add(found);
                }
            }

            Set<String> documentedFilenamesCopy = new HashSet<>(documentedFilenames);
            documentedFilenames.removeAll(filenames);
            filenames.removeAll(documentedFilenamesCopy);
            for (String name : filenames.toArray(new String[filenames.size()]))
                if (name.startsWith(".")) filenames.remove(name);

            String undocumentedErrors = filenames.isEmpty() ? "" : WIKI_LINE_SEP + "**WARNING: The following " + fileType + " file" + (filenames.size() > 1 ? "s were" : " was") + " found in your " + foundWhere + " but "+ (filenames.size() > 1 ? "are" : " is") + " not documented in " + creditsFilename + ":**\\\\" + StringUtils.join(filenames.iterator(), "\\\\");
            String missingErrors = documentedFilenames.isEmpty() ? "" : WIKI_LINE_SEP + "**WARNING: The following " + fileType + " file" + (documentedFilenames.size() > 1 ? "s are" : " is") + " documented in " + creditsFilename + " but " + (documentedFilenames.size() > 1 ? " were" : " was") + " not found in your " + foundWhere + ":**\\\\" + StringUtils.join(documentedFilenames.iterator(), "\\\\");

            return undocumentedErrors + missingErrors;
        }


        @Override
        public void renderView(Object model, PrintWriter out)
        {
            out.print(_html);
        }
    }


    private static String getCreditsFile(Module module, String filename) throws IOException
    {
        // New way... in /resources/credits
        InputStream is = module.getResourceStream("credits/" + filename);

        return null == is ? null : PageFlowUtil.getStreamContentsAsString(is);
    }


    private Set<String> getTomcatJars()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        File tomcat = new File(AppProps.getInstance().getProjectRoot(), "external/lib/tomcat");

        if (!tomcat.exists())
            return null;

        Set<String> filenames = new CaseInsensitiveTreeSet();

        addAllChildren(tomcat, filenames);

        return filenames;
    }


    private static FileFilter _fileFilter = f -> !f.isDirectory();

    private static FileFilter _dirFilter = f -> f.isDirectory() && !".svn".equals(f.getName());

    private void addAllChildren(File root, Set<String> filenames)
    {
        File[] files = root.listFiles(_fileFilter);

        for (File file : files)
            filenames.add(file.getName());

        File[] dirs = root.listFiles(_dirFilter);

        for (File dir : dirs)
            addAllChildren(dir, filenames);
    }


    private void validateNetworkDrive(SiteSettingsForm form, BindException errors)
    {
        if (form.getNetworkDriveLetter() == null)
        {
            errors.reject(ERROR_MSG, "You must specify a drive letter");
        }
        else if (form.getNetworkDriveLetter().trim().length() > 1)
        {
            errors.reject(ERROR_MSG, "Network drive letter must be a single character");
        }
        else
        {
            char letter = form.getNetworkDriveLetter().trim().toLowerCase().charAt(0);

            if (letter < 'a' || letter > 'z')
            {
                errors.reject(ERROR_MSG, "Network drive letter must be a letter");
            }
            else if (form.getNetworkDrivePath() == null || form.getNetworkDrivePath().trim().length() == 0)
            {
                errors.reject(ERROR_MSG, "If you specify a network drive letter, you must also specify a path");
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ResetLogoAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingLogo(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ResetPropertiesAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            Container c = getContainer();
            boolean folder = !(c.isRoot() || c.isProject());
            boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);

            WriteableFolderLookAndFeelProperties props = folder ? LookAndFeelProperties.getWriteableFolderInstance(c) : LookAndFeelProperties.getWriteableInstance(c);
            props.clear(hasAdminOpsPerm);
            props.save();
            // TODO: Audit log?

            if (!folder)
            {
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();
                return new AdminUrlsImpl().getProjectSettingsURL(c);
            }
            else
            {
                return new AdminUrlsImpl().getFolderSettingsURL(c);
            }
        }
    }


    static void deleteExistingLogo(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        Collection<Attachment> attachments = AttachmentService.get().getAttachments(parent);
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().startsWith(AttachmentCache.LOGO_FILE_NAME_PREFIX))
            {
                AttachmentService.get().deleteAttachment(parent, attachment.getName(), user);
                AttachmentCache.clearLogoCache();
            }
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ResetFaviconAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            deleteExistingFavicon(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    static void deleteExistingFavicon(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.FAVICON_FILE_NAME, user);
        AttachmentCache.clearFavIconCache();
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteCustomStylesheetAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            deleteExistingCustomStylesheet(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    static void deleteExistingCustomStylesheet(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.STYLESHEET_FILE_NAME, user);

        // This custom stylesheet is still cached in CoreController, but look & feel revision checking should ensure
        // that it gets cleared out on the next request.
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    @CSRF
    public class CustomizeSiteAction extends FormViewAction<SiteSettingsForm>
    {
        public ModelAndView getView(SiteSettingsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.isUpgradeInProgress())
                getPageConfig().setTemplate(Template.Dialog);

            SiteSettingsBean bean = new SiteSettingsBean(form.isUpgradeInProgress(), form.isTestInPage());
            setHelpTopic("configAdmin");
            getPageConfig().setFocusId("defaultDomain");
            return new JspView<>("/org/labkey/core/admin/customizeSite.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Customize Site", this.getClass());
        }

        public void validateCommand(SiteSettingsForm form, Errors errors)
        {
            if (form.isShowRibbonMessage() && StringUtils.isEmpty(form.getRibbonMessageHtml()))
            {
                errors.reject(ERROR_MSG, "Cannot enable the ribbon message without providing a message to show");
            }
        }

        public boolean handlePost(SiteSettingsForm form, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().setDeferUsageReport(false);
            HttpServletRequest request = getViewContext().getRequest();

            // We only need to check that SSL is running if the user isn't already using SSL
            if (form.isSslRequired() && !(request.isSecure() && (form.getSslPort() == request.getServerPort())))
            {
                URL testURL = new URL("https", request.getServerName(), form.getSslPort(), AppProps.getInstance().getContextPath());
                Pair<String, Integer> sslResponse = HttpsUtil.testSslUrl(testURL, "Ensure that the web server is configured for SSL and the port is correct. If SSL is enabled, try saving these settings while connected via SSL.");

                if (sslResponse != null)
                {
                    errors.reject(ERROR_MSG, sslResponse.first);
                    return false;
                }
            }

            WriteableAppProps props = AppProps.getWriteableInstance();

            props.setDefaultDomain(form.getDefaultDomain());
            props.setPipelineToolsDir(form.getPipelineToolsDirectory());
            props.setSSLRequired(form.isSslRequired());
            props.setSSLPort(form.getSslPort());
            props.setMemoryUsageDumpInterval(form.getMemoryUsageDumpInterval());
            props.setMaxBLOBSize(form.getMaxBLOBSize());
            props.setExt3Required(form.isExt3Required());
            props.setExt3APIRequired(form.isExt3APIRequired());
            props.setSelfReportExceptions(form.isSelfReportExceptions());

            props.setAdminOnlyMessage(form.getAdminOnlyMessage());
            props.setShowRibbonMessage(form.isShowRibbonMessage());
            props.setRibbonMessageHtml(form.getRibbonMessageHtml());
            props.setUserRequestedAdminOnlyMode(form.isAdminOnlyMode());

            props.setUseContainerRelativeURL(form.getUseContainerRelativeURL());
            props.setAllowApiKeys(form.isAllowApiKeys());
            props.setApiKeyExpirationSeconds(form.getApiKeyExpirationSeconds());
            props.setAllowSessionKeys(form.isAllowSessionKeys());

            try
            {
                ExceptionReportingLevel level = ExceptionReportingLevel.valueOf(form.getExceptionReportingLevel());
                props.setExceptionReportingLevel(level);
            }
            catch (IllegalArgumentException ignored) {}

            UsageReportingLevel level = null;

            try
            {
                level = UsageReportingLevel.valueOf(form.getUsageReportingLevel());
                props.setUsageReportingLevel(level);
            }
            catch (IllegalArgumentException e)
            {
            }

            if (form.getNetworkDriveLetter() != null && form.getNetworkDriveLetter().trim().length() > 0)
            {
                validateNetworkDrive(form, errors);

                if (errors.hasErrors())
                    return false;
            }

            props.setNetworkDriveLetter(form.getNetworkDriveLetter() == null ? null : form.getNetworkDriveLetter().trim());
            props.setNetworkDrivePath(form.getNetworkDrivePath() == null ? null : form.getNetworkDrivePath().trim());
            props.setNetworkDriveUser(form.getNetworkDriveUser() == null ? null : form.getNetworkDriveUser().trim());
            props.setNetworkDrivePassword(form.getNetworkDrivePassword() == null ? null : form.getNetworkDrivePassword().trim());
            props.setAdministratorContactEmail(form.getAdministratorContactEmail() == null ? null : form.getAdministratorContactEmail().trim());

            if (null != form.getBaseServerUrl())
            {
                if (form.isSslRequired() && !form.getBaseServerUrl().startsWith("https"))
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL. SSL connection is required. Consider https://.");
                    return false;
                }

                try
                {
                    props.setBaseServerUrl(form.getBaseServerUrl());
                }
                catch (URISyntaxException e)
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL, \"" + e.getMessage() + "\"." +
                            "Please enter a valid base URL containing the protocol, hostname, and port if required. " +
                            "The webapp context path should not be included. " +
                            "For example: \"https://www.example.com\" or \"http://www.labkey.org:8080\" and not \"http://www.example.com/labkey/\"");
                    return false;
                }
            }

            String frameOptions = StringUtils.trimToEmpty(form.getXFrameOptions());
            if (!frameOptions.equals("DENY") && !frameOptions.equals("SAMEORIGIN") && !frameOptions.equals("ALLOW"))
            {
                errors.reject(ERROR_MSG, "XFrameOptions must equal DENY, or SAMEORIGIN, or ALLOW");
                return false;
            }
            props.setXFrameOptions(frameOptions);

            String check = form.getCSRFCheck();
            if (!check.equals("POST") && !check.equals("ADMINONLY"))
            {
                errors.reject(ERROR_MSG, "CSRFCheck must equal POST or ADMINONLY");
                return false;
            }
            props.setCSRFCheck(check);

            props.save(getViewContext().getUser());

            if (null != level)
                level.scheduleUpgradeCheck();

            return true;
        }

        public ActionURL getSuccessURL(SiteSettingsForm form)
        {
            if (form.isUpgradeInProgress())
            {
                return AppProps.getInstance().getHomePageActionURL();
            }
            else
            {
                return new AdminUrlsImpl().getAdminConsoleURL();
            }
        }
    }


    public static class SiteSettingsBean
    {
        public final String helpLink = new HelpTopic("configAdmin").getSimpleLinkHtml("more info...");
        public final boolean upgradeInProgress;
        public final boolean testInPage;
        public final boolean showSelfReportExceptions;

        private SiteSettingsBean(boolean upgradeInProgress, boolean testInPage)
        {
            this.upgradeInProgress = upgradeInProgress;
            this.testInPage = testInPage;
            this.showSelfReportExceptions = MothershipReport.isShowSelfReportExceptions();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SiteValidationAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/sitevalidation/siteValidation.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("siteValidation"));
            return appendAdminNavTrail(root, "Site Validation", this.getClass());
        }
    }

    public interface FileManagementForm
    {
        String getFolderRootPath();

        void setFolderRootPath(String folderRootPath);

        String getFileRootOption();

        void setFileRootOption(String fileRootOption);

        String getConfirmMessage();

        void setConfirmMessage(String confirmMessage);

        boolean isDisableFileSharing();

        boolean hasSiteDefaultRoot();

        String[] getEnabledCloudStore();

        void setEnabledCloudStore(String[] enabledCloudStore);

        boolean isCloudFileRoot();

        @Nullable
        String getCloudRootName();

        void setCloudRootName(String cloudRootName);

        void setFileRootChanged(boolean changed);

        void setEnabledCloudStoresChanged(boolean changed);
    }

    public interface SettingsForm
    {
        String getDefaultDateFormat();

        @SuppressWarnings("UnusedDeclaration")
        void setDefaultDateFormat(String defaultDateFormat);

        String getDefaultDateTimeFormat();

        @SuppressWarnings("UnusedDeclaration")
        void setDefaultDateTimeFormat(String defaultDateTimeFormat);

        String getDefaultNumberFormat();

        @SuppressWarnings("UnusedDeclaration")
        void setDefaultNumberFormat(String defaultNumberFormat);

        boolean areRestrictedColumnsEnabled();

        @SuppressWarnings("UnusedDeclaration")
        void setRestrictedColumnsEnabled(boolean restrictedColumnsEnabled);
    }

    public static class ProjectSettingsForm extends SetupForm implements FileManagementForm, SettingsForm
    {
        private boolean _shouldInherit; // new subfolders should inherit parent permissions
        private String _systemDescription;
        private String _systemShortName;
        private String _themeName;
        private String _themeFont;
        private String _folderDisplayMode;
        private boolean _enableHelpMenu;
        private boolean _enableDiscussion;
        private String _logoHref;
        private String _companyName;
        private String _systemEmailAddress;
        private String _reportAProblemPath;
        private String _tabId;
        private String _folderRootPath;
        private String _fileRootOption;
        private String _supportEmail;
        private String[] _enabledCloudStore;
        private String _dateParsingMode;
        private String _defaultDateFormat;
        private String _defaultDateTimeFormat;
        private String _defaultNumberFormat;
        private boolean _restrictedColumnsEnabled;
        private String _customLogin;
        private String _customWelcome;
        private String _cloudRootName;
        private boolean _fileRootChanged;
        private boolean _enabledCloudStoresChanged;

        public enum FileRootProp
        {
            disable,
            siteDefault,
            folderOverride,
            cloudRoot
        }

        public boolean getShouldInherit()
        {
            return _shouldInherit;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setShouldInherit(boolean b)
        {
            _shouldInherit = b;
        }

        public String getSystemDescription()
        {
            return _systemDescription;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemDescription(String systemDescription)
        {
            _systemDescription = systemDescription;
        }

        public String getSystemShortName()
        {
            return _systemShortName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemShortName(String systemShortName)
        {
            _systemShortName = systemShortName;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

        public String getThemeFont()
        {
            return _themeFont;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setThemeFont(String themeFont)
        {
            _themeFont = themeFont;
        }

        public String getFolderDisplayMode()
        {
            return _folderDisplayMode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFolderDisplayMode(String folderDisplayMode)
        {
            _folderDisplayMode = folderDisplayMode;
        }

        public String getDateParsingMode()
        {
            return _dateParsingMode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDateParsingMode(String dateParsingMode)
        {
            _dateParsingMode = dateParsingMode;
        }

        public boolean isEnableHelpMenu()
        {
            return _enableHelpMenu;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEnableHelpMenu(boolean enableHelpMenu)
        {
            _enableHelpMenu = enableHelpMenu;
        }

        public boolean isEnableDiscussion()
        {
            return _enableDiscussion;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEnableDiscussion(boolean enableDiscussion)
        {
            _enableDiscussion = enableDiscussion;
        }

        public String getLogoHref()
        {
            return _logoHref;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setLogoHref(String logoHref)
        {
            _logoHref = logoHref;
        }

        public String getReportAProblemPath()
        {
            return _reportAProblemPath;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportAProblemPath(String reportAProblemPath)
        {
            _reportAProblemPath = reportAProblemPath;
        }

        public String getCompanyName()
        {
            return _companyName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCompanyName(String companyName)
        {
            _companyName = companyName;
        }

        public String getCustomLogin()
        {
            return _customLogin;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCustomLogin(String customLogin)
        {
            _customLogin = customLogin;
        }

        public String getCustomWelcome()
        {
            return _customWelcome;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCustomWelcome(String customWelcome)
        {
            _customWelcome = customWelcome;
        }

        public String getSystemEmailAddress()
        {
            return _systemEmailAddress;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemEmailAddress(String systemEmailAddress)
        {
            _systemEmailAddress = systemEmailAddress;
        }

        public String getTabId()
        {
            return _tabId;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setTabId(String tabId)
        {
            _tabId = tabId;
        }

        public boolean isResourcesTab()
        {
            return "resources".equals(getTabId());
        }

        public boolean isMenuTab()
        {
            return "menubar".equals(getTabId());
        }

        public boolean isFilesTab()
        {
            return "files".equals(getTabId());
        }

        public boolean isDisableFileSharing()
        {
            return FileRootProp.disable.name().equals(getFileRootOption());
        }

        public boolean hasSiteDefaultRoot()
        {
            return FileRootProp.siteDefault.name().equals(getFileRootOption());
        }

        public String getFolderRootPath()
        {
            return _folderRootPath;
        }

        public void setFolderRootPath(String folderRootPath)
        {
            _folderRootPath = folderRootPath;
        }

        public String getFileRootOption()
        {
            return _fileRootOption;
        }

        public void setFileRootOption(String fileRootOption)
        {
            _fileRootOption = fileRootOption;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSupportEmail(String supportEmail)
        {
            _supportEmail = supportEmail;
        }

        public String getSupportEmail()
        {
            return _supportEmail;
        }

        @Override
        public String[] getEnabledCloudStore()
        {
            return _enabledCloudStore;
        }

        @Override
        public void setEnabledCloudStore(String[] enabledCloudStore)
        {
            _enabledCloudStore = enabledCloudStore;
        }

        @Override
        public String getDefaultDateFormat()
        {
            return _defaultDateFormat;
        }

        @Override
        @SuppressWarnings("UnusedDeclaration")
        public void setDefaultDateFormat(String defaultDateFormat)
        {
            _defaultDateFormat = defaultDateFormat;
        }

        @Override
        public String getDefaultDateTimeFormat()
        {
            return _defaultDateTimeFormat;
        }

        @Override
        @SuppressWarnings("UnusedDeclaration")
        public void setDefaultDateTimeFormat(String defaultDateTimeFormat)
        {
            _defaultDateTimeFormat = defaultDateTimeFormat;
        }

        @Override
        public String getDefaultNumberFormat()
        {
            return _defaultNumberFormat;
        }

        @Override
        @SuppressWarnings("UnusedDeclaration")
        public void setDefaultNumberFormat(String defaultNumberFormat)
        {
            _defaultNumberFormat = defaultNumberFormat;
        }

        public boolean areRestrictedColumnsEnabled()
        {
            return _restrictedColumnsEnabled;
        }

        public void setRestrictedColumnsEnabled(boolean restrictedColumnsEnabled)
        {
            _restrictedColumnsEnabled = restrictedColumnsEnabled;
        }

        public boolean isCloudFileRoot()
        {
            return FileRootProp.cloudRoot.name().equals(getFileRootOption());
        }

        @Nullable
        public String getCloudRootName()
        {
            return _cloudRootName;
        }

        public void setCloudRootName(String cloudRootName)
        {
            _cloudRootName = cloudRootName;
        }

        public boolean isFileRootChanged()
        {
            return _fileRootChanged;
        }

        public void setFileRootChanged(boolean changed)
        {
            _fileRootChanged = changed;
        }

        public boolean isEnabledCloudStoresChanged()
        {
            return _enabledCloudStoresChanged;
        }

        public void setEnabledCloudStoresChanged(boolean enabledCloudStoresChanged)
        {
            _enabledCloudStoresChanged = enabledCloudStoresChanged;
        }
    }

    public static class SiteSettingsForm
    {
        private boolean _upgradeInProgress = false;
        private boolean _testInPage = false;

        private String _defaultDomain;
        private String _pipelineToolsDirectory;
        private boolean _sslRequired;
        private boolean _adminOnlyMode;
        private boolean _showRibbonMessage;
        private boolean _ext3Required;
        private boolean _ext3APIRequired;
        private boolean _selfReportExceptions;
        private String _adminOnlyMessage;
        private String _ribbonMessageHtml;
        private int _sslPort;
        private int _memoryUsageDumpInterval;
        private int _maxBLOBSize;
        private String _exceptionReportingLevel;
        private String _usageReportingLevel;
        private String _administratorContactEmail;

        private String _networkDriveLetter;
        private String _networkDrivePath;
        private String _networkDriveUser;
        private String _networkDrivePassword;
        private String _baseServerUrl;
        private String _callbackPassword;
        private boolean _useContainerRelativeURL;
        private boolean _allowApiKeys;
        private int _apiKeyExpirationSeconds;
        private boolean _allowSessionKeys;

        private String _CSRFCheck;
        private String _XFrameOptions;

        public void setDefaultDomain(String defaultDomain)
        {
            _defaultDomain = defaultDomain;
        }

        public String getDefaultDomain()
        {
            return _defaultDomain;
        }

        public String getPipelineToolsDirectory()
        {
            return _pipelineToolsDirectory;
        }

        public void setPipelineToolsDirectory(String pipelineToolsDirectory)
        {
            _pipelineToolsDirectory = pipelineToolsDirectory;
        }

        public boolean isSslRequired()
        {
            return _sslRequired;
        }

        public void setSslRequired(boolean sslRequired)
        {
            _sslRequired = sslRequired;
        }

        public boolean isExt3Required()
        {
            return _ext3Required;
        }

        public void setExt3Required(boolean ext3Required)
        {
            _ext3Required = ext3Required;
        }

        public boolean isExt3APIRequired()
        {
            return _ext3APIRequired;
        }

        public void setExt3APIRequired(boolean ext3APIRequired)
        {
            _ext3APIRequired = ext3APIRequired;
        }

        public int getSslPort()
        {
            return _sslPort;
        }

        public void setSslPort(int sslPort)
        {
            _sslPort = sslPort;
        }

        public boolean isAdminOnlyMode()
        {
            return _adminOnlyMode;
        }

        public void setAdminOnlyMode(boolean adminOnlyMode)
        {
            _adminOnlyMode = adminOnlyMode;
        }

        public String getAdminOnlyMessage()
        {
            return _adminOnlyMessage;
        }

        public void setAdminOnlyMessage(String adminOnlyMessage)
        {
            _adminOnlyMessage = adminOnlyMessage;
        }

        public boolean isSelfReportExceptions()
        {
            return _selfReportExceptions;
        }

        public void setSelfReportExceptions(boolean selfReportExceptions)
        {
            _selfReportExceptions = selfReportExceptions;
        }

        public String getExceptionReportingLevel()
        {
            return _exceptionReportingLevel;
        }

        public void setExceptionReportingLevel(String exceptionReportingLevel)
        {
            _exceptionReportingLevel = exceptionReportingLevel;
        }

        public String getUsageReportingLevel()
        {
            return _usageReportingLevel;
        }

        public void setUsageReportingLevel(String usageReportingLevel)
        {
            _usageReportingLevel = usageReportingLevel;
        }

        public String getAdministratorContactEmail()
        {
            return _administratorContactEmail;
        }

        public void setAdministratorContactEmail(String administratorContactEmail)
        {
            _administratorContactEmail = administratorContactEmail;
        }

        public boolean isUpgradeInProgress()
        {
            return _upgradeInProgress;
        }

        public void setUpgradeInProgress(boolean upgradeInProgress)
        {
            _upgradeInProgress = upgradeInProgress;
        }

        public int getMemoryUsageDumpInterval()
        {
            return _memoryUsageDumpInterval;
        }

        public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
        {
            _memoryUsageDumpInterval = memoryUsageDumpInterval;
        }

        public int getMaxBLOBSize()
        {
            return _maxBLOBSize;
        }

        public void setMaxBLOBSize(int maxBLOBSize)
        {
            _maxBLOBSize = maxBLOBSize;
        }

        public String getNetworkDriveLetter()
        {
            return _networkDriveLetter;
        }

        public void setNetworkDriveLetter(String networkDriveLetter)
        {
            _networkDriveLetter = networkDriveLetter;
        }

        public String getNetworkDrivePassword()
        {
            return _networkDrivePassword;
        }

        public void setNetworkDrivePassword(String networkDrivePassword)
        {
            _networkDrivePassword = networkDrivePassword;
        }

        public String getNetworkDrivePath()
        {
            return _networkDrivePath;
        }

        public void setNetworkDrivePath(String networkDrivePath)
        {
            _networkDrivePath = networkDrivePath;
        }

        public String getNetworkDriveUser()
        {
            return _networkDriveUser;
        }

        public void setNetworkDriveUser(String networkDriveUser)
        {
            _networkDriveUser = networkDriveUser;
        }

        public String getBaseServerUrl()
        {
            return _baseServerUrl;
        }

        public void setBaseServerUrl(String baseServerUrl)
        {
            _baseServerUrl = baseServerUrl;
        }

        public boolean isTestInPage()
        {
            return _testInPage;
        }

        public void setTestInPage(boolean testInPage)
        {
            _testInPage = testInPage;
        }

        public String getCallbackPassword()
        {
            return _callbackPassword;
        }

        public void setCallbackPassword(String callbackPassword)
        {
            _callbackPassword = callbackPassword;
        }

        public boolean isShowRibbonMessage()
        {
            return _showRibbonMessage;
        }

        public void setShowRibbonMessage(boolean showRibbonMessage)
        {
            _showRibbonMessage = showRibbonMessage;
        }

        public String getRibbonMessageHtml()
        {
            return _ribbonMessageHtml;
        }

        public void setRibbonMessageHtml(String ribbonMessageHtml)
        {
            _ribbonMessageHtml = ribbonMessageHtml;
        }

        public boolean getUseContainerRelativeURL()
        {
            return _useContainerRelativeURL;
        }

        public void setUseContainerRelativeURL(boolean useContainerRelativeURL)
        {
            _useContainerRelativeURL = useContainerRelativeURL;
        }

        public boolean isAllowApiKeys()
        {
            return _allowApiKeys;
        }

        public void setAllowApiKeys(boolean allowApiKeys)
        {
            _allowApiKeys = allowApiKeys;
        }

        public int getApiKeyExpirationSeconds()
        {
            return _apiKeyExpirationSeconds;
        }

        public void setApiKeyExpirationSeconds(int apiKeyExpirationSeconds)
        {
            _apiKeyExpirationSeconds = apiKeyExpirationSeconds;
        }

        public boolean isAllowSessionKeys()
        {
            return _allowSessionKeys;
        }

        public void setAllowSessionKeys(boolean allowSessionKeys)
        {
            _allowSessionKeys = allowSessionKeys;
        }

        public String getCSRFCheck()
        {
            return _CSRFCheck;
        }

        public void setCSRFCheck(String CSRFCheck)
        {
            _CSRFCheck = CSRFCheck;
        }

        public String getXFrameOptions()
        {
            return _XFrameOptions;
        }

        public void setXFrameOptions(String XFrameOptions)
        {
            _XFrameOptions = XFrameOptions;
        }
    }


    @AdminConsoleAction
    public class ShowThreadsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // Log to labkey.log as well as showing through the browser
            DebugInfoDumper.dumpThreads(3);
            return new JspView<>("/org/labkey/core/admin/threads.jsp", new ThreadsBean());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("runningThreads"));
            return appendAdminNavTrail(root, "Current Threads", this.getClass());
        }
    }

    @AdminConsoleAction
    public class DumpHeapAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            File destination = DebugInfoDumper.dumpHeap();
            return new HtmlView(PageFlowUtil.filter("Heap dumped to " + destination.getAbsolutePath()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("dumpHeap"));
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Heap dump", null);
            return root;
        }
    }


    public static class ThreadsBean
    {
        public Map<Thread, Set<Integer>> spids = new HashMap<>();
        public List<Thread> threads;
        public Map<Thread, StackTraceElement[]> stackTraces;

        ThreadsBean()
        {
            stackTraces =  Thread.getAllStackTraces();
            threads = new ArrayList<>(stackTraces.keySet());
            threads.sort(Comparator.comparing(Thread::getName, String.CASE_INSENSITIVE_ORDER));

            spids = new HashMap<>();

            for (Thread t : threads)
            {
                spids.put(t, ConnectionWrapper.getSPIDsForThread(t));
            }
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ShowNetworkDriveTestAction extends SimpleViewAction<SiteSettingsForm>
    {
        public ModelAndView getView(SiteSettingsForm form, BindException errors) throws Exception
        {
            NetworkDrive testDrive = new NetworkDrive();
            testDrive.setPassword(form.getNetworkDrivePassword());
            testDrive.setPath(form.getNetworkDrivePath());
            testDrive.setUser(form.getNetworkDriveUser());

            validateNetworkDrive(form, errors);

            TestNetworkDriveBean bean = new TestNetworkDriveBean();

            if (!errors.hasErrors())
            {
                char driveLetter = form.getNetworkDriveLetter().trim().charAt(0);
                try
                {
                    String mountError = testDrive.mount(driveLetter);
                    if (mountError != null)
                    {
                        errors.reject(ERROR_MSG, mountError);
                    }
                    else
                    {
                        File f = new File(driveLetter + ":\\");
                        if (!f.exists())
                        {
                            errors.reject(ERROR_MSG, "Could not access network drive");
                        }
                        else
                        {
                            String[] fileNames = f.list();
                            if (fileNames == null)
                                fileNames = new String[0];
                            Arrays.sort(fileNames);
                            bean.setFiles(fileNames);
                        }
                    }
                }
                catch (IOException | InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                try
                {
                    testDrive.unmount(driveLetter);
                }
                catch (IOException | InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
            }

            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/testNetworkDrive.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ResetErrorMarkAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o)
        {
            File errorLogFile = getErrorLogFile();
            _errorMark = errorLogFile.length();
            return getShowAdminURL();
        }
    }


    @AdminConsoleAction
    public class ShowErrorsSinceMarkAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            PageFlowUtil.streamLogFile(response, _errorMark, getErrorLogFile());
        }
    }


    @AdminConsoleAction
    public class ShowAllErrorsAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            PageFlowUtil.streamLogFile(response, 0, getErrorLogFile());
        }
    }

    @AdminConsoleAction
    public class ShowPrimaryLogAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            File tomcatHome = new File(System.getProperty("catalina.home"));
            File logFile = new File(tomcatHome, "logs/labkey.log");
            PageFlowUtil.streamLogFile(response, 0, logFile);
        }
    }

    private File getErrorLogFile()
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/labkey-errors.log");
    }

    private static ActionURL getActionsURL()
    {
        return new ActionURL(ActionsAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class ActionsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new ActionsTabStrip();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("actionsDiagnostics"));
            return appendAdminNavTrail(root, "Actions", this.getClass());
        }
    }


    private static class ActionsTabStrip extends TabStripView
    {
        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new ArrayList<>(3);

            tabs.add(new TabInfo("Summary", "summary", getActionsURL()));
            tabs.add(new TabInfo("Details", "details", getActionsURL()));
            tabs.add(new TabInfo("Exceptions", "exceptions", getActionsURL()));

            return tabs;
        }

        public HttpView getTabView(String tabId)
        {
            if ("exceptions".equals(tabId))
                return new ActionsExceptionsView();
            return new ActionsView(!"details".equals(tabId));
        }
    }

    @AdminConsoleAction
    public class ExportActionsAction extends ExportAction<Object>
    {
        public void export(Object form, HttpServletResponse response, BindException errors) throws Exception
        {
            ActionsTsvWriter writer = new ActionsTsvWriter();
            writer.write(response);
        }
    }


    private static ActionURL getQueriesURL(@Nullable String statName)
    {
        ActionURL url = new ActionURL(QueriesAction.class, ContainerManager.getRoot());

        if (null != statName)
            url.addParameter("stat", statName);

        return url;
    }


    @AdminConsoleAction
    public class QueriesAction extends SimpleViewAction<QueriesForm>
    {
        public ModelAndView getView(QueriesForm form, BindException errors) throws Exception
        {
            String buttonHTML = "";
            if (getUser().hasRootAdminPermission())
                buttonHTML += PageFlowUtil.button("Reset All Statistics").href(getResetQueryStatisticsURL()) + "&nbsp;";
            buttonHTML += PageFlowUtil.button("Export").href(getExportQueriesURL()) + "<br/><br/>";

            return QueryProfiler.getInstance().getReportView(form.getStat(), buttonHTML, AdminController::getQueriesURL,
                    sql -> getQueryStackTracesURL(sql.hashCode()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("queryLogger"));
            return appendAdminNavTrail(root, "Queries", this.getClass());
        }
    }

    public static class QueriesForm
    {
        private String _stat = "Count";

        public String getStat()
        {
            return _stat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setStat(String stat)
        {
            _stat = stat;
        }
    }


    private static ActionURL getQueryStackTracesURL(int hashCode)
    {
        ActionURL url = new ActionURL(QueryStackTracesAction.class, ContainerManager.getRoot());
        url.addParameter("sqlHashCode", hashCode);
        return url;
    }


    @AdminConsoleAction
    public class QueryStackTracesAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return QueryProfiler.getInstance().getStackTraceView(form.getSqlHashCode(), AdminController::getExecutionPlanURL);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            appendAdminNavTrail(root, "Queries", QueriesAction.class);
            root.addChild("Query Stack Traces");
            return root;
        }
    }


    private static ActionURL getExecutionPlanURL(String sql)
    {
        ActionURL url = new ActionURL(ExecutionPlanAction.class, ContainerManager.getRoot());
        url.addParameter("sqlHashCode", sql.hashCode());
        return url;
    }


    @AdminConsoleAction
    public class ExecutionPlanAction extends SimpleViewAction<QueryForm>
    {
        private int _hashCode;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _hashCode = form.getSqlHashCode();
            return QueryProfiler.getInstance().getExecutionPlanView(form.getSqlHashCode());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            appendAdminNavTrail(root, "Queries", QueriesAction.class);
            root.addChild("Query Stack Traces", getQueryStackTracesURL(_hashCode));
            root.addChild("Execution Plan");
            return root;
        }
    }


    public static class QueryForm
    {
        int _sqlHashCode;

        public int getSqlHashCode()
        {
            return _sqlHashCode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSqlHashCode(int sqlHashCode)
        {
            _sqlHashCode = sqlHashCode;
        }
    }


    private ActionURL getExportQueriesURL()
    {
        return new ActionURL(ExportQueriesAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class ExportQueriesAction extends ExportAction<Object>
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            QueryProfiler.QueryStatTsvWriter writer = new QueryProfiler.QueryStatTsvWriter();
            writer.write(response);
        }
    }

    private static ActionURL getResetQueryStatisticsURL()
    {
        return new ActionURL(ResetQueryStatisticsAction.class, ContainerManager.getRoot());
    }


    @RequiresPermission(AdminPermission.class)
    public class ResetQueryStatisticsAction extends SimpleRedirectAction<QueriesForm>
    {
        public ActionURL getRedirectURL(QueriesForm form)
        {
            QueryProfiler.getInstance().resetAllStatistics();
            return getQueriesURL(form.getStat());
        }
    }


    @AdminConsoleAction
    public class CachesAction extends SimpleViewAction<MemForm>
    {
        public ModelAndView getView(MemForm form, BindException errors) throws Exception
        {
            if (form.isClearCaches())
            {
                LOG.info("Clearing Introspector caches");
                Introspector.flushCaches();
                LOG.info("Purging all caches");
                CacheManager.clearAllKnownCaches();
                ActionURL redirect = getViewContext().cloneActionURL().deleteParameter("clearCaches");
                throw new RedirectException(redirect);
            }

            List<TrackingCache> caches = CacheManager.getKnownCaches();
            List<CacheStats> cacheStats = new ArrayList<>();
            List<CacheStats> transactionStats = new ArrayList<>();

            for (TrackingCache cache : caches)
            {
                cacheStats.add(CacheManager.getCacheStats(cache));
                transactionStats.add(CacheManager.getTransactionCacheStats(cache));
            }

            StringBuilder html = new StringBuilder();

            html.append(PageFlowUtil.textLink("Clear Caches and Refresh", getCachesURL(true, false)));
            html.append(PageFlowUtil.textLink("Refresh", getCachesURL(false, false)));

            html.append("<br/><br/>\n");
            appendStats(html, "Caches", cacheStats);

            html.append("<br/><br/>\n");
            appendStats(html, "Transaction Caches", transactionStats);

            return new HtmlView(html.toString());
        }

        private void appendStats(StringBuilder html, String title, List<CacheStats> stats)
        {
            Collections.sort(stats);

            html.append("<p><b>");
            html.append(PageFlowUtil.filter(title));
            html.append(" (").append(stats.size()).append(")</b></p>\n");

            html.append("<table class=\"labkey-data-region-legacy labkey-show-borders\">\n");
            html.append("<tr><td class=\"labkey-column-header\">Debug Name</td>");
            html.append("<td class=\"labkey-column-header\">Limit</td>");
            html.append("<td class=\"labkey-column-header\">Max&nbsp;Size</td>");
            html.append("<td class=\"labkey-column-header\">Current&nbsp;Size</td>");
            html.append("<td class=\"labkey-column-header\">Gets</td>");
            html.append("<td class=\"labkey-column-header\">Misses</td>");
            html.append("<td class=\"labkey-column-header\">Puts</td>");
            html.append("<td class=\"labkey-column-header\">Expirations</td>");
            html.append("<td class=\"labkey-column-header\">Removes</td>");
            html.append("<td class=\"labkey-column-header\">Clears</td>");
            html.append("<td class=\"labkey-column-header\">Miss Percentage</td></tr>");

            long size = 0;
            long gets = 0;
            long misses = 0;
            long puts = 0;
            long expirations = 0;
            long removes = 0;
            long clears = 0;
            int rowCount = 0;

            for (CacheStats stat : stats)
            {
                size += stat.getSize();
                gets += stat.getGets();
                misses += stat.getMisses();
                puts += stat.getPuts();
                expirations += stat.getExpirations();
                removes += stat.getRemoves();
                clears += stat.getClears();

                html.append("<tr class=\"").append(rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row").append("\">");

                appendDescription(html, stat.getDescription(), stat.getCreationStackTrace());

                Long limit = stat.getLimit();
                Long maxSize = stat.getMaxSize();

                appendLongs(html, limit, maxSize, stat.getSize(), stat.getGets(), stat.getMisses(), stat.getPuts(), stat.getExpirations(), stat.getRemoves(), stat.getClears());
                appendDoubles(html, stat.getMissRatio());

                if (null != limit && maxSize >= limit)
                    html.append("<td><font class=\"labkey-error\">This cache has been limited</font></td>");

                html.append("</tr>\n");
                rowCount++;
            }

            double ratio = 0 != gets ? misses / (double)gets : 0;
            html.append("<tr class=\"labkey-row\"><td><b>Total</b></td>");

            appendLongs(html, null, null, size, gets, misses, puts, expirations, removes, clears);
            appendDoubles(html, ratio);

            html.append("</tr>\n");
            html.append("</table>\n");
        }

        private void appendDescription(StringBuilder html, String description, StackTraceElement[] creationStackTrace)
        {
            StringBuilder sb = new StringBuilder();

            for (StackTraceElement element : creationStackTrace)
            {
                sb.append(element.toString());
                sb.append("\n");
            }

            String message = PageFlowUtil.jsString(sb);
            html.append("<td><a href=\"#\" onClick=\"alert(");
            html.append(message);
            html.append(");return false;\">");
            html.append(PageFlowUtil.filter(description));
            html.append("</a></td>");
        }

        private void appendLongs(StringBuilder html, Long... stats)
        {
            for (Long stat : stats)
            {
                if (null == stat)
                    html.append("<td>&nbsp;</td>");
                else
                    html.append("<td align=\"right\">").append(Formats.commaf0.format(stat)).append("</td>");
            }
        }

        private void appendDoubles(StringBuilder html, double... stats)
        {
            for (double stat : stats)
                html.append("<td align=\"right\">").append(Formats.percent.format(stat)).append("</td>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("cachesDiagnostics"));
            return appendAdminNavTrail(root, "Cache Statistics", this.getClass());
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class EnvironmentVariablesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/properties.jsp", System.getenv());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Environment Variables", this.getClass());
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class SystemPropertiesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Map<String, String>>("/org/labkey/core/admin/properties.jsp", new HashMap(System.getProperties()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "System Properties", this.getClass());
        }
    }


    public static class ConfigureSystemMaintenanceForm
    {
        private String _maintenanceTime;
        private Set<String> _enable = Collections.emptySet();
        private boolean _enableSystemMaintenance = true;

        public String getMaintenanceTime()
        {
            return _maintenanceTime;
        }

        public void setMaintenanceTime(String maintenanceTime)
        {
            _maintenanceTime = maintenanceTime;
        }

        public Set<String> getEnable()
        {
            return _enable;
        }

        public void setEnable(Set<String> enable)
        {
            _enable = enable;
        }

        public boolean isEnableSystemMaintenance()
        {
            return _enableSystemMaintenance;
        }

        public void setEnableSystemMaintenance(boolean enableSystemMaintenance)
        {
            _enableSystemMaintenance = enableSystemMaintenance;
        }
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureSystemMaintenanceAction extends FormViewAction<ConfigureSystemMaintenanceForm>
    {
        @Override
        public void validateCommand(ConfigureSystemMaintenanceForm form, Errors errors)
        {
            Date date = SystemMaintenance.parseSystemMaintenanceTime(form.getMaintenanceTime());

            if (null == date)
                errors.reject(ERROR_MSG, "Invalid format for system maintenance time");
        }

        @Override
        public ModelAndView getView(ConfigureSystemMaintenanceForm form, boolean reshow, BindException errors) throws Exception
        {
            SystemMaintenanceProperties prop = SystemMaintenance.getProperties();
            return new JspView<>("/org/labkey/core/admin/systemMaintenance.jsp", prop, errors);
        }

        @Override
        public boolean handlePost(ConfigureSystemMaintenanceForm form, BindException errors) throws Exception
        {
            SystemMaintenance.setTimeDisabled(!form.isEnableSystemMaintenance());
            SystemMaintenance.setProperties(form.getEnable(), form.getMaintenanceTime());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ConfigureSystemMaintenanceForm form)
        {
            return new AdminUrlsImpl().getAdminConsoleURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Configure System Maintenance", this.getClass());
        }
    }


    public static class SystemMaintenanceForm
    {
        private String _taskName;
        private boolean _test = false;

        public String getTaskName()
        {
            return _taskName;
        }

        @SuppressWarnings("unused")
        public void setTaskName(String taskName)
        {
            _taskName = taskName;
        }

        public boolean isTest()
        {
            return _test;
        }

        public void setTest(boolean test)
        {
            _test = test;
        }
    }


    @RequiresSiteAdmin
    public class SystemMaintenanceAction extends FormHandlerAction<SystemMaintenanceForm>
    {
        private Integer _jobId = null;
        private URLHelper _url = null;

        @Override
        public void validateCommand(SystemMaintenanceForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getSuccessView(SystemMaintenanceForm form) throws IOException
        {
            // Send the pipeline job details absolute URL back to the test
            sendPlainText(_url.getURIString());

            // Suppress templates, divs, etc.
            getPageConfig().setTemplate(Template.None);
            return new EmptyView();
        }

        @Override
        public boolean handlePost(SystemMaintenanceForm form, BindException errors) throws Exception
        {
            String jobGuid = new SystemMaintenanceJob(form.getTaskName(), getUser()).call();

            if (null != jobGuid)
                _jobId = PipelineService.get().getJobId(getUser(), getContainer(), jobGuid);

            PipelineStatusUrls urls = PageFlowUtil.urlProvider(PipelineStatusUrls.class);
            _url = null != _jobId ? urls.urlDetails(getContainer(), _jobId) : urls.urlBegin(getContainer());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SystemMaintenanceForm form)
        {
            // In the standard case, redirect to the pipeline details URL
            // If the test is invoking system maintenance then return the URL instead
            return form.isTest() ? null : _url;
        }
    }


    @AdminConsoleAction
    public class AttachmentsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return AttachmentService.get().getAdminView(getViewContext().getActionURL());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Attachments", getClass());
        }
    }


    @AdminConsoleAction
    public class FindAttachmentParentsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return AttachmentService.get().getFindAttachmentParentsView();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Find Attachment Parents", getClass());
        }
    }


    public static ActionURL getMemTrackerURL(boolean clearCaches, boolean gc)
    {
        ActionURL url = new ActionURL(MemTrackerAction.class, ContainerManager.getRoot());

        if (clearCaches)
            url.addParameter(MemForm.Params.clearCaches, "1");

        if (gc)
            url.addParameter(MemForm.Params.gc, "1");

        return url;
    }

    public static ActionURL getCachesURL(boolean clearCaches, boolean gc)
    {
        ActionURL url = new ActionURL(CachesAction.class, ContainerManager.getRoot());

        if (clearCaches)
            url.addParameter(MemForm.Params.clearCaches, "1");

        if (gc)
            url.addParameter(MemForm.Params.gc, "1");

        return url;
    }

    private static volatile String lastCacheMemUsed = null;

    @AdminConsoleAction
    public class MemTrackerAction extends SimpleViewAction<MemForm>
    {
        public ModelAndView getView(MemForm form, BindException errors) throws Exception
        {
            Set<Object> objectsToIgnore = MemTracker.getInstance().beforeReport();

            boolean gc = form.isGc();
            boolean cc = form.isClearCaches();

            if (getUser().hasRootAdminPermission() && (gc || cc))
            {
                // If both are requested then try determine and record cache memory usage
                if (gc && cc)
                {
                    // gc once to get an accurate free memory read
                    long before = gc();
                    clearCaches();
                    // gc again now that we cleared caches
                    long cacheMemoryUsed = before - gc();

                    // Difference could be < 0 if JVM or other threads have performed gc, in which case we can't guesstimate cache memory usage
                    String cacheMemUsed = cacheMemoryUsed > 0 ? FileUtils.byteCountToDisplaySize(cacheMemoryUsed) : "Unknown";
                    LOG.info("Estimate of cache memory used: " + cacheMemUsed);
                    lastCacheMemUsed = cacheMemUsed;
                }
                else if (cc)
                {
                    clearCaches();
                }
                else
                {
                    gc();
                }

                LOG.info("Cache clearing and garbage collecting complete");
            }

            return new JspView<>("/org/labkey/core/admin/memTracker.jsp", new MemBean(getViewContext().getRequest(), objectsToIgnore));
        }

        /** @return estimated current memory usage, post-garbage collection */
        private long gc()
        {
            LOG.info("Garbage collecting");
            System.gc();
            // This is more reliable than relying on just free memory size, as the VM can grow/shrink the heap at will
            return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        }

        private void clearCaches()
        {
            LOG.info("Clearing Introspector caches");
            Introspector.flushCaches();
            LOG.info("Purging all caches");
            CacheManager.clearAllKnownCaches();
            SearchService ss = SearchService.get();
            if (null != ss)
            {
                LOG.info("Purging SearchService queues");
                ss.purgeQueues();
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("memTracker"));
            return appendAdminNavTrail(root, "Memory usage -- " + DateUtil.formatDateTime(getContainer()), this.getClass());
        }
    }


    public static class MemForm
    {
        private enum Params {clearCaches, gc}

        private boolean _clearCaches = false;
        private boolean _gc = false;

        public boolean isClearCaches()
        {
            return _clearCaches;
        }

        public void setClearCaches(boolean clearCaches)
        {
            _clearCaches = clearCaches;
        }

        public boolean isGc()
        {
            return _gc;
        }

        public void setGc(boolean gc)
        {
            _gc = gc;
        }
    }


    public static class MemBean
    {
        public final List<Pair<String, MemoryUsageSummary>> memoryUsages = new ArrayList<>();
        public final List<Pair<String, Object>> systemProperties = new ArrayList<>();
        public final List<MemTracker.HeldReference> references;
        public final List<String> graphNames = new ArrayList<>();
        public final List<String> activeThreads = new LinkedList<>();

        public boolean assertsEnabled = false;

        private MemBean(HttpServletRequest request, Set<Object> objectsToIgnore)
        {
            List<MemTracker.HeldReference> all = MemTracker.getInstance().getReferences();
            long threadId = Thread.currentThread().getId();

            // Attempt to detect other threads running labkey code -- mem tracker page will warn if any are found
            for (Thread thread : new ThreadsBean().threads)
            {
                if (thread.getId() == threadId)
                    continue;

                Thread.State state = thread.getState();

                if (state == Thread.State.RUNNABLE || state == Thread.State.BLOCKED)
                {
                    boolean labkeyThread = false;

                    for (StackTraceElement element : thread.getStackTrace())
                    {
                        String className = element.getClassName();

                        if (className.startsWith("org.labkey") || className.startsWith("org.fhcrc"))
                        {
                            labkeyThread = true;
                            break;
                        }
                    }

                    if (labkeyThread)
                        activeThreads.add(thread.getName());
                }
            }

            // ignore recently allocated
            long start = ViewServlet.getRequestStartTime(request) - 2000;
            references = new ArrayList<>(all.size());

            for (MemTracker.HeldReference r : all)
            {
                if (r.getThreadId() == threadId && r.getAllocationTime() >= start)
                    continue;

                if (objectsToIgnore.contains(r.getReference()))
                    continue;

                references.add(r);
            }

            // memory:
            graphNames.add("Heap");
            graphNames.add("Non Heap");

            MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
            if (membean != null)
            {
                memoryUsages.add(new Pair<>(HEAP_MEMORY_KEY, getUsage(membean.getHeapMemoryUsage())));
                memoryUsages.add(new Pair<>("Total Non-heap Memory", getUsage(membean.getNonHeapMemoryUsage())));
            }

            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean pool : pools)
            {
                memoryUsages.add(new Pair<>(pool.getName() + " " + pool.getType(), getUsage(pool)));
                graphNames.add(pool.getName());
            }

            // class loader:
            ClassLoadingMXBean classbean = ManagementFactory.getClassLoadingMXBean();
            if (classbean != null)
            {
                systemProperties.add(new Pair<>("Loaded Class Count", classbean.getLoadedClassCount()));
                systemProperties.add(new Pair<>("Unloaded Class Count", classbean.getUnloadedClassCount()));
                systemProperties.add(new Pair<>("Total Loaded Class Count", classbean.getTotalLoadedClassCount()));
            }

            // runtime:
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
            {
                systemProperties.add(new Pair<>("VM Start Time", DateUtil.formatDateTimeISO8601(new Date(runtimeBean.getStartTime()))));
                long upTime = runtimeBean.getUptime(); // round to sec
                upTime = upTime - (upTime % 1000);
                systemProperties.add(new Pair<>("VM Uptime", DateUtil.formatDuration(upTime)));
                systemProperties.add(new Pair<>("VM Version", runtimeBean.getVmVersion()));
                systemProperties.add(new Pair<>("VM Classpath", runtimeBean.getClassPath()));
            }

            // threads:
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            if (threadBean != null)
            {
                systemProperties.add(new Pair<>("Thread Count", threadBean.getThreadCount()));
                systemProperties.add(new Pair<>("Peak Thread Count", threadBean.getPeakThreadCount()));
                long[] deadlockedThreads = threadBean.findMonitorDeadlockedThreads();
                systemProperties.add(new Pair<>("Deadlocked Thread Count", deadlockedThreads != null ? deadlockedThreads.length : 0));
            }

            // threads:
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcBean : gcBeans)
            {
                systemProperties.add(new Pair<>(gcBean.getName() + " GC count", gcBean.getCollectionCount()));
                systemProperties.add(new Pair<>(gcBean.getName() + " GC time", DateUtil.formatDuration(gcBean.getCollectionTime())));
            }

            String cacheMem = lastCacheMemUsed;

            if (null != cacheMem)
                systemProperties.add(new Pair<>("Most Recent Estimated Cache Memory Usage", cacheMem));

            systemProperties.add(new Pair<>("In-Use DB Connections", ConnectionWrapper.getActiveConnectionCount()));

            //noinspection ConstantConditions
            assert assertsEnabled = true;
        }
    }


    private static MemoryUsageSummary getUsage(MemoryPoolMXBean pool)
    {
        try
        {
            return getUsage(pool.getUsage());
        }
        catch (IllegalArgumentException x)
        {
            // sometimes we get usage>committed exception with older versions of JRockit
            return null;
        }
    }

    public static class MemoryUsageSummary
    {
        private final String _init;
        private final String _used;
        private final String _committed;
        private final String _max;

        public MemoryUsageSummary(MemoryUsage usage)
        {
            _init = FileUtils.byteCountToDisplaySize(usage.getInit());
            _used = FileUtils.byteCountToDisplaySize(usage.getUsed());
            _committed = FileUtils.byteCountToDisplaySize(usage.getCommitted());
            _max = FileUtils.byteCountToDisplaySize(usage.getMax());
        }

        public String getInit()
        {
            return _init;
        }

        public String getUsed()
        {
            return _used;
        }

        public String getCommitted()
        {
            return _committed;
        }

        public String getMax()
        {
            return _max;
        }
    }

    private static MemoryUsageSummary getUsage(MemoryUsage usage)
    {
        if (null == usage)
            return null;

        try
        {
            return new MemoryUsageSummary(usage);
        }
        catch (IllegalArgumentException x)
        {
            // sometime we get usage>committed exception with older verions of JRockit
            return null;
        }
    }


    public static class ChartForm
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }


    private static class MemoryCategory implements Comparable<MemoryCategory>
    {
        private String _type;
        private double _mb;
        public MemoryCategory(String type, double mb)
        {
            _type = type;
            _mb = mb;
        }

        public int compareTo(@NotNull MemoryCategory o)
        {
            return new Double(getMb()).compareTo(new Double(o.getMb()));
        }

        public String getType()
        {
            return _type;
        }

        public double getMb()
        {
            return _mb;
        }
    }


    @AdminConsoleAction
    public class MemoryChartAction extends ExportAction<ChartForm>
    {
        public void export(ChartForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            MemoryUsage usage = null;
            boolean showLegend = false;
            if ("Heap".equals(form.getType()))
            {
                usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                showLegend = true;
            }
            else if ("Non Heap".equals(form.getType()))
                usage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            else
            {
                List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
                for (Iterator it = pools.iterator(); it.hasNext() && usage == null;)
                {
                    MemoryPoolMXBean pool = (MemoryPoolMXBean) it.next();
                    if (form.getType().equals(pool.getName()))
                        usage = pool.getUsage();
                }
            }

            if (usage == null)
                throw new NotFoundException();

            DefaultCategoryDataset dataset = new DefaultCategoryDataset();
            List<MemoryCategory> types = new ArrayList<>(4);

            types.add(new MemoryCategory("Init", usage.getInit() / (1024 * 1024)));
            types.add(new MemoryCategory("Used", usage.getUsed() / (1024 * 1024)));
            types.add(new MemoryCategory("Committed", usage.getCommitted() / (1024 * 1024)));
            types.add(new MemoryCategory("Max", usage.getMax() / (1024 * 1024)));
            Collections.sort(types);

            for (int i = 0; i < types.size(); i++)
            {
                double mbPastPrevious = i > 0 ? types.get(i).getMb() - types.get(i - 1).getMb() : types.get(i).getMb();
                dataset.addValue(mbPastPrevious, types.get(i).getType(), "");
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(form.getType(), null, null, dataset, PlotOrientation.HORIZONTAL, showLegend, false, false);
            response.setContentType("image/png");

            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, showLegend ? 800 : 398, showLegend ? 100 : 70);
        }
    }


    // TODO: Check permissions, what if guests have read perm?, different containers?
    @RequiresLogin
    @RequiresPermission(ReadPermission.class)
    public class SetAdminModeAction extends SimpleRedirectAction<UserPrefsForm>
    {
        public ActionURL getRedirectURL(UserPrefsForm form)
        {
            getViewContext().getSession().setAttribute("adminMode", form.isAdminMode());
            NavTreeManager.uncacheAll();
            if (null != form.getReturnActionURL())
                return form.getReturnActionURL();
            return new ActionURL();
        }
    }

    public static class UserPrefsForm extends ReturnUrlForm
    {
        private boolean adminMode;

        public boolean isAdminMode()
        {
            return adminMode;
        }

        public void setAdminMode(boolean adminMode)
        {
            this.adminMode = adminMode;
        }
    }


    public static ActionURL getDefineWebThemesURL(boolean upgradeInProgress)
    {
        ActionURL url = new ActionURL(DefineWebThemesAction.class, ContainerManager.getRoot());

        if (upgradeInProgress)
            url.addParameter("upgradeInProgress", "1");

        return url;
    }


    @RequiresPermission(AdminPermission.class)
    public class DefineWebThemesAction extends SimpleViewAction<WebThemeForm>
    {
        public ModelAndView getView(WebThemeForm form, BindException errors) throws Exception
        {
            if (form.isUpgradeInProgress())
            {
                getPageConfig().setTemplate(Template.Dialog);
                getPageConfig().setTitle("Web Themes");
            }

            return new DefineWebThemesView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            // TODO: Look & Feel Settings page should be on nav trail

            return appendAdminNavTrail(root, "Web Themes", this.getClass());
        }
    }


    private abstract class AbstractWebThemeAction extends SimpleRedirectAction<WebThemeForm>
    {
        protected abstract void handleTheme(WebThemeForm form, ActionURL redirectURL) throws Exception;

        public ActionURL getRedirectURL(WebThemeForm form) throws Exception
        {
            ActionURL redirectURL = new AdminUrlsImpl().getProjectSettingsURL(getContainer());
            handleTheme(form, redirectURL);
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            return redirectURL;
        }

        @Override
        protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
        {
            try
            {
                throw e;
            }
            catch (IllegalArgumentException iae)
            {
                errors.reject(ERROR_MSG, "Error: " + iae.getMessage());
                getPageConfig().setTemplate(Template.Dialog);
                return new SimpleErrorView(errors);
            }
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class SaveWebThemeAction extends AbstractWebThemeAction
    {
        protected void handleTheme(WebThemeForm form, ActionURL successURL)
        {
            String themeName = form.getThemeName();

            //new theme
            if (null == themeName || 0 == themeName.length())
            {
                themeName = form.getFriendlyName();
                if (themeName == null)
                    throw new IllegalArgumentException("Please provide a name for the new theme");
            }

            //add new theme or update existing theme
            WebThemeManager.updateWebTheme(
                themeName
                , form.getTextColor(), form.getLinkColor()
                , form.getGridColor(), form.getPrimaryBackgroundColor()
                , form.getSecondaryBackgroundColor(), form.getBorderTitleColor(), form.getWebpartColor());

            //parameter to use to set customize page drop-down to user's last choice on define themes page
            successURL.addParameter("themeName", themeName);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteWebThemeAction extends AbstractWebThemeAction
    {
        protected void handleTheme(WebThemeForm form, ActionURL redirectURL)
        {
            WebThemeManager.deleteWebTheme(form.getThemeName());
        }
    }


    private static class DefineWebThemesView extends JspView<WebThemesBean>
    {
        public DefineWebThemesView(WebThemeForm form, BindException errors)
        {
            super("/org/labkey/core/admin/webTheme.jsp", new WebThemesBean(form), errors);
        }
    }


    public static class WebThemesBean
    {
        public Collection<WebTheme> themes;
        public WebTheme selectedTheme;
        public WebThemeForm form;

        public WebThemesBean(WebThemeForm form)
        {
            themes = WebThemeManager.getWebThemes();
            String themeName = form.getThemeName();
            selectedTheme = WebThemeManager.getTheme(themeName);
            this.form = form;
        }
    }


    public static class WebThemeForm implements HasValidator
    {
        String _themeName;
        String _friendlyName;
        String _linkColor;
        String _textColor;
        String _gridColor;
        String _primaryBackgroundColor;
        String _secondaryBackgroundColor;
        String _borderTitleColor;
        String _webpartColor;

        private boolean upgradeInProgress;

        public boolean isUpgradeInProgress()
        {
            return upgradeInProgress;
        }

        public void setUpgradeInProgress(boolean upgradeInProgress)
        {
            this.upgradeInProgress = upgradeInProgress;
        }

        public String getGridColor()
        {
            return _gridColor;
        }

        public void setGridColor(String gridColor)
        {
            _gridColor = gridColor;
        }

        public String getFriendlyName()
        {
            return _friendlyName;
        }

        public void setFriendlyName(String friendlyName)
        {
            _friendlyName = friendlyName;
        }

        public String getPrimaryBackgroundColor()
        {
            return _primaryBackgroundColor;
        }

        public void setPrimaryBackgroundColor(String primaryBackgroundColor)
        {
            _primaryBackgroundColor = primaryBackgroundColor;
        }

        public String getBorderTitleColor()
        {
            return _borderTitleColor;
        }

        public void setBorderTitleColor(String borderTitleColor)
        {
            _borderTitleColor = borderTitleColor;
        }

        public String getSecondaryBackgroundColor()
        {
            return _secondaryBackgroundColor;
        }

        public void setSecondaryBackgroundColor(String secondaryBackgroundColor)
        {
            _secondaryBackgroundColor = secondaryBackgroundColor;
        }

        public String getTextColor()
        {
            return _textColor;
        }

        public void setTextColor(String textColor)
        {
            _textColor = textColor;
        }

        public String getLinkColor()
        {
            return _linkColor;
        }

        public void setLinkColor(String linkColor)
        {
            _linkColor = linkColor;
        }

        public String getWebpartColor()
        {
            return _webpartColor;
        }

        public void setWebpartColor(String webpartColor)
        {
            _webpartColor = webpartColor;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

        private boolean isValidColor(String s)
        {
            if (s.length() != 6) return false;
            int r = -1;
            int g = -1;
            int b = -1;
            try
            {
                r = Integer.parseInt(s.substring(0, 2), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            try
            {
                g = Integer.parseInt(s.substring(2, 4), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            try
            {
                b = Integer.parseInt(s.substring(4, 6), 16);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
            if (r<0 || r>255) return false;
            if (g<0 || g>255) return false;
            return !(b < 0 || b > 255);
        }

        public void validate(Errors errors)
        {
            //check for nulls on submit
            if ((null == _friendlyName || "".equals(_friendlyName)) &&
                (null == _themeName || "".equals(_themeName)))
            {
                errors.reject(ERROR_MSG, "Please choose a theme name.");
            }

            if (_linkColor == null || _textColor == null || _gridColor == null ||
                    _primaryBackgroundColor == null || _secondaryBackgroundColor == null ||
                    _borderTitleColor == null || _webpartColor == null ||
                    !isValidColor(_linkColor) || !isValidColor(_textColor) || !isValidColor(_gridColor) ||
                    !isValidColor(_primaryBackgroundColor) || !isValidColor(_secondaryBackgroundColor) ||
                    !isValidColor(_borderTitleColor) || !isValidColor(_webpartColor))
            {
                errors.reject(ERROR_MSG, "You must provide a valid 6-character hexadecimal value for each field.");
            }
        }
    }


    public static ActionURL getModuleStatusURL(URLHelper returnURL)
    {
        ActionURL url = new ActionURL(ModuleStatusAction.class, ContainerManager.getRoot());
        if (returnURL != null)
            url.addReturnURL(returnURL);
        return url;
    }

    public static class ModuleStatusBean
    {
        public String verb;
        public String verbing;
        public ActionURL nextURL;
    }

    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public class ModuleStatusAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors) throws Exception
        {
            ModuleLoader loader = ModuleLoader.getInstance();

            VBox vbox = new VBox();

            ModuleStatusBean bean = new ModuleStatusBean();

            if (loader.isNewInstall())
                bean.nextURL = new ActionURL(NewInstallSiteSettingsAction.class, ContainerManager.getRoot());
            else if (form.getReturnURL() != null)
            {
                try
                {
                    bean.nextURL = form.getReturnActionURL();
                }
                catch (URLException x)
                {
                    // might not be an ActionURL e.g. /labkey/_webdav/home
                }
            }
            if (null == bean.nextURL)
                bean.nextURL = new ActionURL(InstallCompleteAction.class, ContainerManager.getRoot());

            if (loader.isNewInstall())
                bean.verb = "Install";
            else if (loader.isUpgradeRequired() || loader.isUpgradeInProgress())
                bean.verb = "Upgrade";
            else
                bean.verb = "Start";

            if (loader.isNewInstall())
                bean.verbing = "Installing";
            else if (loader.isUpgradeRequired() || loader.isUpgradeInProgress())
                bean.verbing = "Upgrading";
            else
                bean.verbing = "Starting";

            JspView<ModuleStatusBean> statusView = new JspView<>("/org/labkey/core/admin/moduleStatus.jsp", bean, errors);
            vbox.addView(statusView);

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());

            getPageConfig().setTemplate(Template.Wizard);
            getPageConfig().setTitle(bean.verb + " Modules");
            getPageConfig().setHelpTopic(new HelpTopic(ModuleLoader.getInstance().isNewInstall() ? "config" : "upgrade"));

            return vbox;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class NewInstallSiteSettingsForm extends FileSettingsForm
    {
        private String _notificationEmail;
        private String _siteName;
        private boolean _allowReporting;

        public String getNotificationEmail()
        {
            return _notificationEmail;
        }

        public void setNotificationEmail(String notificationEmail)
        {
            _notificationEmail = notificationEmail;
        }

        public String getSiteName()
        {
            return _siteName;
        }

        public void setSiteName(String siteName)
        {
            _siteName = siteName;
        }

        public boolean isAllowReporting()
        {
            return _allowReporting;
        }

        public void setAllowReporting(boolean allowReporting)
        {
            _allowReporting = allowReporting;
        }
    }

    @RequiresSiteAdmin
    public class NewInstallSiteSettingsAction extends AbstractFileSiteSettingsAction<NewInstallSiteSettingsForm>
    {
        public NewInstallSiteSettingsAction()
        {
            super(NewInstallSiteSettingsForm.class);
        }

        @Override
        public void validateCommand(NewInstallSiteSettingsForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            if (StringUtils.isBlank(form.getNotificationEmail()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Notification email address may not be blank.");
            }
            try
            {
                ValidEmail email = new ValidEmail(form.getNotificationEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public boolean handlePost(NewInstallSiteSettingsForm form, BindException errors) throws Exception
        {
            boolean success = super.handlePost(form, errors);
            if (success)
            {
                WriteableAppProps appProps = AppProps.getWriteableInstance();
                if (form.isAllowReporting() && appProps.getExceptionReportingLevel() == ExceptionReportingLevel.NONE)
                {
                    appProps.setExceptionReportingLevel(ExceptionReportingLevel.MEDIUM);
                    appProps.setUsageReportingLevel(UsageReportingLevel.MEDIUM);
                }
                else if (!form.isAllowReporting())
                {
                    appProps.setExceptionReportingLevel(ExceptionReportingLevel.NONE);
                    appProps.setUsageReportingLevel(UsageReportingLevel.NONE);
                }

                WriteableLookAndFeelProperties lafProps = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
                try
                {
                    lafProps.setSystemEmailAddress(new ValidEmail(form.getNotificationEmail()));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    return false;
                }
                lafProps.setSystemShortName(form.getSiteName());

                appProps.save(getUser());
                lafProps.save();

                // If the admin has not opted out of usage reporting, send an immediate report
                // now that they've set up their account and defaults, and then every 24 hours after.
                ModuleLoader.getInstance().setDeferUsageReport(false);
                AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();

                return true;
            }
            return false;
        }

        @Override
        public ModelAndView getView(NewInstallSiteSettingsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
            {
                File root = _svc.getSiteDefaultRoot();

                if (root.exists())
                    form.setRootPath(FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath());

                form.setAllowReporting(!AppProps.getInstance().isDevMode());
                LookAndFeelProperties props = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
                form.setSiteName(props.getShortName());
                form.setNotificationEmail(props.getSystemEmailAddress());
            }

            JspView<NewInstallSiteSettingsForm> view = new JspView<>("/org/labkey/core/admin/newInstallSiteSettings.jsp", form, errors);

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Set Defaults");
            getPageConfig().setTemplate(Template.Wizard);

            return view;
        }

        @Override
        public URLHelper getSuccessURL(NewInstallSiteSettingsForm form)
        {
            return new ActionURL(InstallCompleteAction.class, ContainerManager.getRoot());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresSiteAdmin
    public class InstallCompleteAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView view = new JspView("/org/labkey/core/admin/installComplete.jsp");

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Complete");
            getPageConfig().setTemplate(Template.Wizard);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static List<NavTree> getInstallUpgradeWizardSteps()
    {
        List<NavTree> navTrail = new ArrayList<>();
        if (ModuleLoader.getInstance().isNewInstall())
        {
            navTrail.add(new NavTree("Account Setup"));
            navTrail.add(new NavTree("Install Modules"));
            navTrail.add(new NavTree("Set Defaults"));
        }
        else if (ModuleLoader.getInstance().isUpgradeRequired() || ModuleLoader.getInstance().isUpgradeInProgress())
        {
            navTrail.add(new NavTree("Upgrade Modules"));
        }
        else
        {
            navTrail.add(new NavTree("Start Modules"));
        }
        navTrail.add(new NavTree("Complete"));
        return navTrail;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DbCheckerAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/checkDatabase.jsp", new DataCheckForm());
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Database Check Tools", this.getClass());
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class DoCheckAction extends SimpleViewAction<DataCheckForm>
    {
        public ModelAndView getView(DataCheckForm form, BindException errors) throws Exception
        {
            ActionURL currentUrl = getViewContext().cloneActionURL();
            String fixRequested = currentUrl.getParameter("_fix");
            StringBuilder contentBuilder = new StringBuilder();

            if (null != fixRequested)
            {
                String sqlcheck=null;
                if (fixRequested.equalsIgnoreCase("container"))
                       sqlcheck = DbSchema.checkAllContainerCols(getUser(), true);
                if (fixRequested.equalsIgnoreCase("descriptor"))
                       sqlcheck = OntologyManager.doProjectColumnCheck(true);
                contentBuilder.append(sqlcheck);
            }
            else
            {
                contentBuilder.append("\n<br/><br/>Checking Container Column References...");
                String strTemp = DbSchema.checkAllContainerCols(getUser(), false);
                if (strTemp.length() > 0)
                {
                    contentBuilder.append(strTemp);
                    currentUrl = getViewContext().cloneActionURL();
                    currentUrl.addParameter("_fix", "container");
                    contentBuilder.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\"");
                    contentBuilder.append(currentUrl.getEncodedLocalURIString());
                    contentBuilder.append("\" >here</a> to attempt recovery .");
                }

                contentBuilder.append("\n<br/><br/>Checking PropertyDescriptor and DomainDescriptor consistency...");
                strTemp = OntologyManager.doProjectColumnCheck(false);
                if (strTemp.length() > 0)
                {
                    contentBuilder.append(strTemp);
                    currentUrl = getViewContext().cloneActionURL();
                    currentUrl.addParameter("_fix", "descriptor");
                    contentBuilder.append("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp; click <a href=\"");
                    contentBuilder.append(currentUrl);
                    contentBuilder.append("\" >here</a> to attempt recovery .");
                }

                contentBuilder.append("\n<br/><br/>Checking Schema consistency with tableXML...");
                Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

                for (DbSchema schema : schemas)
                {
                    String sOut = TableXmlUtils.compareXmlToMetaData(schema, false, false, true).getResultsString();
                    if (null!=sOut)
                    {
                        contentBuilder.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;ERROR: Inconsistency in Schema ");
                        contentBuilder.append(schema.getDisplayName());
                        contentBuilder.append("<br/>");
                        contentBuilder.append(sOut);
                    }
                }

                contentBuilder.append("\n<br/><br/>Checking Consistency of Provisioned Storage...\n");
                StorageProvisioner.ProvisioningReport pr = StorageProvisioner.getProvisioningReport();
                contentBuilder.append(String.format("%d domains use Storage Provisioner", pr.getProvisionedDomains().size()));
                for (StorageProvisioner.ProvisioningReport.DomainReport dr : pr.getProvisionedDomains())
                {
                    for (String error : dr.getErrors())
                    {
                        contentBuilder.append("<div class=\"warning\">").append(error).append("</div>");
                    }
                }
                for (String error : pr.getGlobalErrors())
                {
                    contentBuilder.append("<div class=\"warning\">").append(error).append("</div>");
                }

                contentBuilder.append("\n<br/><br/>Database Consistency checker complete");
            }

            return new HtmlView("<table class=\"DataRegion\"><tr><td>" + contentBuilder.toString() + "</td></tr></table>");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Database Tools", this.getClass());
        }
    }


    public static class DataCheckForm
    {
        private String _dbSchema = "";
        private boolean _full = false;

        public List<Module> modules = ModuleLoader.getInstance().getModules();
        public DataCheckForm(){}

        public List<Module> getModules() { return modules;  }
        public String getDbSchema() { return _dbSchema; }
        public void setDbSchema(String dbSchema){ _dbSchema = dbSchema; }
        public boolean getFull() { return _full; }
        public void setFull(boolean full) { _full = full; }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class GetSchemaXmlDocAction extends ExportAction<DataCheckForm>
    {
        public void export(DataCheckForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String fullyQualifiedSchemaName = form.getDbSchema();
            if (null == fullyQualifiedSchemaName || fullyQualifiedSchemaName.length() == 0)
            {
                throw new NotFoundException("Must specify dbSchema parameter");
            }

            boolean bFull = form.getFull();

            Pair<DbScope, String> scopeAndSchemaName = DbSchema.getDbScopeAndSchemaName(fullyQualifiedSchemaName);
            TablesDocument tdoc = TableXmlUtils.createXmlDocumentFromDatabaseMetaData(scopeAndSchemaName.first, scopeAndSchemaName.second, bFull);
            StringWriter sw = new StringWriter();

            XmlOptions xOpt = new XmlOptions();
            xOpt.setSavePrettyPrint();
            xOpt.setUseDefaultNamespace();

            tdoc.save(sw, xOpt);

            sw.flush();
            PageFlowUtil.streamFileBytes(response, fullyQualifiedSchemaName + ".xml", sw.toString().getBytes(StringUtilsLabKey.DEFAULT_CHARSET), true);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class FolderInformationAction extends FolderManagementViewAction
    {
        @Override
        protected HttpView getTabView()
        {
            return getContainerInfoView(getContainer(), getUser());
        }
    }


    public static class MissingValuesForm
    {
        private boolean _inheritMvIndicators;
        private String[] _mvIndicators;
        private String[] _mvLabels;

        public boolean isInheritMvIndicators()
        {
            return _inheritMvIndicators;
        }

        public void setInheritMvIndicators(boolean inheritMvIndicators)
        {
            _inheritMvIndicators = inheritMvIndicators;
        }

        public String[] getMvIndicators()
        {
            return _mvIndicators;
        }

        public void setMvIndicators(String[] mvIndicators)
        {
            _mvIndicators = mvIndicators;
        }

        public String[] getMvLabels()
        {
            return _mvLabels;
        }

        public void setMvLabels(String[] mvLabels)
        {
            _mvLabels = mvLabels;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class MissingValuesAction extends FolderManagementViewPostAction<MissingValuesForm>
    {
        @Override
        protected HttpView getTabView(MissingValuesForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/mvIndicators.jsp", form, errors);
        }

        @Override
        public void validateCommand(MissingValuesForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(MissingValuesForm form, BindException errors) throws Exception
        {
            if (form.isInheritMvIndicators())
            {
                MvUtil.inheritMvIndicators(getContainer());
                return true;
            }
            else
            {
                // Javascript should have enforced any constraints
                MvUtil.assignMvIndicators(getContainer(), form.getMvIndicators(), form.getMvLabels());
                return true;
            }
        }
    }


    public static class ExportFolderForm
    {
        private String[] _types;
        private int _location;
        private String _format = "new"; // As of 14.3, this is the only supported format. But leave in place for the future.
        private String _exportType;
        private boolean _includeSubfolders;
        private PHI _exportPhiLevel;    // Input: max level when viewing form
        private boolean _shiftDates;
        private boolean _alternateIds;
        private boolean _maskClinic;

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public int getLocation()
        {
            return _location;
        }

        public void setLocation(int location)
        {
            _location = location;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }

        public AbstractFolderContext.ExportType getExportType()
        {
            if ("study".equals(_exportType))
                return AbstractFolderContext.ExportType.STUDY;
            else
                return AbstractFolderContext.ExportType.ALL;
        }

        public void setExportType(String exportType)
        {
            _exportType = exportType;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public PHI getExportPhiLevel()
        {
            return null != _exportPhiLevel ? _exportPhiLevel : PHI.NotPHI;
        }

        public void setExportPhiLevel(PHI exportPhiLevel)
        {
            _exportPhiLevel = exportPhiLevel;
        }

        public boolean isShiftDates()
        {
            return _shiftDates;
        }

        public void setShiftDates(boolean shiftDates)
        {
            _shiftDates = shiftDates;
        }

        public boolean isAlternateIds()
        {
            return _alternateIds;
        }

        public void setAlternateIds(boolean alternateIds)
        {
            _alternateIds = alternateIds;
        }

        public boolean isMaskClinic()
        {
            return _maskClinic;
        }

        public void setMaskClinic(boolean maskClinic)
        {
            _maskClinic = maskClinic;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ExportFolderAction extends FolderManagementViewPostAction<ExportFolderForm>
    {
        private ActionURL _successURL = null;

        @Override
        public ModelAndView getView(ExportFolderForm exportFolderForm, boolean reshow, BindException errors) throws Exception
        {
            // In export-to-browser do nothing (leave the export page in place). We just exported to the response, so
            // rendering a view would throw.
            return reshow && !errors.hasErrors() ? null : super.getView(exportFolderForm, reshow, errors);
        }

        @Override
        protected HttpView getTabView(ExportFolderForm form, BindException errors)
        {
            form.setExportType(PageFlowUtil.filter(getViewContext().getActionURL().getParameter("exportType")));

            form.setExportPhiLevel(ComplianceService.get().getMaxAllowedPhi(getContainer(), getUser()));
            return new JspView<>("/org/labkey/core/admin/exportFolder.jsp", form, errors);
        }

        @Override
        public void validateCommand(ExportFolderForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ExportFolderForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            FolderWriterImpl writer = new FolderWriterImpl();
            FolderExportContext ctx = new FolderExportContext(getUser(), container, PageFlowUtil.set(form.getTypes()),
                    form.getFormat(), form.isIncludeSubfolders(), form.getExportPhiLevel(), form.isShiftDates(),
                    form.isAlternateIds(), form.isMaskClinic(), new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));

            switch(form.getLocation())
            {
                case 0:
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(container);
                    if (root == null || !root.isValid())
                    {
                        throw new NotFoundException("No valid pipeline root found");
                    }
                    File exportDir = root.resolvePath(PipelineService.EXPORT_DIR);
                    try
                    {
                        writer.write(container, ctx, new FileSystemFile(exportDir));
                    }
                    catch (Container.ContainerException e)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    }
                    _successURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(container);
                    break;
                }
                case 1:
                {
                    PipeRoot root = PipelineService.get().findPipelineRoot(container);
                    if (root == null || !root.isValid())
                    {
                        throw new NotFoundException("No valid pipeline root found");
                    }
                    File exportDir = root.resolvePath(PipelineService.EXPORT_DIR);
                    exportDir.mkdir();
                    try (ZipFile zip = new ZipFile(exportDir, FileUtil.makeFileNameWithTimestamp(container.getName(), "folder.zip")))
                    {
                        writer.write(container, ctx, zip);
                    }
                    catch (Container.ContainerException e)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    }
                    _successURL = PageFlowUtil.urlProvider(PipelineUrls.class).urlBrowse(container);
                    break;
                }
                case 2:
                {
                    try
                    {
                        ContainerManager.checkContainerValidity(container);
                        try (ZipFile zip = new ZipFile(getViewContext().getResponse(), FileUtil.makeFileNameWithTimestamp(container.getName(), "folder.zip")))
                        {
                            writer.write(container, ctx, zip);
                        }
                    }
                    catch (Container.ContainerException e)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    }
                    break;
                }
            }

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(ExportFolderForm exportFolderForm)
        {
            return _successURL;
        }
    }


    public static class ImportFolderForm
    {
        private boolean _createSharedDatasets;
        private boolean _validateQueries;
        private boolean _advancedImportOptions;
        private String _sourceTemplateFolder;
        private String _sourceTemplateFolderId;
        private String _origin;

        public boolean isCreateSharedDatasets()
        {
            return _createSharedDatasets;
        }

        public void setCreateSharedDatasets(boolean createSharedDatasets)
        {
            _createSharedDatasets = createSharedDatasets;
        }

        public boolean isValidateQueries()
        {
            return _validateQueries;
        }

        public void setValidateQueries(boolean validateQueries)
        {
            _validateQueries = validateQueries;
        }

        public boolean isAdvancedImportOptions()
        {
            return _advancedImportOptions;
        }

        public void setAdvancedImportOptions(boolean advancedImportOptions)
        {
            _advancedImportOptions = advancedImportOptions;
        }

        public String getSourceTemplateFolder()
        {
            return _sourceTemplateFolder;
        }

        public void setSourceTemplateFolder(String sourceTemplateFolder)
        {
            _sourceTemplateFolder = sourceTemplateFolder;
        }

        public String getSourceTemplateFolderId()
        {
            return _sourceTemplateFolderId;
        }

        public void setSourceTemplateFolderId(String sourceTemplateFolderId)
        {
            _sourceTemplateFolderId = sourceTemplateFolderId;
        }

        public String getOrigin()
        {
            return _origin;
        }

        public void setOrigin(String origin)
        {
            _origin = origin;
        }

        public Container getSourceTemplateFolderContainer()
        {
            if (null == getSourceTemplateFolderId())
                return null;
            return ContainerManager.getForId(getSourceTemplateFolderId().replace(',', ' ').trim());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ImportFolderAction extends FolderManagementViewPostAction<ImportFolderForm>
    {
        private ActionURL _successURL;

        @Override
        protected HttpView getTabView(ImportFolderForm form, BindException errors)
        {
            // default the createSharedDatasets and validateQueries to true if this is not a form error reshow
            if (!errors.hasErrors())
            {
                form.setCreateSharedDatasets(true);
                form.setValidateQueries(true);
            }

            return new JspView<>("/org/labkey/core/admin/importFolder.jsp", form, errors);
        }

        @Override
        public void validateCommand(ImportFolderForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ImportFolderForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            ActionURL url = context.getActionURL();
            User user = getUser();
            Container container = getContainer();
            PipeRoot pipelineRoot;
            boolean isStudy = false;
            File archiveXml;
            File pipelineUnzipDir;
            File pipelineUnzipFile;
            PipelineUrls pipelineUrlProvider;
            String originalFileName;
            File archiveFile;
            boolean fromTemplateSourceFolder;

            if (form.getOrigin() == null)
            {
                form.setOrigin("Folder");
            }

            // don't allow import into the root container
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            // make sure we have a pipeline url provider to use for the success URL redirect
            pipelineUrlProvider = PageFlowUtil.urlProvider(PipelineUrls.class);
            if (pipelineUrlProvider == null)
            {
                errors.reject("folderImport", "Pipeline url provider does not exist.");
                return false;
            }

            // make sure that the pipeline root is valid for this container
            pipelineRoot = PipelineService.get().findPipelineRoot(container);
            if (!PipelineService.get().hasValidPipelineRoot(container) || pipelineRoot == null)
            {
                errors.reject("folderImport", "Pipeline root not set or does not exist on disk.");
                return false;
            }

            // make sure we are able to delete any existing unzip dir in the pipeline root
            try
            {
                pipelineUnzipDir = pipelineRoot.getImportDirectoryPathAndEnsureDeleted();
            }
            catch (DirectoryNotDeletedException e)
            {
                errors.reject("studyImport", "Import failed: Could not delete the directory \"" + PipelineService.UNZIP_DIR + "\"");
                return false;
            }

            if (!StringUtils.isEmpty(form.getSourceTemplateFolder()))
            {
                // user choose to import from a template source folder

                Container sourceContainer = form.getSourceTemplateFolderContainer();

                // In order to support the Advanced import options to import into multiple target folders we need to zip
                // the source template folder so that the zip file can be passed to the pipeline processes.
                FolderExportContext ctx = new FolderExportContext(getUser(), sourceContainer,
                        getRegisteredFolderWritersForImplicitExport(sourceContainer), "new", false,
                        PHI.NotPHI, false, false, false, new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));
                FolderWriterImpl writer = new FolderWriterImpl();
                String zipFileName = FileUtil.makeFileNameWithTimestamp(sourceContainer.getName(), "folder.zip");
                try (ZipFile zip = new ZipFile(pipelineUnzipDir, zipFileName))
                {
                    writer.write(sourceContainer, ctx, zip);
                }
                catch (Container.ContainerException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                }
                File implicitZipFile = new File(pipelineUnzipDir, zipFileName);

                // To support the simple import option unzip the zip file to the pipeline unzip dir of the current container
                ZipUtil.unzipToDirectory(implicitZipFile, pipelineUnzipDir);

                fromTemplateSourceFolder = StringUtils.isNotEmpty(form.getSourceTemplateFolderId());
                originalFileName = implicitZipFile.getName();
                archiveFile = implicitZipFile;
            }
            else
            {
                // user chose to import from a zip file

                Map<String, MultipartFile> map = getFileMap();

                // make sure we have a single file selected for import
                if (map.isEmpty() || map.size() > 1)
                {
                    errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                    return false;
                }

                // make sure the file is not empty and that it has a .zip extension
                MultipartFile zipFile = map.values().iterator().next();
                if (0 == zipFile.getSize() || StringUtils.isBlank(zipFile.getOriginalFilename()) || !zipFile.getOriginalFilename().toLowerCase().endsWith(".zip"))
                {
                    errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                    return false;
                }

                // copy and unzip the uploaded import archive zip file to the pipeline unzip dir
                try
                {
                    pipelineUnzipFile = new File(pipelineUnzipDir, zipFile.getOriginalFilename());
                    pipelineUnzipFile.getParentFile().mkdirs();
                    pipelineUnzipFile.createNewFile();
                    FileUtil.copyData(zipFile.getInputStream(), pipelineUnzipFile);
                    ZipUtil.unzipToDirectory(pipelineUnzipFile, pipelineUnzipDir);
                }
                catch (FileNotFoundException e)
                {
                    errors.reject("folderImport", "File not found.");
                    return false;
                }
                catch (IOException e)
                {
                    errors.reject("folderImport", "This file does not appear to be a valid zip archive file.");
                    return false;
                }

                fromTemplateSourceFolder = false;
                originalFileName = zipFile.getOriginalFilename();
                archiveFile = pipelineUnzipFile;
            }

            // get the main xml file from the unzipped import archive
            archiveXml = new File(pipelineUnzipDir, "folder.xml");
            if (!archiveXml.exists())
            {
                archiveXml = new File(pipelineUnzipDir, "study.xml");
                isStudy = true;
            }
            if (!archiveXml.exists())
            {
                errors.reject("folderImport", "This archive doesn't contain a folder.xml or study.xml file.");
                return false;
            }

            ImportOptions options = new ImportOptions(getContainer().getId(), user.getUserId());
            options.setSkipQueryValidation(!form.isValidateQueries());
            options.setCreateSharedDatasets(form.isCreateSharedDatasets());
            options.setAdvancedImportOptions(form.isAdvancedImportOptions());
            options.setActivity(ComplianceService.get().getCurrentActivity(getViewContext()));

            // if the option is selected to show the advanced import options, redirect to there
            if (form.isAdvancedImportOptions())
            {
                // archiveFile is the zip of the source template folder located in the current container's unzip dir
                _successURL = pipelineUrlProvider.urlStartFolderImport(getContainer(), archiveFile, isStudy, options, fromTemplateSourceFolder);
                return true;
            }

            // finally, create the study or folder import pipeline job
            _successURL = pipelineUrlProvider.urlBegin(container);
            if (isStudy)
                StudyService.get().runStudyImportJob(container, user, url, archiveXml, originalFileName, errors, pipelineRoot, options);
            else
                PipelineService.get().runFolderImportJob(container, user, url, archiveXml, originalFileName, errors, pipelineRoot, options);

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(ImportFolderForm importFolderForm)
        {
            return _successURL;
        }
    }


    private Set<String> getRegisteredFolderWritersForImplicitExport(Container sourceContainer)
    {
        // this method is very similar to CoreController.GetRegisteredFolderWritersAction.execute() method, but instead of
        // of building up a map of Writer object names to display in the UI, we are instead adding them to the list of Writers
        // to apply during the implicit export.
        Set<String> registeredFolderWriters = new HashSet<>();
        FolderSerializationRegistry registry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null == registry)
        {
            throw new RuntimeException();
        }
        Collection<FolderWriter> registeredWriters = registry.getRegisteredFolderWriters();
        for (FolderWriter writer : registeredWriters)
        {
            String dataType = writer.getDataType();
            boolean excludeForDataspace = sourceContainer.isDataspace() && "Study".equals(dataType);
            boolean excludeForTemplate = !writer.includeWithTemplate();

            if (dataType != null && writer.show(sourceContainer) && !excludeForDataspace && !excludeForTemplate)
            {
                registeredFolderWriters.add(dataType);

                // for each Writer also determine if their are related children Writers, if so include them also
                Collection<org.labkey.api.writer.Writer> childWriters = writer.getChildren(true, true);
                if (childWriters != null && childWriters.size() > 0)
                {
                    for (org.labkey.api.writer.Writer child : childWriters)
                    {
                        dataType = child.getDataType();
                        if (dataType != null)
                            registeredFolderWriters.add(dataType);
                    }
                }
            }
        }
        return registeredFolderWriters;
    }


    public static class FolderSettingsForm implements SettingsForm
    {
        private String _defaultDateFormat;
        private String _defaultDateTimeFormat;
        private String _defaultNumberFormat;
        private boolean _restrictedColumnsEnabled;

        public String getDefaultDateFormat()
        {
            return _defaultDateFormat;
        }

        public void setDefaultDateFormat(String defaultDateFormat)
        {
            _defaultDateFormat = defaultDateFormat;
        }

        public String getDefaultDateTimeFormat()
        {
            return _defaultDateTimeFormat;
        }

        public void setDefaultDateTimeFormat(String defaultDateTimeFormat)
        {
            _defaultDateTimeFormat = defaultDateTimeFormat;
        }

        public String getDefaultNumberFormat()
        {
            return _defaultNumberFormat;
        }

        public void setDefaultNumberFormat(String defaultNumberFormat)
        {
            _defaultNumberFormat = defaultNumberFormat;
        }

        public boolean areRestrictedColumnsEnabled()
        {
            return _restrictedColumnsEnabled;
        }

        public void setRestrictedColumnsEnabled(boolean restrictedColumnsEnabled)
        {
            _restrictedColumnsEnabled = restrictedColumnsEnabled;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class FolderSettingsAction extends FolderManagementViewPostAction<FolderSettingsForm>
    {
        @Override
        protected HttpView getTabView(FolderSettingsForm form, BindException errors)
        {
            return new LookAndFeelView(getContainer(), null, errors);
        }

        @Override
        public void validateCommand(FolderSettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(FolderSettingsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            WriteableFolderLookAndFeelProperties props = LookAndFeelProperties.getWriteableFolderInstance(c);

            return ProjectSettingsAction.saveFolderSettings(c, form, props, getUser(), errors);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ModulePropertiesAction extends FolderManagementViewAction
    {
        @Override
        protected HttpView getTabView()
        {
            return new JspView<>("/org/labkey/core/project/modulePropertiesAdmin.jsp");
        }
    }


    public static class FolderTypeForm
    {
        private String[] _activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        private String _defaultModule;
        private String _folderType;
        private boolean _wizard;

        public String[] getActiveModules()
        {
            return _activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            _activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return _defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            _defaultModule = defaultModule;
        }

        public String getFolderType()
        {
            return _folderType;
        }

        public void setFolderType(String folderType)
        {
            _folderType = folderType;
        }

        public boolean isWizard()
        {
            return _wizard;
        }

        public void setWizard(boolean wizard)
        {
            _wizard = wizard;
        }
    }


    @RequiresPermission(AdminPermission.class)
    @IgnoresTermsOfUse  // At the moment, compliance configuration is very sensitive to active modules, so allow those adjustments
    public class FolderTypeAction extends FolderManagementViewPostAction<FolderTypeForm>
    {
        private ActionURL _successURL = null;

        @Override
        protected HttpView getTabView(FolderTypeForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/folderType.jsp", form, errors);
        }

        @Override
        public void validateCommand(FolderTypeForm form, Errors errors)
        {
            boolean fEmpty = true;
            for (String module : form._activeModules)
            {
                if (module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if (fEmpty && "None".equals(form.getFolderType()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Error: Please select at least one module to display.");
            }
        }

        @Override
        public boolean handlePost(FolderTypeForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            String[] modules = form.getActiveModules();

            if (modules.length == 0)
            {
                errors.reject(null, "At least one module must be selected");
                return false;
            }

            Set<Module> activeModules = new HashSet<>();
            for (String moduleName : modules)
            {
                Module module = ModuleLoader.getInstance().getModule(moduleName);
                if (module != null)
                    activeModules.add(module);
            }

            if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
            {
                container.setFolderType(FolderType.NONE, activeModules, getUser(), errors);
                Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
                container.setDefaultModule(defaultModule);
            }
            else
            {
                FolderType folderType = FolderTypeManager.get().getFolderType(form.getFolderType());
                if (container.isContainerTab() && folderType.hasContainerTabs())
                    errors.reject(null, "You cannot set a tab folder to a folder type that also has tab folders");
                else
                    container.setFolderType(folderType, activeModules, getUser(), errors);
            }
            if (errors.hasErrors())
                return false;

            if (form.isWizard())
            {
                _successURL = PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(container);
                _successURL.addParameter("wizard", Boolean.TRUE.toString());
            }
            else
                _successURL = container.getFolderType().getStartURL(container, getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(FolderTypeForm folderTypeForm)
        {
            return _successURL;
        }
    }


    public static class FileRootsForm extends SetupForm implements FileManagementForm
    {
        private String _folderRootPath;
        private String _fileRootOption;
        private String _cloudRootName;
        private boolean _isFolderSetup;
        private boolean _fileRootChanged;
        private boolean _enabledCloudStoresChanged;

        // cloud settings
        private String[] _enabledCloudStore;
        //file management
        public String getFolderRootPath()
        {
            return _folderRootPath;
        }

        public void setFolderRootPath(String folderRootPath)
        {
            _folderRootPath = folderRootPath;
        }

        public String getFileRootOption()
        {
            return _fileRootOption;
        }

        public void setFileRootOption(String fileRootOption)
        {
            _fileRootOption = fileRootOption;
        }

        @Override
        public String[] getEnabledCloudStore()
        {
            return _enabledCloudStore;
        }

        @Override
        public void setEnabledCloudStore(String[] enabledCloudStore)
        {
            _enabledCloudStore = enabledCloudStore;
        }

        public boolean isDisableFileSharing()
        {
            return ProjectSettingsForm.FileRootProp.disable.name().equals(getFileRootOption());
        }

        public boolean hasSiteDefaultRoot()
        {
            return ProjectSettingsForm.FileRootProp.siteDefault.name().equals(getFileRootOption());
        }

        public boolean isCloudFileRoot()
        {
            return ProjectSettingsForm.FileRootProp.cloudRoot.name().equals(getFileRootOption());
        }

        @Nullable
        public String getCloudRootName()
        {
            return _cloudRootName;
        }

        public void setCloudRootName(String cloudRootName)
        {
            _cloudRootName = cloudRootName;
        }

        public boolean isFolderSetup()
        {
            return _isFolderSetup;
        }

        public void setFolderSetup(boolean folderSetup)
        {
            _isFolderSetup = folderSetup;
        }

        public boolean isFileRootChanged()
        {
            return _fileRootChanged;
        }

        public void setFileRootChanged(boolean changed)
        {
            _fileRootChanged = changed;
        }

        public boolean isEnabledCloudStoresChanged()
        {
            return _enabledCloudStoresChanged;
        }

        public void setEnabledCloudStoresChanged(boolean enabledCloudStoresChanged)
        {
            _enabledCloudStoresChanged = enabledCloudStoresChanged;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FileRootsStandAloneAction extends FormViewAction<FileRootsForm>
    {
        @Override
        public ModelAndView getView(FileRootsForm form, boolean reShow, BindException errors)
        {
            JspView view = getFileRootsView(form, errors);
            view.setFrame(WebPartView.FrameType.NONE);

            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(getContainer(), getContainer().getParent()));
            getPageConfig().setTemplate(PageConfig.Template.Wizard);
            getPageConfig().setTitle("Change File Root");
            return view;
        }

        @Override
        public void validateCommand(FileRootsForm form, Errors errors)
        {
            validateCloudFileRoot(form, getContainer(), errors);
        }

        @Override
        public boolean handlePost(FileRootsForm form, BindException errors) throws Exception
        {
            return handleFileRootsPost(form, errors);
        }

        public ActionURL getSuccessURL(FileRootsForm form)
        {
            ActionURL url = new ActionURL(FileRootsStandAloneAction.class, getContainer())
                    .addParameter("folderSetup", true)
                    .addReturnURL(getViewContext().getActionURL().getReturnURL());

            if (form.isFileRootChanged())
                url.addParameter("rootSet", true);
            if (form.isEnabledCloudStoresChanged())
                url.addParameter("cloudChanged", true);
            return url;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FileRootsAction extends FolderManagementViewPostAction<FileRootsForm>
    {
        @Override
        protected HttpView getTabView(FileRootsForm form, BindException errors)
        {
            return getFileRootsView(form, errors);
        }

        @Override
        public void validateCommand(FileRootsForm form, Errors errors)
        {
            validateCloudFileRoot(form, getContainer(), errors);
        }

        @Override
        public boolean handlePost(FileRootsForm form, BindException errors) throws Exception
        {
            return handleFileRootsPost(form, errors);
        }

        public ActionURL getSuccessURL(FileRootsForm form)
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getFileRootsURL(getContainer());

            if (form.isFileRootChanged())
                url.addParameter("rootSet", true);
            if (form.isEnabledCloudStoresChanged())
                url.addParameter("cloudChanged", true);
            return url;
        }
    }

    private JspView getFileRootsView(FileRootsForm form, BindException errors)
    {
        JspView view = new JspView<>("/org/labkey/core/admin/view/filesProjectSettings.jsp", form, errors);
        String title = "Configure File Root";
        if (CloudStoreService.get() != null)
            title += " And Enable Cloud Stores";
        view.setTitle(title);

        try
        {
            setFormAndConfirmMessage(getViewContext(), form);
        }
        catch (IllegalArgumentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }

        return view;
    }

    private boolean handleFileRootsPost(FileRootsForm form, BindException errors) throws Exception
    {
        if (form.isPipelineRootForm())
        {
            return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
        }
        else
        {
            setFileRootFromForm(getViewContext(), form);
            setEnabledCloudStores(getViewContext(), form, errors);
            return true;
        }
    }

    public static void validateCloudFileRoot(FileManagementForm form, Container container, Errors errors)
    {
        FileContentService service = FileContentService.get();
        if (null != service)
        {
            boolean isOrDefaultsToCloudRoot = form.isCloudFileRoot();
            String cloudRootName = form.getCloudRootName();
            if (!isOrDefaultsToCloudRoot && form.hasSiteDefaultRoot())
            {
                Path defaultRootPath = service.getDefaultRootPath(container, false);
                cloudRootName = service.getDefaultRootInfo(container).getCloudName();
                isOrDefaultsToCloudRoot = (null != defaultRootPath && FileUtil.hasCloudScheme(defaultRootPath));
            }

            if (isOrDefaultsToCloudRoot && null != cloudRootName)
            {
                if (null != form.getEnabledCloudStore())
                {
                    for (String storeName : form.getEnabledCloudStore())
                    {
                        if (StringUtils.equalsIgnoreCase(cloudRootName, storeName))
                            return;
                    }
                }
                // Didn't find cloud root in enabled list
                errors.reject(ERROR_MSG, "Cannot disable cloud store used as File Root.");
            }
        }
    }

    public static void setFileRootFromForm(ViewContext ctx, FileManagementForm form)
    {
        boolean changed = false;
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        if (null != service)
        {
            if (form.isDisableFileSharing())
            {
                if (!service.isFileRootDisabled(ctx.getContainer()))
                {
                    service.disableFileRoot(ctx.getContainer());
                    changed = true;
                }
            }
            else if (form.hasSiteDefaultRoot())
            {
                if (!service.isUseDefaultRoot(ctx.getContainer()))
                {
                    service.setIsUseDefaultRoot(ctx.getContainer(), true);
                    changed = true;
                }
            }
            else if (form.isCloudFileRoot())
            {
                throwIfUnauthorizedFileRootChange(ctx, service, form);
                String cloudRootName = form.getCloudRootName();
                if (null != cloudRootName &&
                        (!service.isCloudRoot(ctx.getContainer()) ||
                                !cloudRootName.equalsIgnoreCase(service.getCloudRootName(ctx.getContainer()))))
                {
                    service.setIsUseDefaultRoot(ctx.getContainer(), false);
                    service.setCloudRoot(ctx.getContainer(), cloudRootName);
                    try
                    {
                        PipelineService.get().setPipelineRoot(ctx.getUser(), ctx.getContainer(), PipelineService.PRIMARY_ROOT, false);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
                    }
                    changed = true;
                }
            }
            else
            {
                throwIfUnauthorizedFileRootChange(ctx, service, form);
                String root = StringUtils.trimToNull(form.getFolderRootPath());
                if (root != null)
                {
                    Path currentFileRootPath = service.getFileRootPath(ctx.getContainer());
                    if (null == currentFileRootPath || !root.equalsIgnoreCase(currentFileRootPath.toAbsolutePath().toString()))
                    {
                        service.setIsUseDefaultRoot(ctx.getContainer(), false);
                        service.setFileRootPath(ctx.getContainer(), root);
                        changed = true;
                    }
                }
                else
                {
                    service.setFileRootPath(ctx.getContainer(), null);
                    changed = true;
                }
            }
        }
        form.setFileRootChanged(changed);
    }

    private static void throwIfUnauthorizedFileRootChange(ViewContext ctx, FileContentService service, FileManagementForm form)
    {
        // test permissions.  only site admins are able to turn on a custom file root for a folder
        // this is only relevant if the folder is either being switched to a custom file root,
        // or if the file root is changed.
        if (!service.isUseDefaultRoot(ctx.getContainer()))
        {
            Path fileRootPath = service.getFileRootPath(ctx.getContainer());
            if (null != fileRootPath && !FileUtil.getAbsolutePath(ctx.getContainer(), fileRootPath).equalsIgnoreCase(form.getFolderRootPath()))
            {
                if (!ctx.getUser().hasRootPermission(AdminOperationsPermission.class))
                    throw new UnauthorizedException("Only site admins change change file roots");
            }
        }
    }

    public static void setEnabledCloudStores(ViewContext ctx, FileManagementForm form, BindException errors)
    {
        String[] enabledCloudStores = form.getEnabledCloudStore();
        CloudStoreService cloud = CloudStoreService.get();
        if (cloud != null)
        {
            Set<String> enabled = Collections.emptySet();
            if (enabledCloudStores != null)
                enabled = new HashSet<>(Arrays.asList(enabledCloudStores));

            try
            {
                // Check if anything changed
                boolean changed = false;
                Collection<String> storeNames = cloud.getEnabledCloudStores(ctx.getContainer());
                if (enabled.size() != storeNames.size())
                    changed = true;
                else
                    if (!enabled.containsAll(storeNames))
                        changed = true;
                if (changed)
                    cloud.setEnabledCloudStores(ctx.getContainer(), enabled);
                form.setEnabledCloudStoresChanged(changed);
            }
            catch (UncheckedExecutionException e)
            {
                // UncheckedExecutionException with cause org.jclouds.blobstore.ContainerNotFoundException
                // is what BlobStore hands us if bucket (S3 container) does not exist
                if (null != e.getCause())
                    errors.reject(ERROR_MSG, e.getCause().getMessage());
                else
                    throw e;
            }
        }
    }


    public static void setFormAndConfirmMessage(ViewContext ctx, FileManagementForm form) throws IllegalArgumentException
    {
        FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
        String confirmMessage = null;
        String rootSetParam = ctx.getActionURL().getParameter("rootSet");
        boolean fileRootChanged = null != rootSetParam && "true".equalsIgnoreCase(rootSetParam);
        String cloudChangedParam = ctx.getActionURL().getParameter("cloudChanged");
        boolean enabledCloudChanged = null != cloudChangedParam && "true".equalsIgnoreCase(cloudChangedParam);

        if (service != null)
        {
            if (service.isFileRootDisabled(ctx.getContainer()))
            {
                form.setFileRootOption(ProjectSettingsForm.FileRootProp.disable.name());
                if (fileRootChanged)
                    confirmMessage = "File sharing has been disabled for this " + ctx.getContainer().getContainerNoun();
            }
            else if (service.isUseDefaultRoot(ctx.getContainer()))
            {
                form.setFileRootOption(ProjectSettingsForm.FileRootProp.siteDefault.name());
                Path root = service.getFileRootPath(ctx.getContainer());
                if (root != null && Files.exists(root) && fileRootChanged)
                    confirmMessage = "The file root is set to a default of: " + FileUtil.getAbsolutePath(ctx.getContainer(), root);
            }
            else if (!service.isCloudRoot(ctx.getContainer()))
            {
                Path root = service.getFileRootPath(ctx.getContainer());

                form.setFileRootOption(ProjectSettingsForm.FileRootProp.folderOverride.name());
                if (root != null)
                {
                    String absolutePath = FileUtil.getAbsolutePath(ctx.getContainer(), root);
                    form.setFolderRootPath(absolutePath);
                    if (Files.exists(root))
                    {
                        if (fileRootChanged)
                            confirmMessage = "The file root is set to: " + absolutePath;
                    }
                    else
                        throw new IllegalArgumentException("File root '" + root + "' does not appear to be a valid directory accessible to the server at " + ctx.getRequest().getServerName() + ".");
                }
            }
            else
            {
                form.setFileRootOption(ProjectSettingsForm.FileRootProp.cloudRoot.name());
                form.setCloudRootName(service.getCloudRootName(ctx.getContainer()));
                Path root = service.getFileRootPath(ctx.getContainer());
                if (root != null && fileRootChanged)
                {
                    confirmMessage = "The file root is set to: " + FileUtil.getCloudRootPathString(form.getCloudRootName());
                }
            }
        }

        if (fileRootChanged && confirmMessage != null)
            form.setConfirmMessage(confirmMessage);
        else if (enabledCloudChanged)
            form.setConfirmMessage("The enabled cloud stores changed.");
    }


    @RequiresPermission(AdminPermission.class)
    public class ManageFoldersAction extends FolderManagementViewAction
    {
        @Override
        protected HttpView getTabView()
        {
            return new JspView<>("/org/labkey/core/admin/manageFolders.jsp");
        }
    }


    public static class NotificationsForm
    {
        private String _provider;

        public String getProvider()
        {
            return _provider;
        }

        public void setProvider(String provider)
        {
            _provider = provider;
        }
    }


    private static final String DATA_REGION_NAME = "Users";

    @RequiresPermission(AdminPermission.class)
    public class NotificationsAction extends FolderManagementViewPostAction<NotificationsForm>
    {
        @Override
        protected HttpView getTabView(NotificationsForm form, BindException errors) throws Exception
        {
            final String key = DataRegionSelection.getSelectionKey("core", CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME, null, DATA_REGION_NAME);
            DataRegionSelection.clearAll(getViewContext(), key);

            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("DisplayName"));

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
            QueryView queryView = new QueryView(schema, settings, errors)
            {
                @Override
                public List<DisplayColumn> getDisplayColumns()
                {
                    List<DisplayColumn> columns = new ArrayList<>();
                    SecurityPolicy policy = getContainer().getPolicy();
                    Set<String> assignmentSet = new HashSet<>();

                    assignmentSet.add(SecurityManager.getGroup(Group.groupAdministrators).getName());
                    assignmentSet.add(SecurityManager.getGroup(Group.groupDevelopers).getName());

                    for (RoleAssignment assignment : policy.getAssignments())
                    {
                        Group g = SecurityManager.getGroup(assignment.getUserId());
                        if (g != null)
                            assignmentSet.add(g.getName());
                    }

                    for (DisplayColumn col : super.getDisplayColumns())
                    {
                        if (col.getName().equalsIgnoreCase("Groups"))
                            columns.add(new FolderGroupColumn(assignmentSet, col.getColumnInfo()));
                        else
                            columns.add(col);
                    }
                    return columns;
                }

                @Override
                protected void populateButtonBar(DataView dataView, ButtonBar bar)
                {
                    try
                    {
                        // add the provider configuration menu items to the admin panel button
                        MenuButton adminButton = new MenuButton("Update user settings");
                        adminButton.setRequiresSelection(true);
                        for (ConfigTypeProvider provider : MessageConfigService.get().getConfigTypes())
                            adminButton.addMenuItem("For " + provider.getName().toLowerCase(), null, "userSettings_"+provider.getName()+"(LABKEY.DataRegions.Users.getSelectionCount())" );

                        bar.add(adminButton);
                        super.populateButtonBar(dataView, bar);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(false);
            queryView.setShowRecordSelectors(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            VBox defaultsView = new VBox(
                    new HtmlView(
                            "<div class=\"labkey-announcement-title\"><span>Default settings</span></div><div class=\"labkey-title-area-line\"></div>" +
                                    "You can change this folder's default settings for email notifications here.")
            );

            PanelConfig config = new PanelConfig(getViewContext().getActionURL().clone(), key);
            for (ConfigTypeProvider provider : MessageConfigService.get().getConfigTypes())
            {
                defaultsView.addView(new JspView<>("/org/labkey/core/admin/view/notifySettings.jsp", provider.createConfigForm(getViewContext(), config)));
            }

            return new VBox(
                    new JspView<>("/org/labkey/core/admin/view/folderSettingsHeader.jsp", null, errors),
                    defaultsView,
                    new VBox(
                            new HtmlView(
                                    "<div class='labkey-announcement-title'><span>User settings</span></div><div class='labkey-title-area-line'></div>" +
                                            "The list below contains all users with READ access to this folder who are able to receive notifications<br/>" +
                                            "by email for message boards and file content events. A user's current message or file notification setting is<br/>" +
                                            "visible in the appropriately named column.<br/><br/>" +
                                            "To bulk edit individual settings: select one or more users, click the 'Update user settings' menu, and select the notification type."),
                            queryView
                    )
            );
        }

        @Override
        public void validateCommand(NotificationsForm form, Errors errors)
        {
            ConfigTypeProvider provider = MessageConfigService.get().getConfigType(form.getProvider());

            if (provider != null)
                provider.validateCommand(getViewContext(), errors);
        }

        @Override
        public boolean handlePost(NotificationsForm form, BindException errors) throws Exception
        {
            ConfigTypeProvider provider = MessageConfigService.get().getConfigType(form.getProvider());

            if (provider != null)
            {
                return provider.handlePost(getViewContext(), errors);
            }
            errors.reject(SpringActionController.ERROR_MSG, "Unable to find the selected config provider");
            return false;
        }
    }


    private static class FolderGroupColumn extends DataColumn
    {
        private final Set<String> _assignmentSet;

        public FolderGroupColumn(Set<String> assignmentSet, ColumnInfo col)
        {
            super(col);
            _assignmentSet = assignmentSet;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String value = (String)ctx.get(getBoundColumn().getDisplayField().getFieldKey());

            if (value != null)
            {
                StringBuilder sb = new StringBuilder();
                String delim = "";

                for (String name : value.split(","))
                {
                    if (_assignmentSet.contains(name))
                    {
                        sb.append(delim);
                        sb.append(name);
                        delim = ",<br>";
                    }
                }
                out.write(sb.toString());
            }
        }
    }


    private static class PanelConfig implements MessageConfigService.PanelInfo
    {
        private final ActionURL _returnUrl;
        private final String _dataRegionSelectionKey;

        public PanelConfig(ActionURL returnUrl, String selectionKey)
        {
            _returnUrl = returnUrl;
            _dataRegionSelectionKey = selectionKey;
        }

        @Override
        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }
    }


    public static class ConceptsForm
    {
        private String _conceptURI;
        private String _containerId;
        private String _schemaName;
        private String _queryName;

        public String getConceptURI()
        {
            return _conceptURI;
        }

        public void setConceptURI(String conceptURI)
        {
            _conceptURI = conceptURI;
        }

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ConceptsAction extends FolderManagementViewPostAction<ConceptsForm>
    {
        @Override
        protected HttpView getTabView(ConceptsForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/manageConcepts.jsp", form, errors);
        }

        @Override
        public void validateCommand(ConceptsForm form, Errors errors)
        {
            // validate that the required input fields are provided
            String missingRequired = "", sep = "";
            if (form.getConceptURI() == null)
            {
                missingRequired += "conceptURI";
                sep = ", ";
            }
            if (form.getSchemaName() == null)
            {
                missingRequired += sep + "schemaName";
                sep = ", ";
            }
            if (form.getQueryName() == null)
                missingRequired += sep + "queryName";
            if (missingRequired.length() > 0)
                errors.reject(SpringActionController.ERROR_MSG, "Missing required field(s): " + missingRequired + ".");

            // validate that, if provided, the containerId matches an existing container
            Container postContainer = null;
            if (form.getContainerId() != null)
            {
                postContainer = ContainerManager.getForId(form.getContainerId());
                if (postContainer == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Container does not exist for containerId provided.");
            }

            // validate that the schema and query names provided exist
            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                Container c = postContainer != null ? postContainer : getContainer();
                UserSchema schema = QueryService.get().getUserSchema(getUser(), c, form.getSchemaName());
                if (schema == null)
                    errors.reject(SpringActionController.ERROR_MSG, "UserSchema '" + form.getSchemaName() + "' not found.");
                else if (schema.getTable(form.getQueryName()) == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Table '" + form.getSchemaName() + "." + form.getQueryName() + "' not found.");
            }
        }

        @Override
        public boolean handlePost(ConceptsForm form, BindException errors) throws Exception
        {
            Lookup lookup = new Lookup(ContainerManager.getForId(form.getContainerId()), form.getSchemaName(), form.getQueryName());
            ConceptURIProperties.setLookup(getContainer(), form.getConceptURI(), lookup);

            return true;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class FolderAliasesAction extends FormViewAction<FolderAliasesForm>
    {
        public void validateCommand(FolderAliasesForm target, Errors errors)
        {
        }

        public ModelAndView getView(FolderAliasesForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<ViewContext>("/org/labkey/core/admin/folderAliases.jsp");
        }

        public boolean handlePost(FolderAliasesForm form, BindException errors) throws Exception
        {
            List<String> aliases = new ArrayList<>();
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

            return true;
        }

        public ActionURL getSuccessURL(FolderAliasesForm form)
        {
            return getManageFoldersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Folder Aliases: " + getContainer().getPath(), this.getClass());
        }
    }


    public static class FolderAliasesForm
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


    public ActionURL getCustomizeEmailURL(String templateClassName)
    {
        ActionURL url = new ActionURL(CustomizeEmailAction.class, getContainer());

        if (null != templateClassName)
            url.addParameter("templateClassName", templateClassName);

        return url;
    }


    @RequiresPermission(AdminPermission.class)
    public class CustomizeEmailAction extends FormViewAction<CustomEmailForm>
    {
        public void validateCommand(CustomEmailForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomEmailForm form, boolean reshow, BindException errors) throws Exception
        {
            JspView<CustomEmailForm> result = new JspView<>("/org/labkey/core/admin/customizeEmail.jsp", form, errors);
            result.setTitle("Email Template");
            return result;
        }

        public boolean handlePost(CustomEmailForm form, BindException errors) throws Exception
        {
            if (form.getTemplateClass() != null)
            {
                EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());

                template.setSubject(form.getEmailSubject());
                template.setSenderName(form.getEmailSender());
                template.setReplyToEmail(form.getEmailReplyTo());
                template.setBody(form.getEmailMessage());

                String[] errorStrings = new String[1];
                if (template.isValid(errorStrings))  // TODO: Pass in errors collection directly?  Should also build a list of all validation errors and display them all.
                    EmailTemplateService.get().saveEmailTemplate(template, getContainer());
                else
                    errors.reject(ERROR_MSG, errorStrings[0]);
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(CustomEmailForm form)
        {
            ActionURL result = new ActionURL(CustomizeEmailAction.class, getContainer());
            result.replaceParameter("templateClass", form.getTemplateClass());
            if (form.getReturnActionURL() != null)
            {
                result.replaceParameter(ActionURL.Param.returnUrl, form.getReturnUrl());
            }
            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("customEmail"));
            return appendAdminNavTrail(root, "Customize " + (getContainer().isRoot() ? "Site-Wide" : StringUtils.capitalize(getContainer().getContainerNoun()) + "-Level") + " Email", this.getClass());
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class DeleteCustomEmailAction extends SimpleRedirectAction<CustomEmailForm>
    {
        public ActionURL getRedirectURL(CustomEmailForm form)
        {
            if (form.getTemplateClass() != null)
            {
                EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());
                template.setSubject(form.getEmailSubject());
                template.setBody(form.getEmailMessage());

                EmailTemplateService.get().deleteEmailTemplate(template, getContainer());
            }
            return new AdminUrlsImpl().getCustomizeEmailURL(getContainer(), form.getTemplateClass(), form.getReturnURLHelper());
        }
    }


    public static class CustomEmailForm extends ReturnUrlForm
    {
        private String _templateClass;
        private String _emailSubject;
        private String _emailSender;
        private String _emailReplyTo;
        private String _emailMessage;
        private String _templateDescription;

        public void setTemplateClass(String name){_templateClass = name;}
        public String getTemplateClass(){return _templateClass;}
        public void setEmailSubject(String subject){_emailSubject = subject;}
        public String getEmailSubject(){return _emailSubject;}
        public void setEmailSender(String sender){_emailSender = sender;}
        public String getEmailSender(){return _emailSender;}
        public void setEmailMessage(String body){_emailMessage = body;}
        public String getEmailMessage(){return _emailMessage;}
        public String getEmailReplyTo(){return _emailReplyTo;}
        public void setEmailReplyTo(String emailReplyTo){_emailReplyTo = emailReplyTo;}

        public String getTemplateDescription()
        {
            return _templateDescription;
        }

        public void setTemplateDescription(String templateDescription)
        {
            _templateDescription = templateDescription;
        }
    }

    private ActionURL getManageFoldersURL()
    {
        return new AdminUrlsImpl().getManageFoldersURL(getContainer());
    }

    public static class ManageFoldersForm extends ReturnUrlForm
    {
        private String name;
        private String title;
        private boolean titleSameAsName;
        private String folder;
        private String target;
        private String folderType;
        private String defaultModule;
        private String[] activeModules;
        private boolean hasLoaded = false;
        private boolean showAll;
        private boolean confirmed = false;
        private boolean addAlias = false;
        private boolean recurse = false;
        private String templateSourceId;
        private String[] templateWriterTypes;
        private boolean templateIncludeSubfolders = false;

        public boolean getHasLoaded()
        {
            return hasLoaded;
        }

        public void setHasLoaded(boolean hasLoaded)
        {
            this.hasLoaded = hasLoaded;
        }

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

        public boolean isShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean showAll)
        {
            this.showAll = showAll;
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

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public boolean isTitleSameAsName()
        {
            return titleSameAsName;
        }

        public void setTitleSameAsName(boolean updateTitle)
        {
            this.titleSameAsName = updateTitle;
        }
        public void setName(String name)
        {
            this.name = name;
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

        public String getTarget()
        {
            return target;
        }

        public void setTarget(String target)
        {
            this.target = target;
        }

        public void setTemplateSourceId(String templateSourceId)
        {
            this.templateSourceId = templateSourceId;
        }

        public String getTemplateSourceId()
        {
            return templateSourceId;
        }

        public Container getTemplateSourceContainer()
        {
            if (null == getTemplateSourceId())
                return null;
            return ContainerManager.getForId(getTemplateSourceId());
        }

        public String[] getTemplateWriterTypes()
        {
            return templateWriterTypes;
        }

        public void setTemplateWriterTypes(String[] templateWriterTypes)
        {
            this.templateWriterTypes = templateWriterTypes;
        }

        public boolean getTemplateIncludeSubfolders()
        {
            return templateIncludeSubfolders;
        }

        public void setTemplateIncludeSubfolders(boolean templateIncludeSubfolders)
        {
            this.templateIncludeSubfolders = templateIncludeSubfolders;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RenameFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private ActionURL _returnURL;

        public void validateCommand(ManageFoldersForm target, Errors errors)
        {
            Container c = getContainer();
            if (!ContainerManager.isRenameable(c))
            {
                // 16221
                errors.reject(ERROR_MSG, "This folder may not be renamed as it is reserved by the system.");
            }
        }

        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/renameFolder.jsp", form, errors);
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                Container c = getContainer();
                if (updateFolderName(c, form, errors) && updateFolderTitle(c, form, errors))
                {
                    transaction.commit();
                    return true;
                }
            }
            return false;
        }

        private boolean updateFolderName(Container c, ManageFoldersForm form, BindException errors)
        {
            String folderName = StringUtils.trimToNull(form.getName());
            StringBuilder error = new StringBuilder();

            if (Container.isLegalName(folderName, c.isProject(), error))
            {
                // 19061: Unable to do case-only container rename
                if (c.getParent().hasChild(folderName) && !c.equals(c.getParent().getChild(folderName)))
                {
                    if (c.getParent().isRoot())
                    {
                        error.append("The server already has a project with this name.");
                    }
                    else
                    {
                        error.append("The ").append(c.getParent().isProject() ? "project " : "folder ").append(c.getParent().getPath()).append(" already has a folder with this name.");
                    }
                }
                else
                {
                    ContainerManager.rename(c, getUser(), folderName);
                    if (form.isAddAlias())
                    {
                        String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                        List<String> newAliases = new ArrayList<>(Arrays.asList(originalAliases));
                        newAliases.add(c.getPath());
                        ContainerManager.saveAliasesForContainer(c, newAliases);
                    }
                    c = ContainerManager.getForId(c.getId());     // Reload container to populate new name
                    _returnURL = new AdminUrlsImpl().getManageFoldersURL(c);
                    return true;
                }
            }

            errors.reject(ERROR_MSG, "Error: " + error + " Please enter a different name.");
            return false;
        }

        private boolean updateFolderTitle(Container c, ManageFoldersForm form, BindException errors)
        {
            //if we have gotten this far, we can assume that the formName is valid.
            String folderTitle = form.isTitleSameAsName() ? null : StringUtils.trimToNull(form.getTitle());
            StringBuilder error = new StringBuilder();
            if(Container.isLegalTitle(folderTitle, error))
            {
                try
                {
                    ContainerManager.updateTitle(c, folderTitle, getUser());
                    return true;
                }
                catch (ValidationException e)
                {
                    error.append(e.getMessage());
                }
            }
            errors.reject(ERROR_MSG, "Error: " + error + " Please enter a different name.");
            return false;
        }

        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            return _returnURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            String containerType = getContainer().isProject() ? "Project" : "Folder";
            return appendAdminNavTrail(root, "Change " + containerType  + " Name Settings", this.getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowMoveFolderTreeAction extends SimpleViewAction<ManageFoldersForm>
    {
        public ModelAndView getView(ManageFoldersForm form, BindException errors) throws Exception
        {
            return new MoveFolderTreeView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = root.addChild("Folder Management", getManageFoldersURL());
            return root.addChild("Move Folder");
        }
    }


    public static class MoveFolderTreeView extends JspView<ManageFoldersForm>
    {
        private MoveFolderTreeView(ManageFoldersForm form, BindException errors)
        {
            super("/org/labkey/core/admin/moveFolder.jsp", form, errors);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class MoveFolderAction extends SimpleViewAction<ManageFoldersForm>
    {
        public ModelAndView getView(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            Container newParent =  ContainerManager.getForPath(form.getTarget());

            if (c.isRoot())
                throw new NotFoundException("Can't move the root folder.");  // Don't show move tree from root

            if (c.equals(ContainerManager.getSharedContainer()) || c.equals(ContainerManager.getHomeContainer()))
                throw new UnsupportedOperationException("Moving /Shared or /home is not possible.");

            if (null == newParent)
            {
                errors.reject(ERROR_MSG, "Target '" + form.getTarget() + "' folder does not exist.");
                return new MoveFolderTreeView(form, errors);    // Redisplay the move folder tree
            }

            if (!newParent.hasPermission(getUser(), AdminPermission.class))
            {
                throw new UnauthorizedException();
            }

            if (newParent.hasChild(c.getName()))
            {
                errors.reject(ERROR_MSG, "Error: The selected folder already has a folder with that name.  Please select a different location (or Cancel).");
                return new MoveFolderTreeView(form, errors);    // Redisplay the move folder tree
            }

            assert !errors.hasErrors();

            Container oldProject = c.getProject();
            Container newProject = newParent.isRoot() ? c : newParent.getProject();
            if (!oldProject.getId().equals(newProject.getId()) && !form.isConfirmed())
            {
                getPageConfig().setTemplate(Template.Dialog);
                return new JspView<>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
            }

            try
            {
                ContainerManager.move(c, newParent, getUser());
            }
            catch (ValidationException e)
            {
                getPageConfig().setTemplate(Template.Dialog);
                for (ValidationError validationError : e.getErrors())
                {
                    errors.addError(new LabKeyError(validationError.getMessage()));
                }
                return new SimpleErrorView(errors);
            }

            if (form.isAddAlias())
            {
                String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                List<String> newAliases = new ArrayList<>(Arrays.asList(originalAliases));
                newAliases.add(c.getPath());
                ContainerManager.saveAliasesForContainer(c, newAliases);
            }

            c = ContainerManager.getForId(c.getId());      // Reload container to populate new location
            return HttpView.redirect(new AdminUrlsImpl().getManageFoldersURL(c));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root = root.addChild("Folder Management", getManageFoldersURL());
            return root.addChild("Move Folder");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ConfirmProjectMoveAction extends SimpleViewAction<ManageFoldersForm>
    {
        public ModelAndView getView(ManageFoldersForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class CreateFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private ActionURL _successURL;

        public void validateCommand(ManageFoldersForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            JspView statusView = new JspView<>("/org/labkey/core/admin/createFolder.jsp", form, errors);
            vbox.addView(statusView);

            Container c = getViewContext().getContainerNoTab();         // Cannot create subfolder of tab folder

            setHelpTopic("createProject");
            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(null, c));
            getPageConfig().setTemplate(Template.Wizard);

            if (c.isRoot())
                getPageConfig().setTitle("Create Project");
            else
            {
                String title = "Create Folder";

                title += " in /";
                if (c == ContainerManager.getHomeContainer())
                    title += "Home";
                else
                    title += c.getName();

                getPageConfig().setTitle(title);
            }

            return vbox;
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container parent = getViewContext().getContainerNoTab();
            String folderName = StringUtils.trimToNull(form.getName());
            StringBuilder error = new StringBuilder();

            if (Container.isLegalName(folderName, parent.isRoot(), error))
            {
                if (parent.hasChild(folderName))
                {
                    if (parent.isRoot())
                    {
                        error.append("The server already has a project with this name.");
                    }
                    else
                    {
                        error.append("The ").append(parent.isProject() ? "project " : "folder ").append(parent.getPath()).append(" already has a folder with this name.");
                    }
                }
                else
                {
                    Container c;
                    String folderType = form.getFolderType();

                    if (null == folderType)
                    {
                        errors.reject(null, "Folder type must be specified");
                        return false;
                    }

                    if ("Template".equals(folderType)) // Create folder from selected template
                    {
                        Container sourceContainer = form.getTemplateSourceContainer();
                        if (null == sourceContainer)
                        {
                            errors.reject(null, "Source template folder not selected");
                            return false;
                        }
                        else if (!sourceContainer.hasPermission(getUser(), AdminPermission.class))
                        {
                            errors.reject(null, "User does not have administrator permissions to the source container");
                            return false;
                        }
                        else if (!sourceContainer.hasEnableRestrictedModules(getUser()) && sourceContainer.hasRestrictedActiveModule(sourceContainer.getActiveModules()))
                        {
                            errors.reject(null, "The source folder has a restricted module for which you do not have permission.");
                            return false;
                        }

                        MemoryVirtualFile vf = new MemoryVirtualFile();

                        // export objects from the source folder
                        FolderWriterImpl writer = new FolderWriterImpl();
                        FolderExportContext exportCtx = new FolderExportContext(getUser(), sourceContainer, PageFlowUtil.set(form.getTemplateWriterTypes()), "new",
                                form.getTemplateIncludeSubfolders(), PHI.NotPHI, false, false, false, new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));
                        writer.write(sourceContainer, exportCtx, vf);

                        // create the new target container
                        c = ContainerManager.createContainer(parent, folderName, null, null, Container.TYPE.normal, getUser());

                        // import objects into the target folder
                        XmlObject folderXml = vf.getXmlBean("folder.xml");
                        if (folderXml instanceof FolderDocument)
                        {
                            FolderDocument folderDoc = (FolderDocument)folderXml;
                            FolderImportContext importCtx = new FolderImportContext(getUser(), c, folderDoc, null, new StaticLoggerGetter(Logger.getLogger(FolderImporterImpl.class)), vf);

                            FolderImporterImpl importer = new FolderImporterImpl();
                            importer.process(null, importCtx, vf);
                        }
                    }
                    else
                    {
                        FolderType type = FolderTypeManager.get().getFolderType(folderType);

                        if (type == null)
                        {
                            errors.reject(null, "Folder type not recognized");
                            return false;
                        }

                        String[] modules = form.getActiveModules();

                        if (null == StringUtils.trimToNull(folderType) || FolderType.NONE.getName().equals(folderType))
                        {
                            if (null == modules || modules.length == 0)
                            {
                                errors.reject(null, "At least one module must be selected");
                                return false;
                            }
                        }

                        c = ContainerManager.createContainer(parent, folderName, ((form.isTitleSameAsName() || folderName.equals(form.getTitle())) ? null : form.getTitle()), null, Container.TYPE.normal, getUser());
                        c.setFolderType(type, getUser());

                        if (null == StringUtils.trimToNull(folderType) || FolderType.NONE.getName().equals(folderType))
                        {
                            Set<Module> activeModules = new HashSet<>();
                            for (String moduleName : modules)
                            {
                                Module module = ModuleLoader.getInstance().getModule(moduleName);
                                if (module != null)
                                    activeModules.add(module);
                            }

                            c.setFolderType(FolderType.NONE, activeModules, getUser());
                            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
                            c.setDefaultModule(defaultModule);
                        }
                    }

                    _successURL = new AdminUrlsImpl().getSetFolderPermissionsURL(c);
                    _successURL.addParameter("wizard", Boolean.TRUE.toString());

                    return true;
                }
            }

            errors.reject(ERROR_MSG, "Error: " + error + " Please enter a different name.");
            return false;
        }

        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class SetFolderPermissionsAction extends FormViewAction<SetFolderPermissionsForm>
    {
        private ActionURL _successURL;

        public void validateCommand(SetFolderPermissionsForm target, Errors errors)
        {
        }


        public ModelAndView getView(SetFolderPermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            JspView statusView = new JspView<>("/org/labkey/core/admin/setFolderPermissions.jsp", form, errors);
            vbox.addView(statusView);

            Container c = getContainer();
            getPageConfig().setTitle("Users / Permissions");
            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(c, c.getParent()));
            getPageConfig().setTemplate(Template.Wizard);
            setHelpTopic("createProject");

            return vbox;

        }

        public boolean handlePost(SetFolderPermissionsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String permissionType = form.getPermissionType();

            if(c.isProject()){
                _successURL = new AdminUrlsImpl().getInitialFolderSettingsURL(c);
            }
            else
            {
                List<NavTree> extraSteps = getContainer().getFolderType().getExtraSetupSteps(getContainer());
                if (extraSteps.isEmpty())
                {
                    if (form.isAdvanced())
                    {
                        _successURL = new SecurityController.SecurityUrlsImpl().getPermissionsURL(getContainer());
                    }
                    else
                    {
                        _successURL = getContainer().getStartURL(getUser());
                    }
                }
                else
                {
                    _successURL = new ActionURL(extraSteps.get(0).getHref());
                }
            }

            if(permissionType == null){
                errors.reject(ERROR_MSG, "You must select one of the options for permissions.");
                return false;
            }

            switch (permissionType)
            {
                case "CurrentUser":
                    MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
                    Role role = RoleManager.getRole(c.isProject() ? ProjectAdminRole.class : FolderAdminRole.class);

                    policy.addRoleAssignment(getUser(), role);
                    SecurityPolicyManager.savePolicy(policy);
                    break;
                case "Inherit":
                    SecurityManager.setInheritPermissions(c);
                    break;
                case "CopyExistingProject":
                    String targetProject = form.getTargetProject();
                    if (targetProject == null)
                    {
                        errors.reject(ERROR_MSG, "In order to copy permissions from an existing project, you must pick a project.");
                        return false;
                    }
                    Container source = ContainerManager.getForId(targetProject);
                    assert source != null;

                    Map<UserPrincipal, UserPrincipal> groupMap = GroupManager.copyGroupsToContainer(source, c);

                    //copy role assignments
                    SecurityPolicy op = SecurityPolicyManager.getPolicy(source);
                    MutableSecurityPolicy np = new MutableSecurityPolicy(c);
                    for (RoleAssignment assignment : op.getAssignments())
                    {
                        Integer userId = assignment.getUserId();
                        UserPrincipal p = SecurityManager.getPrincipal(userId);
                        Role r = assignment.getRole();

                        if (p instanceof Group)
                        {
                            Group g = (Group) p;
                            if (!g.isProjectGroup())
                            {
                                np.addRoleAssignment(p, r);
                            }
                            else
                            {
                                np.addRoleAssignment(groupMap.get(p), r);
                            }
                        }
                        else
                        {
                            np.addRoleAssignment(p, r);
                        }
                    }

                    SecurityPolicyManager.savePolicy(np);
                    break;
                default:
                    throw new UnsupportedOperationException("An Unknown permission type was supplied: " + permissionType);
            }
            _successURL.addParameter("wizard", Boolean.TRUE.toString());

            return true;
        }

        public ActionURL getSuccessURL(SetFolderPermissionsForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            return null;
        }
    }

    public static class SetFolderPermissionsForm
    {
        private String targetProject;
        private String permissionType;
        private boolean advanced;

        public String getPermissionType()
        {
            return permissionType;
        }

        public void setPermissionType(String permissionType)
        {
            this.permissionType = permissionType;
        }

        public String getTargetProject()
        {
            return targetProject;
        }

        public void setTargetProject(String targetProject)
        {
            this.targetProject = targetProject;
        }

        public boolean isAdvanced()
        {
            return advanced;
        }

        public void setAdvanced(boolean advanced)
        {
            this.advanced = advanced;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SetInitialFolderSettingsAction extends FormViewAction<ProjectSettingsForm>
    {
        private ActionURL _successURL;

        public void validateCommand(ProjectSettingsForm target, Errors errors)
        {
        }

        public ModelAndView getView(ProjectSettingsForm form, boolean reshow, BindException errors) throws Exception
        {
            VBox vbox = new VBox();
            Container c = getContainer();

            JspView statusView = new JspView<>("/org/labkey/core/admin/setInitialFolderSettings.jsp", form, errors);
            vbox.addView(statusView);

            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(c, c.getParent()));
            getPageConfig().setTemplate(Template.Wizard);

            String noun = c.isProject() ? "Project": "Folder";
            getPageConfig().setTitle(noun + " Settings");

            return vbox;

        }

        public boolean handlePost(ProjectSettingsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String folderRootPath = StringUtils.trimToNull(form.getFolderRootPath());
            String fileRootOption = form.getFileRootOption() != null ? form.getFileRootOption() : "default";

            if(folderRootPath == null && !fileRootOption.equals("default"))
            {
                errors.reject(ERROR_MSG, "Error: Must supply a default file location.");
                return false;
            }

            FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
            if(fileRootOption.equals("default"))
            {
                service.setIsUseDefaultRoot(c, true);
            }
            // Requires AdminOperationsPermission to set file root
            else if (c.hasPermission(getUser(), AdminOperationsPermission.class))
            {
                if (!service.isValidProjectRoot(folderRootPath))
                {
                    errors.reject(ERROR_MSG, "File root '" + folderRootPath + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                    return false;
                }

                service.setIsUseDefaultRoot(c.getProject(), false);
                service.setFileRootPath(c.getProject(), folderRootPath);
            }

            List<NavTree> extraSteps = getContainer().getFolderType().getExtraSetupSteps(getContainer());
            if (extraSteps.isEmpty())
            {
                _successURL = getContainer().getStartURL(getUser());
            }
            else
            {
                _successURL = new ActionURL(extraSteps.get(0).getHref());
            }

            return true;
        }

        public ActionURL getSuccessURL(ProjectSettingsForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            getPageConfig().setHelpTopic(new HelpTopic("createProject"));
            return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private Container target;

        public void validateCommand(ManageFoldersForm form, Errors errors)
        {
            target = getContainer();

            if (!ContainerManager.isDeletable(target))
                errors.reject(ERROR_MSG, "The path " + target.getPath() + " is not deletable.");
        }

        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/deleteFolder.jsp", form);
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            // Must be site/app admin to delete a project
            if (c.isProject() && !getUser().hasRootAdminPermission())
            {
                throw new UnauthorizedException();
            }

            if (c.equals(ContainerManager.getSharedContainer()) || c.equals(ContainerManager.getHomeContainer()))
                throw new UnsupportedOperationException("Deleting /Shared or /home is not possible.");

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

            return true;
        }

        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            // If we just deleted a project then redirect to the home page, otherwise back to managing the project folders
            Container c = getContainer();

            if (c.isProject())
                return AppProps.getInstance().getHomePageActionURL();
            else
                return new AdminUrlsImpl().getManageFoldersURL(c.getParent());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ReorderFoldersAction extends FormViewAction<FolderReorderForm>
    {
        public void validateCommand(FolderReorderForm target, Errors errors)
        {
        }

        public ModelAndView getView(FolderReorderForm folderReorderForm, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<ViewContext>("/org/labkey/core/admin/reorderFolders.jsp");
        }

        public boolean handlePost(FolderReorderForm form, BindException errors) throws Exception
        {
            return ReorderFolders(form, errors);
        }

        public ActionURL getSuccessURL(FolderReorderForm folderReorderForm)
        {
            if (getContainer().isRoot())
                return getShowAdminURL();
            else
                return getManageFoldersURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String title = "Reorder " + (getContainer().isRoot() || getContainer().getParent().isRoot() ? "Projects" : "Folders");
            return appendAdminNavTrail(root, title, this.getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ReorderFoldersApiAction extends MutatingApiAction<FolderReorderForm>
    {
        @Override
        public ApiResponse execute(FolderReorderForm form, BindException errors) throws Exception
        {
            return new ApiSimpleResponse("success", ReorderFolders(form, errors));
        }
    }

    private boolean ReorderFolders(FolderReorderForm form, BindException errors)
    {
        Container parent = getContainer().isRoot() ? getContainer() : getContainer().getParent();
        if (form.isResetToAlphabetical())
            ContainerManager.setChildOrderToAlphabetical(parent);
        else if (form.getOrder() != null)
        {
            List<Container> children = parent.getChildren();
            String[] order = form.getOrder().split(";");
            Map<String, Container> nameToContainer = new HashMap<>();
            for (Container child : children)
                nameToContainer.put(child.getName(), child);
            List<Container> sorted = new ArrayList<>(children.size());
            for (String childName : order)
            {
                Container child = nameToContainer.get(childName);
                sorted.add(child);
            }

            try
            {
                ContainerManager.setChildOrder(parent, sorted);
            }
            catch (ContainerException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
        }

        return true;
    }

    public static class FolderReorderForm
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

    @RequiresPermission(AdminPermission.class)
    public class RevertFolderAction extends ApiAction<RevertFolderForm>
    {
        public ApiResponse execute(RevertFolderForm form, BindException errors) throws Exception
        {
            boolean success = false;
            Container revertContainer = ContainerManager.getForPath(form.getContainerPath());
            if (null != revertContainer)
            {
                if (revertContainer.isContainerTab())
                {
                    FolderTab tab = revertContainer.getParent().getFolderType().findTab(revertContainer.getName());
                    if (null != tab)
                    {
                        FolderType origFolderType = tab.getFolderType();
                        if (null != origFolderType)
                        {
                            revertContainer.setFolderType(origFolderType, getUser(), errors);
                            if (!errors.hasErrors())
                                success = true;
                        }
                    }
                }
                else if (revertContainer.getFolderType().hasContainerTabs())
                {
                    try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                    {
                        List<Container> children = revertContainer.getChildren();
                        for (Container container : children)
                        {
                            if (container.isContainerTab())
                            {
                                FolderTab tab = revertContainer.getFolderType().findTab(container.getName());
                                if (null != tab)
                                {
                                    FolderType origFolderType = tab.getFolderType();
                                    if (null != origFolderType)
                                    {
                                        container.setFolderType(origFolderType, getUser(), errors);
                                    }
                                }
                            }
                        }
                        if (!errors.hasErrors())
                        {
                            transaction.commit();
                            success = true;
                        }
                    }
                }
            }
            return new ApiSimpleResponse("success", success);
        }
    }

    public static class RevertFolderForm
    {
        private String _containerPath;

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }
    }

    public static class EmailTestForm
    {
        private String _to;
        private String _body;
        private ConfigurationException _exception;

        public String getTo()
        {
            return _to;
        }

        public void setTo(String to)
        {
            _to = to;
        }

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }

        public ConfigurationException getException()
        {
            return _exception;
        }

        public void setException(ConfigurationException exception)
        {
            _exception = exception;
        }

        public String getFrom(Container c)
        {
            LookAndFeelProperties props = LookAndFeelProperties.getInstance(c);
            return props.getSystemEmailAddress();
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class EmailTestAction extends FormViewAction<EmailTestForm>
    {
        @Override
        public void validateCommand(EmailTestForm form, Errors errors)
        {
            if(null == form.getTo() || form.getTo().equals(""))
            {
                errors.reject("To field cannot be blank.");
                form.setException(new ConfigurationException("To field cannot be blank"));
                return;
            }

            try
            {
                ValidEmail email = new ValidEmail(form.getTo());
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                errors.reject(e.getMessage());
                form.setException(new ConfigurationException(e.getMessage()));
            }
        }

        @Override
        public ModelAndView getView(EmailTestForm form, boolean reshow, BindException errors) throws Exception
        {
            JspView<EmailTestForm> testView = new JspView<>("/org/labkey/core/admin/emailTest.jsp", form);
            testView.setTitle("Send a Test Email");

            if(null != MailHelper.getSession() && null != MailHelper.getSession().getProperties())
            {
                JspView emailPropsView = new JspView("/org/labkey/core/admin/emailProps.jsp");
                emailPropsView.setTitle("Current Email Settings");

                return new VBox(emailPropsView, testView);
            }
            else
                return testView;
        }

        @Override
        public boolean handlePost(EmailTestForm form, BindException errors) throws Exception
        {
            if(errors.hasErrors())
            {
                return false;
            }

            LookAndFeelProperties props = LookAndFeelProperties.getInstance(getContainer());
                try
                {
                    MailHelper.ViewMessage msg = MailHelper.createMessage(props.getSystemEmailAddress(), new ValidEmail(form.getTo()).toString());
                    msg.setSubject("Test email message sent from " + props.getShortName());
                    msg.setText(PageFlowUtil.filter(form.getBody()));

                    try
                    {
                        MailHelper.send(msg, getUser(), getContainer());
                    }
                    catch (ConfigurationException e)
                    {
                        form.setException(e);
                        return false;
                    }
                    catch (Exception e)
                    {
                        form.setException(new ConfigurationException(e.getMessage()));
                        return false;
                    }
                }
                catch (MessagingException e)
                {
                    errors.reject(e.getMessage());
                    return false;
                }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(EmailTestForm emailTestForm)
        {
            return new ActionURL(EmailTestAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", new ActionURL(ShowAdminAction.class, getContainer()).getLocalURIString());
            return root.addChild("Test Email Configuration");
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class RecreateViewsAction extends ConfirmAction
    {
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            getPageConfig().setShowHeader(false);
            getPageConfig().setTitle("Recreate Views?");
            return new HtmlView("Are you sure you want to drop and recreate all module views?");
        }

        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().recreateViews();
            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
    }


    static public class LoggingForm
    {
        public boolean isLogging()
        {
            return logging;
        }

        public void setLogging(boolean logging)
        {
            this.logging = logging;
        }

        public boolean logging = false;
    }


    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class GetSessionLogEventsAction extends ApiAction
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            int eventId = 0;
            try
            {
                String s = getViewContext().getRequest().getParameter("eventId");
                if (null != s)
                    eventId = Integer.parseInt(s);
            }
            catch (NumberFormatException x) {}
            LoggingEvent[] events = SessionAppender.getLoggingEvents(getViewContext().getRequest());
            ArrayList<Map<String, Object>> list = new ArrayList<>(events.length);
            for (LoggingEvent e : events)
            {
                if (eventId==0 || eventId<Integer.parseInt(e.getProperty("eventId")))
                {
                    HashMap<String, Object> m = new HashMap<>();
                    m.put("eventId", e.getProperty("eventId"));
                    m.put("level", e.getLevel().toString());
                    m.put("message", e.getMessage());
                    m.put("timestamp", new Date(e.getTimeStamp()));
                    list.add(m);
                }
            }
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("success", true);
            res.put("events", list);
            return res;
        }
    }

    @RequiresLogin
    @AllowedBeforeInitialUserIsSet
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking  /* ignore so that we don't get an update in the UI for each time it requests the newest data */
    public class GetTrackedAllocationsAction extends ApiAction
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            long requestId = 0;
            try
            {
                String s = getViewContext().getRequest().getParameter("requestId");
                if (null != s)
                    requestId = Long.parseLong(s);
            }
            catch (NumberFormatException ignored) {}
            List<RequestInfo> requests = MemTracker.getInstance().getNewRequests(requestId);
            List<Map<String, Object>> jsonRequests = new ArrayList<>(requests.size());
            for (RequestInfo requestInfo : requests)
            {
                Map<String, Object> m = new HashMap<>();
                m.put("requestId", requestInfo.getId());
                m.put("url", requestInfo.getUrl());
                m.put("date", requestInfo.getDate());


                List<Map.Entry<String, Integer>> sortedObjects = sortByCounts(requestInfo);

                List<Map<String, Object>> jsonObjects = new ArrayList<>(sortedObjects.size());
                for (Map.Entry<String, Integer> entry : sortedObjects)
                {
                    Map<String, Object> jsonObject = new HashMap<>();
                    jsonObject.put("name", entry.getKey());
                    jsonObject.put("count", entry.getValue());
                    jsonObjects.add(jsonObject);
                }
                m.put("objects", jsonObjects);
                jsonRequests.add(m);
            }
            return new ApiSimpleResponse("requests", jsonRequests);
        }

        private List<Map.Entry<String, Integer>> sortByCounts(RequestInfo requestInfo)
        {
            List<Map.Entry<String, Integer>> objects = new ArrayList<>(requestInfo.getObjects().entrySet());
            objects.sort(Comparator.comparing(Map.Entry::getValue, Comparator.reverseOrder()));
            return objects;
        }
    }

    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class TrackedAllocationsViewerAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.Print);
            return new JspView("/org/labkey/core/admin/memTrackerViewer.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public class SessionLoggingAction extends FormViewAction<LoggingForm>
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isDeveloper())
                throw new UnauthorizedException();
        }

        public boolean handlePost(LoggingForm form, BindException errors) throws Exception
        {
            boolean on = SessionAppender.isLogging(getViewContext().getRequest());
            if (form.logging != on)
            {
                if (!form.logging)
                    Logger.getLogger(AdminController.class).info("turn session logging OFF");
                SessionAppender.setLoggingForSession(getViewContext().getRequest(), form.logging);
                if (form.logging)
                    Logger.getLogger(AdminController.class).info("turn session logging ON");
            }
            return true;
        }

        public void validateCommand(LoggingForm target, Errors errors)
        {
        }

        public ModelAndView getView(LoggingForm o, boolean reshow, BindException errors) throws Exception
        {
            SessionAppender.setLoggingForSession(getViewContext().getRequest(), true);
            getPageConfig().setTemplate(Template.Print);
            return new LoggingView();
        }

        public ActionURL getSuccessURL(LoggingForm o)
        {
            return new ActionURL(SessionLoggingAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", new ActionURL(ShowAdminAction.class, getContainer()).getLocalURIString());
            return root.addChild("View Event Log");
        }
    }


    class LoggingView extends JspView
    {
        LoggingView()
        {
            super(AdminController.class, "logging.jsp", null);
        }
    }


    public static class LogForm
    {
        private String _message;
        private String _level;

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = level;
        }
    }


    // Simple action that writes "message" parameter to the labkey log. Used by the test harness to indicate when
    // each test begins and ends. Message parameter is output as sent, except that \n is translated to newline.
    @RequiresLogin
    public class LogAction extends SimpleViewAction<LogForm>
    {
        @Override
        public ModelAndView getView(LogForm logForm, BindException errors) throws Exception
        {
            // Could use %A0 for newline in the middle of the message, however, parameter values get trimmed so translate
            // \n to newlines to allow them at the beginning or end of the message as well.
            StringBuilder message = new StringBuilder();
            message.append(StringUtils.replace(logForm.getMessage(), "\\n", "\n"));

            Level level = Level.toLevel(logForm.getLevel(), Level.INFO);
            CLIENT_LOG.log(level, message);
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ValidateDomainsAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(PipelineUrls.class).urlBegin(ContainerManager.getRoot());
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            // Find a valid pipeline root - we don't really care which one, we just need somewhere to write the log file
            for (Container project : Arrays.asList(ContainerManager.getSharedContainer(), ContainerManager.getHomeContainer()))
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(project);
                if (root != null && root.isValid())
                {
                    ViewBackgroundInfo info = getViewBackgroundInfo();
                    PipelineJob job = new ValidateDomainsPipelineJob(info, root);
                    PipelineService.get().queueJob(job);
                    return true;
                }
            }
            return false;
        }
    }


    public static class ModulesForm
    {
        private double[] _ignore = new double[0];  // Module versions to ignore (filter out of the results)
        private boolean _managedOnly = false;
        private boolean _unmanagedOnly = false;

        public double[] getIgnore()
        {
            return _ignore;
        }

        public void setIgnore(double[] ignore)
        {
            _ignore = ignore;
        }

        private Set<Double> getIgnoreSet()
        {
            return new LinkedHashSet<>(Arrays.asList(ArrayUtils.toObject(_ignore)));
        }

        public boolean isManagedOnly()
        {
            return _managedOnly;
        }

        @SuppressWarnings("unused")
        public void setManagedOnly(boolean managedOnly)
        {
            _managedOnly = managedOnly;
        }

        public boolean isUnmanagedOnly()
        {
            return _unmanagedOnly;
        }

        @SuppressWarnings("unused")
        public void setUnmanagedOnly(boolean unmanagedOnly)
        {
            _unmanagedOnly = unmanagedOnly;
        }
    }


    enum ManageFilter
    {
        ManagedOnly
            {
                @Override
                boolean accept(Module module)
                {
                    return module.shouldManageVersion();
                }
            },
        UnmanagedOnly
            {
                @Override
                boolean accept(Module module)
                {
                    return !module.shouldManageVersion();
                }
            },
        All
            {
                @Override
                boolean accept(Module module)
                {
                    return true;
                }
            };

        abstract boolean accept(Module module);
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ModulesAction extends SimpleViewAction<ModulesForm>
    {
        @Override
        public ModelAndView getView(ModulesForm form, BindException errors) throws Exception
        {
            ModuleLoader ml = ModuleLoader.getInstance();
            boolean hasAdminOpsPerm = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

            Collection<ModuleContext> unknownModules = ml.getUnknownModuleContexts().values();
            Collection<ModuleContext> knownModules = ml.getAllModuleContexts();
            knownModules.removeAll(unknownModules);

            Set<Double> ignoreSet = form.getIgnoreSet();
            String managedLink = "";
            String unmanagedLink = "";

            // Option to filter out all modules whose version shouldn't be managed, or whose version matches the previous release
            // version or 0.00. This can be helpful during the end-of-release consolidation process. Show the link only in dev mode.
            if (AppProps.getInstance().isDevMode())
            {
                if (ignoreSet.isEmpty() && !form.isManagedOnly())
                {
                    String previousRelease = ModuleContext.formatVersion(Constants.getPreviousReleaseVersion());
                    String nextRelease = ModuleContext.formatVersion(Constants.getNextReleaseVersion());
                    ActionURL url = new ActionURL(ModulesAction.class, ContainerManager.getRoot());
                    url.addParameter("ignore", "0.00," + previousRelease + "," + nextRelease);
                    url.addParameter("managedOnly", true);
                    managedLink = PageFlowUtil.textLink("Click here to ignore 0.00, " + previousRelease + ", " + nextRelease + " and unmanaged modules", url);
                }
                else
                {
                    List<String> ignore = ignoreSet
                        .stream()
                        .map(ModuleContext::formatVersion)
                        .collect(Collectors.toCollection(LinkedList::new));

                    String ignoreString = ignore.isEmpty() ? null : ignore.toString();
                    String unmanaged = form.isManagedOnly() ? "unmanaged" : null;

                    managedLink = "(Currently ignoring " + Joiner.on(" and ").skipNulls().join(new String[]{ignoreString, unmanaged}) + ") ";
                }

                if (!form.isUnmanagedOnly())
                {
                    ActionURL url = new ActionURL(ModulesAction.class, ContainerManager.getRoot());
                    url.addParameter("unmanagedOnly", true);
                    unmanagedLink = PageFlowUtil.textLink("Click here to show unmanaged modules only", url);
                }
                else
                {
                    unmanagedLink = "(Currently showing unmanaged modules only)";
                }
            }

            ManageFilter filter = form.isManagedOnly() ? ManageFilter.ManagedOnly : (form.isUnmanagedOnly() ? ManageFilter.UnmanagedOnly : ManageFilter.All);
            String deleteInstructions = hasAdminOpsPerm ? "<br><br>" + PageFlowUtil.filter("The delete links below will remove all record of a module from the database tables, " +
                "but to remove a module completely you must also manually delete its .module file and exploded module directory from your LabKey Server deployment directory. " +
                "Module files are typically deployed in <labkey_deployment_root>/modules and <labkey_deployment_root>/externalModules.") : "";
            String docLink = "<br><br>Additional modules available, click " + (new HelpTopic("defaultModules").getSimpleLinkHtml("here")) + " to learn more.";
            HttpView known = new ModulesView(knownModules, "Known", PageFlowUtil.filter("Each of these modules is installed and has a valid module file. ") + managedLink + unmanagedLink + deleteInstructions + docLink, null, ignoreSet, filter);
            HttpView unknown = new ModulesView(unknownModules, "Unknown",
                PageFlowUtil.filter((1 == unknownModules.size() ? "This module" : "Each of these modules") + " has been installed on this server " +
                "in the past but the corresponding module file is currently missing or invalid. Possible explanations: the " +
                "module is no longer being distributed, the module has been renamed, the server location where the module " +
                "is stored is not accessible, or the module file is corrupted.") + deleteInstructions, PageFlowUtil.filter("A module is considered \"unknown\" if it was installed on this server " +
                "in the past but the corresponding module file is currently missing or invalid. This server has no unknown modules."), Collections.emptySet(), filter);

            return new VBox(known, unknown);
        }

        private class ModulesView extends WebPartView
        {
            private final Collection<ModuleContext> _contexts;
            private final String _type;
            private final String _descriptionHtml;
            private final String _noModulesDescriptionHtml;
            private final Set<Double> _ignoreVersions;
            private final ManageFilter _manageFilter;

            private ModulesView(Collection<ModuleContext> contexts, String type, String descriptionHtml, String noModulesDescriptionHtml, Set<Double> ignoreVersions, ManageFilter manageFilter)
            {
                super(FrameType.PORTAL);
                List<ModuleContext> sorted = new ArrayList<>(contexts);
                sorted.sort(Comparator.comparing(ModuleContext::getName, String.CASE_INSENSITIVE_ORDER));

                _contexts = sorted;
                _type = type;
                _descriptionHtml = descriptionHtml;
                _noModulesDescriptionHtml = noModulesDescriptionHtml;
                _ignoreVersions = ignoreVersions;
                _manageFilter = manageFilter;
                setTitle(_type + " Modules");
            }

            @Override
            protected void renderView(Object model, PrintWriter out)
            {
                boolean hasAdminOpsPerm = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

                if (_contexts.isEmpty())
                {
                    out.println(_noModulesDescriptionHtml);
                }
                else
                {
                    out.println("<div>" + _descriptionHtml + "</div><br/>");
                    out.println("\n<table class=\"labkey-data-region-legacy labkey-show-borders\">");
                    out.println("<tr><td class=\"labkey-column-header\">Name</td>");
                    out.println("<td class=\"labkey-column-header\">Version</td>");
                    out.println("<td class=\"labkey-column-header\">Class</td>");
                    out.println("<td class=\"labkey-column-header\">Source</td>");
                    out.println("<td class=\"labkey-column-header\">Schemas</td>");
                    if (hasAdminOpsPerm) // this is for the "delete module and schema" column links
                        out.println("<td class=\"labkey-column-header\"></td></tr>");

                    int rowCount = 0;
                    for (ModuleContext moduleContext : _contexts)
                    {
                        if (_ignoreVersions.contains(moduleContext.getInstalledVersion()))
                            continue;

                        Module module = ModuleLoader.getInstance().getModule(moduleContext.getName());

                        if (null != module && !_manageFilter.accept(module))
                            continue;

                        List<String> schemas = moduleContext.getSchemaList();
                        if (rowCount % 2 == 0)
                            out.println("  <tr class=\"labkey-alternate-row\">");
                        else
                            out.println("  <tr class=\"labkey-row\">");

                        out.print("    <td>");
                        out.print(PageFlowUtil.filter(moduleContext.getName()));
                        out.println("</td>");

                        out.print("    <td>");
                        out.print(ModuleContext.formatVersion(moduleContext.getInstalledVersion()));
                        out.println("</td>");

                        out.print("    <td>");
                        out.print(PageFlowUtil.filter(moduleContext.getClassName()));
                        out.println("</td>");

                        out.print("    <td>");
                        out.print(null != module ? PageFlowUtil.filter(module.getSourcePath()) : "");
                        out.println("</td>");

                        out.print("    <td>");
                        out.print(PageFlowUtil.filter(StringUtils.join(schemas, ", ")));
                        out.println("</td>");

                        if (hasAdminOpsPerm)
                        {
                            out.print("    <td>");
                            out.print(PageFlowUtil.textLink("Delete Module" + (schemas.isEmpty() ? "" : (" and Schema" + (schemas.size() > 1 ? "s" : ""))), getDeleteURL(moduleContext.getName())));
                            out.println("</td>");
                        }

                        out.println("  </tr>");

                        rowCount++;
                    }

                    out.println("</table>");
                }
            }
        }

        private ActionURL getDeleteURL(String name)
        {
            ActionURL url = new ActionURL(DeleteModuleAction.class, ContainerManager.getRoot());
            url.addParameter("name", name);

            return url;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setHelpTopic(new HelpTopic("defaultModules"));
            return appendAdminNavTrail(root, "Modules", getClass());
        }
    }


    public static class ModuleForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(String name)
        {
            _name = name;
        }

        private ModuleContext getModuleContext()
        {
            ModuleLoader ml = ModuleLoader.getInstance();
            ModuleContext ctx = ml.getModuleContext(getName());

            if (null == ctx)
                throw new NotFoundException("Module not found");

            return ctx;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteModuleAction extends ConfirmAction<ModuleForm>
    {
        @Override
        public void validateCommand(ModuleForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getConfirmView(ModuleForm form, BindException errors)
        {
            ModuleContext ctx = form.getModuleContext();
            List<String> schemas = ctx.getSchemaList();
            String description = "\"" + ctx.getName() + "\" module";

            if (!schemas.isEmpty())
            {
                description += " and delete all data in ";
                description += schemas.size() > 1 ? "these schemas: " + StringUtils.join(schemas, ", ") : "the \"" + schemas.get(0) + "\" schema";
            }

            String message = "Are you sure you want to remove the " + PageFlowUtil.filter(description) + "? This operation may render the server unusable and cannot be undone!<br><br>";
            message += "Deleting modules on a running server could leave it in an unpredictable state; be sure to restart your server.";
            return new HtmlView(message);
        }

        @Override
        public boolean handlePost(ModuleForm form, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().removeModule(form.getModuleContext());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ModuleForm form)
        {
            return new ActionURL(ModulesAction.class, ContainerManager.getRoot());
        }
    }

    public static class ExperimentalFeaturesForm
    {
        private String feature;
        private boolean enabled;

        public String getFeature()
        {
            return feature;
        }

        public void setFeature(String feature)
        {
            this.feature = feature;
        }

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ExperimentalFeatureAction extends ApiAction<ExperimentalFeaturesForm>
    {
        @Override
        public ApiResponse execute(ExperimentalFeaturesForm form, BindException errors) throws Exception
        {
            String feature = StringUtils.trimToNull(form.getFeature());
            if (feature == null)
                throw new ApiUsageException("feature is required");

            if (isPost())
            {
                ExperimentalFeatureService svc = ServiceRegistry.get().getService(ExperimentalFeatureService.class);
                if (svc != null)
                    svc.setFeatureEnabled(form.getFeature(), form.isEnabled(), getUser());
            }

            Map<String, Object> ret = new HashMap<>();
            ret.put("feature", form.getFeature());
            ret.put("enabled", AppProps.getInstance().isExperimentalFeatureEnabled(form.getFeature()));
            return new ApiSimpleResponse(ret);
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class ExperimentalFeaturesAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Object form, boolean reshow, BindException errors) throws Exception
        {
            JspView view = new JspView<>("/org/labkey/core/admin/experimentalFeatures.jsp");
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        @Override
        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            throw new UnsupportedOperationException("Nope");
        }

        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return getShowAdminURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("experimental");
            return root.addChild("Experimental Features");
        }
    }

    public static class FolderTypesBean
    {
        private final Collection<FolderType> _allFolderTypes;
        private final Collection<FolderType> _enabledFolderTypes;

        public FolderTypesBean(Collection<FolderType> allFolderTypes, Collection<FolderType> enabledFolderTypes)
        {
            _allFolderTypes = allFolderTypes;
            _enabledFolderTypes = enabledFolderTypes;
        }

        public Collection<FolderType> getAllFolderTypes()
        {
            return _allFolderTypes;
        }

        public Collection<FolderType> getEnabledFolderTypes()
        {
            return _enabledFolderTypes;
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class FolderTypesAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Object form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/enabledFolderTypes.jsp", new FolderTypesBean(FolderTypeManager.get().getAllFolderTypes(), FolderTypeManager.get().getEnabledFolderTypes()));
        }

        @Override
        public boolean handlePost(Object form, BindException errors) throws Exception
        {
            List<FolderType> enabledFolderTypes = new ArrayList<>();
            for (FolderType folderType : FolderTypeManager.get().getAllFolderTypes())
            {
                boolean enabled = Boolean.TRUE.toString().equalsIgnoreCase(getViewContext().getRequest().getParameter(folderType.getName()));
                if (enabled)
                {
                    enabledFolderTypes.add(folderType);
                }
            }
            FolderTypeManager.get().setEnabledFolderTypes(enabledFolderTypes);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return getShowAdminURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Folder Types");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CustomizeMenuAction extends ApiAction<CustomizeMenuForm>
    {
        public ApiResponse execute(CustomizeMenuForm form, BindException errors) throws Exception
        {
            if (null != form.getUrl())
            {
                String errorMessage = StringExpressionFactory.validateURL(form.getUrl());
                if (null != errorMessage)
                {
                    errors.reject(ERROR_MSG, errorMessage);
                    return new ApiSimpleResponse("success", false);
                }
            }

            setCustomizeMenuForm(form, getContainer(),  getUser());
            return new ApiSimpleResponse("success", true);
        }
    }

    protected static final String CUSTOMMENU_SCHEMA = "customMenuSchemaName";
    protected static final String CUSTOMMENU_QUERY = "customMenuQueryName";
    protected static final String CUSTOMMENU_VIEW = "customMenuViewName";
    protected static final String CUSTOMMENU_COLUMN = "customMenuColumnName";
    protected static final String CUSTOMMENU_FOLDER = "customMenuFolderName";
    protected static final String CUSTOMMENU_TITLE = "customMenuTitle";
    protected static final String CUSTOMMENU_URL = "customMenuUrl";
    protected static final String CUSTOMMENU_ROOTFOLDER = "customMenuRootFolder";
    protected static final String CUSTOMMENU_FOLDERTYPES = "customMenuFolderTypes";
    protected static final String CUSTOMMENU_CHOICELISTQUERY = "customMenuChoiceListQuery";
    protected static final String CUSTOMMENU_INCLUDEALLDESCENDANTS = "customIncludeAllDescendants";
    protected static final String CUSTOMMENU_CURRENTPROJECTONLY = "customCurrentProjectOnly";

    public static CustomizeMenuForm getCustomizeMenuForm(Portal.WebPart webPart)
    {
        CustomizeMenuForm form = new CustomizeMenuForm();
        Map<String, String> menuProps = webPart.getPropertyMap();

        String schemaName = menuProps.get(CUSTOMMENU_SCHEMA);
        String queryName = menuProps.get(CUSTOMMENU_QUERY);
        String columnName = menuProps.get(CUSTOMMENU_COLUMN);
        String viewName = menuProps.get(CUSTOMMENU_VIEW);
        String folderName = menuProps.get(CUSTOMMENU_FOLDER);
        String title = menuProps.get(CUSTOMMENU_TITLE); if (null == title) title = "My Menu";
        String urlBottom = menuProps.get(CUSTOMMENU_URL);
        String rootFolder = menuProps.get(CUSTOMMENU_ROOTFOLDER);
        String folderTypes = menuProps.get(CUSTOMMENU_FOLDERTYPES);
        String choiceListQueryString = menuProps.get(CUSTOMMENU_CHOICELISTQUERY);
        boolean choiceListQuery = null == choiceListQueryString || choiceListQueryString.equalsIgnoreCase("true");
        String includeAllDescendantsString = menuProps.get(CUSTOMMENU_INCLUDEALLDESCENDANTS);
        boolean includeAllDescendants = null == includeAllDescendantsString || includeAllDescendantsString.equalsIgnoreCase("true");
        String currentProjectOnlyString = menuProps.get(CUSTOMMENU_CURRENTPROJECTONLY);
        boolean currentProjectOnly = null != currentProjectOnlyString && currentProjectOnlyString.equalsIgnoreCase("true");

        form.setSchemaName(schemaName);
        form.setQueryName(queryName);
        form.setColumnName(columnName);
        form.setViewName(viewName);
        form.setFolderName(folderName);
        form.setTitle(title);
        form.setUrl(urlBottom);
        form.setRootFolder(rootFolder);
        form.setFolderTypes(folderTypes);
        form.setChoiceListQuery(choiceListQuery);
        form.setIncludeAllDescendants(includeAllDescendants);
        form.setCurrentProjectOnly(currentProjectOnly);

        form.setWebPartIndex(webPart.getIndex());
        form.setPageId(webPart.getPageId());
        return form;
    }

    private static void setCustomizeMenuForm(CustomizeMenuForm form, Container container, User user)
    {
        Portal.WebPart webPart = Portal.getPart(container, form.getPageId(), form.getWebPartIndex());
        Map<String, String> menuProps = webPart.getPropertyMap();

        menuProps.put(CUSTOMMENU_SCHEMA, form.getSchemaName());
        menuProps.put(CUSTOMMENU_QUERY, form.getQueryName());
        menuProps.put(CUSTOMMENU_COLUMN, form.getColumnName());
        menuProps.put(CUSTOMMENU_VIEW, form.getViewName());
        menuProps.put(CUSTOMMENU_FOLDER, form.getFolderName());
        menuProps.put(CUSTOMMENU_TITLE, form.getTitle());
        menuProps.put(CUSTOMMENU_URL, form.getUrl());

        // If root folder not specified, set as current container
        menuProps.put(CUSTOMMENU_ROOTFOLDER, StringUtils.trimToNull(form.getRootFolder()) != null ? form.getRootFolder() : container.getPath());
        menuProps.put(CUSTOMMENU_FOLDERTYPES, form.getFolderTypes());
        menuProps.put(CUSTOMMENU_CHOICELISTQUERY, form.isChoiceListQuery() ? "true" : "false");
        menuProps.put(CUSTOMMENU_INCLUDEALLDESCENDANTS, form.isIncludeAllDescendants() ? "true" : "false");
        menuProps.put(CUSTOMMENU_CURRENTPROJECTONLY, form.isCurrentProjectOnly() ? "true" : "false");

        Portal.updatePart(user, webPart);
    }

    @RequiresPermission(AdminPermission.class)
    public class AddTabAction extends ApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            Container tabContainer = getTabContainer(getContainer());
            if(tabContainer.getFolderType() == FolderType.NONE)
            {
                errors.reject(ERROR_MSG, "Cannot add tabs to custom folder types.");
            }
            else
            {
                String name = form.getTabName();
                if (StringUtils.isEmpty(name))
                {
                    errors.reject(ERROR_MSG, "A tab name must be specified.");
                    return;
                }

                // Note: The name, which shows up on the url, is trimmed to 50 characters. The caption, which is derived
                // from the name, and is editable, is allowed to be 64 characters, so we only error if passed something
                // longer than 64 characters.
                if (name.length() > 64)
                {
                    errors.reject(ERROR_MSG, "Tab name cannot be longer than 64 characters.");
                    return;
                }

                if (name.length() > 50)
                    name = name.substring(0, 50).trim();

                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
                CaseInsensitiveHashMap<FolderTab> folderTabMap = new CaseInsensitiveHashMap<>();

                for (FolderTab tab : tabContainer.getFolderType().getDefaultTabs())
                {
                    folderTabMap.put(tab.getName(), tab);
                }

                if (pages.containsKey(name))
                {
                    errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                    return;
                }

                for (Portal.PortalPage page : pages.values())
                {
                    if (page.getCaption() != null && page.getCaption().equals(name))
                    {
                        errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                        return;
                    }
                    else if (folderTabMap.containsKey(page.getPageId()))
                    {
                        if (folderTabMap.get(page.getPageId()).getCaption(getViewContext()).equalsIgnoreCase(name))
                        {
                            errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public ApiResponse execute(TabActionForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            validateCommand(form, errors);

            if(errors.hasErrors())
            {
                return response;
            }

            Container container = getTabContainer(getContainer());
            String name = form.getTabName();
            String caption = form.getTabName();

            // The name, which shows up on the url, is trimmed to 50 characters. The caption, which is derived from the
            // name, and is editable, is allowed to be 64 characters.
            if (name.length() > 50)
                name = name.substring(0, 50).trim();

            Portal.saveParts(container, name);
            Portal.addProperty(container, name, Portal.PROP_CUSTOMTAB);

            if (!name.equals(caption))
            {
                // If we had to truncate the name then we want to set the caption to the un-truncated version of the name.
                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(container, true));
                Portal.PortalPage page = pages.get(name);
                page.setCaption(caption);
                Portal.updatePortalPage(container, page);
            }

            ActionURL tabURL = new ActionURL(ProjectController.BeginAction.class, container);
            tabURL.addParameter("pageId", name);
            response.put("url", tabURL);
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ShowTabAction extends ApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(getTabContainer(getContainer()), true));

            if (form.getTabPageId() == null)
            {
                errors.reject(ERROR_MSG, "PageId cannot be blank.");
            }

            if (!pages.containsKey(form.getTabPageId()))
            {
                errors.reject(ERROR_MSG, "Page cannot be found. Check with your system administrator.");
            }
        }

        public ApiResponse execute(TabActionForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Container tabContainer = getTabContainer(getContainer());

            validateCommand(form, errors);
            if (errors.hasErrors())
                return response;

            Portal.showPage(tabContainer, form.getTabPageId());
            ActionURL tabURL = new ActionURL(ProjectController.BeginAction.class, tabContainer);
            tabURL.addParameter("pageId", form.getTabPageId());
            response.put("url", tabURL);
            response.put("success", true);
            return response;
        }
    }


    public static class TabActionForm extends ReturnUrlForm
    {
        // This class is used for tab related actions (add, rename, show, etc.)
        String _tabName;
        String _tabPageId;

        public String getTabName()
        {
            return _tabName;
        }

        public void setTabName(String name)
        {
            _tabName = name;
        }

        public String getTabPageId()
        {
            return _tabPageId;
        }

        public void setTabPageId(String tabPageId)
        {
            _tabPageId = tabPageId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MoveTabAction extends ApiAction<MoveTabForm>
    {
        @Override
        public ApiResponse execute(MoveTabForm form, BindException errors) throws Exception
        {
            final Map<String, Object> properties = new HashMap<>();
            Container tabContainer = getTabContainer(getContainer());
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
            Portal.PortalPage tab = pages.get(form.getPageId());

            if (null != tab)
            {
                int oldIndex = tab.getIndex();
                Portal.PortalPage pageToSwap = handleMovePortalPage(tabContainer, tab, form.getDirection());

                if (null != pageToSwap)
                {
                    properties.put("oldIndex", oldIndex);
                    properties.put("newIndex", tab.getIndex());
                    properties.put("pageId", tab.getPageId());
                    properties.put("pageIdToSwap", pageToSwap.getPageId());
                }
                else
                {
                    properties.put("error", "Unable to move tab.");
                }
            }
            else
            {
                properties.put("error", "Requested tab does not exist.");
            }

            return new ApiSimpleResponse(properties);
        }
    }

    public static class MoveTabForm implements HasViewContext
    {
        private int _direction;
        private String _pageId;
        private ViewContext _viewContext;

        public int getDirection()
        {
            // 0 moves left, 1 moves right.
            return _direction;
        }

        public void setDirection(int direction)
        {
            _direction = direction;
        }

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }
    }

    private Portal.PortalPage handleMovePortalPage(Container c, Portal.PortalPage page, int direction)
    {
        List<Portal.PortalPage> pagesList = Portal.getTabPages(c, true);

        int visibleIndex;
        for (visibleIndex = 0; visibleIndex < pagesList.size(); visibleIndex++)
        {
            if (pagesList.get(visibleIndex).getIndex() == page.getIndex())
            {
                break;
            }
        }

        if (visibleIndex == pagesList.size())
        {
            return null;
        }

        if (direction == Portal.MOVE_DOWN)
        {
            if (visibleIndex == pagesList.size() - 1)
            {
                return page;
            }

            Portal.PortalPage nextPage = pagesList.get(visibleIndex + 1);

            if (null == nextPage)
                return null;
            Portal.swapPageIndexes(c, page, nextPage);
            return nextPage;
        }
        else
        {
            if (visibleIndex < 1)
            {
                return page;
            }

            Portal.PortalPage prevPage = pagesList.get(visibleIndex - 1);

            if (null == prevPage)
                return null;
            Portal.swapPageIndexes(c, page, prevPage);
            return prevPage;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RenameTabAction extends ApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            Container tabContainer = getTabContainer(getContainer());

            if (tabContainer.getFolderType() == FolderType.NONE)
            {
                errors.reject(ERROR_MSG, "Cannot change tab names in custom folder types.");
            }
            else
            {
                String name = form.getTabName();
                if (StringUtils.isEmpty(name))
                {
                    errors.reject(ERROR_MSG, "A tab name must be specified.");
                    return;
                }

                if (name.length() > 64)
                {
                    errors.reject(ERROR_MSG, "Tab name cannot be longer than 64 characters.");
                    return;
                }

                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
                Portal.PortalPage pageToChange = pages.get(form.getTabPageId());
                if (null == pageToChange)
                {
                    errors.reject(ERROR_MSG, "Page cannot be found. Check with your system administrator.");
                    return;
                }

                for (Portal.PortalPage page : pages.values())
                {
                    if (!page.equals(pageToChange))
                    {
                        if (null != page.getCaption() && page.getCaption().equalsIgnoreCase(name))
                        {
                            errors.reject(ERROR_MSG, "A tab with the same name already exists in this folder.");
                            return;
                        }
                        if (page.getPageId().equalsIgnoreCase(name))
                        {
                            if (null != page.getCaption() || "portal.default".equalsIgnoreCase(name))
                                errors.reject(ERROR_MSG, "You cannot change a tab's name to another tab's original name even if the original name is not visible.");
                            else
                                errors.reject(ERROR_MSG, "A tab with the same name already exists in this folder.");
                            return;
                        }
                    }
                }

                List<FolderTab> folderTabs = tabContainer.getFolderType().getDefaultTabs();
                for (FolderTab folderTab : folderTabs)
                {
                    String folderTabCaption = folderTab.getCaption(getViewContext());
                    if (!folderTab.getName().equalsIgnoreCase(pageToChange.getPageId()) && null != folderTabCaption && folderTabCaption.equalsIgnoreCase(name))
                    {
                        errors.reject(ERROR_MSG, "You cannot change a tab's name to another tab's original name even if the original name is not visible.");
                        return;
                    }
                }
            }
        }

        public ApiResponse execute(TabActionForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            validateCommand(form, errors);

            if (errors.hasErrors())
            {
                return response;
            }

            Container container = getTabContainer(getContainer());
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(container, true));
            Portal.PortalPage page = pages.get(form.getTabPageId());
            page.setCaption(form.getTabName());
            // Update the page the caption is saved.
            Portal.updatePortalPage(container, page);

            response.put("success", true);
            return response;
        }
    }

    private Container getTabContainer(Container c)
    {
        if (c.isContainerTab() || c.isWorkbook())
            return c.getParent();
        else
            return c;
    }

    @RequiresPermission(ReadPermission.class)
    public class ClearDeletedTabFoldersAction extends ApiAction<DeletedFoldersForm>
    {
        @Override
        public ApiResponse execute(DeletedFoldersForm form, BindException errors) throws Exception
        {
            Container container = ContainerManager.getForPath(form.getContainerPath());
            for (String tabName : form.getResurrectFolders())
            {
                ContainerManager.clearContainerTabDeleted(container, tabName, form.getNewFolderType());
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    public static class DeletedFoldersForm
    {
        private String _containerPath;
        private String _newFolderType;
        private List<String> _resurrectFolders;

        public List<String> getResurrectFolders()
        {
            return _resurrectFolders;
        }

        public void setResurrectFolders(List<String> resurrectFolders)
        {
            _resurrectFolders = resurrectFolders;
        }

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }

        public String getNewFolderType()
        {
            return _newFolderType;
        }

        public void setNewFolderType(String newFolderType)
        {
            _newFolderType = newFolderType;
        }
    }

    public static class ShortURLForm
    {
        private String _shortURL;
        private String _fullURL;
        private boolean _delete;

        private List<ShortURLRecord> _savedShortURLs;

        public void setShortURL(String shortURL)
        {
            _shortURL = shortURL;
        }

        public void setFullURL(String fullURL)
        {
            _fullURL = fullURL;
        }

        public void setDelete(boolean delete)
        {
            _delete = delete;
        }

        public String getShortURL()
        {
            return _shortURL;
        }

        public String getFullURL()
        {
            return _fullURL;
        }

        public boolean isDelete()
        {
            return _delete;
        }

        public List<ShortURLRecord> getSavedShortURLs()
        {
            return _savedShortURLs;
        }

        public void setSavedShortURLs(List<ShortURLRecord> savedShortURLs)
        {
            _savedShortURLs = savedShortURLs;
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class ShortURLAdminAction extends FormViewAction<ShortURLForm>
    {
        @Override
        public void validateCommand(ShortURLForm target, Errors errors) {}

        @Override
        public ModelAndView getView(ShortURLForm form, boolean reshow, BindException errors) throws Exception
        {
            form.setSavedShortURLs(ServiceRegistry.get(ShortURLService.class).getAllShortURLs());
            JspView<ShortURLForm> newView = new JspView<>("/org/labkey/core/admin/createNewShortURL.jsp", form, errors);
            newView.setTitle("Create New Short URL");
            newView.setFrame(WebPartView.FrameType.PORTAL);
            JspView<ShortURLForm> existingView = new JspView<>("/org/labkey/core/admin/existingShortURLs.jsp", form, errors);
            existingView.setTitle("Existing Short URLs");
            existingView.setFrame(WebPartView.FrameType.PORTAL);

            return new VBox(newView, existingView);
        }

        @Override
        public boolean handlePost(ShortURLForm form, BindException errors) throws Exception
        {
            String shortURL = StringUtils.trimToEmpty(form.getShortURL());
            if (StringUtils.isEmpty(shortURL))
            {
                errors.addError(new LabKeyError("Short URL must not be blank"));
            }
            if (shortURL.endsWith(".url"))
                shortURL = shortURL.substring(0,shortURL.length()-".url".length());
            if (shortURL.contains("#") || shortURL.contains("/") || shortURL.contains("."))
            {
                errors.addError(new LabKeyError("Short URLs may not contain '#' or '/' or '.'"));
            }
            URLHelper fullURL = null;
            if (!form.isDelete())
            {
                String trimmedFullURL = StringUtils.trimToNull(form.getFullURL());
                if (trimmedFullURL == null)
                {
                    errors.addError(new LabKeyError("Target URL must not be blank"));
                }
                else
                {
                    try
                    {
                        fullURL = new URLHelper(trimmedFullURL);
                    }
                    catch (URISyntaxException e)
                    {
                        errors.addError(new LabKeyError("Invalid Target URL. " + e.getMessage()));
                    }
                }
            }
            if (errors.getErrorCount() > 0)
            {
                return false;
            }

            ShortURLService service = ServiceRegistry.get(ShortURLService.class);
            if (form.isDelete())
            {
                ShortURLRecord shortURLRecord = service.resolveShortURL(shortURL);
                if (shortURLRecord == null)
                {
                    throw new NotFoundException("No such short URL: " + shortURL);
                }
                try
                {
                    service.deleteShortURL(shortURLRecord, getUser());
                }
                catch (ValidationException e)
                {
                    errors.addError(new LabKeyError("Error deleting short URL:"));
                    for(ValidationError error: e.getErrors())
                    {
                        errors.addError(new LabKeyError(error.getMessage()));
                    }
                }

                if (errors.getErrorCount() > 0)
                {
                    return false;
                }
            }
            else
            {
                ShortURLRecord shortURLRecord = service.saveShortURL(shortURL, fullURL, getUser());
                MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortURLRecord));
                // Add a role assignment to let another group manage the URL. This grants permission to the journal
                // to change where the URL redirects you to after they copy the data
//                policy.addRoleAssignment(org.labkey.api.security.SecurityManager.getGroupId(c, "SomeGroup"));
                SecurityPolicyManager.savePolicy(policy);
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ShortURLForm form)
        {
            return new ActionURL(ShortURLAdminAction.class, getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("shortURL");
            return root.addChild("Short URL Admin");
        }
    }

    // API for reporting client-side exceptions.
    // UNDONE: Throttle by IP to avoid DOS from buggy clients.
    @Marshal(Marshaller.Jackson)
    @SuppressWarnings("UnusedDeclaration")
    @RequiresNoPermission
    public class LogClientExceptionAction extends MutatingApiAction<ExceptionForm>
    {
        @Override
        public Object execute(ExceptionForm form, BindException errors) throws Exception
        {
            if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP))
            {
                ExceptionUtil.logClientExceptionToMothership(
                        form.getStackTrace(),
                        form.getExceptionMessage(),
                        form.getBrowser(),
                        null,
                        form.getRequestURL(),
                        form.getReferrerURL(),
                        form.getUsername()
                );
            }
            else if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER))
            {
                LOG.error("Client exception detected:\n" +
                        form.getRequestURL() + "\n" +
                        form.getReferrerURL() + "\n" +
                        form.getBrowser() + "\n" +
                        form.getUsername() + "\n" +
                        form.getStackTrace()
                );
            }

            return null;
        }
    }

    public static class ExceptionForm
    {
        private String _exceptionMessage;
        private String _stackTrace;
        private String _requestURL;
        private String _browser;
        private String _username;
        private String _referrerURL;
        private String _file;
        private String _line;
        private String _platform;

        public String getExceptionMessage()
        {
            return _exceptionMessage;
        }

        public void setExceptionMessage(String exceptionMessage)
        {
            _exceptionMessage = exceptionMessage;
        }

        public String getUsername()
        {
            return _username;
        }

        public void setUsername(String username)
        {
            _username = username;
        }

        public String getStackTrace()
        {
            return _stackTrace;
        }

        public void setStackTrace(String stackTrace)
        {
            _stackTrace = stackTrace;
        }

        public String getRequestURL()
        {
            return _requestURL;
        }

        public void setRequestURL(String requestURL)
        {
            _requestURL = requestURL;
        }

        public String getBrowser()
        {
            return _browser;
        }

        public void setBrowser(String browser)
        {
            _browser = browser;
        }

        public String getReferrerURL()
        {
            return _referrerURL;
        }

        public void setReferrerURL(String referrerURL)
        {
            _referrerURL = referrerURL;
        }

        public String getFile()
        {
            return _file;
        }

        public void setFile(String file)
        {
            _file = file;
        }

        public String getLine()
        {
            return _line;
        }

        public void setLine(String line)
        {
            _line = line;
        }

        public String getPlatform()
        {
            return _platform;
        }

        public void setPlatform(String platform)
        {
            _platform = platform;
        }
    }


    /** generate URLS to seed web-site scanner */
    @SuppressWarnings("UnusedDeclaration")
    @RequiresSiteAdmin
    public static class SpiderAction extends SimpleViewAction
    {
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<String> urls = new ArrayList<>(1000);

            if (getContainer().equals(ContainerManager.getRoot()))
            {
                for (Container c : ContainerManager.getAllChildren(ContainerManager.getRoot()))
                {
                    urls.add(c.getStartURL(getUser()).toString());
                    urls.add(new ActionURL(SpiderAction.class, c).toString());
                }

                Container home = ContainerManager.getHomeContainer();
                for (ActionDescriptor d : SpringActionController.getRegisteredActionDescriptors())
                {
                    ActionURL url = new ActionURL(d.getControllerName(), d.getPrimaryName(), home);
                    urls.add(url.toString());
                }
            }
            else
            {
                DefaultSchema def = DefaultSchema.get(getUser(), getContainer());
                def.getSchemaNames().forEach(name ->
                {
                    QuerySchema q = def.getSchema(name);
                    if (null == q)
                        return;
                    Collection<TableInfo> tables = q.getTables();
                    if (null == tables)
                        return;
                    tables.forEach(t ->
                    {
                        ActionURL grid = t.getGridURL(getContainer());
                        if (null != grid)
                            urls.add(grid.toString());
                        else
                            urls.add(new ActionURL("query", "executeQuery.view", getContainer())
                                    .addParameter("schemaName", q.getSchemaName())
                                    .addParameter("query.queryName", t.getName())
                                    .toString());
                    });
                });

                ModuleLoader.getInstance().getModules().forEach(m ->
                {
                    ActionURL url = m.getTabURL(getContainer(), getUser());
                    if (null != url)
                        urls.add(url.toString());
                });
            }

            StringBuilder sb = new StringBuilder();
            urls.forEach(url ->
            {
                sb.append("<a href=\"").append(PageFlowUtil.filter(url)).append("\">")
                        .append(PageFlowUtil.filter(url)).append("</a><br>\n");
            });
            return new HtmlView(sb.toString());
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @RequiresPermission(AdminReadPermission.class)
    public class TestMothershipReportAction extends ApiAction<MothershipReportSelectionForm>
    {
        @Override
        public Object execute(MothershipReportSelectionForm form, BindException errors) throws Exception
        {
            MothershipReport report;
            if (MothershipReport.Type.CheckForUpdates.toString().equals(form.getType()))
            {
                report = UsageReportingLevel.generateReport(UsageReportingLevel.valueOf(form.getLevel()), true);
            }
            else
            {
                report = ExceptionUtil.createReportFromThrowable(getViewContext().getRequest(),
                        new SQLException("Intentional exception for testing purposes", "400"),
                        (String)getViewContext().getRequest().getAttribute(ViewServlet.ORIGINAL_URL_STRING),
                        true,
                        ExceptionReportingLevel.valueOf(form.getLevel()), null);
            }
            if (null != report && form.isSubmit())
            {
                report.run();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            if (null != report)
            {
                result.put("report", report.getParams());
                if (null != report.getContent())
                    result.put("upgradeMessage", report.getContent());
            }
            return new ObjectMapper().writeValueAsString(result);
        }
    }


    public static class ValueForm
    {
        private String _value;

        public String getValue()
        {
            return _value;
        }

        public void setValue(String value)
        {
            _value = value;
        }
    }


    @RequiresSiteAdmin
    static public class ToggleCSRFAction extends MutatingApiAction<ValueForm>
    {
        @Override
        public void validateForm(ValueForm valueForm, Errors errors)
        {
            if (!Arrays.asList("POST","ADMINONLY").contains(valueForm.getValue()))
                errors.reject(ERROR_MSG,"value should be POST or ADMINONLY");
        }

        @Override
        public Object execute(ValueForm valueForm, BindException errors) throws Exception
        {
            if (!AppProps.getInstance().isDevMode())
                throw new UnsupportedOperationException("only allowed in dev mode");
            String old = AppProps.getInstance().getCSRFCheck();
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setCSRFCheck(valueForm.getValue());
            props.save(getUser());
            JSONObject ret = new JSONObject();
            ret.put("success",true);
            ret.put("previousValue", old);
            ret.put("currentValue",  AppProps.getInstance().getCSRFCheck());
            return ret;
        }
    }


    static class MothershipReportSelectionForm
    {
        private String _type = MothershipReport.Type.CheckForUpdates.toString();
        private String _level = UsageReportingLevel.MEDIUM.toString();
        private boolean _submit = false;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = StringUtils.upperCase(level);
        }

        public boolean isSubmit()
        {
            return _submit;
        }

        public void setSubmit(boolean submit)
        {
            _submit = submit;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            AdminController controller = new AdminController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new GetModulesAction(),
                controller.new ClearDeletedTabFoldersAction(),
                controller.new SetAdminModeAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new ResetLogoAction(),
                controller.new ResetPropertiesAction(),
                controller.new ResetFaviconAction(),
                controller.new DeleteCustomStylesheetAction(),
                controller.new SiteValidationAction(),
                controller.new ResetQueryStatisticsAction(),
                controller.new DefineWebThemesAction(),
                controller.new SaveWebThemeAction(),
                controller.new DeleteWebThemeAction(),
                controller.new FolderAliasesAction(),
                controller.new CustomizeEmailAction(),
                controller.new DeleteCustomEmailAction(),
                controller.new RenameFolderAction(),
                controller.new ShowMoveFolderTreeAction(),
                controller.new MoveFolderAction(),
                controller.new ConfirmProjectMoveAction(),
                controller.new CreateFolderAction(),
                controller.new SetFolderPermissionsAction(),
                controller.new SetInitialFolderSettingsAction(),
                controller.new DeleteFolderAction(),
                controller.new ReorderFoldersAction(),
                controller.new ReorderFoldersApiAction(),
                controller.new RevertFolderAction(),
                controller.new CustomizeMenuAction(),
                controller.new AddTabAction(),
                controller.new ShowTabAction(),
                controller.new MoveTabAction(),
                controller.new RenameTabAction()
            );

            //TODO @RequiresPermission(AdminReadPermission.class)
            //controller.new TestMothershipReportAction()

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(),user,
                controller.new EmailTestAction(),
                controller.new ShowNetworkDriveTestAction(),
                controller.new DbCheckerAction(),
                controller.new DoCheckAction(),
                controller.new GetSchemaXmlDocAction(),
                controller.new RecreateViewsAction(),
                controller.new ValidateDomainsAction(),
                controller.new DeleteModuleAction(),
                controller.new ExperimentalFeatureAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                controller.new ShowAdminAction(),
                controller.new ShowThreadsAction(),
                controller.new DumpHeapAction(),
                controller.new ResetErrorMarkAction(),
                controller.new ShowErrorsSinceMarkAction(),
                controller.new ShowAllErrorsAction(),
                controller.new ShowPrimaryLogAction(),
                controller.new ActionsAction(),
                controller.new ExportActionsAction(),
                controller.new QueriesAction(),
                controller.new QueryStackTracesAction(),
                controller.new ExecutionPlanAction(),
                controller.new ExportQueriesAction(),
                controller.new MemTrackerAction(),
                controller.new MemoryChartAction(),
                controller.new FolderTypesAction(),
                controller.new ShortURLAdminAction(),
                controller.new CustomizeSiteAction(),
                controller.new CachesAction(),
                controller.new EnvironmentVariablesAction(),
                controller.new SystemPropertiesAction(),
                controller.new ConfigureSystemMaintenanceAction(),
                controller.new ModulesAction()
            );

            // @AdminConsoleAction
            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                controller.new ExperimentalFeaturesAction()
            );

            // @RequiresSiteAdmin
            assertForRequiresSiteAdmin(user,
                controller.new ShowModuleErrors(),
                controller.new GetPendingRequestCountAction(),
                controller.new SystemMaintenanceAction(),
                controller.new ModuleStatusAction(),
                controller.new NewInstallSiteSettingsAction(),
                controller.new InstallCompleteAction()
            );
        }
    }
}
