/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
package org.labkey.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.ApiXmlWriter;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientAPIAuditViewFactory;
import org.labkey.api.audit.ClientApiAuditProvider;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.collections.Sampler;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.etl.CachingDataIterator;
import org.labkey.api.etl.RemoveDuplicatesDataIterator;
import org.labkey.api.etl.ResultSetDataIterator;
import org.labkey.api.etl.SimpleTranslator;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.iterator.MarkableIterator;
import org.labkey.api.module.FirstRequestHandler;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeResourceLoader;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleResourceLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.HTMLDataLoader;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.NestedGroupsTest;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.PrincipalType;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.*;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.ContactWebPart;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.FolderMenu;
import org.labkey.api.view.template.MenuBarView;
import org.labkey.api.webdav.FileSystemAuditProvider;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.FileSystemBatchAuditProvider;
import org.labkey.api.webdav.FileSystemBatchAuditViewFactory;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.CustomizeMenuForm;
import org.labkey.core.admin.MenuViewFactory;
import org.labkey.core.admin.importer.FolderTypeImporterFactory;
import org.labkey.core.admin.importer.ModulePropertiesImporterFactory;
import org.labkey.core.admin.importer.PageImporterFactory;
import org.labkey.core.admin.importer.SearchSettingsImporterFactory;
import org.labkey.core.admin.importer.SubfolderImporterFactory;
import org.labkey.core.admin.logger.LoggerController;
import org.labkey.core.admin.miniprofiler.MiniProfilerController;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.admin.writer.FolderSerializationRegistryImpl;
import org.labkey.core.admin.writer.FolderTypeWriterFactory;
import org.labkey.core.admin.writer.ModulePropertiesWriterFactory;
import org.labkey.core.admin.writer.PageWriterFactory;
import org.labkey.core.admin.writer.SearchSettingsWriterFactory;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.portal.PortalJUnitTest;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.portal.UtilController;
import org.labkey.core.project.FolderNavigationForm;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.core.query.ContainerAuditProvider;
import org.labkey.core.query.ContainerAuditViewFactory;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.GroupAuditProvider;
import org.labkey.core.query.GroupAuditViewFactory;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UserAuditViewFactory;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.reader.DataLoaderServiceImpl;
import org.labkey.core.security.SecurityController;
import org.labkey.core.statistics.StatsServiceImpl;
import org.labkey.core.test.TestController;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.UserController;
import org.labkey.core.view.ShortURLServiceImpl;
import org.labkey.core.webdav.DavController;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;

import javax.servlet.ServletContextEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    public static final String EXPERIMENTAL_JSDOC = "experimental-jsdoc";

    public static final Logger LOG = Logger.getLogger(CoreModule.class);

    // Register dialect extra early, since we need to initialize the data sources before calling DefaultModule.initialize()
    static
    {
        SqlDialectManager.register(new PostgreSqlDialectFactory());
    }

