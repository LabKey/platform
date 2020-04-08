/*
 * Copyright (c) 2005-2018 Fred Hutchinson Cancer Research Center
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

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AdminConsoleService;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.HealthCheck;
import org.labkey.api.admin.HealthCheckRegistry;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientApiAuditProvider;
import org.labkey.api.audit.DefaultAuditProvider;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.data.dialect.SqlDialectRegistry;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.TestDomainKind;
import org.labkey.api.files.FileContentService;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.HTMLDataLoader;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider;
import org.labkey.api.security.DummyAntiVirusService;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPointcutService;
import org.labkey.api.security.SecurityPointcutServiceImpl;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.WikiTermsOfUseProvider;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.QCAnalystPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.CustomLabelService;
import org.labkey.api.settings.CustomLabelService.CustomLabelServiceImpl;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.settings.ExperimentalFeatureService.ExperimentalFeatureServiceImpl;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.LookAndFeelPropertiesManager;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.CommandLineTokenizer;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.vcs.VcsService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.FolderMenu;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.webdav.FileSystemBatchAuditProvider;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.UserResolverImpl;
import org.labkey.api.webdav.WebFilesResolverImpl;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminConsoleServiceImpl;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.CopyFileRootPipelineJob;
import org.labkey.core.admin.CustomizeMenuForm;
import org.labkey.core.admin.FilesSiteSettingsAction;
import org.labkey.core.admin.MenuViewFactory;
import org.labkey.core.admin.importer.FolderTypeImporterFactory;
import org.labkey.core.admin.importer.ModulePropertiesImporterFactory;
import org.labkey.core.admin.importer.PageImporterFactory;
import org.labkey.core.admin.importer.RoleAssignmentsImporterFactory;
import org.labkey.core.admin.importer.SearchSettingsImporterFactory;
import org.labkey.core.admin.importer.SecurityGroupImporterFactory;
import org.labkey.core.admin.importer.SubfolderImporterFactory;
import org.labkey.core.admin.logger.LoggerController;
import org.labkey.core.admin.miniprofiler.MiniProfilerController;
import org.labkey.core.admin.sitevalidation.SiteValidationServiceImpl;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.admin.test.SchemaXMLTestCase;
import org.labkey.core.admin.usageMetrics.UsageMetricsServiceImpl;
import org.labkey.core.admin.writer.FolderSerializationRegistryImpl;
import org.labkey.core.admin.writer.FolderTypeWriterFactory;
import org.labkey.core.admin.writer.ModulePropertiesWriterFactory;
import org.labkey.core.admin.writer.PageWriterFactory;
import org.labkey.core.admin.writer.RoleAssignmentsWriterFactory;
import org.labkey.core.admin.writer.SearchSettingsWriterFactory;
import org.labkey.core.admin.writer.SecurityGroupWriterFactory;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.dialect.PostgreSql92Dialect;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.dialect.PostgreSqlVersion;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.notification.EmailPreferenceConfigServiceImpl;
import org.labkey.core.notification.EmailPreferenceContainerListener;
import org.labkey.core.notification.EmailPreferenceUserListener;
import org.labkey.core.notification.EmailServiceImpl;
import org.labkey.core.notification.NotificationController;
import org.labkey.core.notification.NotificationServiceImpl;
import org.labkey.core.portal.CollaborationFolderType;
import org.labkey.core.portal.PortalJUnitTest;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.portal.UtilController;
import org.labkey.core.products.ProductController;
import org.labkey.core.project.FolderNavigationForm;
import org.labkey.core.qc.QCStateImporter;
import org.labkey.core.qc.QCStateWriter;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.reader.DataLoaderServiceImpl;
import org.labkey.core.reports.ScriptEngineManagerImpl;
import org.labkey.core.security.SecurityApiActions;
import org.labkey.core.security.SecurityController;
import org.labkey.core.security.validators.PermissionsValidator;
import org.labkey.core.statistics.AnalyticsProviderRegistryImpl;
import org.labkey.core.statistics.StatsServiceImpl;
import org.labkey.core.statistics.SummaryStatisticRegistryImpl;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.UserController;
import org.labkey.core.vcs.VcsServiceImpl;
import org.labkey.core.view.ShortURLServiceImpl;
import org.labkey.core.view.template.bootstrap.CoreWarningProvider;
import org.labkey.core.view.template.bootstrap.ViewServiceImpl;
import org.labkey.core.view.template.bootstrap.WarningServiceImpl;
import org.labkey.core.webdav.DavController;
import org.labkey.core.wiki.MarkdownServiceImpl;
import org.labkey.core.wiki.RadeoxRenderer;
import org.labkey.core.wiki.WikiRenderingServiceImpl;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(CoreModule.class);

    static
    {
        // Accept most of the standard Quartz properties, but set a system property to skip Quartz's update check.
        // These properties need to be set here (previously set in startBackgroundThreads()), so that if any other module touches Quartz in its setup, it initializes with these setting.
        Properties props = System.getProperties();
        props.setProperty(StdSchedulerFactory.PROP_SCHED_SKIP_UPDATE_CHECK, "true");
        props.setProperty("org.quartz.jobStore.misfireThreshold", "300000");

        // Register dialect extra early, since we need to initialize the data sources before calling DefaultModule.initialize()
        SqlDialectRegistry.register(new PostgreSqlDialectFactory());
    }

    @Override
    public int compareTo(@NotNull Module m)
    {
        //core module always sorts first
        // TODO: Nice try, but this doesn't work consistently, since no one told DefaultModule.compareTo() that core is special -- fix this or remove the override
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
        ContainerService.setInstance(new ContainerServiceImpl());
        FolderSerializationRegistry.setInstance(new FolderSerializationRegistryImpl());

        // Register the default DataLoaders during init so they are available to sql upgrade scripts
        DataLoaderServiceImpl dls = new DataLoaderServiceImpl();
        dls.registerFactory(new ExcelLoader.Factory());
        dls.registerFactory(new TabLoader.TsvFactory());
        dls.registerFactory(new TabLoader.CsvFactory());
        dls.registerFactory(new HTMLDataLoader.Factory());
        dls.registerFactory(new JSONDataLoader.Factory());
        dls.registerFactory(new FastaDataLoader.Factory());
        DataLoaderService.setInstance(dls);

        addController("admin", AdminController.class);
        addController("admin-sql", SqlScriptController.class);
        addController("security", SecurityController.class);
        addController("user", UserController.class);
        addController("login", LoginController.class);
        addController("junit", JunitController.class);
        addController("core", CoreController.class);
        addController("analytics", AnalyticsController.class);
        addController("project", ProjectController.class);
        addController("util", UtilController.class);
        addController("logger", LoggerController.class);
        addController("mini-profiler", MiniProfilerController.class);
        addController("notification", NotificationController.class);
        addController("product", ProductController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.setInstance(new AttachmentServiceImpl());
        AnalyticsService.setInstance(new AnalyticsServiceImpl());
        RhinoService.register();
        CacheManager.addListener(RhinoService::clearCaches);
        NotificationService.setInstance(NotificationServiceImpl.getInstance());

        ViewService.setInstance(ViewServiceImpl.getInstance());
        ExperimentalFeatureService.setInstance(new ExperimentalFeatureServiceImpl());
        ThumbnailService.setInstance(new ThumbnailServiceImpl());
        ShortURLService.setInstance(new ShortURLServiceImpl());
        StatsService.setInstance(new StatsServiceImpl());
        SiteValidationService.setInstance(new SiteValidationServiceImpl());
        AnalyticsProviderRegistry.setInstance(new AnalyticsProviderRegistryImpl());
        SummaryStatisticRegistry.setInstance(new SummaryStatisticRegistryImpl());
        UsageMetricsService.setInstance(new UsageMetricsServiceImpl());
        CustomLabelService.setInstance(new CustomLabelServiceImpl());
        WarningService.setInstance(new WarningServiceImpl());
        SecurityPointcutService.setInstance(new SecurityPointcutServiceImpl());
        AdminConsoleService.setInstance(new AdminConsoleServiceImpl());
        WikiRenderingService.setInstance(new WikiRenderingServiceImpl());
        VcsService.setInstance(new VcsServiceImpl());
        ServiceRegistry.get().registerService(LabkeyScriptEngineManager.class, new ScriptEngineManagerImpl());

        try
        {
            ContainerTypeRegistry.get().register("normal", new NormalContainerType());
            ContainerTypeRegistry.get().register("tab", new TabContainerType());
            ContainerTypeRegistry.get().register("workbook", new WorkbookContainerType());
        }
        catch (Exception e)
        {
            throw new UnexpectedException(e);
        }

        WarningService.get().register(new CoreWarningProvider());

        WebdavService.get().setResolver(ModuleStaticResolverImpl.get());
        // need to register webdav resolvers in init() instead of startupAfterSpringConfig since static module files are loaded during module startup
        WebdavService.get().registerRootResolver(WebdavResolverImpl.get());
        WebdavService.get().registerRootResolver(WebFilesResolverImpl.get());
        if (AppProps.getInstance().isExperimentalFeatureEnabled(AppProps.EXPERIMENTAL_USER_FOLDERS))
        {
            WebdavService.get().registerRootResolver(UserResolverImpl.get());
        }

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

        AdminConsole.addExperimentalFeatureFlag(NotificationMenuView.EXPERIMENTAL_NOTIFICATION_MENU, "Notifications Menu",
                "Notifications 'inbox' count display in the header bar with click to show the notifications panel of unread notifications.", false);
        AdminConsole.addExperimentalFeatureFlag(RemapCache.EXPERIMENTAL_RESOLVE_LOOKUPS_BY_VALUE, "Resolve lookups by Value",
                "This feature will attempt to resolve lookups by value through the UI insert/update form. This can be useful when the " +
                        "lookup list is long (> 10000) and the UI stops rendering a dropdown.", false);

        SiteValidationService svc = SiteValidationService.get();
        if (null != svc)
        {
            svc.registerProvider("core", new PermissionsValidator());
        }

        registerHealthChecks();

        ContextListener.addNewInstallCompleteListener(() -> sendSystemReadyEmail(UserManager.getAppAdmins()));
    }

    private void registerHealthChecks()
    {
        HealthCheckRegistry.get().registerHealthCheck("database",  HealthCheckRegistry.DEFAULT_CATEGORY, () ->
            {
                Map<String, Object> healthValues = new HashMap<>();
                boolean allConnected = true;
                for (DbScope dbScope : DbScope.getDbScopes())
                {
                    boolean dbConnected;
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

                return new HealthCheck.Result(allConnected, healthValues);
            }
        );

        HealthCheckRegistry.get().registerHealthCheck("modules", HealthCheckRegistry.TRIAL_INSTANCES_CATEGORY, () -> {
            Map<String, Throwable> failures =  ModuleLoader.getInstance().getModuleFailures();
            Map<String, Object> failureDetails = new HashMap<>();
            for (Map.Entry<String, Throwable> failure : failures.entrySet())
            {
                failureDetails.put(failure.getKey(), failure.getValue().getMessage());
            }
            return new HealthCheck.Result(failures.isEmpty(), failureDetails);
        });

        HealthCheckRegistry.get().registerHealthCheck("users",  HealthCheckRegistry.TRIAL_INSTANCES_CATEGORY, () -> {
            Map<String, Object> userHealth = new HashMap<>();
            ZonedDateTime now = ZonedDateTime.now();
            int userCount = UserManager.getUserCount(Date.from(now.toInstant()));
            userHealth.put("registeredUsers", userCount);
            return new HealthCheck.Result(userCount > 0, userHealth);
        });
    }

    private void sendSystemReadyEmail(List<User> users)
    {
        if (users.isEmpty())
            return;

        Collection<ConfigProperty> properties = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_SITE_SETTINGS);
        String fromEmail = null;
        String subject = null;
        String body = null;
        for (ConfigProperty prop : properties)
        {
            if (prop.getName().equalsIgnoreCase("siteAvailableEmailMessage"))
                body = StringUtils.trimToNull(prop.getValue());
            else if (prop.getName().equalsIgnoreCase("siteAvailableEmailSubject"))
                subject = StringUtils.trimToNull(prop.getValue());
            else if (prop.getName().equalsIgnoreCase("siteAvailableEmailFrom"))
                fromEmail = StringUtils.trimToNull(prop.getValue());

        }
        if (fromEmail == null || subject == null || body == null)
            return;

        EmailService svc = EmailService.get();
        List<EmailMessage> messages = new ArrayList<>();
        for (User user: users)
        {
            EmailMessage message = svc.createMessage(fromEmail, Collections.singletonList(user.getEmail()), subject);
            message.addContent(MimeMap.MimeType.HTML, body);
            messages.add(message);
        }
        // For audit purposes, we use the first user as the originator of the message.
        // Would be better to have this be a site admin, but we aren't guaranteed to have such a user
        // for hosted sites.  Another option is to use the guest user here, but that's strange.
        svc.sendMessages(messages, users.get(0), ContainerManager.getRoot());
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
            new AlwaysAvailableWebPartFactory("Contacts")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext ctx, @NotNull WebPart webPart)
                {
                    UserSchema schema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), CoreQuerySchema.NAME);
                    QuerySettings settings = new QuerySettings(ctx, QueryView.DATAREGIONNAME_DEFAULT);

                    settings.setQueryName(CoreQuerySchema.USERS_TABLE_NAME);

                    QueryView view = schema.createView(ctx, settings, null);
                    view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
                    view.setFrame(WebPartView.FrameType.PORTAL);
                    view.setTitle("Project Contacts");

                    return view;
                }
            },
            new BaseWebPartFactory("FolderNav")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    FolderNavigationForm form = getForm(portalCtx);

                    form.setFolderMenu(new FolderMenu(portalCtx));

                    JspView<FolderNavigationForm> view = new JspView<>("/org/labkey/core/project/folderNav.jsp", form);
                    view.setTitle("Folder Navigation");
                    view.setFrame(WebPartView.FrameType.NONE);
                    return view;
                }

                @Override
                public boolean isAvailable(Container c, String scope, String location)
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
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    UserSchema schema = QueryService.get().getUserSchema(portalCtx.getUser(), portalCtx.getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
                    WorkbookQueryView wbqview = new WorkbookQueryView(portalCtx, schema);
                    VBox box = new VBox(new WorkbookSearchView(wbqview), wbqview);
                    box.setFrame(WebPartView.FrameType.PORTAL);
                    box.setTitle("Workbooks");
                    return box;
                }

                @Override
                public boolean isAvailable(Container c, String scope, String location)
                {
                    return !c.isWorkbook() && "folder".equals(scope) && location.equalsIgnoreCase(HttpView.BODY);
                }
            },
            new BaseWebPartFactory("Workbook Description")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView view = new JspView("/org/labkey/core/workbook/workbookDescription.jsp");
                    view.setTitle("Workbook Description");
                    view.setFrame(WebPartView.FrameType.NONE);
                    return view;
                }

                @Override
                public boolean isAvailable(Container c, String scope, String location)
                {
                    return false;
                }
            },
            new AlwaysAvailableWebPartFactory("Projects")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);

                    String title = webPart.getPropertyMap().getOrDefault("title", "Projects");
                    view.setTitle(title);

                    if (portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
                    {
                        NavTree customize = new NavTree("");
                        customize.setScript("customizeProjectWebpart(" + webPart.getRowId() + ", '" + webPart.getPageId() + "', " + webPart.getIndex() + ");");
                        view.setCustomize(customize);
                    }
                    return view;
                }
            },
            new AlwaysAvailableWebPartFactory("Subfolders")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);

                    if (webPart.getPropertyMap().isEmpty())
                    {
                        // Configure to show subfolders if not previously configured
                        webPart.getPropertyMap().put("title", "Subfolders");
                        webPart.getPropertyMap().put("containerFilter", ContainerFilter.Type.CurrentAndFirstChildren.name());
                        webPart.getPropertyMap().put("containerTypes", "folder");
                    }

                    String title = webPart.getPropertyMap().get("title");
                    view.setTitle(title);

                    if (portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
                    {
                        NavTree customize = new NavTree("");
                        customize.setScript("customizeProjectWebpart(" + webPart.getRowId() + ", '" + webPart.getPageId() + "', " + webPart.getIndex() + ");");
                        view.setCustomize(customize);
                    }
                    return view;
                }
            },
            new AlwaysAvailableWebPartFactory("Custom Menu", true, true, WebPartFactory.LOCATION_MENUBAR)
            {
                @Override
                public WebPartView getWebPartView(@NotNull final ViewContext portalCtx, @NotNull WebPart webPart)
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

                @Override
                public HttpView getEditView(WebPart webPart, ViewContext context)
                {
                    CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                    JspView<CustomizeMenuForm> view = new JspView<>("/org/labkey/core/admin/customizeMenu.jsp", form);
                    view.setTitle(form.getTitle());
                    view.setFrame(WebPartView.FrameType.PORTAL);
                    return view;
                }
            },
            new BaseWebPartFactory("MenuProjectNav")
            {
                @Override
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/menuProjectNav.jsp", webPart);
                    view.setTitle("Menu Project Navigation");
                    view.setFrame(WebPartView.FrameType.NONE);
                    return view;
                }

                @Override
                public boolean isAvailable(Container c, String scope, String location)
                {
                    return false;
                }
            }
        ));
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
        {
            bootstrap(moduleContext.getUpgradeUser());
        }

        // Increment on every core module upgrade to defeat browser caching of static resources.
        WriteableAppProps.incrementLookAndFeelRevisionAndSave();

        // Allow dialect to make adjustments to the just upgraded core database (e.g., install aggregate functions, etc.)
        CoreSchema.getInstance().getSqlDialect().afterCoreUpgrade(moduleContext);

        // The core SQL scripts install aggregate functions and other objects that dialects need to know about. Prepare the
        // dialects again to make sure they're aware of all the changes. Prepare all the scopes because we could have more
        // than one scope pointed at the core database (e.g., external schemas). See #17077 (pg example) and #19177 (ss example)
        for (DbScope scope : DbScope.getDbScopes())
            scope.getSqlDialect().prepare(scope);

        // Now that we know the standard containers have been created, add a listener that warms the just-cleared caches with
        // core.Containers meta data and a few common containers. This may prevent some deadlocks during upgrade, #33550.
        CacheManager.addListener(() -> {
            ContainerManager.getRoot();
            ContainerManager.getHomeContainer();
            ContainerManager.getSharedContainer();
        });

        if (moduleContext.getInstalledVersion() < 18.30)
        {
            new CoreUpgradeCode().purgeDeveloperRole();
        }
    }


    private void bootstrap(User upgradeUser)
    {
        // Create the initial groups
        GroupManager.bootstrapGroup(Group.groupAdministrators, "Administrators");
        GroupManager.bootstrapGroup(Group.groupUsers, "Users");
        GroupManager.bootstrapGroup(Group.groupGuests, "Guests");
        GroupManager.bootstrapGroup(Group.groupDevelopers, "Developers");

        // Other containers inherit permissions from root; admins get all permissions, users & guests none
        Role noPermsRole = RoleManager.getRole(NoPermissionsRole.class);
        Role readerRole = RoleManager.getRole(ReaderRole.class);
        Role devRole = RoleManager.getRole(PlatformDeveloperRole.class);

        ContainerManager.bootstrapContainer("/", noPermsRole, noPermsRole, devRole);
        Container rootContainer = ContainerManager.getRoot();

        MutableSecurityPolicy policy = new MutableSecurityPolicy(rootContainer, rootContainer.getPolicy());
        Group devs = SecurityManager.getGroup(Group.groupDevelopers);
        policy.addRoleAssignment(devs, PlatformDeveloperRole.class);
        SecurityPolicyManager.savePolicy(policy, false);

        // Create all the standard containers (Home, Home/support, Shared) using an empty Collaboration folder type
        FolderType collaborationType = new CollaborationFolderType(Collections.emptyList());

        // Users & guests can read from /home
        Container home = ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, readerRole, readerRole, null);
        home.setFolderType(collaborationType, upgradeUser);
        addWebPart("Projects", home, HttpView.BODY, 0); // Wiki module used to do this, but it's optional now. If wiki isn't present, at least we'll have the projects webpart.

        ContainerManager.createDefaultSupportContainer().setFolderType(collaborationType, upgradeUser);

        // Only users can read from /Shared
        ContainerManager.bootstrapContainer(ContainerManager.SHARED_CONTAINER_PATH, readerRole, null, null).setFolderType(collaborationType, upgradeUser);

        try
        {
            // Need to insert standard MV indicators for the root -- okay to call getRoot() since we just created it.
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
        FolderTypeManager.get().registerFolderType(this, FolderType.NONE);
        FolderTypeManager.get().registerFolderType(this, new CollaborationFolderType());
        EmailService.setInstance(new EmailServiceImpl());

        if (null != AuditLogService.get() && AuditLogService.get().getClass() != DefaultAuditProvider.class)
        {
            AuditLogService.get().registerAuditType(new UserAuditProvider());
            AuditLogService.get().registerAuditType(new GroupAuditProvider());
            AuditLogService.get().registerAuditType(new AttachmentAuditProvider());
            AuditLogService.get().registerAuditType(new ContainerAuditProvider());
            AuditLogService.get().registerAuditType(new FileSystemAuditProvider());
            AuditLogService.get().registerAuditType(new FileSystemBatchAuditProvider());
            AuditLogService.get().registerAuditType(new ClientApiAuditProvider());
            AuditLogService.get().registerAuditType(new AuthenticationSettingsAuditTypeProvider());
        }
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
            public void shutdownPre()
            {
                try
                {
                    // Halt firing of Quartz triggers
                    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                    scheduler.standby();
                }
                catch (SchedulerException ignored)
                {
                }

                Logger logger = Logger.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    LOG.info("Starting to log statistics for actions prior to web application shut down");
                    Appender appender = logger.getAppender("ACTION_STATS");

                    if (appender instanceof RollingFileAppender)
                        ((RollingFileAppender)appender).rollOver();
                    else
                        Logger.getLogger(CoreModule.class).warn("Could not rollover the action stats tsv file--there was no appender named ACTION_STATS, or it is not a RollingFileAppender.");

                    StringBuilder buf = new StringBuilder();

                    try (TSVWriter writer = new ActionsTsvWriter())
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
            public void shutdownStarted()
            {
                try
                {
                    // Clean up Quartz resources and wait for jobs to complete
                    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                    scheduler.shutdown(true);
                }
                catch (SchedulerException ignored)
                {
                }
            }
        });

        // populate look and feel settings and site settings with values read from startup properties as appropriate for not bootstrap
        populateSiteSettingsWithStartupProps();
        populateLookAndFeelWithStartupProps();
        WriteableLookAndFeelProperties.populateLookAndFeelWithStartupProps();
        WriteableAppProps.populateSiteSettingsWithStartupProps();
        // create users and groups and assign roles with values read from startup properties as appropriate for not bootstrap
        SecurityManager.populateGroupRolesWithStartupProps();
        SecurityManager.populateUserRolesWithStartupProps();
        SecurityManager.populateUserGroupsWithStartupProps();

        LabkeyScriptEngineManager svc = ServiceRegistry.get().getService(LabkeyScriptEngineManager.class);
        // populate script engine definitions values read from startup properties as appropriate for not bootstrap
        if (svc instanceof ScriptEngineManagerImpl)
            ((ScriptEngineManagerImpl)svc).populateScriptEngineDefinitionsWithStartupProps();

        // populate folder types from startup properties as appropriate for not bootstrap
        FolderTypeManager.get().populateWithStartupProps();

        AdminController.registerAdminConsoleLinks();
        AdminController.registerManagementTabs();
        AnalyticsController.registerAdminConsoleLinks();
        UserController.registerAdminConsoleLinks();
        LoggerController.registerAdminConsoleLinks();
        CoreController.registerAdminConsoleLinks();

        FolderTypeManager.get().registerFolderType(this, new WorkbookFolderType());

        SecurityManager.addViewFactory(new SecurityController.GroupDiagramViewFactory());

        FolderSerializationRegistry fsr = FolderSerializationRegistry.get();
        if (null != fsr)
        {
            fsr.addFactories(new FolderTypeWriterFactory(), new FolderTypeImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
            fsr.addFactories(new ModulePropertiesWriterFactory(), new ModulePropertiesImporterFactory());
            fsr.addFactories(new SecurityGroupWriterFactory(), new SecurityGroupImporterFactory());
            fsr.addFactories(new RoleAssignmentsWriterFactory(), new RoleAssignmentsImporterFactory());
            fsr.addFactories(new QCStateWriter.Factory(), new QCStateImporter.Factory());
            fsr.addImportFactory(new SubfolderImporterFactory());
        }

        SearchService ss = SearchService.get();
        if (null != ss)
        {
            ss.addDocumentParser(new TabLoader.CsvFactoryNoConversions());
            ss.addDocumentProvider(this);

            // Register indexable DataLoaders with the search service
            DataLoaderServiceImpl.get().getFactories()
                .stream()
                .filter(DataLoaderFactory::indexable)
                .forEach(ss::addDocumentParser);
        }

        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_API,
                "Use @labkey/api on the client-side",
                "Serve @labkey/api as the default client-side implementation of JavaScript API.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_MOTHERSHIP,
                "Client-side Exception Logging To Mothership",
                "Report unhandled JavaScript exceptions to mothership.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_JAVASCRIPT_SERVER,
                "Client-side Exception Logging To Server",
                "Report unhandled JavaScript exceptions to the server log.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_USER_FOLDERS,
                "User Folders",
                "Enable personal folders for users.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_NO_GUESTS,
                "No Guest Account",
                "Disable the guest account",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_BLOCKER,
                "Block malicious clients",
                "Reject requests from clients that appear malicious.  Turn this feature off if you want to run a security scanner.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_STRICT_RETURN_URL,
                "Check for return URL parameter casing as 'returnUrl'",
                "Raise an error if the return URL parameter is capitalized incorrectly. It should be 'returnUrl' and not 'returnURL'.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_NO_QUESTION_MARK_URL,
                "No Question Marks in URLs",
                "Don't append '?' to URLs unless there are query parameters.",
                false);

        if (null != PropertyService.get())
        {
            PropertyService.get().registerDomainKind(new UsersDomainKind());
            UsersDomainKind.ensureDomain(moduleContext);
        }

        // Register the standard, wiki-based terms-of-use provider
        SecurityManager.addTermsOfUseProvider(new WikiTermsOfUseProvider());

        if (null != PropertyService.get())
            PropertyService.get().registerDomainKind(new TestDomainKind());

        AuthenticationManager.populateSettingsWithStartupProps();
        AnalyticsServiceImpl.populateSettingsWithStartupProps();

        UsageMetricsService.get().registerUsageMetrics(UsageReportingLevel.LOW, getName(), () -> {
            Map<String, Object> results = new HashMap<>();
            Map<String, Object> javaInfo = new HashMap<>();
            javaInfo.put("java.vendor", System.getProperty("java.vendor"));
            javaInfo.put("java.vm.name", System.getProperty("java.vm.name"));
            results.put("javaRuntime", javaInfo);
            return results;
        });

        if (AppProps.getInstance().isDevMode())
            PremiumService.get().registerAntiVirusProvider(new DummyAntiVirusService.Provider());

        FileContentService fileContentService = FileContentService.get();
        if (fileContentService != null)
            fileContentService.addFileListener(WebFilesResolverImpl.get());

        RoleManager.registerPermission(new QCAnalystPermission());

        try
        {
            MarkdownService.setInstance(new MarkdownServiceImpl());
        }
        catch (Exception e)
        {
            LOG.error("Exception registering MarkdownServiceImpl", e);
        }

        // initialize email preference service and listeners
        MessageConfigService.setInstance(new EmailPreferenceConfigServiceImpl());
        ContainerManager.addContainerListener(new EmailPreferenceContainerListener());
        UserManager.addUserListener(new EmailPreferenceUserListener());
    }

    @Override
    public void startBackgroundThreads()
    {
        SystemMaintenance.setTimer();
        ThumbnailServiceImpl.startThread();

        // Start up the default Quartz scheduler, used in many places
        try
        {
            StdSchedulerFactory.getDefaultScheduler().start();
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }

        // On bootstrap in production mode, this will send an initial ping with very little information, as the admin will
        // not have set up their account yet. On later startups, depending on the reporting level, this will send an immediate
        // ping, and then once every 24 hours.
        AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
        TempTableTracker.init();
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
            return AppProps.getInstance().getHomePageActionURL();

        return PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c);
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
        // Must be mutable since we add the dialect tests below
        Set<Class> testClasses = Sets.newHashSet
        (
            AdminController.SchemaVersionTestCase.class,
            AdminController.SerializationTest.class,
            AdminController.TestCase.class,
            AttachmentServiceImpl.TestCase.class,
            CoreController.TestCase.class,
            DavController.TestCase.class,
            EmailServiceImpl.TestCase.class,
            FilesSiteSettingsAction.TestCase.class,
            LoggerController.TestCase.class,
            LoginController.TestCase.class,
            ModuleInfoTestCase.class,
            ModulePropertiesTestCase.class,
            NotificationServiceImpl.TestCase.class,
            PortalJUnitTest.class,
            PostgreSql92Dialect.TestCase.class,
            ProductRegistry.TestCase.class,
            RadeoxRenderer.RadeoxRenderTest.class,
            SchemaXMLTestCase.class,
            SecurityApiActions.TestCase.class,
            SecurityController.TestCase.class,
            SqlScriptController.TestCase.class,
            UserController.TestCase.class
        );

        testClasses.addAll(SqlDialectManager.getAllJUnitTests());

        return testClasses;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return Set.of(
            CommandLineTokenizer.TestCase.class,
            CopyFileRootPipelineJob.TestCase.class,
            PostgreSqlVersion.TestCase.class,
            ScriptEngineManagerImpl.TestCase.class,
            StatsServiceImpl.TestCase.class
        );
    }

    @Override
    public DbSchema createModuleDbSchema(DbScope scope, String metaDataName, Map<String, SchemaTableInfoFactory> tableInfoFactoryMap)
    {
        // Special case for the "labkey" schema we create in every module data source
        if ("labkey".equals(metaDataName))
            return new LabKeyDbSchema(scope, tableInfoFactoryMap);

        return super.createModuleDbSchema(scope, metaDataName, tableInfoFactoryMap);
    }

    @Override
    @NotNull
    public Collection<String> getSchemaNames()
    {
        return Arrays.asList
        (
            CoreSchema.getInstance().getSchemaName(),       // core
            PropertySchema.getInstance().getSchemaName(),   // prop
            TestSchema.getInstance().getSchemaName(),       // test
            DbSchema.TEMP_SCHEMA_NAME                       // temp
        );
    }

    @NotNull
    @Override
    public Collection<String> getProvisionedSchemaNames()
    {
        return Collections.singleton(DbSchema.TEMP_SCHEMA_NAME);
    }

    @NotNull
    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        Set<DbSchema> result = new LinkedHashSet<>(super.getSchemasToTest());

        // Add the "labkey" schema in all module data sources as well... should match labkey.xml
        for (String dataSourceName : ModuleLoader.getInstance().getAllModuleDataSourceNames())
        {
            DbScope scope = DbScope.getDbScope(dataSourceName);
            result.add(scope.getLabKeySchema());
        }

        return result;
    }

    @Override
    public void enumerateDocuments(final SearchService.IndexTask task, @NotNull final Container c, Date since)
    {
        final SearchService ss = SearchService.get();
        if (ss == null)
            return;

        if (c.isRoot())
            return;

        Runnable r = () -> {
            Container p = c.getProject();
            if (null == p)
                return;
            String title;
            String keywords;
            String body;

            // UNDONE: generalize to other folder types
            StudyService svc = StudyService.get();
            Study study = svc != null ? svc.getStudy(c) : null;

            if (null != study)
            {
                title = study.getSearchDisplayTitle();
                keywords = study.getSearchKeywords();
                body = study.getSearchBody();
            }
            else
            {
                String type = c.getContainerNoun(true);

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
            properties.put(SearchService.PROPERTY.identifiersMed.toString(), identifiers);
            properties.put(SearchService.PROPERTY.keywordsMed.toString(), keywords);
            properties.put(SearchService.PROPERTY.title.toString(), title);
            properties.put(SearchService.PROPERTY.categories.toString(), SearchService.navigationCategory.getName());
            ActionURL startURL = PageFlowUtil.urlProvider(ProjectUrls.class).getStartURL(c);
            startURL.setExtraPath(c.getId());
            WebdavResource doc = new SimpleDocumentResource(c.getParsedPath(),
                    "link:" + c.getId(),
                    c.getId(),
                    "text/plain",
                    body,
                    startURL,
                    UserManager.getUser(c.getCreatedBy()), c.getCreated(),
                    null, null,
                    properties);
            (null==task?ss.defaultTask():task).addResource(doc, SearchService.PRIORITY.item);
        };
        // running this asynchronously seems to expose race conditions in domain checking/creation
        // (null==task?ss.defaultTask():task).addRunnable(r, SearchService.PRIORITY.item);
        r.run();
    }

    @Override
    public void indexDeleted()
    {
        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute("UPDATE core.Documents SET LastIndexed = NULL");
    }

    /**
     * This method will handle those startup props for LookAndFeelSettings which are not stored in WriteableLookAndFeelProperties.populateLookAndFeelWithStartupProps().
     */
    private void populateLookAndFeelWithStartupProps()
    {
        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_LOOK_AND_FEEL_SETTINGS);
        User user = User.guest; // using guest user since the server startup doesn't have a true user (this will be used for audit events)
        boolean incrementRevision = false;

        for (ConfigProperty prop : startupProps)
        {
            SiteResourceHandler handler = getResourceHandler(prop.getName());
            if (handler != null)
                incrementRevision = setSiteResource(handler, prop, user);
        }

        // Bump the look & feel revision so browsers retrieve the new logo, custom stylesheet, etc.
        if (incrementRevision)
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
    }

    /**
     * This method will handle those startup props for settings to apply at the site level.
     */
    private void populateSiteSettingsWithStartupProps()
    {
        Collection<ConfigProperty> startupProps = ModuleLoader.getInstance().getConfigProperties(ConfigProperty.SCOPE_SITE_SETTINGS);
        User user = User.guest; // using guest user since the server startup doesn't have a true user (this will be used for audit events)

        for (ConfigProperty prop : startupProps)
        {
            if ("homeProjectFolderType".equalsIgnoreCase(prop.getName()))
            {
                FolderType folderType = FolderTypeManager.get().getFolderType(prop.getValue());
                if (folderType != null)
                    ContainerManager.getHomeContainer().setFolderType(folderType, user);
                else
                    LOG.error("Unable to find folder type for home project during server startup: " + prop.getValue());
            }
            else if ("homeProjectResetPermissions".equalsIgnoreCase(prop.getName()) && Boolean.valueOf(prop.getValue()))
            {
                // reset the home project permissions to remove the default assignments given at server install
                MutableSecurityPolicy homePolicy = new MutableSecurityPolicy(ContainerManager.getHomeContainer());
                SecurityPolicyManager.savePolicy(homePolicy);
                // remove the guest role assignment from the support subfolder
                MutableSecurityPolicy supportPolicy = new MutableSecurityPolicy(ContainerManager.getDefaultSupportContainer().getPolicy());
                Group guests = SecurityManager.getGroup(Group.groupGuests);
                for (Role assignedRole : supportPolicy.getAssignedRoles(guests))
                    supportPolicy.removeRoleAssignment(guests, assignedRole);
                SecurityPolicyManager.savePolicy(supportPolicy);
            }
        }
    }

    private @Nullable SiteResourceHandler getResourceHandler(@NotNull String name)
    {
        switch (name)
        {
            case "logoImage":
                return LookAndFeelPropertiesManager.get()::handleLogoFile;
            case "iconImage":
                return LookAndFeelPropertiesManager.get()::handleIconFile;
            case "customStylesheet":
                return LookAndFeelPropertiesManager.get()::handleCustomStylesheetFile;
            default:
                return null;
        }
    }

    @FunctionalInterface
    public interface SiteResourceHandler {
        void accept(Resource resource, Container container, User user) throws ServletException, IOException;
    }

    private boolean setSiteResource(SiteResourceHandler resourceHandler, ConfigProperty prop, User user)
    {
        Resource resource = getModuleResourceFromPropValue(prop.getValue());
        if (resource != null)
        {
            try
            {
                resourceHandler.accept(resource, ContainerManager.getRoot(), user);
                return true;
            }
            catch(Exception e)
            {
                LOG.error(String.format("Exception setting %1$s during server startup.", prop.getName()), e);
            }
        }

        LOG.error(String.format("Unable to find %1$s resource during server startup: %2$s", prop.getName(), prop.getValue()));
        return false;
    }

    private Resource getModuleResourceFromPropValue(String propValue)
    {
        if (propValue != null)
        {
            // split the prop value on the separator char to get the module name and resource path in that module
            String moduleName = propValue.substring(0, propValue.indexOf(":"));
            String resourcePath = propValue.substring(propValue.indexOf(":") + 1);

            Module module = ModuleLoader.getInstance().getModule(moduleName);
            if (module != null)
                return module.getModuleResource(resourcePath);
        }

        return null;
    }
}
