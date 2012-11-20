/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
import org.apache.commons.lang3.time.DateUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasValidator;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.StatusReportingRunnableAction;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporterImpl;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheStats;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.Container.ContainerException;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.ContainerParent;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.QueryProfiler;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableXmlUtils;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.files.FileContentService;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.ms2.MS2Service;
import org.labkey.api.ms2.SearchClient;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.SettingsLinkType;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.BreakpointThread;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Formats;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionAppender;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.SystemMaintenance.SystemMaintenanceProperties;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig.Template;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.portal.ProjectController;
import org.labkey.data.xml.TablesDocument;
import org.labkey.folder.xml.FolderDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.Introspector;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Feb 27, 2008
 */
public class AdminController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
            AdminController.class,
            FilesSiteSettingsAction.class,
            ProjectSettingsAction.class,
            FolderManagementAction.class);
    private static final NumberFormat _formatInteger = DecimalFormat.getIntegerInstance();

    private static final Logger LOG = Logger.getLogger(AdminController.class);
    private static final Logger CLIENT_LOG = Logger.getLogger(LogAction.class);

    private static long _errorMark = 0;

    public static void registerAdminConsoleLinks()
    {
        Container root = ContainerManager.getRoot();

        // Configuration
        AdminConsole.addLink(SettingsLinkType.Configuration, "site settings", new AdminUrlsImpl().getCustomizeSiteURL());
        AdminConsole.addLink(SettingsLinkType.Configuration, "system maintenance", new ActionURL(ConfigureSystemMaintenanceAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "look and feel settings", new AdminUrlsImpl().getProjectSettingsURL(root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "authentication", PageFlowUtil.urlProvider(LoginUrls.class).getConfigureURL());
        AdminConsole.addLink(SettingsLinkType.Configuration, "email customization", new ActionURL(CustomizeEmailAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "project display order", new ActionURL(ReorderFoldersAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "missing value indicators", new ActionURL(FolderManagementAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "files", new ActionURL(FilesSiteSettingsAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Configuration, "experimental features", new ActionURL(ExperimentalFeaturesAction.class, root));

        // Diagnostics
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "running threads", new ActionURL(ShowThreadsAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "memory usage", new ActionURL(MemTrackerAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "dump heap", new ActionURL(DumpHeapAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "environment variables", new ActionURL(EnvironmentVariablesAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "system properties", new ActionURL(SystemPropertiesAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "actions", new ActionURL(ActionsAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "queries", getQueriesURL(null));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "caches", new ActionURL(CachesAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "sql scripts", new ActionURL(SqlScriptController.ScriptsAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "view all site errors", new ActionURL(ShowAllErrorsAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "view all site errors since reset", new ActionURL(ShowErrorsSinceMarkAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "view primary site log file", new ActionURL(ShowPrimaryLogAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "reset site errors", new ActionURL(ResetErrorMarkAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "check database", new ActionURL(DbCheckerAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "test email configuration", new ActionURL(EmailTestAction.class, root));
        AdminConsole.addLink(SettingsLinkType.Diagnostics, "credits", new ActionURL(CreditsAction.class, root));
    }

    public AdminController()
    {
        setActionResolver(_actionResolver);
    }


    @RequiresNoPermission
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
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
        if (null == action)
            root.addChild("Admin Console", getShowAdminURL()).addChild(childTitle);
        else
            root.addChild("Admin Console", getShowAdminURL()).addChild(childTitle, new ActionURL(action, container));

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
            AdminBean bean = new AdminBean(getUser());
            return new JspView<AdminBean>("/org/labkey/core/admin/admin.jsp", bean);
        }

        public NavTree appendNavTrail(NavTree root)
        {
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
            modules = new ArrayList<Module>(ModuleLoader.getInstance().getModules());
            Collections.sort(modules, new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
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
        public ActionURL getModuleErrorsURL(Container container)
        {
            return new ActionURL(ShowModuleErrors.class, container);
        }

        public ActionURL getAdminConsoleURL()
        {
            return getShowAdminURL();
        }

        public ActionURL getModuleStatusURL()
        {
            return AdminController.getModuleStatusURL();
        }

        public ActionURL getCustomizeSiteURL()
        {
            return new ActionURL(CustomizeSiteAction.class, ContainerManager.getRoot());
        }

        public ActionURL getCustomizeSiteURL(boolean upgradeInProgress)
        {
            ActionURL url = getCustomizeSiteURL();

            if (upgradeInProgress)
                url.addParameter("upgradeInProgress", "1");

            return url;
        }

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

        public ActionURL getProjectSettingsMenuURL(Container c)
        {
            ActionURL url = getProjectSettingsURL(c);
            url.addParameter("tabId", "menubar");
            return url;
        }

        public ActionURL getProjectSettingsFileURL(Container c)
        {
            ActionURL url = getProjectSettingsURL(c);
            url.addParameter("tabId", "files");
            return url;
        }

        public ActionURL getCustomizeEmailURL(Container c, Class<? extends EmailTemplate> selectedTemplate, URLHelper returnURL)
        {
            return getCustomizeEmailURL(c, selectedTemplate == null ? null : selectedTemplate.getName(), returnURL);
        }

        public ActionURL getCustomizeEmailURL(Container c, String selectedTemplate, URLHelper returnURL)
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

        public ActionURL getMaintenanceURL()
        {
            return new ActionURL(MaintenanceAction.class, ContainerManager.getRoot());
        }

        public ActionURL getManageFoldersURL(Container c)
        {
            ActionURL url = new ActionURL(FolderManagementAction.class, c);
            url.addParameter("tabId", "folderTree");
            return url;
        }

        public ActionURL getExportFolderURL(Container c)
        {
            ActionURL url = new ActionURL(FolderManagementAction.class, c);
            url.addParameter("tabId", "export");
            return url;
        }

        public ActionURL getImportFolderURL(Container c)
        {
            ActionURL url = new ActionURL(FolderManagementAction.class, c);
            url.addParameter("tabId", "import");
            return url;
        }

        public ActionURL getCreateProjectURL()
        {
            return new ActionURL(CreateFolderAction.class, ContainerManager.getRoot());
        }

        public ActionURL getSetFolderPermissionsURL(Container c)
        {
            return new ActionURL(SetFolderPermissionsAction.class, c);
        }

        public NavTree appendAdminNavTrail(NavTree root, String childTitle, @Nullable ActionURL childURL)
        {
            root.addChild("Admin Console", getAdminConsoleURL());

            if (null != childURL)
                root.addChild(childTitle, childURL);
            else
                root.addChild(childTitle);

            return root;
        }

        public ActionURL getFolderManagementURL(Container c)
        {
            return new ActionURL(FolderManagementAction.class, c);
        }

        public ActionURL getInitialFolderSettingsURL(Container c)
        {
            return new ActionURL(SetInitialFolderSettingsAction.class, c);
        }

        public ActionURL getMemTrackerURL()
        {
            return new ActionURL(MemTrackerAction.class, ContainerManager.getRoot());
        }

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

        public ActionURL getAddTabURL(Container c, @Nullable URLHelper returnURL)
        {
            if (c.isContainerTab())
                c = c.getParent();          // If we're in a container tab container, context should be the parent
            ActionURL url = new ActionURL(AddTabAction.class, c);

            if (returnURL != null)
            {
                url.addParameter(ActionURL.Param.returnUrl, returnURL.toString());
            }
            return url;
        }
    }


    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class MaintenanceAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!getUser().isAdministrator())
            {
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            getPageConfig().setTemplate(Template.Dialog);
            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            String content = "This site is currently undergoing maintenance.";
            if (null != wikiService)
            {
                content =  wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage());
            }
            return new HtmlView("The site is currently undergoing maintenance", content);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetModulesAction extends ApiAction<GetModulesForm>
    {
        public ApiResponse execute(GetModulesForm form, BindException errors) throws Exception
        {
            Container c = ContainerManager.getForPath(getContainer().getPath());

            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Map<String, Object>> qinfos = new ArrayList<Map<String, Object>>();

            FolderType folderType = c.getFolderType();
            List<Module> allModules = new ArrayList<Module>(ModuleLoader.getInstance().getModules());
            Collections.sort(allModules, new Comparator<Module>()
            {
                public int compare(Module o1, Module o2)
                {
                    return o1.getTabName(getViewContext()).compareToIgnoreCase(o2.getTabName(getViewContext()));
                }
            });

            //note: this has been altered to use Container.getRequiredModules() instead of FolderType
            //this is b/c a parent container must consider child workbooks when determining the set of requiredModules
            Set<Module> requiredModules = c.getRequiredModules(); //folderType.getActiveModules() != null ? folderType.getActiveModules() : new HashSet<Module>();
            Set<Module> activeModules = c.getActiveModules();

            for (Module m : allModules)
            {
                Map<String, Object> qinfo = new HashMap<String, Object>();

                qinfo.put("name", m.getName());
                qinfo.put("required", requiredModules.contains(m));
                qinfo.put("active", activeModules.contains(m) || requiredModules.contains(m));
                qinfo.put("enabled", (m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE ||
                    m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT) && !requiredModules.contains(m));
                qinfo.put("tabName", m.getTabName(getViewContext()));
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
            HtmlView v = getContainerInfoView(c);
            v.setTitle("Container details: " + StringUtils.defaultIfEmpty(c.getName(),"/"));
            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static HtmlView getContainerInfoView(Container c)
    {
        return new HtmlView(
            "<table>" +
            "<tr><td class='labkey-form-label'>Path</td><td>" + PageFlowUtil.filter(c.getPath()) + "</td></tr>" +
            "<tr><td class='labkey-form-label'>Name</td><td>" + c.getName() + "</td></tr>" +
            "<tr><td class='labkey-form-label'>EntityId</td><td>" + c.getId() + "</td></tr>" +
            "<tr><td class='labkey-form-label'>RowId</td><td>" + c.getRowId() + "</td></tr>" +
            "<tr><td class='labkey-form-label'>FolderType</td><td>" + c.getFolderType().getName() + "</td></tr>" +
            "</table>"
        );
    }


    @RequiresNoPermission
    public class GuidAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            response.getWriter().write(GUID.makeGUID());
        }
    }


    // No security checks... anyone (even guests) can view the credits page
    @RequiresNoPermission
    public class CreditsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String jarRegEx = "^([\\w-\\.]+\\.jar)\\|";
            Module core = ModuleLoader.getInstance().getCoreModule();

            HttpView jars = new CreditsView("/core/META-INF/core/jars.txt", getCreditsFile(core, "jars.txt"), getWebInfJars(true), "JAR", "webapp", null, jarRegEx);
            VBox views = new VBox(jars);

            List<Module> modules = ModuleLoader.getInstance().getModules();

            for (Module module : modules)
            {
                if (!module.equals(core))
                {
                    String wikiSource = getCreditsFile(module, "jars.txt");

                    if (null != wikiSource)
                    {
                        HttpView moduleJars = new CreditsView("jars.txt", wikiSource, module.getJarFilenames(), "JAR", "webapp", "the " + module.getName() + " Module", jarRegEx);
                        views.addView(moduleJars);
                    }
                }
            }

            views.addView(new CreditsView("/core/META-INF/core/common_jars.txt", getCreditsFile(core, "common_jars.txt"), getCommonJars(), "Common JAR", "/external/lib/common directory", null, jarRegEx));
            views.addView(new CreditsView("/core/META-INF/core/scripts.txt", getCreditsFile(core, "scripts.txt"), null, "JavaScript and Icons", null, null, null));

            for (Module module : modules)
            {
                if (!module.equals(core))
                {
                    String wikiSource = getCreditsFile(module, "scripts.txt");

                    if (null != wikiSource)
                    {
                        HttpView moduleJS = new CreditsView("scripts.txt", wikiSource, null, "JavaScript and Icons", null, "the " + module.getName() + " Module", null);
                        views.addView(moduleJS);
                    }
                }
            }

            views.addView(new CreditsView("/core/META-INF/core/source.txt", getCreditsFile(core, "source.txt"), null, "Java Source Code", null, null, null));
            views.addView(new CreditsView("/core/META-INF/core/executables.txt", getCreditsFile(core, "executables.txt"), getBinFilenames(), "Executable", "/external/bin directory", null, "([\\w\\.]+\\.(exe|dll|manifest|jar))"));

            for (Module module : modules)
            {
                if (!module.equals(core))
                {
                    String wikiSource = getCreditsFile(module, "executables.txt");

                    if (null != wikiSource)
                    {
                        HttpView moduleJS = new CreditsView("executables.txt", wikiSource, null, "Executable Files", null, "the " + module.getName() + " Module", null);
                        views.addView(moduleJS);
                    }
                }
            }

            views.addView(new CreditsView("/core/META-INF/core/installer.txt", getCreditsFile(core, "installer.txt"), null, "Executable", null, "the Graphical Windows Installer", null));

            return views;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Credits", this.getClass());
        }
    }


    private static class CreditsView extends WebPartView
    {
        private final String WIKI_LINE_SEP = "\r\n\r\n";
        private String _html;

        CreditsView(String creditsFilename, String wikiSource, Collection<String> filenames, String fileType, String foundWhere, String component, String wikiSourceSearchPattern) throws IOException
        {
            super();
            setTitle(fileType + " Files Distributed with " + (null == component ? "LabKey Core" : component));

            if (null != filenames)
                wikiSource = wikiSource + getErrors(wikiSource, creditsFilename, filenames, fileType, foundWhere, wikiSourceSearchPattern);

            WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
            if (null != wikiService)
            {
                String html = wikiService.getFormattedHtml(WikiRendererType.RADEOX, wikiSource);
                _html = "<style type=\"text/css\">\ntr.table-odd td { background-color: #EEEEEE; }</style>\n" + html;
            }
            else
                _html = "<p class='labkey-error'>NO WIKI SERVICE AVAILABLE!</p>";
        }


        private String getErrors(String wikiSource, String creditsFilename, Collection<String> filenames, String fileType, String foundWhere, String wikiSourceSearchPattern)
        {
            Set<String> documentedFilenames = new CaseInsensitiveTreeSet();
            Set<String> documentedFilenamesCopy = new HashSet<String>();

            Pattern p = Pattern.compile(wikiSourceSearchPattern, Pattern.MULTILINE);
            Matcher m = p.matcher(wikiSource);

            while(m.find())
            {
                String found = m.group(1);
                documentedFilenames.add(found);
            }

            documentedFilenamesCopy.addAll(documentedFilenames);
            documentedFilenames.removeAll(filenames);
            filenames.removeAll(documentedFilenamesCopy);
            for (String name : filenames.toArray(new String[filenames.size()]))
                if (name.startsWith(".")) filenames.remove(name);

            String undocumentedErrors = filenames.isEmpty() ? "" : WIKI_LINE_SEP + "**WARNING: The following " + fileType + " file" + (filenames.size() > 1 ? "s were" : " was") + " found in your " + foundWhere + " but "+ (filenames.size() > 1 ? "are" : " is") + " not documented in " + creditsFilename + ":**\\\\" + StringUtils.join(filenames.iterator(), "\\\\");
            String missingErrors = documentedFilenames.isEmpty() ? "" : WIKI_LINE_SEP + "**WARNING: The following " + fileType + " file" + (documentedFilenames.size() > 1 ? "s are" : " is") + " documented in " + creditsFilename + " but " + (documentedFilenames.size() > 1 ? " were" : " was") + " not found in your " + foundWhere + ":**\\\\" + StringUtils.join(documentedFilenames.iterator(), "\\\\");

            return undocumentedErrors + missingErrors;
        }


        @Override
        public void renderView(Object model, PrintWriter out) throws IOException, ServletException
        {
            out.print(_html);
        }
    }


    private static String getCreditsFile(Module module, String filename) throws IOException
    {
        InputStream is = module.getResourceStream("/META-INF/" + module.getName().toLowerCase() + "/" + filename);
        return null == is ? null : PageFlowUtil.getStreamContentsAsString(is);
    }


    private static final String _libPath = "/WEB-INF/lib/";

    private Set<String> getWebInfJars(boolean removeInternalJars)
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        //noinspection unchecked
        Set<String> resources = ViewServlet.getViewServletContext().getResourcePaths(_libPath);
        Set<String> filenames = new CaseInsensitiveTreeSet();
        Set<String> internalJars = removeInternalJars ? new CsvSet("api.jar,schemas.jar,internal.jar") : Collections.<String>emptySet();

        // Remove path prefix and copy to a modifiable collection
        for (String filename : resources)
        {
            String name = filename.substring(_libPath.length());

            if (DefaultModule.isRuntimeJar(name) && !internalJars.contains(name))
                filenames.add(name);
        }

        return filenames;
    }


    private Set<String> getCommonJars()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        File common = new File(AppProps.getInstance().getProjectRoot(), "external/lib/common");

        if (!common.exists())
            return null;

        Set<String> filenames = new CaseInsensitiveTreeSet();

        addAllChildren(common, filenames);

        return filenames;
    }


    private Set<String> getBinFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        File binRoot = new File(AppProps.getInstance().getProjectRoot(), "external/bin");

        if (!binRoot.exists())
            return null;

        Set<String> filenames = new CaseInsensitiveTreeSet();

        addAllChildren(binRoot, filenames);

        return filenames;
    }


    private static FileFilter _fileFilter = new FileFilter() {
        public boolean accept(File f)
        {
            return !f.isDirectory();
        }
    };

    private static FileFilter _dirFilter = new FileFilter() {
        public boolean accept(File f)
        {
            return f.isDirectory() && !".svn".equals(f.getName());
        }
    };

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

    @RequiresPermissionClass(AdminPermission.class)
    public class ResetLogoAction extends SimpleRedirectAction
    {
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            if (getContainer().isRoot() && !getUser().isAdministrator())
                throw new UnauthorizedException();
        }

        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingLogo(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ResetPropertiesAction extends SimpleRedirectAction
    {
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            if (getContainer().isRoot() && !getUser().isAdministrator())
                throw new UnauthorizedException();
        }

        public ActionURL getRedirectURL(Object o) throws Exception
        {
            WriteableLookAndFeelProperties props = LookAndFeelProperties.getWriteableInstance(getContainer());
            props.clear();
            props.save();
            // TODO: Audit log?

            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return new AdminUrlsImpl().getProjectSettingsURL(getContainer());
        }
    }


    static void deleteExistingLogo(Container c, User user) throws SQLException
    {
        ContainerParent parent = new ContainerParent(c);
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


    @RequiresPermissionClass(AdminPermission.class)
    public class ResetFaviconAction extends SimpleRedirectAction
    {
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            if (getContainer().isRoot() && !getUser().isAdministrator())
                throw new UnauthorizedException();
        }

        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingFavicon(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    static void deleteExistingFavicon(Container c, User user) throws SQLException
    {
        ContainerParent parent = new ContainerParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.FAVICON_FILE_NAME, user);
        AttachmentCache.clearFavIconCache();
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteCustomStylesheetAction extends SimpleRedirectAction
    {
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();

            if (getContainer().isRoot() && !getUser().isAdministrator())
                throw new UnauthorizedException();
        }

        public ActionURL getRedirectURL(Object o) throws Exception
        {
            deleteExistingCustomStylesheet(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }


    static void deleteExistingCustomStylesheet(Container c, User user) throws SQLException
    {
        ContainerParent parent = new ContainerParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.STYLESHEET_FILE_NAME, user);

        // This custom stylesheet is still cached in CoreController, but look & feel revision checking should ensure
        // that it gets cleared out on the next request.
    }


    @AdminConsoleAction @CSRF
    public class CustomizeSiteAction extends FormViewAction<SiteSettingsForm>
    {
        public ModelAndView getView(SiteSettingsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.isUpgradeInProgress())
                getPageConfig().setTemplate(Template.Dialog);

            SiteSettingsBean bean = new SiteSettingsBean(form.isUpgradeInProgress(), form.isTestInPage());
            setHelpTopic("configAdmin");
            getPageConfig().setFocusId("defaultDomain");
            return new JspView<SiteSettingsBean>("/org/labkey/core/admin/customizeSite.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Customize Site", this.getClass());
        }

        public void validateCommand(SiteSettingsForm target, Errors errors)
        {
        }

        public boolean handlePost(SiteSettingsForm form, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().setDeferUsageReport(false);
            HttpServletRequest request = getViewContext().getRequest();

            // We only need to check that SSL is running if the user isn't already using SSL
            if (form.isSslRequired() && !(request.isSecure() && (form.getSslPort() == request.getServerPort())))
            {
                URL testURL = new URL("https", request.getServerName(), form.getSslPort(), AppProps.getInstance().getContextPath());
                String error = HttpsUtil.testSslUrl(testURL, "Ensure that the web server is configured for SSL and the port is correct. If SSL is enabled, try saving these settings while connected via SSL.");

                if (error != null)
                {
                    errors.reject(ERROR_MSG, error);
                    return false;
                }
            }

            if (!"".equals(form.getMascotServer()))
            {
                // we perform the Mascot setting test here in case user did not do so
                SearchClient mascotClient = MS2Service.get().createSearchClient("mascot",form.getMascotServer(), Logger.getLogger("null"),
                    form.getMascotUserAccount(), form.getMascotUserPassword());
                mascotClient.setProxyURL(form.getMascotHTTPProxy());
                mascotClient.findWorkableSettings(true);

                if (0 != mascotClient.getErrorCode())
                {
                    errors.reject(ERROR_MSG, mascotClient.getErrorString());
                    return false;
                }
            }

            String lsidAuthority = form.getDefaultLsidAuthority();
            lsidAuthority = lsidAuthority == null ? null : lsidAuthority.trim();
            if (lsidAuthority == null || "".equals(lsidAuthority))
            {
                errors.reject(ERROR_MSG, "Default LSID Authority may not be blank");
                return false;
            }
            if (lsidAuthority.indexOf(":") != -1)
            {
                errors.reject(ERROR_MSG, "Default LSID Authority may not contain ':'. It should be a domain name, like 'labkey.com'.");
                return false;
            }

            WriteableAppProps props = AppProps.getWriteableInstance();

            props.setDefaultDomain(form.getDefaultDomain());
            props.setDefaultLsidAuthority(lsidAuthority);
            props.setPipelineToolsDir(form.getPipelineToolsDirectory());
            props.setSSLRequired(form.isSslRequired());
            props.setSSLPort(form.getSslPort());
            props.setMemoryUsageDumpInterval(form.getMemoryUsageDumpInterval());
            props.setMaxBLOBSize(form.getMaxBLOBSize());
            props.setExt3Required(form.isExt3Required());

            props.setAdminOnlyMessage(form.getAdminOnlyMessage());
            props.setUserRequestedAdminOnlyMode(form.isAdminOnlyMode());
            props.setMascotServer(form.getMascotServer());
            props.setMascotUserAccount(form.getMascotUserAccount());
            props.setMascotUserPassword(form.getMascotUserPassword());
            props.setMascotHTTPProxy(form.getMascotHTTPProxy());

            try
            {
                ExceptionReportingLevel level = ExceptionReportingLevel.valueOf(form.getExceptionReportingLevel());
                props.setExceptionReportingLevel(level);
            }
            catch (IllegalArgumentException e)
            {
            }

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
                try
                {
                    props.setBaseServerUrl(form.getBaseServerUrl());
                }
                catch (URISyntaxException e)
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL, " + e.getMessage() + ".  Please enter a valid URL, for example: http://www.labkey.org, https://www.labkey.org, or http://www.labkey.org:8080");
                    return false;
                }
            }

            props.save();

            //write an audit log event
            props.writeAuditLogEvent(getViewContext().getUser(), props.getOldProperties());

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
                return new AdminUrlsImpl().getCustomizeSiteURL();
            }
        }
    }


    public static class SiteSettingsBean
    {
        public String helpLink = new HelpTopic("configAdmin").getSimpleLinkHtml("more info...");
        public boolean upgradeInProgress;
        public boolean testInPage;

        private SiteSettingsBean(boolean upgradeInProgress, boolean testInPage) throws SQLException
        {
            this.upgradeInProgress = upgradeInProgress;
            this.testInPage = testInPage;
        }
    }


    public static class ProjectSettingsForm extends SetupForm
    {
        private boolean _shouldInherit; // new subfolders should inherit parent permissions
        private String _systemDescription;
        private String _systemShortName;
        private String _themeName;
        private String _themeFont;
        private String _folderDisplayMode;
        private boolean _enableHelpMenu;
        private String _navigationBarWidth;
        private String _logoHref;
        private String _companyName;
        private String _systemEmailAddress;
        private String _reportAProblemPath;
        private boolean _enableMenuBar;
        private String _tabId;
        private String _projectRootPath;
        private String _fileRootOption;
        private String _supportEmail;

        public enum FileRootProp
        {
            disable,
            siteDefault,
            projectSpecified,
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

        public boolean isEnableHelpMenu()
        {
            return _enableHelpMenu;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEnableHelpMenu(boolean enableHelpMenu)
        {
            _enableHelpMenu = enableHelpMenu;
        }

        // TODO: Delete: not used?
        public String getNavigationBarWidth()
        {
            return _navigationBarWidth;
        }

        public void setNavigationBarWidth(String navigationBarWidth)
        {
            _navigationBarWidth = navigationBarWidth;
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

        public boolean isEnableMenuBar()
        {
            return _enableMenuBar;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setEnableMenuBar(boolean enableMenuBar)
        {
            _enableMenuBar = enableMenuBar;
        }

        public String getProjectRootPath()
        {
            return _projectRootPath;
        }

        public void setProjectRootPath(String projectRootPath)
        {
            _projectRootPath = projectRootPath;
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
    }

    public static class SiteSettingsForm
    {
        private boolean _upgradeInProgress = false;
        private boolean _testInPage = false;

        private String _defaultDomain;
        private String _defaultLsidAuthority;
        private String _pipelineToolsDirectory;
        private boolean _sslRequired;
        private boolean _adminOnlyMode;
        private boolean _ext3Required;
        private String _adminOnlyMessage;
        private int _sslPort;
        private int _memoryUsageDumpInterval;
        private int _maxBLOBSize;
        private String _exceptionReportingLevel;
        private String _usageReportingLevel;
        private String _administratorContactEmail;
        private String _mascotServer;
        private String _mascotUserAccount;
        private String _mascotUserPassword;
        private String _mascotHTTPProxy;

        private String _networkDriveLetter;
        private String _networkDrivePath;
        private String _networkDriveUser;
        private String _networkDrivePassword;
        private String _baseServerUrl;
        private String _callbackPassword;

        public String getMascotServer()
        {
            return (null == _mascotServer) ? "" : _mascotServer;
        }

        public void setMascotServer(String mascotServer)
        {
            _mascotServer = mascotServer;
        }

        public String getMascotUserAccount()
        {
            return (null == _mascotUserAccount) ? "" : _mascotUserAccount;
        }

        public void setMascotUserAccount(String mascotUserAccount)
        {
            _mascotUserAccount = mascotUserAccount;
        }

        public String getMascotUserPassword()
        {
            return (null == _mascotUserPassword) ? "" : _mascotUserPassword;
        }

        public void setMascotUserPassword(String mascotUserPassword)
        {
            _mascotUserPassword = mascotUserPassword;
        }

        public String getMascotHTTPProxy()
        {
            return (null == _mascotHTTPProxy) ? "" : _mascotHTTPProxy;
        }

        public void setMascotHTTPProxy(String mascotHTTPProxy)
        {
            _mascotHTTPProxy = mascotHTTPProxy;
        }

        public void setDefaultDomain(String defaultDomain)
        {
            _defaultDomain = defaultDomain;
        }

        public String getDefaultDomain()
        {
            return _defaultDomain;
        }

        public String getDefaultLsidAuthority()
        {
            return _defaultLsidAuthority;
        }

        public void setDefaultLsidAuthority(String defaultLsidAuthority)
        {
            _defaultLsidAuthority = defaultLsidAuthority;
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
    }


    @AdminConsoleAction
    public class ShowThreadsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<ThreadsBean>("/org/labkey/core/admin/threads.jsp", new ThreadsBean());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Current Threads", this.getClass());
        }
    }

    @AdminConsoleAction
    public class DumpHeapAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            File destination = BreakpointThread.dumpHeap();
            return new HtmlView(PageFlowUtil.filter("Heap dumped to " + destination.getAbsolutePath()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Heap dump", null);
            return root;
        }
    }


    public static class ThreadsBean
    {
        public Map<Thread, Set<Integer>> spids = new HashMap<Thread, Set<Integer>>();
        public List<Thread> threads;
        public Map<Thread, StackTraceElement[]> stackTraces;

        ThreadsBean()
        {
            stackTraces =  Thread.getAllStackTraces();
            threads = new ArrayList<Thread>(stackTraces.keySet());
            Collections.sort(threads, new Comparator<Thread>()
            {
                public int compare(Thread o1, Thread o2)
                {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });

            spids = new HashMap<Thread, Set<Integer>>();

            for (Thread t : threads)
            {
                spids.put(t, ConnectionWrapper.getSPIDsForThread(t));
            }
        }
    }


    @RequiresSiteAdmin
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
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                catch (InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                try
                {
                    testDrive.unmount(driveLetter);
                }
                catch (IOException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                catch (InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
            }

            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<TestNetworkDriveBean>("/org/labkey/core/admin/testNetworkDrive.jsp", bean, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class ResetErrorMarkAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
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
            showLogFile(response, _errorMark, getErrorLogFile());
        }
    }


    @AdminConsoleAction
    public class ShowAllErrorsAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            showLogFile(response, 0, getErrorLogFile());
        }
    }

    @AdminConsoleAction
    public class ShowPrimaryLogAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            File tomcatHome = new File(System.getProperty("catalina.home"));
            File logFile = new File(tomcatHome, "logs/labkey.log");
            showLogFile(response, 0, logFile);
        }
    }

    private File getErrorLogFile()
    {
        File tomcatHome = new File(System.getProperty("catalina.home"));
        return new File(tomcatHome, "logs/labkey-errors.log");
    }

    public void showLogFile(HttpServletResponse response, long startingOffset, File logFile) throws Exception
    {
        if (logFile.exists())
        {
            FileInputStream fIn = null;
            try
            {
                fIn = new FileInputStream(logFile);
                //noinspection ResultOfMethodCallIgnored
                fIn.skip(startingOffset);
                OutputStream out = response.getOutputStream();
                response.setContentType("text/plain");
                byte[] b = new byte[4096];
                int i;
                while ((i = fIn.read(b)) != -1)
                {
                    out.write(b, 0, i);
                }
            }
            finally
            {
                if (fIn != null)
                {
                    fIn.close();
                }
            }
        }
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
            return appendAdminNavTrail(root, "Spring Actions", this.getClass());
        }
    }


    private static class ActionsTabStrip extends TabStripView
    {
        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new ArrayList<NavTree>(2);

            tabs.add(new TabInfo("Summary", "summary", getActionsURL()));
            tabs.add(new TabInfo("Details", "details", getActionsURL()));

            return tabs;
        }

        public HttpView getTabView(String tabId) throws Exception
        {
            return new ActionsView("summary".equals(tabId));
        }
    }

    public static class ExportActionsForm
    {
        private boolean _asWebPage = false;

        public boolean isAsWebPage()
        {
            return _asWebPage;
        }

        public void setAsWebPage(boolean asWebPage)
        {
            _asWebPage = asWebPage;
        }
    }

    @AdminConsoleAction
    public class ExportActionsAction extends ExportAction<ExportActionsForm>
    {
        public void export(ExportActionsForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            ActionsTsvWriter writer = new ActionsTsvWriter();
            writer.setExportAsWebPage(form.isAsWebPage());
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
            String buttonHTML = PageFlowUtil.generateButton("Reset All Statistics", getResetQueryStatisticsURL()) + "&nbsp;" + PageFlowUtil.generateButton("Export", getExportQueriesURL());

            return QueryProfiler.getReportView(form.getStat(), buttonHTML, new QueryProfiler.ActionURLFactory() {
                public ActionURL getActionURL(String name)
                {
                    return getQueriesURL(name);
                }
            },
            new QueryProfiler.ActionURLFactory() {
                public ActionURL getActionURL(String sql)
                {
                    return getQueryStackTracesURL(sql.hashCode());
                }
            });
        }

        public NavTree appendNavTrail(NavTree root)
        {
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
            return QueryProfiler.getStackTraceView(form.getSqlHashCode(), new QueryProfiler.ActionURLFactory() {
                @Override
                public ActionURL getActionURL(String sql)
                {
                    return getExecutionPlanURL(sql);
                }
            });
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
            return QueryProfiler.getExecutionPlanView(form.getSqlHashCode());
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
//            writer.setExportAsWebPage(form.isAsWebPage());
            writer.write(response);
        }
    }

    private static ActionURL getResetQueryStatisticsURL()
    {
        return new ActionURL(ResetQueryStatisticsAction.class, ContainerManager.getRoot());
    }


    @RequiresSiteAdmin
    public class ResetQueryStatisticsAction extends SimpleRedirectAction<QueriesForm>
    {
        public ActionURL getRedirectURL(QueriesForm form) throws Exception
        {
            QueryProfiler.resetAllStatistics();
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
                SearchService ss = ServiceRegistry.get().getService(SearchService.class);
                if (null != ss)
                {
                    LOG.info("Purging SearchService queues");
                    ss.purgeQueues();
                }
            }

            List<TrackingCache> caches = CacheManager.getKnownCaches();
            List<CacheStats> cacheStats = new ArrayList<CacheStats>();
            List<CacheStats> transactionStats = new ArrayList<CacheStats>();

            for (TrackingCache cache : caches)
            {
                cacheStats.add(CacheManager.getCacheStats(cache));
                transactionStats.add(CacheManager.getTransactionCacheStats(cache));
            }

            StringBuilder html = new StringBuilder();
            html.append(PageFlowUtil.textLink("Clear Caches and Refresh", AdminController.getCachesURL(true, false)));
            html.append(PageFlowUtil.textLink("Refresh", AdminController.getCachesURL(false, false)));
            html.append("<hr size=1>\n");
            html.append("<table>\n");
            appendStats(html, "Caches", cacheStats);
            appendStats(html, "Transaction Caches", transactionStats);
            html.append("</table>\n");

            return new HtmlView(html.toString());
        }

        private void appendStats(StringBuilder html, String title, List<CacheStats> stats)
        {
            Collections.sort(stats);

            html.append("<tr><td colspan=4>&nbsp;</td></tr>");
            html.append("<tr><td><b>").append(title).append(" (").append(stats.size()).append(")</b></td></tr>\n");
            html.append("<tr><td colspan=4>&nbsp;</td></tr>");

            html.append("<tr><th>Debug Name</th>");
            html.append("<th>Limit</th><th>Max&nbsp;Size</th><th>Current&nbsp;Size</th><th>Gets</th><th>Misses</th><th>Puts</th><th>Expirations</th><th>Removes</th><th>Clears</th><th>Miss Percentage</th></tr>");

            long size = 0;
            long gets = 0;
            long misses = 0;
            long puts = 0;
            long expirations = 0;
            long removes = 0;
            long clears = 0;

            for (CacheStats stat : stats)
            {
                size += stat.getSize();
                gets += stat.getGets();
                misses += stat.getMisses();
                puts += stat.getPuts();
                expirations += stat.getExpirations();
                removes += stat.getRemoves();
                clears += stat.getClears();

                html.append("<tr>");

                appendDescription(html, stat.getDescription(), stat.getCreationStackTrace());

                Long limit = stat.getLimit();
                Long maxSize = stat.getMaxSize();

                appendLongs(html, limit, maxSize, stat.getSize(), stat.getGets(), stat.getMisses(), stat.getPuts(), stat.getExpirations(), stat.getRemoves(), stat.getClears());
                appendDoubles(html, stat.getMissRatio());

                if (null != limit && maxSize >= limit)
                    html.append("<td><font class=\"labkey-error\">This cache has been limited</font></td>");

                html.append("</tr>\n");
            }

            double ratio = 0 != gets ? misses / (double)gets : 0;
            html.append("<tr><td colspan=4>&nbsp;</td></tr>");
            html.append("<tr><td>Total</td>");

            appendLongs(html, null, null, size, gets, misses, puts, expirations, removes, clears);
            appendDoubles(html, ratio);

            html.append("</tr>\n");
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
            return appendAdminNavTrail(root, "Cache Statistics", this.getClass());
        }
    }

    @AdminConsoleAction
    public class EnvironmentVariablesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Map<String, String>>("/org/labkey/core/admin/properties.jsp", System.getenv());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Environment Variables", this.getClass());
        }
    }

    @AdminConsoleAction
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
        private boolean _enableSystemMaintenance;

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


    @AdminConsoleAction
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
            return new JspView<SystemMaintenanceProperties>("/org/labkey/core/admin/systemMaintenance.jsp", prop, errors);
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

    @RequiresSiteAdmin
    public class SystemMaintenanceAction extends StatusReportingRunnableAction<SystemMaintenance>
    {
        @Override
        protected SystemMaintenance newStatusReportingRunnable()
        {
            String taskName = (String)getViewContext().get("taskName");
            return new SystemMaintenance(true, taskName);
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


    @AdminConsoleAction
    public class MemTrackerAction extends SimpleViewAction<MemForm>
    {
        public ModelAndView getView(MemForm form, BindException errors) throws Exception
        {
            Set<Object> objectsToIgnore = MemTracker.beforeReport();

            if (form.isClearCaches())
            {
                LOG.info("Clearing Introspector caches");
                Introspector.flushCaches();
                LOG.info("Purging all caches");
                CacheManager.clearAllKnownCaches();
                SearchService ss = ServiceRegistry.get().getService(SearchService.class);
                if (null != ss)
                {
                    LOG.info("Purging SearchService queues");
                    ss.purgeQueues();
                }
            }

            if (form.isGc())
            {
                LOG.info("Garbage collecting");
                System.gc();
            }

            if (form.isGc() || form.isClearCaches())
            {
                LOG.info("Cache clearing and garbage collecting complete");
            }

            return new JspView<MemBean>("/org/labkey/core/admin/memTracker.jsp", new MemBean(getViewContext().getRequest(), objectsToIgnore));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Memory usage -- " + DateUtil.formatDateTime(), this.getClass());
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
        public final List<Pair<String, Object>> systemProperties = new ArrayList<Pair<String,Object>>();
        public final List<MemTracker.HeldReference> references;
        public final List<String> graphNames = new ArrayList<String>();
        public final List<String> activeThreads = new LinkedList<String>();

        public boolean assertsEnabled = false;

        private MemBean(HttpServletRequest request, Set<Object> objectsToIgnore)
        {
            List<MemTracker.HeldReference> all = MemTracker.getReferences();
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
            references = new ArrayList<MemTracker.HeldReference>(all.size());

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
                systemProperties.add(new Pair<String,Object>("Total Heap Memory", getUsageString(membean.getHeapMemoryUsage())));
                systemProperties.add(new Pair<String,Object>("Total Non-heap Memory", getUsageString(membean.getNonHeapMemoryUsage())));
            }

            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean pool : pools)
            {
                systemProperties.add(new Pair<String,Object>(pool.getName() + " " + pool.getType(), getUsageString(pool)));
                graphNames.add(pool.getName());
            }

            // class loader:
            ClassLoadingMXBean classbean = ManagementFactory.getClassLoadingMXBean();
            if (classbean != null)
            {
                systemProperties.add(new Pair<String,Object>("Loaded Class Count", classbean.getLoadedClassCount()));
                systemProperties.add(new Pair<String,Object>("Unloaded Class Count", classbean.getUnloadedClassCount()));
                systemProperties.add(new Pair<String,Object>("Total Loaded Class Count", classbean.getTotalLoadedClassCount()));
            }

            // runtime:
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
            {
                systemProperties.add(new Pair<String,Object>("VM Start Time", DateUtil.formatDateTime(new Date(runtimeBean.getStartTime()))));
                long upTime = runtimeBean.getUptime(); // round to sec
                upTime = upTime - (upTime % 1000);
                systemProperties.add(new Pair<String,Object>("VM Uptime", DateUtil.formatDuration(upTime)));
                systemProperties.add(new Pair<String,Object>("VM Version", runtimeBean.getVmVersion()));
                systemProperties.add(new Pair<String,Object>("VM Classpath", runtimeBean.getClassPath()));
            }

            // threads:
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            if (threadBean != null)
            {
                systemProperties.add(new Pair<String,Object>("Thread Count", threadBean.getThreadCount()));
                systemProperties.add(new Pair<String,Object>("Peak Thread Count", threadBean.getPeakThreadCount()));
                long[] deadlockedThreads = threadBean.findMonitorDeadlockedThreads();
                systemProperties.add(new Pair<String,Object>("Deadlocked Thread Count", deadlockedThreads != null ? deadlockedThreads.length : 0));
            }

            // threads:
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcBean : gcBeans)
            {
                systemProperties.add(new Pair<String,Object>(gcBean.getName() + " GC count", gcBean.getCollectionCount()));
                systemProperties.add(new Pair<String,Object>(gcBean.getName() + " GC time", DateUtil.formatDuration(gcBean.getCollectionTime())));
            }

            systemProperties.add(new Pair<String, Object>("In-use Connections", ConnectionWrapper.getActiveConnectionCount()));

            //noinspection ConstantConditions
            assert assertsEnabled = true;
        }
    }


    private static String getUsageString(MemoryPoolMXBean pool)
    {
        try
        {
            return getUsageString(pool.getUsage());
        }
        catch (IllegalArgumentException x)
        {
            // sometimes we get usage>committed exception with older versions of JRockit
            return "exception getting usage";
        }
    }


    private static String getUsageString(MemoryUsage usage)
    {
        if (null == usage)
            return "null";

        try
        {
            StringBuilder sb = new StringBuilder();
            sb.append("init = ").append(_formatInteger.format(usage.getInit()));
            sb.append("; used = ").append(_formatInteger.format(usage.getUsed()));
            sb.append("; committed = ").append(_formatInteger.format(usage.getCommitted()));
            sb.append("; max = ").append(_formatInteger.format(usage.getMax()));
            return sb.toString();
        }
        catch (IllegalArgumentException x)
        {
            // sometime we get usage>committed exception with older verions of JRockit
            return "exception getting usage";
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

        public int compareTo(MemoryCategory o)
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
            List<MemoryCategory> types = new ArrayList<MemoryCategory>(4);

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

            // TODO: Remove try/catch once we require Java 7
            // Ignore known Java 6 issue with sun.font.FileFontStrike.getCachedGlyphPtr(), http://bugs.sun.com/view_bug.do?bug_id=7007299
            try
            {
                ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, showLegend ? 800 : 398, showLegend ? 100 : 70);
            }
            catch (NullPointerException e)
            {
                if ("getCachedGlyphPtr".equals(e.getStackTrace()[0].getMethodName()))
                    LOG.warn("Ignoring known synchronization issue with FileFontStrike.getCachedGlyphPtr()", e);
                else
                    throw e;
            }
        }
    }


    // TODO: Check permissions, what if guests have read perm?, different containers?
    @RequiresLogin
    @RequiresPermissionClass(ReadPermission.class)
    public class SetAdminModeAction extends SimpleRedirectAction<UserPrefsForm>
    {
        public ActionURL getRedirectURL(UserPrefsForm form) throws Exception
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


    @RequiresPermissionClass(AdminPermission.class)
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


    @RequiresPermissionClass(AdminPermission.class)
    public class SaveWebThemeAction extends AbstractWebThemeAction
    {
        protected void handleTheme(WebThemeForm form, ActionURL successURL) throws SQLException
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


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteWebThemeAction extends AbstractWebThemeAction
    {
        protected void handleTheme(WebThemeForm form, ActionURL redirectURL) throws SQLException
        {
            WebThemeManager.deleteWebTheme(form.getThemeName());
        }
    }


    private static class DefineWebThemesView extends JspView<WebThemesBean>
    {
        public DefineWebThemesView(WebThemeForm form, BindException errors) throws SQLException
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

        ArrayList<String> _errorList = new ArrayList<String>();

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
            if (b<0 || b>255) return false;
            return true;
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


    public static ActionURL getModuleStatusURL()
    {
        return new ActionURL(ModuleStatusAction.class, ContainerManager.getRoot());
    }

    @RequiresSiteAdmin
    @AllowedDuringUpgrade
    public class ModuleStatusAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ModuleLoader.getInstance().startNonCoreUpgrade(getUser());

            VBox vbox = new VBox();

            JspView statusView = new JspView("/org/labkey/core/admin/moduleStatus.jsp");
            vbox.addView(statusView);

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());

            getPageConfig().setTemplate(Template.Wizard);
            getPageConfig().setTitle((ModuleLoader.getInstance().isNewInstall() ? "Install" : "Upgrade") + " Modules");

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

                appProps.save();
                lafProps.save();
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

                if (root != null && root.exists())
                    form.setRootPath(FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath());

                form.setAllowReporting(true);
                LookAndFeelProperties props = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
                form.setSiteName(props.getShortName());
                form.setNotificationEmail(props.getSystemEmailAddress());
            }

            JspView<NewInstallSiteSettingsForm> view = new JspView<NewInstallSiteSettingsForm>("/org/labkey/core/admin/newInstallSiteSettings.jsp", form, errors);

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
        List<NavTree> navTrail = new ArrayList<NavTree>();
        if (ModuleLoader.getInstance().isNewInstall())
        {
            navTrail.add(new NavTree("Account Setup"));
            navTrail.add(new NavTree("Install Modules"));
            navTrail.add(new NavTree("Set Defaults"));
        }
        else
        {
            navTrail.add(new NavTree("Upgrade Modules"));
        }
        navTrail.add(new NavTree("Complete"));
        return navTrail;
    }

    @RequiresSiteAdmin
    public class DbCheckerAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<DataCheckForm>("/org/labkey/core/admin/checkDatabase.jsp", new DataCheckForm());
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return appendAdminNavTrail(root, "Database Check Tools", this.getClass());
        }
    }


    @RequiresSiteAdmin
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
                       sqlcheck = DbSchema.checkAllContainerCols(true);
                if (fixRequested.equalsIgnoreCase("descriptor"))
                       sqlcheck = OntologyManager.doProjectColumnCheck(true);
                contentBuilder.append(sqlcheck);
            }
            else
            {
                contentBuilder.append("\n<br/><br/>Checking Container Column References...");
                String strTemp = DbSchema.checkAllContainerCols(false);
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
                    String sOut = TableXmlUtils.compareXmlToMetaData(schema.getName(), false, false);
                    if (null!=sOut)
                    {
                        contentBuilder.append("<br/>&nbsp;&nbsp;&nbsp;&nbsp;ERROR: Inconsistency in Schema ");
                        contentBuilder.append(schema.getName());
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
                        contentBuilder.append("<div class=\"warning\">"+error+"</div>");
                    }
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


    @RequiresSiteAdmin
    public class GetSchemaXmlDocAction extends ExportAction<DataCheckForm>
    {
        public void export(DataCheckForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String dbSchemaName = form.getDbSchema();
            if (null == dbSchemaName || dbSchemaName.length() == 0)
            {
                throw new NotFoundException("Must specify dbSchema parameter");
            }

            boolean bFull = form.getFull();

            TablesDocument tdoc = TableXmlUtils.createXmlDocumentFromDatabaseMetaData(dbSchemaName, bFull);
            StringWriter sw = new StringWriter();

            XmlOptions xOpt = new XmlOptions();
            xOpt.setSavePrettyPrint();

            tdoc.save(sw, xOpt);

            sw.flush();
            PageFlowUtil.streamFileBytes(response, dbSchemaName + ".xml", sw.toString().getBytes(), true);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
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


    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeEmailAction extends FormViewAction<CustomEmailForm>
    {
        public void validateCommand(CustomEmailForm target, Errors errors)
        {
        }

        public ModelAndView getView(CustomEmailForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getContainer().isRoot() && !getUser().isAdministrator())
            {
                // Must be a site admin to customize in the root, which is where the site-wide templates are stored
                throw new UnauthorizedException();
            }
            return new JspView<CustomEmailForm>("/org/labkey/core/admin/customizeEmail.jsp", form, errors);
        }

        public boolean handlePost(CustomEmailForm form, BindException errors) throws Exception
        {
            if (getContainer().isRoot() && !getUser().isAdministrator())
            {
                // Must be a site admin to customize in the root, which is where the site-wide templates are stored
                throw new UnauthorizedException();
            }

            if (form.getTemplateClass() != null)
            {
                EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());

                template.setSubject(form.getEmailSubject());
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
            return new ActionURL(CustomizeEmailAction.class, getContainer()).replaceParameter("templateClass", form.getTemplateClass());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("customEmail"));
            return appendAdminNavTrail(root, "Customize Email", this.getClass());
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteCustomEmailAction extends SimpleRedirectAction<CustomEmailForm>
    {
        public ActionURL getRedirectURL(CustomEmailForm form) throws Exception
        {
            if (getContainer().isRoot() && !getUser().isAdministrator())
            {
                // Must be a site admin to customize in the root, which is where the site-wide templates are stored
                throw new UnauthorizedException();
            }

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
        private String _emailMessage;
        private String _returnURL;

        public void setTemplateClass(String name){_templateClass = name;}
        public String getTemplateClass(){return _templateClass;}
        public void setEmailSubject(String subject){_emailSubject = subject;}
        public String getEmailSubject(){return _emailSubject;}
        public void setEmailMessage(String body){_emailMessage = body;}
        public String getEmailMessage(){return _emailMessage;}
    }

    private ActionURL getManageFoldersURL()
    {
        return new AdminUrlsImpl().getManageFoldersURL(getContainer());
    }

    public static class ManageFoldersForm
    {
        private String name;
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


    private String getTitle(String action)
    {
        return action + " " + (getContainer().isProject() ? "Project" : "Folder");
    }


    @RequiresPermissionClass(AdminPermission.class)
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
            return new JspView<ManageFoldersForm>("/org/labkey/core/admin/renameFolder.jsp", form, errors);
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String folderName = StringUtils.trimToNull(form.getName());
            StringBuilder error = new StringBuilder();

            if (Container.isLegalName(folderName, error))
            {
                if (c.getParent().hasChild(folderName))
                    error.append("The parent folder already has a folder with this name.");
                else
                {
                    ContainerManager.rename(c, getUser(), folderName);
                    if (form.isAddAlias())
                    {
                        String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                        List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
                        newAliases.add(c.getPath());
                        ContainerManager.saveAliasesForContainer(c, newAliases);
                    }
                    c = ContainerManager.getForId(c.getId());     // Reload container to populate new name
                    _returnURL = new AdminUrlsImpl().getManageFoldersURL(c);
                    return true;
                }
            }

            errors.reject(ERROR_MSG, "Error: " + error + "  Please enter a different folder name.");
            return false;
        }

        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            return _returnURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            return appendAdminNavTrail(root, getTitle("Rename"), this.getClass());
        }
    }


    public static ActionURL getShowMoveFolderTreeURL(Container c, boolean addAlias, boolean showAll)
    {
        ActionURL url = new ActionURL(ShowMoveFolderTreeAction.class, c);

        if (addAlias)
            url.addParameter("addAlias", "1");

        if (showAll)
            url.addParameter("showAll", "1");

        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
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


    public static ActionURL getMoveFolderURL(Container c, boolean addAlias)
    {
        ActionURL url = new ActionURL(MoveFolderAction.class, c);

        if (addAlias)
            url.addParameter("addAlias", "1");

        return url;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class MoveFolderAction extends SimpleViewAction<ManageFoldersForm>
    {
        public ModelAndView getView(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            Container newParent =  ContainerManager.getForPath(form.getTarget());

            if (c.isRoot())
                throw new NotFoundException("Can't move the root folder.");  // Don't show move tree from root

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
                return new JspView<ManageFoldersForm>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
            }

            ContainerManager.move(c, newParent, getUser());

            if (form.isAddAlias())
            {
                String[] originalAliases = ContainerManager.getAliasesForContainer(c);
                List<String> newAliases = new ArrayList<String>(Arrays.asList(originalAliases));
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


    @RequiresPermissionClass(AdminPermission.class)
    public class ConfirmProjectMoveAction extends SimpleViewAction<ManageFoldersForm>
    {
        public ModelAndView getView(ManageFoldersForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<ManageFoldersForm>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class CreateFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private ActionURL _successURL;

        public void validateCommand(ManageFoldersForm target, Errors errors)
        {
        }

        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            JspView statusView = new JspView<ManageFoldersForm>("/org/labkey/core/admin/createFolder.jsp", form, errors);
            vbox.addView(statusView);

            Container c = getViewContext().getContainer();

            getPageConfig().setNavTrail(getCreateProjectWizardSteps(c.isRoot()));
            getPageConfig().setTemplate(Template.Wizard);
            getPageConfig().setTitle("Name and Type");

            return vbox;
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container parent = getContainer();
            String folderName = StringUtils.trimToNull(form.getName());
            StringBuilder error = new StringBuilder();

            if (Container.isLegalName(folderName, error))
            {
                if (parent.hasChild(folderName))
                    error.append("The parent folder already has a folder with this name.");
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
                            
                        MemoryVirtualFile vf = new MemoryVirtualFile();

                        // export objects from the source folder
                        FolderWriterImpl writer = new FolderWriterImpl();
                        FolderExportContext exportCtx = new FolderExportContext(getUser(), sourceContainer, PageFlowUtil.set(form.getTemplateWriterTypes()), "new",
                                form.getTemplateIncludeSubfolders(), false, false, false, new StaticLoggerGetter(Logger.getLogger(FolderWriterImpl.class)));
                        writer.write(sourceContainer, exportCtx, vf);

                        // create the new target container
                        c = ContainerManager.createContainer(parent, folderName, null, null, Container.TYPE.normal, getUser());

                        // import objects into the target folder
                        XmlObject folderXml = vf.getXmlBean("folder.xml");
                        if (folderXml instanceof FolderDocument)
                        {
                            FolderDocument folderDoc = (FolderDocument)folderXml;
                            FolderImportContext importCtx = new FolderImportContext(getUser(), c, folderDoc, new StaticLoggerGetter(Logger.getLogger(FolderImporterImpl.class)), vf);

                            FolderImporterImpl importer = new FolderImporterImpl();
                            importer.process(null, importCtx, vf);
                        }
                    }
                    else
                    {
                        FolderType type = ModuleLoader.getInstance().getFolderType(folderType);

                        if (type == null)
                        {
                            errors.reject(null, "Folder type not recognized");
                            return false;
                        }

                        String[] modules = form.getActiveModules();

                        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
                        {
                            if (null == modules || modules.length == 0)
                            {
                                errors.reject(null, "At least one module must be selected");
                                return false;
                            }
                        }

                        c = ContainerManager.createContainer(parent, folderName, null, null, Container.TYPE.normal, getUser());
                        c.setFolderType(type, getUser());

                        if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
                        {
                            Set<Module> activeModules = new HashSet<Module>();
                            for (String moduleName : modules)
                            {
                                Module module = ModuleLoader.getInstance().getModule(moduleName);
                                if (module != null)
                                    activeModules.add(module);
                            }

                            c.setFolderType(FolderType.NONE, activeModules);
                            Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
                            c.setDefaultModule(defaultModule);
                        }
                    }

                    _successURL = new AdminUrlsImpl().getSetFolderPermissionsURL(c);
                    _successURL.addParameter("wizard", Boolean.TRUE.toString());

                    return true;
                }
            }

            errors.reject(ERROR_MSG, "Error: " + error + "  Please enter a different folder name (or Cancel).");
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


    @RequiresPermissionClass(AdminPermission.class)
    public class SetFolderPermissionsAction extends FormViewAction<SetFolderPermissionsForm>
    {
        private ActionURL _successURL;

        public void validateCommand(SetFolderPermissionsForm target, Errors errors)
        {
        }


        public ModelAndView getView(SetFolderPermissionsForm form, boolean reshow, BindException errors) throws Exception
        {
            VBox vbox = new VBox();

            JspView statusView = new JspView<SetFolderPermissionsForm>("/org/labkey/core/admin/setFolderPermissions.jsp", form, errors);
            vbox.addView(statusView);

            Container c = this.getViewContext().getContainer();
            getPageConfig().setTitle("Users / Permissions");
            getPageConfig().setNavTrail(getCreateProjectWizardSteps(c.isProject()));
            getPageConfig().setTemplate(Template.Wizard);

            return vbox;

        }

        public boolean handlePost(SetFolderPermissionsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String permissionType = form.getPermissionType();
            StringBuilder error = new StringBuilder();

            if(c.isProject()){
                _successURL = new AdminUrlsImpl().getInitialFolderSettingsURL(c);
            }
            else {
                _successURL = getContainer().getStartURL(getUser());
            }

            if(permissionType == null){
                errors.reject(ERROR_MSG, "You must select one of the options for permissions.");
                return false;
            }

            if(permissionType.equals("CurrentUser") | permissionType.equals("Advanced"))
            {
                MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
                Role role = RoleManager.getRole(c.isProject() ? ProjectAdminRole.class : FolderAdminRole.class);

                policy.addRoleAssignment(this.getViewContext().getUser(), role);
                SecurityPolicyManager.savePolicy(policy);
            }
            else if (permissionType.equals("Inherit"))
            {
                SecurityManager.setInheritPermissions(c);
            }
            else if (permissionType.equals("CopyExistingProject"))
            {
                String targetProject = form.getTargetProject();
                if(targetProject == null){
                    errors.reject(ERROR_MSG, "In order to copy permissions from an existing project, you must pick a project.");
                    return false;
                }
                Container source = ContainerManager.getForId(targetProject);
                assert source != null;

                HashMap<UserPrincipal, UserPrincipal> groupMap = GroupManager.copyGroupsToContainer(source, c);

                //copy role assignments
                SecurityPolicy op = SecurityPolicyManager.getPolicy(source);
                MutableSecurityPolicy np = new MutableSecurityPolicy(c);
                for (RoleAssignment assignment : op.getAssignments()){
                    Integer userId = assignment.getUserId();
                    UserPrincipal p = SecurityManager.getPrincipal(userId);
                    Role r = assignment.getRole();

                    if(p instanceof Group){
                        Group g = (Group)p;
                        if(!g.isProjectGroup()){
                            np.addRoleAssignment(p, r);
                        }
                        else {
                            np.addRoleAssignment(groupMap.get(p), r);
                        }
                    }
                    else {
                        np.addRoleAssignment(p, r);
                    }
                }

                SecurityPolicyManager.savePolicy(np);
            }
            else {
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
            //can't use getTitle() here, as it assumes the title relates to the current
            //container, but in this case, it relates to the thing we're going to create
            String title = "Users / Permissions";
            return appendAdminNavTrail(root, title, this.getClass());
        }
    }

    public static class SetFolderPermissionsForm
    {
        private String targetProject;
        private String permissionType;

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
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetInitialFolderSettingsAction extends FormViewAction<ProjectSettingsForm>
    {
        private ActionURL _successURL;

        public void validateCommand(ProjectSettingsForm target, Errors errors)
        {
        }

        public ModelAndView getView(ProjectSettingsForm form, boolean reshow, BindException errors) throws Exception
        {

            VBox vbox = new VBox();
            Container c = getViewContext().getContainer();

            JspView statusView = new JspView<ProjectSettingsForm>("/org/labkey/core/admin/setInitialFolderSettings.jsp", form, errors);
            vbox.addView(statusView);

            getPageConfig().setNavTrail(getCreateProjectWizardSteps(c.isProject()));
            getPageConfig().setTemplate(Template.Wizard);

            String noun = c.isProject() ? "Project": "Folder";
            getPageConfig().setTitle(noun + " Settings");

            return vbox;

        }

        public boolean handlePost(ProjectSettingsForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            String projectRootPath = StringUtils.trimToNull(form.getProjectRootPath());
            String fileRootOption = form.getFileRootOption();

            if(projectRootPath == null && !fileRootOption.equals("default"))
            {
                errors.reject(ERROR_MSG, "Error: Must supply a default file location.");
                return false;
            }

            FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
            if(fileRootOption.equals("default"))
            {
                service.setIsUseDefaultRoot(c.getProject(), true);
            }
            else
            {
                if (!service.isValidProjectRoot(projectRootPath))
                {
                    errors.reject(ERROR_MSG, "File root '" + projectRootPath + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                    return false;
                }

                service.setIsUseDefaultRoot(c.getProject(), false);
                service.setFileRoot(c.getProject(), new File(projectRootPath));
            }

            _successURL = getContainer().getStartURL(getUser());

            return true;
        }

        public ActionURL getSuccessURL(ProjectSettingsForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            //can't use getTitle() here, as it assumes the title relates to the current
            //container, but in this case, it relates to the thing we're going to create
            String title = "Users / Permissions";
            return appendAdminNavTrail(root, title, this.getClass());
        }
    }

    public static List<NavTree> getCreateProjectWizardSteps(Boolean isProject)
    {
        List<NavTree> navTrail = new ArrayList<NavTree>();

        navTrail.add(new NavTree("Name and Type"));
        navTrail.add(new NavTree("Users / Permissions"));
        if(isProject)
            navTrail.add(new NavTree("Project Settings"));
        return navTrail;
    }

    // For backward compatibility only -- old welcomeWiki text has link to admin/modifyFolder.view?action=create

    @RequiresNoPermission
    public class ModifyFolderAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            if ("create".equalsIgnoreCase(getViewContext().getActionURL().getParameter("action")))
                return new ActionURL(CreateFolderAction.class, getContainer());

            throw new NotFoundException();
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
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
            return new JspView<ManageFoldersForm>("/org/labkey/core/admin/deleteFolder.jsp", form);
        }

        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            // Must be site admin to delete a project
            if (c.isProject() && !getUser().isAdministrator())
            {
                throw new UnauthorizedException();
            }

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


    @RequiresPermissionClass(AdminPermission.class)
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

    @RequiresPermissionClass(AdminPermission.class)
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
            Map<String, Container> nameToContainer = new HashMap<String, Container>();
            for (Container child : children)
                nameToContainer.put(child.getName(), child);
            List<Container> sorted = new ArrayList<Container>(children.size());
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

    @RequiresPermissionClass(AdminPermission.class)
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
            JspView<EmailTestForm> testView = new JspView<EmailTestForm>("/org/labkey/core/admin/emailTest.jsp", form);
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


    @RequiresSiteAdmin
    public class RecreateViewsAction extends ConfirmAction
    {
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
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
    public class GetSessionLogEventsAction extends ApiAction
    {
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
            ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(events.length);
            for (LoggingEvent e : events)
            {
                if (eventId==0 || eventId<Integer.parseInt(e.getProperty("eventId")))
                {
                    HashMap<String, Object> m = new HashMap<String, Object>();
                    m.put("eventId", e.getProperty("eventId"));
                    m.put("level", e.getLevel().toString());
                    m.put("message", e.getMessage());
                    m.put("timestamp", new Date(e.getTimeStamp()));
                    list.add(m);
                }
            }
            return new ApiSimpleResponse("events", list);
        }
    }


    @RequiresLogin
    public class SessionLoggingAction extends FormViewAction<LoggingForm>
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            super.checkPermissions();
            User user = getUser();
            if (!user.isDeveloper())
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
        String _level;

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


    @RequiresSiteAdmin
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
            for (Container project : ContainerManager.getProjects())
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(project);
                if (root != null && root.isValid())
                {
                    ViewBackgroundInfo info = getViewBackgroundInfo();
                    PipelineJob job = new ValidateDomainsPipelineJob(info, root);
                    PipelineService.get().getPipelineQueue().addJob(job);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }


    @AdminConsoleAction
    public class ModulesAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            ModuleLoader ml = ModuleLoader.getInstance();

            Collection<ModuleContext> unknownModules = ml.getUnknownModuleContexts().values();
            Collection<ModuleContext> knownModules = ml.getAllModuleContexts();
            knownModules.removeAll(unknownModules);

            HttpView known = new ModulesView(knownModules, "Known", "Each of these modules is installed and has a valid module file.", "This message should never appear");
            HttpView unknown = new ModulesView(unknownModules, "Unknown",
                (1 == unknownModules.size() ? "This module" : "Each of these modules") + " has been installed on this server " +
                "in the past but the corresponding module file is currently missing or invalid. Possible explanations: the " +
                "module is no longer being distributed, the module has been renamed, the server location where the module " +
                "is stored is not accessible, or the module file is corrupted.", "A module is considered \"unknown\" if it was installed on this server " +
                "in the past but the corresponding module file is currently missing or invalid. This server has no unknown modules.");

            return new VBox(known, unknown);
        }

        private class ModulesView extends WebPartView
        {
            private final Collection<ModuleContext> _contexts;
            private final String _type;
            private final String _description;
            private final String _noModulesDescription;

            private ModulesView(Collection<ModuleContext> contexts, String type, String description, String noModulesDescription)
            {
                List<ModuleContext> sorted = new ArrayList<ModuleContext>(contexts);
                Collections.sort(sorted, new Comparator<ModuleContext>(){
                    @Override
                    public int compare(ModuleContext mc1, ModuleContext mc2)
                    {
                        return mc1.getName().compareToIgnoreCase(mc2.getName());
                    }
                });

                _contexts = sorted;
                _type = type;
                _description = description;
                _noModulesDescription = noModulesDescription;
                setTitle(_type + " Modules");
            }

            @Override
            protected void renderView(Object model, PrintWriter out) throws Exception
            {
                if (_contexts.isEmpty())
                {
                    out.println(_noModulesDescription);
                }
                else
                {
                    out.println("\n<table>");
                    out.println("<tr><td colspan=\"5\">" + PageFlowUtil.filter(_description) + "</td></tr>");
                    out.println("<tr><td colspan=\"5\">&nbsp;</td></tr>");
                    out.println("<tr><th>Name</th><th>Version</th><th>Class</th><th>Schemas</th><th></th></tr>");

                    for (ModuleContext moduleContext : _contexts)
                    {
                        List<String> schemas = moduleContext.getSchemaList();
                        out.println("  <tr>");

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
                        out.print(PageFlowUtil.filter(StringUtils.join(schemas, ", ")));
                        out.println("</td>");

                        out.print("    <td>");
                        out.print(PageFlowUtil.textLink("Delete Module" + (schemas.isEmpty() ? "" : (" and Schema" + (schemas.size() > 1 ? "s" : ""))), getDeleteURL(moduleContext.getName())));
                        out.println("</td>");

                        out.println("  </tr>");
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


    @RequiresSiteAdmin
    public class DeleteModuleAction extends ConfirmAction<ModuleForm>
    {
        @Override
        public void validateCommand(ModuleForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getConfirmView(ModuleForm form, BindException errors) throws Exception
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

    @RequiresSiteAdmin
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
                WriteableAppProps props = AppProps.getWriteableInstance();
                props.setExperimentalFeatureEnabled(form.getFeature(), form.isEnabled());
                props.save();
            }

            Map<String, Object> ret = new HashMap<String, Object>();
            ret.put("feature", form.getFeature());
            ret.put("enabled", AppProps.getInstance().isExperimentalFeatureEnabled(form.getFeature()));
            return new ApiSimpleResponse(ret);
        }
    }

    @RequiresSiteAdmin
    public class ExperimentalFeaturesAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Object form, boolean reshow, BindException errors) throws Exception
        {
            JspView<Object> view = new JspView<Object>("/org/labkey/core/admin/experimentalFeatures.jsp", null);
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
            return root.addChild("Experimental Features");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CustomizeMenuAction extends FormViewAction<CustomizeMenuForm>
    {
        Portal.WebPart _webPart;

        public void validateCommand(CustomizeMenuForm form, Errors errors)
        {
        }
        public ModelAndView getView(CustomizeMenuForm form, boolean reshow, BindException errors) throws Exception
        {
            return null;
        }

        public boolean handlePost(CustomizeMenuForm form, BindException errors) throws Exception
        {
            setCustomizeMenuForm(form, getContainer(),  getUser());
            return true;
        }

        public ActionURL getSuccessURL(CustomizeMenuForm form)
        {
            return new ActionURL(ProjectSettingsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
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

        try
        {
            Portal.updatePart(user, webPart);
        }
        catch (SQLException e)
        {

        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class AddTabAction extends FormViewAction<AddTabForm>
    {
        public void validateCommand(AddTabForm form, Errors errors)
        {
            String name = form.getTabName();
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<Portal.PortalPage>(Portal.getPages(getContainer(), false));
            CaseInsensitiveHashMap<FolderTab> folderTabMap = new CaseInsensitiveHashMap<FolderTab>();

            for (FolderTab tab : getContainer().getFolderType().getDefaultTabs())
            {
                folderTabMap.put(tab.getName(), tab);
            }

            if (form.getTabType().equalsIgnoreCase("portal"))
            {
                if(name == null)
                {
                    errors.reject(ERROR_MSG, "A tab name must be specified.");
                }
                else if (pages.containsKey(name))
                {
                    errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                    return;
                }
            }
            else
            {
                if(name == null)
                {
                    // Caption not set by user, use default caption.
                    FolderTab tab = folderTabMap.get(form.getTabType());
                    if (tab != null)
                        name = tab.getCaption(getViewContext());
                }
            }

            for (Portal.PortalPage page : pages.values())
            {
                if (page.getCaption() != null && page.getCaption().equals(name))
                {
                    errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                }
                else if (folderTabMap.containsKey(page.getPageId()))
                {
                    if (folderTabMap.get(page.getPageId()).getCaption(getViewContext()).equalsIgnoreCase(name))
                        errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                }
            }
        }

        public boolean handlePost(AddTabForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if (form.getTabType().equalsIgnoreCase("portal"))
            {
                Portal.saveParts(container, form.getTabName(), new Portal.WebPart[0]);
                Portal.addProperty(container, form.getTabName(), Portal.PROP_CUSTOMTAB);
            }
            else
            {
                // TabType is really pageId of hidden page
                Portal.showPage(container, form.getTabType());

                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<Portal.PortalPage>(Portal.getPages(container));
                Portal.PortalPage page = pages.get(form.getTabType());

                if (null == page)
                {
                    errors.reject(ERROR_MSG, "Page cannot be found. Check with your system administrator.");
                    return false;
                }

                if(form.getTabName() != null && !form.getTabName().equals(""))
                {
                    page.setCaption(form.getTabName());
                }
                else
                {
                    String caption = "";

                    for(FolderTab folderTab : container.getFolderType().getDefaultTabs())
                    {
                        if(folderTab.getName().equals(form.getTabType()))
                        {
                            caption = folderTab.getCaption(getViewContext());
                            break;
                        }
                    }

                    page.setCaption(caption);
                }

                // Update the page the caption is saved.
                Portal.updatePortalPage(container, page);
            }
            return true;
        }

        public ModelAndView getView(AddTabForm form, boolean reshow, BindException errors) throws Exception
        {
            HttpView view = new JspView<AddTabForm>("/org/labkey/core/admin/addTab.jsp", form, errors);

            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Add Tab");
            return root;
        }

        public ActionURL getSuccessURL(AddTabForm form)
        {
            if (form.getReturnActionURL() != null && !form.getReturnActionURL().getAction().equals("addTab"))
            {
                return form.getReturnActionURL();
            }

            return new ActionURL(ProjectController.BeginAction.class, getContainer());
        }
    }

    public static class AddTabForm extends ReturnUrlForm
    {
        String _tabType;
        String _tabName;

        public String getTabName()
        {
            return _tabName;
        }

        public void setTabName(String name)
        {
            _tabName = name;
        }

        public String getTabType()
        {
            return _tabType;
        }

        public void setTabType(String tabType)
        {
            _tabType = tabType;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MoveTabAction extends ApiAction<MoveTabForm>
    {
        @Override
        public ApiResponse execute(MoveTabForm form, BindException errors) throws Exception
        {
            final Map<String, Object> properties = new HashMap<String, Object>();
            Container tabContainer;

            if (getContainer().isContainerTab())
            {
                tabContainer = getContainer().getParent();
            }
            else
            {
                tabContainer = getContainer();
            }

            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<Portal.PortalPage>(Portal.getPages(tabContainer));
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

            return new ApiResponse()
            {
                @Override
                public Map<String, Object> getProperties()
                {
                    return properties;
                }
            };
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
        Map<String, Portal.PortalPage> pages = Portal.getPages(c, false);
        ArrayList<Portal.PortalPage> pagesList = new ArrayList<Portal.PortalPage>(pages.values());

        Collections.sort(pagesList, new Comparator<Portal.PortalPage>()
        {
            @Override
            public int compare(Portal.PortalPage o1, Portal.PortalPage o2)
            {
                return o1.getIndex() - o2.getIndex();
            }
        });

        int visibleIndex;
        for (visibleIndex = 0; visibleIndex < pagesList.size(); visibleIndex++)
        {
            if (pagesList.get(visibleIndex).getIndex() == page.getIndex())
            {
                break;
            }
        }

        if(visibleIndex == pagesList.size())
        {
            return null;
        }

        if (direction == Portal.MOVE_DOWN)
        {
            if(visibleIndex == pagesList.size() - 1)
            {
                return page;
            }

            Portal.PortalPage nextPage = pagesList.get(visibleIndex + 1);
            int newIndex = nextPage.getIndex();

            nextPage.setIndex(page.getIndex());
            page.setIndex(newIndex);

            DbScope scope = Portal.getSchema().getScope();
            try
            {
                scope.ensureTransaction();
                Portal.updatePortalPage(getContainer(), nextPage);
                Portal.updatePortalPage(getContainer(), page);
                scope.commitTransaction();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }

            return nextPage;
        }
        else
        {
            if(visibleIndex <= 1)
            {
                return page;
            }

            Portal.PortalPage prevPage = pagesList.get(visibleIndex - 1);
            int newIndex = prevPage.getIndex();

            prevPage.setIndex(page.getIndex());
            page.setIndex(newIndex);

            DbScope scope = Portal.getSchema().getScope();
            try
            {
                scope.ensureTransaction();
                Portal.updatePortalPage(getContainer(), prevPage);
                Portal.updatePortalPage(getContainer(), page);
                scope.commitTransaction();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }

            return prevPage;
        }
    }
}