//    NOTE: CoreModule name & version are now updated in module.properties. See #18923.
//
//    @Override
//    public String getName()
//    {
//        return CORE_MODULE_NAME;
//    }
//
//    @Override
//    public double getVersion()
//    {
//        return xx.xx;
//    }
//
    @Override
    public int compareTo(@NotNull Module m)
    {
        //core module always sorts first
        return (m instanceof CoreModule) ? 0 : -1;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected void init()
    {
        ServiceRegistry.get().registerService(ContainerService.class, ContainerManager.getContainerService());
        ServiceRegistry.get().registerService(FolderSerializationRegistry.class, FolderSerializationRegistryImpl.get());

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("test", TestController.class);
        addController("analytics", AnalyticsController.class);
        addController("project", ProjectController.class);
        addController("util", UtilController.class);
        addController("logger", LoggerController.class);
        addController("mini-profiler", MiniProfilerController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        FirstRequestHandler.addFirstRequestListener(new CoreFirstRequestHandler());
        RhinoService.register();
        ServiceRegistry.get().registerService(ThumbnailService.class, new ThumbnailServiceImpl());
        ServiceRegistry.get().registerService(DataLoaderService.I.class, new DataLoaderServiceImpl());
        ServiceRegistry.get().registerService(ShortURLService.class, new ShortURLServiceImpl());
        ServiceRegistry.get().registerService(StatsService.class, new StatsServiceImpl());
        AnalyticsServiceImpl.register();

        ModuleStaticResolverImpl.get();

        DefaultSchema.registerProvider("core", new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return true;
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new CoreQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        String projectRoot = AppProps.getInstance().getProjectRoot();
        if (projectRoot != null)
        {
            File root = new File(projectRoot);
            if (root.isDirectory())
            {
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", "API", root + "/server/api", root + "/build/modules/api");
                ModuleLoader.getInstance().registerResourcePrefix("/org/labkey/api", "Internal", root + "/server/internal", root + "/build/modules/internal");
            }
        }

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_JSDOC, "Javascript Documentation", "Displays LabKey javascript API's from the Developer Links menu.", false);
        AdminConsole.addExperimentalFeatureFlag(MenuBarView.EXPERIMENTAL_NAV, "Combined Navigation Drop-down",
                "This feature will combine the Navigation of Projects and Folders into one drop-down.", false);
    }

    @NotNull
    @Override
    public Set<? extends ModuleResourceLoader> getResourceLoaders()
    {
        return PageFlowUtil.set(new FolderTypeResourceLoader());
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new AlwaysAvailableWebPartFactory("Contacts")
                {
                    public WebPartView getWebPartView(ViewContext ctx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
                    {
                        return new ContactWebPart();
                    }
                },
                new BaseWebPartFactory("FolderNav")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        FolderNavigationForm form = getForm(portalCtx);

                        final FolderMenu folders = new FolderMenu(portalCtx);
                        form.setFolderMenu(folders);

                        JspView<FolderNavigationForm> view = new JspView<>("/org/labkey/core/project/folderNav.jsp", form);
                        view.setTitle("Folder Navigation");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }

                    private FolderNavigationForm getForm(ViewContext context)
                    {
                        FolderNavigationForm form = new FolderNavigationForm();
                        form.setPortalContext(context);
                        return form;
                    }
                },
                new BaseWebPartFactory("Workbooks")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        UserSchema schema = QueryService.get().getUserSchema(portalCtx.getUser(), portalCtx.getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
                        WorkbookQueryView wbqview = new WorkbookQueryView(portalCtx, schema);
                        VBox box = new VBox(new WorkbookSearchView(wbqview), wbqview);
                        box.setFrame(WebPartView.FrameType.PORTAL);
                        box.setTitle("Workbooks");
                        return box;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return !c.isWorkbook() && location.equalsIgnoreCase(HttpView.BODY);
                    }
                },
                new BaseWebPartFactory("Workbook Description")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView view = new JspView("/org/labkey/core/workbook/workbookDescription.jsp");
                        view.setTitle("Workbook Description");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }
                },
                new AlwaysAvailableWebPartFactory("Projects")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);

                        String title = webPart.getPropertyMap().containsKey("title") ? webPart.getPropertyMap().get("title") : "Projects";
                        view.setTitle(title);

                        if (portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
                        {
                            NavTree customize = new NavTree("");
                            customize.setScript("customizeProjectWebpart(" + webPart.getRowId() + ", \'" + webPart.getPageId() + "\', " + webPart.getIndex() + ");");
                            view.setCustomize(customize);
                        }
                        return view;
                    }
                },
                new BaseWebPartFactory("ProjectNav")
                {
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/core/project/projectNav.jsp", webPart);
                        view.setTitle("Project Navigation");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }
                },
                new AlwaysAvailableWebPartFactory("Custom Menu", WebPartFactory.LOCATION_MENUBAR, true, true) {
                    public WebPartView getWebPartView(final ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        final CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                        String title = "My Menu";
                        if (form.getTitle() != null && !form.getTitle().equals(""))
                            title = form.getTitle();

                        WebPartView view;
                        if (form.isChoiceListQuery())
                        {
                            view = MenuViewFactory.createMenuQueryView(portalCtx, title, form);
                        }
                        else
                        {
                            view = MenuViewFactory.createMenuFolderView(portalCtx, title, form);
                        }
                        view.setFrame(WebPartView.FrameType.PORTAL);
                        return view;
                    }

                    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
                    {
                        CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                        JspView<CustomizeMenuForm> view = new JspView<>("/org/labkey/core/admin/customizeMenu.jsp", form);
                        view.setTitle(form.getTitle());
                        view.setFrame(WebPartView.FrameType.PORTAL);
                        return view;
                    }
                },
                new BaseWebPartFactory("BetaNav")
                {
                    @Override
                    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
                    {
                        FolderNavigationForm form = getForm(portalCtx);

                        final FolderMenu folders = new FolderMenu(portalCtx);
                        form.setFolderMenu(folders);

                        JspView<FolderNavigationForm> view = new JspView<>("/org/labkey/core/project/betaNav.jsp", form);
                        view.setTitle("Beta Navigation");
                        view.setFrame(WebPartView.FrameType.NONE);
                        return view;
                    }

                    @Override
                    public boolean isAvailable(Container c, String location)
                    {
                        return false;
                    }

                    private FolderNavigationForm getForm(ViewContext context)
                    {
                        FolderNavigationForm form = new FolderNavigationForm();
                        form.setPortalContext(context);
                        return form;
                    }
                }
        ));
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            bootstrap();

        // Increment on every core module upgrade to defeat browser caching of static resources.
        WriteableAppProps.incrementLookAndFeelRevisionAndSave();

        // Allow dialect to make adjustments to the just upgraded core database (e.g., install aggregate functions, etc.)
        CoreSchema.getInstance().getSqlDialect().afterCoreUpgrade(moduleContext);

        // The core SQL scripts install aggregate functions and other objects that dialects need to know about. Prepare the
        // dialects again to make sure they're aware of all the changes. Prepare all the scopes because we could have more
        // than one scope pointed at the core database (e.g., external schemas). See #17077 (pg example) and #19177 (ss example)
        for (DbScope scope : DbScope.getDbScopes())
            scope.getSqlDialect().prepare(scope);
    }


    private void bootstrap()
    {
        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
        GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers", PrincipalType.ROLE);

        // Other containers inherit permissions from root; admins get all permissions, users & guests none
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role siteAdminRole = RoleManager.getRole(SiteAdminRole.class);
        Role readerRole = RoleManager.getRole(ReaderRole.class);

        ContainerManager.bootstrapContainer("/", siteAdminRole, noPermsRole, noPermsRole);

        // Users & guests can read from /home
        ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, siteAdminRole, readerRole, readerRole);

        // Only users can read from /Shared
        ContainerManager.bootstrapContainer(ContainerManager.SHARED_CONTAINER_PATH, siteAdminRole, readerRole, noPermsRole);

        try
        {
            // Need to insert standard MV indicators for the root -- okay to call getRoot() since we just created it.
            Container rootContainer = ContainerManager.getRoot();
            String rootContainerId = rootContainer.getId();
            TableInfo mvTable = CoreSchema.getInstance().getTableInfoMvIndicators();

            for (Map.Entry<String,String> qcEntry : MvUtil.getDefaultMvIndicators().entrySet())
            {
                Map<String, Object> params = new HashMap<>();
                params.put("Container", rootContainerId);
                params.put("MvIndicator", qcEntry.getKey());
                params.put("Label", qcEntry.getValue());

                Table.insert(null, mvTable, params);
            }
        }
        catch (Throwable t)
        {
            ExceptionUtil.logExceptionToMothership(null, t);
        }
    }


    @Override
    public CoreUpgradeCode getUpgradeCode()
    {
        return new CoreUpgradeCode();
    }


    @Override
    public void destroy()
    {
        super.destroy();
        UsageReportingLevel.cancelUpgradeCheck();
    }


    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // Any containers in the cache have bogus folder types since they aren't registered until startup().  See #10310
        ContainerManager.clearCache();

        // This listener deletes all properties; make sure it executes after most of the other listeners
        ContainerManager.addContainerListener(new CoreContainerListener(), ContainerManager.ContainerListener.Order.Last);
        ContainerManager.addContainerListener(new FolderSettingsCache.FolderSettingsCacheListener());
        SecurityManager.init();
        ModuleLoader.getInstance().registerFolderType(this, FolderType.NONE);
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        SystemMaintenance.setTimer();

        AuditLogService.get().addAuditViewFactory(UserAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(GroupAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(AttachmentAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ContainerAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(FileSystemBatchAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(ClientAPIAuditViewFactory.getInstance());

        AuditLogService.get().registerAuditType(new UserAuditProvider());
        AuditLogService.get().registerAuditType(new GroupAuditProvider());
        AuditLogService.get().registerAuditType(new AttachmentAuditProvider());
        AuditLogService.get().registerAuditType(new ContainerAuditProvider());
        AuditLogService.get().registerAuditType(new FileSystemAuditProvider());
        AuditLogService.get().registerAuditType(new FileSystemBatchAuditProvider());
        AuditLogService.get().registerAuditType(new ClientApiAuditProvider());

        TempTableTracker.init();
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(DavController.getShutdownListener());

        // Export action stats on graceful shutdown
        ContextListener.addShutdownListener(new ShutdownListener() {
            @Override
            public String getName()
            {
                return "Action stats export";
            }

            @Override
            public void shutdownPre(ServletContextEvent servletContextEvent)
            {
                Logger logger = Logger.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    LOG.info("Starting to log statistics for actions prior to web application shut down");
                    Appender appender = logger.getAppender("ACTION_STATS");

                    if (null != appender && appender instanceof RollingFileAppender)
                        ((RollingFileAppender)appender).rollOver();
                    else
                        Logger.getLogger(CoreModule.class).warn("Could not rollover the action stats tsv file--there was no appender named ACTION_STATS, or it is not a RollingFileAppender.");

                    TSVWriter writer = new ActionsTsvWriter();
                    StringBuilder buf = new StringBuilder();

                    try
                    {
                        writer.write(buf);
                    }
                    catch (IOException e)
                    {
                        Logger.getLogger(CoreModule.class).error("Exception exporting action stats", e);
                    }

                    logger.info(buf.toString());
                    LOG.info("Completed logging statistics for actions prior to web application shut down");
                }
            }

            @Override
            public void shutdownStarted(ServletContextEvent servletContextEvent)
            {
            }
        });

        AdminController.registerAdminConsoleLinks();
        AnalyticsController.registerAdminConsoleLinks();
        UserController.registerAdminConsoleLinks();
        LoggerController.registerAdminConsoleLinks();

        WebdavService.get().setResolver(WebdavResolverImpl.get());
        ModuleLoader.getInstance().registerFolderType(this, new WorkbookFolderType());

        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null != ss)
        {
            ss.addDocumentProvider(this);
        }

        SecurityManager.addViewFactory(new SecurityController.GroupDiagramViewFactory());

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != fsr)
        {
            fsr.addFactories(new FolderTypeWriterFactory(), new FolderTypeImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
            fsr.addFactories(new ModulePropertiesWriterFactory(), new ModulePropertiesImporterFactory());
            fsr.addImportFactory(new SubfolderImporterFactory());
        }

        // Register the default DataLoaders.
        // The DataLoaderFactories also register a SearchService.DocumentParser so this should be done after SearchService is available.
        DataLoaderService.I dls = DataLoaderService.get();
        if (dls != null)
        {
            dls.registerFactory(new ExcelLoader.Factory());
            dls.registerFactory(new TabLoader.TsvFactory());
            dls.registerFactory(new TabLoader.CsvFactory());
            dls.registerFactory(new HTMLDataLoader.Factory());
            dls.registerFactory(new JSONDataLoader.Factory());
            dls.registerFactory(new FastaDataLoader.Factory());
        }

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_CONTAINER_RELATIVE_URL,
                "Container Relative URL",
                "Use container relative URLs of the form /labkey/container/controller-action instead of the current /labkey/controller/container/action URLs.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP,
                "Client-side Exception Logging To Mothership",
                "Report unhandled JavaScript exceptions to mothership.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER,
                "Client-side Exception Logging To Server",
                "Report unhandled JavaScript exceptions to the server log.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_RSERVE_REPORTING,
                "Rserve Reports",
                "Use an R Server for R script evaluation instead of running R from a command shell.",
                false);

        PropertyService.get().registerDomainKind(new UsersDomainKind());
        UsersDomainKind.ensureDomain(moduleContext);
    }

    @Override
    public String getTabName(ViewContext context)
    {
        return "Portal";
    }


    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        if (user == null)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
        else
        {
            return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c);

        }
