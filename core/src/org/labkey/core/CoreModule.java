/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiXmlWriter;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientApiAuditProvider;
import org.labkey.api.audit.DefaultAuditProvider;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.collections.MultiValuedMapCollectors;
import org.labkey.api.collections.Sampler;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.dataiterator.CachingDataIterator;
import org.labkey.api.dataiterator.RemoveDuplicatesDataIterator;
import org.labkey.api.dataiterator.ResultSetDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StatementDataIterator;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.TestDomainKind;
import org.labkey.api.iterator.MarkableIterator;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SpringModule;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.FastaDataLoader;
import org.labkey.api.reader.HTMLDataLoader;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.script.RhinoService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.AuthenticationProviderConfigAuditTypeProvider;
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
import org.labkey.api.security.WikiTermsOfUseProvider;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.ExperimentalFeatureService;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.ReplacedRunFilter;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.*;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.ContactWebPart;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.FolderMenu;
import org.labkey.api.webdav.FileSystemBatchAuditProvider;
import org.labkey.api.webdav.ModuleStaticResolverImpl;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.UserResolverImpl;
import org.labkey.api.webdav.WebFilesResolverImpl;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminController;
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
import org.labkey.core.authentication.ldap.LdapAuthenticationProvider;
import org.labkey.core.authentication.ldap.LdapController;
import org.labkey.core.authentication.test.TestSecondaryController;
import org.labkey.core.authentication.test.TestSecondaryProvider;
import org.labkey.core.authentication.test.TestSsoController;
import org.labkey.core.authentication.test.TestSsoProvider;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.LoginController;
import org.labkey.core.notification.NotificationController;
import org.labkey.core.notification.NotificationServiceImpl;
import org.labkey.core.portal.PortalJUnitTest;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.portal.UtilController;
import org.labkey.core.project.FolderNavigationForm;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.reader.DataLoaderServiceImpl;
import org.labkey.core.security.SecurityApiActions;
import org.labkey.core.security.SecurityController;
import org.labkey.core.security.validators.PermissionsValidator;
import org.labkey.core.statistics.AnalyticsProviderRegistryImpl;
import org.labkey.core.statistics.StatsServiceImpl;
import org.labkey.core.statistics.SummaryStatisticRegistryImpl;
import org.labkey.core.test.TestController;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.UserController;
import org.labkey.core.view.ShortURLServiceImpl;
import org.labkey.core.view.template.bootstrap.ViewServiceImpl;
import org.labkey.core.webdav.DavController;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import javax.servlet.ServletContext;
import java.io.IOException;
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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 2:54:30 PM
 */