//        else if (c != null && "/".equals(c.getPath()) && user.isAdministrator())
//        {
//            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
//        }
//        else if (c != null && c.hasPermission(user, AdminPermission.class))
//        {
//            return PageFlowUtil.urlProvider(SecurityUrls.class).getProjectURL(c);
//        }
//        else
//        {
//            return PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), AppProps.getInstance().getHomePageActionURL());
//        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT;
    }


    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<Class>(Arrays.asList(
                Table.TestCase.class,
                Table.DataIteratorTestCase.class,
                DbSchema.TestCase.class,
                TableViewFormTestCase.class,
                ActionURL.TestCase.class,
                SecurityManager.TestCase.class,
                PropertyManager.TestCase.class,
                ContainerManager.TestCase.class,
                TabLoader.TabLoaderTestCase.class,
                MapLoader.MapLoaderTestCase.class,
                GroupManager.TestCase.class,
                SecurityController.TestCase.class,
                AttachmentServiceImpl.TestCase.class,
                WebdavResolverImpl.TestCase.class,
                MimeMap.TestCase.class,
                ModuleStaticResolverImpl.TestCase.class,
                StorageProvisioner.TestCase.class,
                RhinoService.TestCase.class,
                DbScope.TransactionTestCase.class,
                SimpleTranslator.TranslateTestCase.class,
                ResultSetDataIterator.TestCase.class,
                ExceptionUtil.TestCase.class,
                ViewCategoryManager.TestCase.class,
                TableSelectorTestCase.class,
                SqlSelectorTestCase.class,
                ResultSetSelectorTestCase.class,
                NestedGroupsTest.class,
                ModulePropertiesTestCase.class,
                PortalJUnitTest.class,
                ContainerDisplayColumn.TestCase.class,
                AliasManager.TestCase.class,
                AtomicDatabaseInteger.TestCase.class,
                DbSequenceManager.TestCase.class,
                //RateLimiter.TestCase.class,
                StatementUtils.TestCase.class,
                Encryption.TestCase.class
        ));

        testClasses.addAll(SqlDialectManager.getAllJUnitTests());

        return testClasses;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
                DateUtil.TestCase.class,
                TSVWriter.TestCase.class,
                TSVMapWriter.Tests.class,
                ExcelLoader.ExcelLoaderTestCase.class,
                ExcelFactory.ExcelFactoryTestCase.class,
                ModuleDependencySorter.TestCase.class,
                DateUtil.TestCase.class,
                DatabaseCache.TestCase.class,
                PasswordExpiration.TestCase.class,
                BooleanFormat.TestCase.class,
                FileUtil.TestCase.class,
                FileType.TestCase.class,
                MemTracker.TestCase.class,
                StringExpressionFactory.TestCase.class,
                Path.TestCase.class,
                Lsid.TestCase.class,
                HString.TestCase.class,
                PageFlowUtil.TestCase.class,
                ResultSetUtil.TestCase.class,
                ArrayListMap.TestCase.class,
                DbScope.DialectTestCase.class,
                ValidEmail.TestCase.class,
                RemoveDuplicatesDataIterator.DeDuplicateTestCase.class,
                CachingDataIterator.ScrollTestCase.class,
                StringUtilsLabKey.TestCase.class,
                Compress.TestCase.class,
                ExtUtil.TestCase.class,
                JsonTest.class,
                ExtUtil.TestCase.class,
                ReplacedRunFilter.TestCase.class,
                MultiValuedRenderContext.TestCase.class,
                SubfolderWriter.TestCase.class,
                Aggregate.TestCase.class,
                CaseInsensitiveHashSet.TestCase.class,
                SwapQueue.TestCase.class,
                ApiXmlWriter.TestCase.class,
                TidyUtil.TestCase.class,
                JSONDataLoader.HeaderMatchTest.class,
                JSONDataLoader.MetadataTest.class,
                JSONDataLoader.RowTest.class,
                EmailTemplate.TestCase.class,
                HelpTopic.TestCase.class,
                CaseInsensitiveHashMap.TestCase.class,
                StatsServiceImpl.TestCase.class,
                NumberUtilsLabKey.TestCase.class,
                SimpleFilter.FilterTestCase.class,
                SimpleFilter.InClauseTestCase.class,
                SimpleFilter.BetweenClauseTestCase.class,
                InlineInClauseGenerator.TestCase.class,
                CollectionUtils.TestCase.class,
                MarkableIterator.TestCase.class,
                Sampler.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Collection<String> getSchemaNames()
    {
        return Arrays.asList
            (
                CoreSchema.getInstance().getSchemaName(),       // core
                PropertySchema.getInstance().getSchemaName(),   // prop
                TestSchema.getInstance().getSchemaName()        // test
            );
    }

    @NotNull
    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        Set<DbSchema> result = new LinkedHashSet<>(super.getSchemasToTest());

        // Add the "labkey" schema in all module data sources as well... should match labkey.xml
        for (String dataSourceName : ModuleLoader.getInstance().getAllModuleDataSources())
        {
            DbScope scope = DbScope.getDbScope(dataSourceName);
            result.add(scope.getLabKeySchema());
        }

        return result;
    }

    @NotNull
    @Override
    public List<File> getStaticFileDirectories()
    {
        List<File> dirs = super.getStaticFileDirectories();
        if (AppProps.getInstance().isDevMode())
        {
            if (null != getSourcePath())
            {
                dirs.add(0, new File(getSourcePath(), "../../internal/webapp"));
                dirs.add(0, new File(getSourcePath(), "../../api/webapp"));
            }
        }
        return dirs;
    }

    @Override
    public void enumerateDocuments(SearchService.IndexTask task, @NotNull Container c, Date since)
    {
        SearchService ss = ServiceRegistry.get(SearchService.class);
        if (ss == null)
            return;

        if (null == task)
            task = ss.defaultTask();

        if (c.isRoot())
            return;

        Container p = c.getProject();
        assert null != p;
        String title;
        String keywords;
        String body;

        // UNDONE: generalize to other folder types
        StudyService.Service svc = StudyService.get();
        Study study = svc != null ? svc.getStudy(c) : null;

        if (null != study)
        {
            title = study.getSearchDisplayTitle();
            keywords = study.getSearchKeywords();
            body = study.getSearchBody();
        }
        else
        {
            String type;

            if (c.isProject())
                type = "Project";
            else if (c.isWorkbook())
                type = "Workbook";
            else
                type = "Folder";

            String containerTitle = c.getTitle();

            String description = StringUtils.trimToEmpty(c.getDescription());
            title = type + " -- " + containerTitle;
            User u_user = UserManager.getUser(c.getCreatedBy());
            String user = (u_user == null) ? "" : u_user.getDisplayName(User.getSearchUser());
            keywords = description + " " + type + " " + user;
            body = type + " " + containerTitle + (c.isProject() ? "" : " in Project " + p.getName());
            body += "\n" + description;
        }

        String identifiers = c.getName();

        Map<String, Object> properties = new HashMap<>();

        assert (null != keywords);
        properties.put(SearchService.PROPERTY.indentifiersMed.toString(), identifiers);
        properties.put(SearchService.PROPERTY.keywordsMed.toString(), keywords);
        properties.put(SearchService.PROPERTY.title.toString(), title);
        properties.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
        ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
        startURL.setExtraPath(c.getId());
        WebdavResource doc = new SimpleDocumentResource(c.getParsedPath(),
                "link:" + c.getId(),
                c.getId(),
                "text/plain",
                body.getBytes(),
                startURL,
                properties);
        task.addResource(doc, SearchService.PRIORITY.item);
    }

    
    @Override
    public void indexDeleted()
    {
        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.Documents SET LastIndexed = NULL");
    }
}