public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    private static final Logger LOG = Logger.getLogger(CoreModule.class);

    // Register dialect extra early, since we need to initialize the data sources before calling DefaultModule.initialize()
    static
    {
        SqlDialectManager.register(new PostgreSqlDialectFactory());
    }

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
        // Start up the default Quartz scheduler, used in many places
        try
        {
            // Accept most of the standard Quartz properties, but set a system property to skip Quartz's update check.
            Properties props = System.getProperties();
            props.setProperty(StdSchedulerFactory.PROP_SCHED_SKIP_UPDATE_CHECK, "true");
            StdSchedulerFactory.getDefaultScheduler().start();
        }
        catch (SchedulerException e)
        {
            throw new UnexpectedException(e);
        }

        ServiceRegistry.get().registerService(ContainerService.class, ContainerManager.getContainerService());
        ServiceRegistry.get().registerService(FolderSerializationRegistry.class, FolderSerializationRegistryImpl.get());

        // Register the default DataLoaders during init so they are available to sql upgrade scripts
        DataLoaderServiceImpl dls = new DataLoaderServiceImpl();
        dls.registerFactory(new ExcelLoader.Factory());
        dls.registerFactory(new TabLoader.TsvFactory());
        dls.registerFactory(new TabLoader.CsvFactory());
        dls.registerFactory(new HTMLDataLoader.Factory());
        dls.registerFactory(new JSONDataLoader.Factory());
        dls.registerFactory(new FastaDataLoader.Factory());
        ServiceRegistry.get().registerService(DataLoaderService.class, dls);

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
        addController("ldap", LdapController.class);
        addController("notification", NotificationController.class);

        AuthenticationManager.registerProvider(new DbLoginAuthenticationProvider(), Priority.Low);
        AttachmentService.register(new AttachmentServiceImpl());
        AnalyticsServiceImpl.register();
        RhinoService.register();
        CacheManager.addListener(RhinoService::clearCaches);
        NotificationService.register(NotificationServiceImpl.getInstance());
        ViewService.setInstance(ViewServiceImpl.getInstance());

        ServiceRegistry.get().registerService(ExperimentalFeatureService.class, new ExperimentalFeatureService.ExperimentalFeatureServiceImpl());
        ServiceRegistry.get().registerService(ThumbnailService.class, new ThumbnailServiceImpl());
        ServiceRegistry.get().registerService(ShortURLService.class, new ShortURLServiceImpl());
        ServiceRegistry.get().registerService(StatsService.class, new StatsServiceImpl());
        ServiceRegistry.get().registerService(SiteValidationService.class, new SiteValidationServiceImpl());
        ServiceRegistry.get().registerService(AnalyticsProviderRegistry.class, new AnalyticsProviderRegistryImpl());
        ServiceRegistry.get().registerService(SummaryStatisticRegistry.class, new SummaryStatisticRegistryImpl());
        ServiceRegistry.get().registerService(UsageMetricsService.class, new UsageMetricsServiceImpl());

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

        // test authentication provider implementations... dev mode only
        if (AppProps.getInstance().isDevMode())
        {
            addController("testsecondary", TestSecondaryController.class);
            AuthenticationManager.registerProvider(new TestSecondaryProvider());
            addController("testsso", TestSsoController.class);
            AuthenticationManager.registerProvider(new TestSsoProvider());
        }
        AuthenticationManager.registerProvider(new LdapAuthenticationProvider());

        SiteValidationService svc = ServiceRegistry.get().getService(SiteValidationService.class);
        if (null != svc)
        {
            svc.registerProvider("core", new PermissionsValidator());
        }

        ContextListener.addNewInstallCompleteListener(() -> sendSystemReadyEmail(UserManager.getAppAdmins()));
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
                public WebPartView getWebPartView(@NotNull ViewContext ctx, @NotNull WebPart webPart)
                {
                    return new ContactWebPart();
                }
            },
            new BaseWebPartFactory("FolderNav")
            {
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
                public boolean isAvailable(Container c, String location)
                {
                    return !c.isWorkbook() && location.equalsIgnoreCase(HttpView.BODY);
                }
            },
            new BaseWebPartFactory("Workbook Description")
            {
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
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
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);

                    String title = webPart.getPropertyMap().getOrDefault("title", "Projects");
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
                public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projectNav.jsp", webPart);
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
            new AlwaysAvailableWebPartFactory("Custom Menu", true, true, WebPartFactory.LOCATION_MENUBAR)
            {
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
                public boolean isAvailable(Container c, String location)
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
            bootstrap();
        }
        else
        {
            WriteableAppProps app = AppProps.getWriteableInstance();

            if (!app.isSetUseContainerRelativeURL())
            {
                app.setUseContainerRelativeURL(false);
                app.save(moduleContext.getUpgradeUser());
            }
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
        FolderTypeManager.get().registerFolderType(this, FolderType.NONE);

        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "Schedule Upgrade Check";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                // On bootstrap in production mode, this will send an initial ping with very little
                // information, as the admin will not have set up their account yet.
                // On later startups, depending on the reporting level, this will send an immediate
                // ping, and then once every 24 hours.
                AppProps.getInstance().getUsageReportingLevel().scheduleUpgradeCheck();
            }
        });

        if (null != AuditLogService.get() && AuditLogService.get().getClass() != DefaultAuditProvider.class)
        {
            AuditLogService.get().registerAuditType(new UserAuditProvider());
            AuditLogService.get().registerAuditType(new GroupAuditProvider());
            AuditLogService.get().registerAuditType(new AttachmentAuditProvider());
            AuditLogService.get().registerAuditType(new ContainerAuditProvider());
            AuditLogService.get().registerAuditType(new FileSystemAuditProvider());
            AuditLogService.get().registerAuditType(new FileSystemBatchAuditProvider());
            AuditLogService.get().registerAuditType(new ClientApiAuditProvider());
            AuditLogService.get().registerAuditType(new AuthenticationProviderConfigAuditTypeProvider());
        }
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
            public void shutdownPre()
            {
                try
                {
                    // Halt firing of Quartz triggers
                    Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
                    scheduler.standby();
                }
                catch (SchedulerException x)
                {
                }

                Logger logger = Logger.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    LOG.info("Starting to log statistics for actions prior to web application shut down");
                    Appender appender = logger.getAppender("ACTION_STATS");

                    if (null != appender && appender instanceof RollingFileAppender)
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
                catch (SchedulerException x)
                {
                }
            }
        });

        // populate look and feel settings and site settings with values read from startup properties as appropriate for not bootstrap
        WriteableLookAndFeelProperties.populateLookAndFeelWithStartupProps();
        WriteableAppProps.populateSiteSettingsWithStartupProps();
        // create users and groups and assign roles with values read from startup properties as appropriate for not bootstrap
        SecurityManager.populateGroupRolesWithStartupProps();
        SecurityManager.populateUserRolesWithStartupProps();
        SecurityManager.populateUserGroupsWithStartupProps();
        // populate script engine definitions values read from startup properties as appropriate for not bootstrap
        LabKeyScriptEngineManager.populateScriptEngineDefinitionsWithStartupProps();

        AdminController.registerAdminConsoleLinks();
        AdminController.registerFolderManagementTabs();
        AnalyticsController.registerAdminConsoleLinks();
        UserController.registerAdminConsoleLinks();
        LoggerController.registerAdminConsoleLinks();

        FolderTypeManager.get().registerFolderType(this, new WorkbookFolderType());

        SecurityManager.addViewFactory(new SecurityController.GroupDiagramViewFactory());

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != fsr)
        {
            fsr.addFactories(new FolderTypeWriterFactory(), new FolderTypeImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
            fsr.addFactories(new ModulePropertiesWriterFactory(), new ModulePropertiesImporterFactory());
            fsr.addFactories(new SecurityGroupWriterFactory(), new SecurityGroupImporterFactory());
            fsr.addFactories(new RoleAssignmentsWriterFactory(), new RoleAssignmentsImporterFactory());
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
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_USER_FOLDERS,
                "User Folders",
                "Enable personal folders for users.",
                false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_NO_GUESTS,
                "No Guest Account",
                "Disable the guest account",
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
    }

    @Override
    public void startBackgroundThreads()
    {
        SystemMaintenance.setTimer();
    }

    private static final String LIB_PATH = "/WEB-INF/lib/";
    private static final Pattern LABKEY_JAR_PATTERN = Pattern.compile("^(?:schemas|labkey-client-api).*\\.jar$");

    @Nullable
    @Override
    public Collection<String> getJarFilenames()
    {
        if (!AppProps.getInstance().isDevMode())
            return null;

        //noinspection unchecked
        Set<String> resources = ViewServlet.getViewServletContext().getResourcePaths(LIB_PATH);
        Set<String> filenames = new CaseInsensitiveTreeSet();

        // Remove path prefix and copy to a modifiable collection
        for (String filename : resources)
        {
            String name = filename.substring(LIB_PATH.length());

            // We don't need to include licensing information for our own JAR files (only third-party JARs), so filter out
            // our JARs that end up in WEB-INF/lib
            if (DefaultModule.isRuntimeJar(name) && !LABKEY_JAR_PATTERN.matcher(name).matches())
                filenames.add(name);
        }

        return filenames;
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
        @SuppressWarnings({"unchecked"})
        Set<Class> testClasses = new HashSet<>(Arrays.asList(
                Table.TestCase.class,
                Table.DataIteratorTestCase.class,
                SchemaXMLTestCase.class,
                DbSchema.TableSelectTestCase.class,
                DbSchema.TransactionTestCase.class,
                DbSchema.CachingTestCase.class,
                DbSchema.DDLMethodsTestCase.class,
                DbSchema.SchemaCasingTestCase.class,
                TableViewFormTestCase.class,
                ActionURL.TestCase.class,
                URLHelper.TestCase.class,
                SecurityManager.TestCase.class,
                PropertyManager.TestCase.class,
                ContainerManager.TestCase.class,
                TabLoader.TabLoaderTestCase.class,
                MapLoader.MapLoaderTestCase.class,
                GroupManager.TestCase.class,
                AttachmentServiceImpl.TestCase.class,
                WebdavResolverImpl.TestCase.class,
                MimeMap.TestCase.class,
                ModuleStaticResolverImpl.TestCase.class,
                StorageProvisioner.TestCase.class,
                RhinoService.TestCase.class,
                MarkdownService.TestCase.class,
                DbScope.GroupConcatTestCase.class,
                DbScope.TransactionTestCase.class,
                SimpleTranslator.TranslateTestCase.class,
                ResultSetDataIterator.TestCase.class,
                ExceptionUtil.TestCase.class,
                ViewCategoryManager.TestCase.class,
                TableSelectorTestCase.class,
                RowTrackingResultSetWrapper.TestCase.class,
                SqlSelectorTestCase.class,
                ResultSetSelectorTestCase.class,
                NestedGroupsTest.class,
                ModulePropertiesTestCase.class,
                ModuleInfoTestCase.class,
                PortalJUnitTest.class,
                ContainerDisplayColumn.TestCase.class,
                AliasManager.TestCase.class,
                AtomicDatabaseInteger.TestCase.class,
                DbSequenceManager.TestCase.class,
                //RateLimiter.TestCase.class,
                StatementUtils.TestCase.class,
                StatementDataIterator.TestCase.class,
                Encryption.TestCase.class,
                NotificationServiceImpl.TestCase.class,
                JspTemplate.TestCase.class,
                SQLFragment.TestCase.class,
                DavController.TestCase.class,
                DomainTemplateGroup.TestCase.class,
                AdminController.TestCase.class,
                CoreController.TestCase.class,
                FilesSiteSettingsAction.TestCase.class,
                LoginController.TestCase.class,
                LoggerController.TestCase.class,
                SecurityController.TestCase.class,
                SecurityApiActions.TestCase.class,
                SqlScriptController.TestCase.class,
                UserController.TestCase.class,
                FolderTypeManager.TestCase.class,
                ModuleHtmlView.TestCase.class,
                Portal.TestCase.class,
                MultiValuedMapCollectors.TestCase.class
        ));

        testClasses.addAll(SqlDialectManager.getAllJUnitTests());

        return testClasses;
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
                DateUtil.TestCase.class,
                TSVWriter.TestCase.class,
                TSVMapWriter.Tests.class,
                ExcelLoader.ExcelLoaderTestCase.class,
                ExcelFactory.ExcelFactoryTestCase.class,
                ModuleDependencySorter.TestCase.class,
                DatabaseCache.TestCase.class,
                PasswordExpiration.TestCase.class,
                BooleanFormat.TestCase.class,
                FileUtil.TestCase.class,
                FileType.TestCase.class,
                TabLoader.HeaderMatchTest.class,
                MemTracker.TestCase.class,
                StringExpressionFactory.TestCase.class,
                Path.TestCase.class,
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
                CaseInsensitiveMapWrapper.TestCase.class,
                StatsServiceImpl.TestCase.class,
                NumberUtilsLabKey.TestCase.class,
                SimpleFilter.FilterTestCase.class,
                SimpleFilter.InClauseTestCase.class,
                SimpleFilter.BetweenClauseTestCase.class,
                InlineInClauseGenerator.TestCase.class,
                CollectionUtils.TestCase.class,
                MarkableIterator.TestCase.class,
                Sampler.TestCase.class,
                BuilderObjectFactory.TestCase.class,
                ChecksumUtil.TestCase.class,
                MaterializedQueryHelper.TestCase.class,
                LabKeyScriptEngineManager.TestCase.class,
                ConvertHelper.TestCase.class,
                RReport.TestCase.class
        ));
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
}
