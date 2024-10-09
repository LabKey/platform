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

import com.fasterxml.jackson.core.io.CharTypes;
import com.google.common.collect.Sets;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminConsoleService;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.HealthCheck;
import org.labkey.api.admin.HealthCheckRegistry;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.admin.sitevalidation.SiteValidationService;
import org.labkey.api.analytics.AnalyticsService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.ClientApiAuditProvider;
import org.labkey.api.audit.DefaultAuditProvider;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.audit.provider.GroupAuditProvider;
import org.labkey.api.audit.provider.ModulePropertiesAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerService;
import org.labkey.api.data.ContainerServiceImpl;
import org.labkey.api.data.ContainerTypeRegistry;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.FileSqlScriptProvider;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.OutOfRangeDisplayColumn;
import org.labkey.api.data.PropertySchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfoFactory;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.TabContainerType;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TempTableTracker;
import org.labkey.api.data.TestSchema;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.data.dialect.SqlDialectRegistry;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.TestDomainKind;
import org.labkey.api.external.tools.ExternalToolsViewService;
import org.labkey.api.files.FileBrowserConfigImporter;
import org.labkey.api.files.FileBrowserConfigWriter;
import org.labkey.api.files.FileContentService;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.SchemaUpdateType;
import org.labkey.api.module.SpringModule;
import org.labkey.api.module.Summary;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.premium.AntiVirusProviderRegistry;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.qc.DataStateManager;
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
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationManager.Priority;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider;
import org.labkey.api.security.DbLoginService;
import org.labkey.api.security.DummyAntiVirusService;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.LimitActiveUsersService;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPointcutService;
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
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AdminConsole.OptionalFeatureFlag;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.CustomLabelService;
import org.labkey.api.settings.CustomLabelService.CustomLabelServiceImpl;
import org.labkey.api.settings.FolderSettingsCache;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.LookAndFeelPropertiesManager;
import org.labkey.api.settings.LookAndFeelPropertiesManager.ResourceType;
import org.labkey.api.settings.LookAndFeelPropertiesManager.SiteResourceHandler;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.OptionalFeatureService.FeatureType;
import org.labkey.api.settings.OptionalFeatureService.OptionalFeatureServiceImpl;
import org.labkey.api.settings.ProductConfiguration;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.settings.StashedStartupProperties;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ShutdownListener;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.vcs.VcsService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.menu.FolderMenu;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.api.writer.ContainerUser;
import org.labkey.core.admin.ActionsTsvWriter;
import org.labkey.core.admin.AdminConsoleServiceImpl;
import org.labkey.core.admin.AdminController;
import org.labkey.core.admin.CopyFileRootPipelineJob;
import org.labkey.core.admin.CustomizeMenuForm;
import org.labkey.core.admin.DisplayFormatAnalyzer;
import org.labkey.core.admin.DisplayFormatValidationProviderFactory;
import org.labkey.core.admin.FilesSiteSettingsAction;
import org.labkey.core.admin.MenuViewFactory;
import org.labkey.core.admin.importer.FolderTypeImporterFactory;
import org.labkey.core.admin.importer.MissingValueImporterFactory;
import org.labkey.core.admin.importer.ModulePropertiesImporterFactory;
import org.labkey.core.admin.importer.PageImporterFactory;
import org.labkey.core.admin.importer.RoleAssignmentsImporterFactory;
import org.labkey.core.admin.importer.SearchSettingsImporterFactory;
import org.labkey.core.admin.importer.SecurityGroupImporterFactory;
import org.labkey.core.admin.importer.SubfolderImporterFactory;
import org.labkey.core.admin.logger.LoggerController;
import org.labkey.core.admin.logger.LoggingTestCase;
import org.labkey.core.admin.miniprofiler.MiniProfilerController;
import org.labkey.core.admin.sitevalidation.SiteValidationServiceImpl;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.admin.test.SchemaXMLTestCase;
import org.labkey.core.admin.test.UnknownSchemasTest;
import org.labkey.core.admin.usageMetrics.UsageMetricsServiceImpl;
import org.labkey.core.admin.writer.FolderSerializationRegistryImpl;
import org.labkey.core.admin.writer.FolderTypeWriterFactory;
import org.labkey.core.admin.writer.MissingValueWriterFactory;
import org.labkey.core.admin.writer.ModulePropertiesWriterFactory;
import org.labkey.core.admin.writer.PageWriterFactory;
import org.labkey.core.admin.writer.RoleAssignmentsWriterFactory;
import org.labkey.core.admin.writer.SearchSettingsWriterFactory;
import org.labkey.core.admin.writer.SecurityGroupWriterFactory;
import org.labkey.core.analytics.AnalyticsController;
import org.labkey.core.analytics.AnalyticsServiceImpl;
import org.labkey.core.attachment.AttachmentServiceImpl;
import org.labkey.core.dialect.PostgreSqlDialectFactory;
import org.labkey.core.dialect.PostgreSqlInClauseTest;
import org.labkey.core.dialect.PostgreSqlVersion;
import org.labkey.core.junit.JunitController;
import org.labkey.core.login.DbLoginAuthenticationProvider;
import org.labkey.core.login.DbLoginManager;
import org.labkey.core.login.LoginController;
import org.labkey.core.metrics.SimpleMetricsServiceImpl;
import org.labkey.core.metrics.WebSocketConnectionManager;
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
import org.labkey.core.qc.CoreQCStateHandler;
import org.labkey.core.qc.DataStateImporter;
import org.labkey.core.qc.DataStateWriter;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.UserAuditProvider;
import org.labkey.core.query.UsersDomainKind;
import org.labkey.core.reader.DataLoaderServiceImpl;
import org.labkey.core.reports.DocumentConversionServiceImpl;
import org.labkey.core.reports.ScriptEngineManagerImpl;
import org.labkey.core.script.RhinoService;
import org.labkey.core.security.ApiKeyViewProvider;
import org.labkey.core.security.SecurityApiActions;
import org.labkey.core.security.SecurityController;
import org.labkey.core.security.SecurityPointcutServiceImpl;
import org.labkey.core.security.validators.PermissionsValidatorFactory;
import org.labkey.core.statistics.AnalyticsProviderRegistryImpl;
import org.labkey.core.statistics.StatsServiceImpl;
import org.labkey.core.statistics.SummaryStatisticRegistryImpl;
import org.labkey.core.thumbnail.ThumbnailServiceImpl;
import org.labkey.core.user.LimitActiveUsersSettings;
import org.labkey.core.user.UserController;
import org.labkey.core.vcs.VcsServiceImpl;
import org.labkey.core.view.ShortURLServiceImpl;
import org.labkey.core.view.TableViewFormTestCase;
import org.labkey.core.view.external.tools.ExternalToolsViewServiceImpl;
import org.labkey.core.view.template.bootstrap.CoreWarningProvider;
import org.labkey.core.view.template.bootstrap.ViewServiceImpl;
import org.labkey.core.view.template.bootstrap.WarningServiceImpl;
import org.labkey.core.webdav.DavController;
import org.labkey.core.webdav.ModuleStaticResolverImpl;
import org.labkey.core.webdav.WebFilesResolverImpl;
import org.labkey.core.webdav.WebdavServlet;
import org.labkey.core.wiki.MarkdownServiceImpl;
import org.labkey.core.wiki.RadeoxRenderer;
import org.labkey.core.wiki.WikiRenderingServiceImpl;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.core.workbook.WorkbookQueryView;
import org.labkey.core.workbook.WorkbookSearchView;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.radeox.test.BaseRenderEngineTest;
import org.radeox.test.filter.BasicRegexTest;
import org.radeox.test.filter.BoldFilterTest;
import org.radeox.test.filter.EscapeFilterTest;
import org.radeox.test.filter.FilterPipeTest;
import org.radeox.test.filter.HeadingFilterTest;
import org.radeox.test.filter.HtmlRemoveFilterTest;
import org.radeox.test.filter.ItalicFilterTest;
import org.radeox.test.filter.KeyFilterTest;
import org.radeox.test.filter.LineFilterTest;
import org.radeox.test.filter.LinkTestFilterTest;
import org.radeox.test.filter.ListFilterTest;
import org.radeox.test.filter.NewlineFilterTest;
import org.radeox.test.filter.ParamFilterTest;
import org.radeox.test.filter.SmileyFilterTest;
import org.radeox.test.filter.StrikeThroughFilterTest;
import org.radeox.test.filter.TypographyFilterTest;
import org.radeox.test.filter.UrlFilterTest;
import org.radeox.test.filter.WikiLinkFilterTest;
import org.radeox.test.macro.list.AtoZListFormatterTest;
import org.radeox.test.macro.list.ExampleListFormatterTest;
import org.radeox.test.macro.list.SimpleListTest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.settings.StashedStartupProperties.homeProjectFolderType;
import static org.labkey.api.settings.StashedStartupProperties.homeProjectResetPermissions;
import static org.labkey.api.settings.StashedStartupProperties.homeProjectWebparts;
import static org.labkey.api.settings.StashedStartupProperties.siteAvailableEmailFrom;
import static org.labkey.api.settings.StashedStartupProperties.siteAvailableEmailMessage;
import static org.labkey.api.settings.StashedStartupProperties.siteAvailableEmailSubject;
import static org.labkey.api.util.MothershipReport.EXPERIMENTAL_LOCAL_MARKETING_UPDATE;
import static org.labkey.core.login.LoginController.REMOTE_LOGIN_FEATURE_FLAG;
import static org.labkey.filters.ContentSecurityPolicyFilter.FEATURE_FLAG_DISABLE_ENFORCE_CSP;

public class CoreModule extends SpringModule implements SearchService.DocumentProvider
{
    private static final Logger LOG = LogHelper.getLogger(CoreModule.class, "Errors during server startup and shut down");
    public static final String PROJECTS_WEB_PART_NAME = "Projects";
    private static final String EXPERIMENTAL_CALCULATED_FIELDS = "experimental-calculated-fields";

    static Runnable _afterUpdateRunnable = null;

    static
    {
        // Accept most of the standard Quartz properties, but set the misfire threshold to five minutes. This prevents
        // Quartz from dropping scheduled work if a lot of items fire at the same time, like a lot of ETLs triggering at 2AM.
        // This can overwhelm the thread pool running them so they don't complete in the default 1 minute window. Set it early so
        // if any other module touches Quartz in its setup, it initializes with this setting.
        Properties props = System.getProperties();
        props.setProperty("org.quartz.jobStore.misfireThreshold", "300000");

        // Register dialect extra early, since we need to initialize the data sources before calling DefaultModule.initialize()
        SqlDialectRegistry.register(new PostgreSqlDialectFactory());

        try
        {
            var field = CharTypes.class.getDeclaredField("sOutputEscapes128");
            field.setAccessible(true);
            ((int[])field.get(null))['/'] = '/';
            field.setAccessible(false);
        }
        catch (NoSuchFieldException|IllegalArgumentException|IllegalAccessException x)
        {
            // pass
        }
    }

    private CoreWarningProvider _warningProvider;
    private ServletRegistration.Dynamic _webdavServletDynamic;

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
        ExternalToolsViewService.setInstance(new ExternalToolsViewServiceImpl());
        ExternalToolsViewService.get().registerExternalAccessViewProvider(new ApiKeyViewProvider());
        LimitActiveUsersService.setInstance(() -> new LimitActiveUsersSettings().isUserLimitReached());

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

        WarningService.setInstance(new WarningServiceImpl());

        ViewService.setInstance(ViewServiceImpl.getInstance());
        OptionalFeatureService.setInstance(new OptionalFeatureServiceImpl());
        ThumbnailService.setInstance(new ThumbnailServiceImpl());
        ShortURLService.setInstance(new ShortURLServiceImpl());
        StatsService.setInstance(new StatsServiceImpl());
        SiteValidationService.setInstance(new SiteValidationServiceImpl());
        AnalyticsProviderRegistry.setInstance(new AnalyticsProviderRegistryImpl());
        SummaryStatisticRegistry.setInstance(new SummaryStatisticRegistryImpl());
        UsageMetricsService.setInstance(new UsageMetricsServiceImpl());
        CustomLabelService.setInstance(new CustomLabelServiceImpl());
        SecurityPointcutService.setInstance(new SecurityPointcutServiceImpl());
        AdminConsoleService.setInstance(new AdminConsoleServiceImpl());
        WikiRenderingService.setInstance(new WikiRenderingServiceImpl());
        VcsService.setInstance(new VcsServiceImpl());
        LabKeyScriptEngineManager.setInstance(new ScriptEngineManagerImpl());
        DocumentConversionService.setInstance(new DocumentConversionServiceImpl());
        DbLoginService.setInstance(new DbLoginManager());

        try
        {
            ContainerTypeRegistry.get().register("normal", new NormalContainerType());
            ContainerTypeRegistry.get().register("tab", new TabContainerType());
            ContainerTypeRegistry.get().register("workbook", new WorkbookContainerType());
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        _warningProvider = new CoreWarningProvider();
        WarningService.get().register(_warningProvider);

        WebdavService.get().setResolver(ModuleStaticResolverImpl.get());
        // need to register webdav resolvers in init() instead of startupAfterSpringConfig since static module files are loaded during module startup
        WebdavService.get().registerRootResolver(WebdavResolverImpl.get());
        WebdavService.get().registerRootResolver(WebFilesResolverImpl.get());

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
        AdminConsole.addExperimentalFeatureFlag(DataColumn.EXPERIMENTAL_USE_QUERYSELECT_COMPONENT, "Use QuerySelect for row insert/update form",
                "This feature will switch the query based select inputs on the row insert/update form to use the React QuerySelect" +
                "component. This will allow for a user to view the first 100 options in the select but then use type ahead" +
                "search to find the other select values.", false);
        AdminConsole.addExperimentalFeatureFlag(SQLFragment.FEATUREFLAG_DISABLE_STRICT_CHECKS, "Disable SQLFragment strict checks",
                "SQLFragment now has very strict usage validation, these checks may cause errors in code that has not been updated. Turn on this feature to disable checks.", false);
        AdminConsole.addExperimentalFeatureFlag(LoginController.FEATUREFLAG_DISABLE_LOGIN_XFRAME, "Disable Login X-FRAME-OPTIONS=DENY",
                "By default LabKey disables all framing of login related actions. Disabling this feature will revert to using the standard site settings.", false);
        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_CALCULATED_FIELDS, "Calculated Fields",
                "Allow defining calculated fields in the Field Editor for supported data types: Sample Type, Data Class, List, Study Dataset, Assay Design", false);

        SiteValidationService svc = SiteValidationService.get();
        if (null != svc)
        {
            svc.registerProviderFactory(getName(), new PermissionsValidatorFactory());
            svc.registerProviderFactory(getName(), new DisplayFormatValidationProviderFactory());
        }

        registerHealthChecks();

        ContextListener.addNewInstallCompleteListener(() -> sendSystemReadyEmail(UserManager.getAppAdmins()));

        ScriptEngineManagerImpl.registerEncryptionMigrationHandler();

        deleteTempFiles();
    }

    private void deleteTempFiles()
    {
        try
        {
            // Issue 46598 - clean up previously created temp files from file uploads
            FileUtil.deleteDirectoryContents(SpringActionController.getTempUploadDir().toPath());
        }
        catch (IOException e)
        {
            LOG.warn("Failed to clean up previously uploaded files from " + SpringActionController.getTempUploadDir(), e);
        }
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

        Map<StashedStartupProperties, StartupPropertyEntry> map = AppProps.getInstance().getStashedStartupProperties();
        String fromEmail = getValue(map, siteAvailableEmailFrom);
        String subject = getValue(map, siteAvailableEmailSubject);
        String body = getValue(map, siteAvailableEmailMessage);

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
        // for hosted sites. Another option is to use the guest user here, but that's strange.
        svc.sendMessages(messages, users.get(0), ContainerManager.getRoot());
    }

    private @Nullable String getValue(Map<StashedStartupProperties, StartupPropertyEntry> map, StashedStartupProperties prop)
    {
        StartupPropertyEntry entry = map.get(prop);
        return null != entry ? StringUtils.trimToNull(entry.getValue()) : null;
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new AlwaysAvailableWebPartFactory("Contacts")
            {
                @Override
                public WebPartView<?> getWebPartView(@NotNull ViewContext ctx, @NotNull WebPart webPart)
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
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
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
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
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
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<?> view = new JspView<>("/org/labkey/core/workbook/workbookDescription.jsp");
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
            new AlwaysAvailableWebPartFactory(PROJECTS_WEB_PART_NAME, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
            {
                @Override
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);

                    String title = webPart.getPropertyMap().getOrDefault("title", "Projects");
                    view.setTitle(title);

                    if (portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
                    {
                        NavTree customize = new NavTree("");
                        customize.setScript("customizeProjectWebpart" + webPart.getRowId() + "();");
                        view.setCustomize(customize);
                    }
                    return view;
                }
            },
            new AlwaysAvailableWebPartFactory("Subfolders", WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT)
            {
                @Override
                public WebPart createWebPart()
                {
                    // Issue 44913: Set the default properties for all new instances of the Subfolders webpart
                    WebPart webPart = super.createWebPart();
                    return setDefaultProperties(webPart);
                }

                @Override
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    if (webPart.getPropertyMap().isEmpty())
                    {
                        // Configure to show subfolders if not previously configured
                        webPart = setDefaultProperties(new WebPart(webPart));
                    }

                    JspView<WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", webPart);
                    view.setTitle(webPart.getPropertyMap().get("title"));

                    if (portalCtx.hasPermission(getClass().getName(), AdminPermission.class))
                    {
                        NavTree customize = new NavTree("");
                        customize.setScript("customizeProjectWebpart" + webPart.getRowId() + "(" + webPart.getRowId() + ", " + PageFlowUtil.jsString(webPart.getPageId()) + ", " + webPart.getIndex() + ");");
                        view.setCustomize(customize);
                    }

                    return view;
                }

                private WebPart setDefaultProperties(WebPart webPart)
                {
                    webPart.setProperty("title", "Subfolders");
                    webPart.setProperty("containerFilter", ContainerFilter.Type.CurrentAndFirstChildren.name());
                    webPart.setProperty("containerTypes", "folder");
                    return webPart;
                }
            },
            new AlwaysAvailableWebPartFactory("Custom Menu", true, true, WebPartFactory.LOCATION_MENUBAR)
            {
                @Override
                public WebPartView<?> getWebPartView(@NotNull final ViewContext portalCtx, @NotNull WebPart webPart)
                {
                    final CustomizeMenuForm form = AdminController.getCustomizeMenuForm(webPart);
                    String title = "My Menu";
                    if (form.getTitle() != null && !form.getTitle().isEmpty())
                        title = form.getTitle();

                    WebPartView<?> view;
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
                public HttpView<?> getEditView(WebPart webPart, ViewContext context)
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
                public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull WebPart webPart)
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
        );
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
        {
            bootstrap();
        }

        // Increment on every core module upgrade to defeat browser caching of static resources.
        WriteableAppProps.incrementLookAndFeelRevisionAndSave();

        // Allow dialect to make adjustments to the just upgraded core database (e.g., install aggregate functions, etc.)
        CoreSchema.getInstance().getSqlDialect().afterCoreUpgrade(moduleContext);

        // The core SQL scripts install aggregate functions and other objects that dialects need to know about. Prepare
        // the previously initialized dialects again to make sure they're aware of all the changes. Prepare all the
        // initialized scopes because we could have more than one scope pointed at the core database (e.g., external
        // schemas). See #17077 (pg example) and #19177 (ss example).
        for (DbScope scope : DbScope.getInitializedDbScopes())
            scope.getSqlDialect().prepare(scope);

        // Now that we know the standard containers have been created, add a listener that warms the just-cleared caches with
        // core.Containers metadata and a few common containers. This may prevent some deadlocks during upgrade, #33550.
        CacheManager.addListener(() -> {
            ContainerManager.getRoot();
            ContainerManager.getHomeContainer();
            ContainerManager.getSharedContainer();
        });

        if (_afterUpdateRunnable != null)
            _afterUpdateRunnable.run();
    }

    private void bootstrap()
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
        Role adminRole = RoleManager.getRole(SiteAdminRole.class);

        ContainerManager.bootstrapContainer("/", noPermsRole, noPermsRole, devRole, adminRole);
        Container rootContainer = ContainerManager.getRoot();

        // Create all the standard containers (Home, Home/support, Shared) using an empty Collaboration folder type
        FolderType collaborationType = new CollaborationFolderType(Collections.emptyList());

        // Users & guests can read from /home
        Container home = ContainerManager.bootstrapContainer(ContainerManager.HOME_PROJECT_PATH, readerRole, readerRole, null, null);
        home.setFolderType(collaborationType, null);

        ContainerManager.createDefaultSupportContainer().setFolderType(collaborationType, null);

        // Only users can read from /Shared
        ContainerManager.bootstrapContainer(ContainerManager.SHARED_CONTAINER_PATH, readerRole, null, null, null).setFolderType(collaborationType, null);

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
        UsageReportingLevel.shutdown();
    }


    @Override
    public void startupAfterSpringConfig(ModuleContext moduleContext)
    {
        // Any containers in the cache have bogus folder types since they aren't registered until startup().  See #10310
        ContainerManager.clearCache();

        checkForMissingDbViews();

        ProductConfiguration.handleStartupProperties();
        // This listener deletes all properties; make sure it executes after most of the other listeners
        ContainerManager.addContainerListener(new CoreContainerListener(), ContainerManager.ContainerListener.Order.Last);
        ContainerManager.addContainerListener(new FolderSettingsCache.FolderSettingsCacheListener());
        SecurityManager.init();
        FolderTypeManager.get().registerFolderType(this, FolderType.NONE);
        FolderTypeManager.get().registerFolderType(this, new CollaborationFolderType());

        AnalyticsServiceImpl.get().resetCSP();

        if (moduleContext.isNewInstall())
        {
            // In order to initialize the portal layout correctly, we need to add the web parts after the folder
            // types have been registered. Thus, needs to be here in startupAfterSpringConfig() instead of grouped
            // in bootstrap().
            Container homeContainer = ContainerManager.getHomeContainer();
            int count = Portal.getParts(homeContainer, homeContainer.getFolderType().getDefaultPageId(homeContainer)).size();
            addWebPart(PROJECTS_WEB_PART_NAME, homeContainer, HttpView.BODY, count);
        }

        EmailService.setInstance(new EmailServiceImpl());

        if (null != AuditLogService.get() && AuditLogService.get().getClass() != DefaultAuditProvider.class)
        {
            AuditLogService.get().registerAuditType(new UserAuditProvider());
            AuditLogService.get().registerAuditType(new GroupAuditProvider());
            AuditLogService.get().registerAuditType(new AttachmentAuditProvider());
            AuditLogService.get().registerAuditType(new ContainerAuditProvider());
            AuditLogService.get().registerAuditType(new FileSystemAuditProvider());
            AuditLogService.get().registerAuditType(new ClientApiAuditProvider());
            AuditLogService.get().registerAuditType(new AuthenticationSettingsAuditTypeProvider());
            AuditLogService.get().registerAuditType(new TransactionAuditProvider());
            AuditLogService.get().registerAuditType(new ModulePropertiesAuditProvider());

            DataStateManager.getInstance().registerDataStateHandler(new CoreQCStateHandler());
        }
        ContextListener.addShutdownListener(TempTableTracker.getShutdownListener());
        ContextListener.addShutdownListener(DavController.getShutdownListener());
        ContextListener.addShutdownListener(new ShutdownListener()
        {
            @Override
            public String getName()
            {
                return "Temp file cleanup";
            }

            @Override
            public void shutdownPre()
            {}

            @Override
            public void shutdownStarted()
            {
                deleteTempFiles();
            }
        });

        SimpleMetricsService.setInstance(new SimpleMetricsServiceImpl());

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

                Logger logger = LogManager.getLogger(ActionsTsvWriter.class);

                if (null != logger)
                {
                    StringBuilder buf = new StringBuilder();

                    try (TSVWriter writer = new ActionsTsvWriter())
                    {
                        writer.write(buf);
                    }
                    catch (IOException e)
                    {
                        LOG.error("Exception exporting action stats", e);
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
        populateLookAndFeelResourcesWithStartupProps();
        WriteableLookAndFeelProperties.populateLookAndFeelWithStartupProps();
        WriteableAppProps.populateSiteSettingsWithStartupProps();
        // create users and groups and assign roles with values read from startup properties as appropriate for not bootstrap
        SecurityManager.populateStartupProperties();
        // This method depends on resources (FolderType) from other modules, so invoke after startup
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public String getName()
            {
                return "CoreModule.populateSiteSettingsWithStartupProps";
            }

            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                populateSiteSettingsWithStartupProps();
            }
        });

        LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
        // populate script engine definitions values read from startup properties
        if (svc instanceof ScriptEngineManagerImpl)
            ((ScriptEngineManagerImpl)svc).populateScriptEngineDefinitionsWithStartupProps();

        // populate folder types from startup properties as appropriate for not bootstrap
        FolderTypeManager.get().populateWithStartupProps();
        LimitActiveUsersSettings.populateStartupProperties();

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
            fsr.addFactories(new MissingValueWriterFactory(), new MissingValueImporterFactory());
            fsr.addFactories(new SearchSettingsWriterFactory(), new SearchSettingsImporterFactory());
            fsr.addFactories(new PageWriterFactory(), new PageImporterFactory());
            fsr.addFactories(new ModulePropertiesWriterFactory(), new ModulePropertiesImporterFactory());
            fsr.addFactories(new SecurityGroupWriterFactory(), new SecurityGroupImporterFactory());
            fsr.addFactories(new RoleAssignmentsWriterFactory(), new RoleAssignmentsImporterFactory());
            fsr.addFactories(new DataStateWriter.Factory(), new DataStateImporter.Factory());
            fsr.addFactories(new FileBrowserConfigWriter.Factory(), new FileBrowserConfigImporter.Factory());
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
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_NO_GUESTS,
            "No Guest Account",
            "Disable the guest account",
            false);
        AdminConsole.addExperimentalFeatureFlag(AppProps.EXPERIMENTAL_BLOCKER,
            "Block malicious clients",
            "Reject requests from clients that appear malicious. Turn this feature off if you want to run a security scanner.",
            false);
        AdminConsole.addExperimentalFeatureFlag(FEATURE_FLAG_DISABLE_ENFORCE_CSP,
            "Disable enforce Content Security Policy",
            "Stop sending the " + ContentSecurityPolicyFilter.ContentSecurityPolicyType.Enforce.getHeaderName() + " header to browsers, " +
            "but continue sending the " + ContentSecurityPolicyFilter.ContentSecurityPolicyType.Report.getHeaderName() + " header. " +
            "This turns off an important layer of security for the entire site, so use it as a last resort only on a temporary basis " +
            "(e.g., if an enforce CSP breaks critical functionality).",
            false);

        AdminConsole.addOptionalFeatureFlag(new OptionalFeatureFlag(EXPERIMENTAL_LOCAL_MARKETING_UPDATE,
            "Self test marketing updates", "Test marketing updates from this local server (requires the mothership module).", false, true, FeatureType.Experimental));
        OptionalFeatureService.get().addFeatureListener(EXPERIMENTAL_LOCAL_MARKETING_UPDATE, (feature, enabled) -> {
            // update the timer task when this setting changes
            MothershipReport.setSelfTestMarketingUpdates(enabled);
            UsageReportingLevel.reportNow();
        });

        AdminConsole.addOptionalFeatureFlag(new OptionalFeatureFlag(REMOTE_LOGIN_FEATURE_FLAG, "Restore ability to use the deprecated Remote Login API", "This option and all support for the Remote Login API will be removed in LabKey Server v24.12.", false, false, FeatureType.Deprecated));

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

        UsageMetricsService.get().registerUsageMetrics(getName(), () -> {
            Map<String, Object> results = new HashMap<>();
            Map<String, Object> javaInfo = new HashMap<>();
            javaInfo.put("java.vendor", System.getProperty("java.vendor"));
            javaInfo.put("java.vm.name", System.getProperty("java.vm.name"));
            results.put("javaRuntime", javaInfo);
            results.put("distributionFilename", AppProps.getInstance().getDistributionFilename());
            results.put("applicationMenuDisplayMode", LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getApplicationMenuDisplayMode());
            results.put("optionalFeatures", AdminConsole.getOptionalFeatureFlags().stream()
                .collect(Collectors.groupingBy(optionalFeatureFlag -> optionalFeatureFlag.getType().name().toLowerCase(),
                    Collectors.mapping(flag -> flag, Collectors.toMap(OptionalFeatureFlag::getFlag, OptionalFeatureFlag::isEnabled))
                ))
            );
            results.put("productFeaturesEnabled", ProductRegistry.getProductFeatureSet());
            results.put("analyticsTrackingStatus", AnalyticsServiceImpl.get().getTrackingStatus().toString());
            String labkeyContextPath = AppProps.getInstance().getContextPath();
            results.put("webappContextPath", labkeyContextPath);
            results.put("embeddedTomcat", true);
            boolean customLog4JConfig = false;
            if (ModuleLoader.getServletContext() != null)
            {
                customLog4JConfig = Boolean.parseBoolean(ModuleLoader.getServletContext().getInitParameter("org.labkey.customLog4JConfig"));
            }
            results.put("customLog4JConfig", customLog4JConfig);
            results.put("runtimeMode", AppProps.getInstance().isDevMode() ? "development" : "production");
            Set<String> deployedApps = new HashSet<>(CoreWarningProvider.collectAllDeployedApps());
            deployedApps.remove(labkeyContextPath);
            if (labkeyContextPath.startsWith("/"))
            {
                deployedApps.remove(labkeyContextPath.substring(1));
            }
            results.put("otherDeployedWebapps", StringUtils.join(deployedApps, ","));

            // Report the total number of login entries in the audit log
            results.put("totalLogins", UserManager.getAuthCount(null, false, false, false));
            results.put("apiKeyLogins", UserManager.getAuthCount(null, false, true, false));
            results.put("sessionTimeout", ModuleLoader.getServletContext().getSessionTimeout());
            results.put("userLimits", new LimitActiveUsersSettings().getMetricsMap());
            results.put("systemUserCount", UserManager.getSystemUserCount());
            results.put("workbookCount", ContainerManager.getWorkbookCount());
            results.put("databaseSize", CoreSchema.getInstance().getSchema().getScope().getDatabaseSize());
            Calendar cal = new GregorianCalendar();
            cal.add(Calendar.DATE, -30);
            results.put("uniqueRecentUserCount", UserManager.getAuthCount(cal.getTime(), false, false, true));
            results.put("uniqueRecentNonSystemUserCount", UserManager.getAuthCount(cal.getTime(), true, false, true));
            results.put("scriptEngines", LabKeyScriptEngineManager.get().getScriptEngineMetrics());
            results.put("customLabels", CustomLabelService.get().getCustomLabelMetrics());
            Map<String, Long> roleAssignments = new HashMap<>();
            final String roleCountSql = "SELECT COUNT(*) FROM core.RoleAssignments WHERE userid > 0 AND role = ?";
            roleAssignments.put("assayDesignerCount", new SqlSelector(CoreSchema.getInstance().getSchema(), roleCountSql, "org.labkey.assay.security.AssayDesignerRole").getObject(Long.class));
            roleAssignments.put("dataClassDesignerCount", new SqlSelector(CoreSchema.getInstance().getSchema(), roleCountSql, "org.labkey.experiment.security.DataClassDesignerRole").getObject(Long.class));
            roleAssignments.put("sampleTypeDesignerCount", new SqlSelector(CoreSchema.getInstance().getSchema(), roleCountSql, "org.labkey.experiment.security.SampleTypeDesignerRole").getObject(Long.class));
            results.put("roleAssignments", roleAssignments);
            return results;
        });

        UsageMetricsService.get().registerUsageMetrics(getName(), WebSocketConnectionManager.getInstance());
        UsageMetricsService.get().registerUsageMetrics(getName(), DbLoginManager.getMetricsProvider());
        UsageMetricsService.get().registerUsageMetrics(getName(), SecurityManager.getMetricsProvider());
        UsageMetricsService.get().registerUsageMetrics(getName(), DisplayFormatAnalyzer.getMetricsProvider());

        if (AppProps.getInstance().isDevMode())
            AntiVirusProviderRegistry.get().registerAntiVirusProvider(new DummyAntiVirusService.Provider());

        FileContentService fileContentService = FileContentService.get();
        if (fileContentService != null)
            fileContentService.addFileListener(WebFilesResolverImpl.get());

        RoleManager.registerPermission(new QCAnalystPermission());

        try
        {
            MarkdownService.setInstance(new MarkdownServiceImpl());
        }
        catch (RuntimeException | Error e)
        {
            if (AppProps.getInstance().isDevMode())
            {
                // Be tolerant of inability to render Markdown in dev mode, as
                // redeploying the webapp without bouncing Tomcat causes problems
                // with Graal's JNI registration. See issue 50315
                LOG.error("Error registering MarkdownServiceImpl", e);
            }
            else
            {
                // In production mode, treat this as a fatal error
                throw e;
            }
        }

        // initialize email preference service and listeners
        MessageConfigService.setInstance(new EmailPreferenceConfigServiceImpl());
        ContainerManager.addContainerListener(new EmailPreferenceContainerListener());
        UserManager.addUserListener(new EmailPreferenceUserListener());
    }

    // Issue 7527: Auto-detect missing sql views and attempt to recreate
    private void checkForMissingDbViews()
    {
        ModuleLoader.getInstance().getModules().stream()
            .map(FileSqlScriptProvider::new)
            .flatMap(p -> p.getSchemas().stream()
                    .filter(schema-> SchemaUpdateType.Before.getScript(p, schema) != null || SchemaUpdateType.After.getScript(p, schema) != null)
            )
            .filter(schema -> TableXmlUtils.compareXmlToMetaData(schema, false, false, true).hasViewProblem())
            .findAny()
            .ifPresent(schema ->
            {
                LOG.warn("At least one database view was not as expected in the {} schema. Attempting to recreate views automatically", schema.getName());
                ModuleLoader.getInstance().recreateViews();
            });
    }

    @Override
    public void registerServlets(ServletContext servletCtx)
    {
//        even though there is one webdav tree rooted at "/" we still use two servlet bindings.
//        This is because we want /_webdav/* to be resolved BEFORE all other servlet-mappings
//        and /* to resolve AFTER all other servlet-mappings
        _webdavServletDynamic = servletCtx.addServlet("static", new WebdavServlet(true));
        _webdavServletDynamic.setMultipartConfig(new MultipartConfigElement(SpringActionController.getTempUploadDir().getPath()));
        _webdavServletDynamic.addMapping("/_webdav/*");
    }

    @Override
    public void registerFinalServlets(ServletContext servletCtx)
    {
        _webdavServletDynamic.addMapping("/");
    }

    @Override
    public void startBackgroundThreads()
    {
        SystemMaintenance.setTimer();
        ThumbnailServiceImpl.startThread();
        // Launch in the background, but delay by 10 seconds to reduce impact on other startup tasks
        _warningProvider.startSchemaCheck(10);

        // Start up the default Quartz scheduler, used in many places
        try
        {
            StdSchedulerFactory.getDefaultScheduler().start();
        }
        catch (SchedulerException e)
        {
            throw UnexpectedException.wrap(e);
        }

        if (MothershipReport.shouldReceiveMarketingUpdates())
        {
            if (AppProps.getInstance().getUsageReportingLevel() == UsageReportingLevel.NONE)
            {
                // force the usage reporting level to on for community edition distributions
                WriteableAppProps appProps = AppProps.getWriteableInstance();
                appProps.setUsageReportingLevel(UsageReportingLevel.ON);
                appProps.save(User.getAdminServiceUser());
            }
        }
        // On bootstrap in production mode, this will send an initial ping with very little information, as the admin will
        // not have set up their account yet. On later startups, depending on the reporting level, this will send an immediate
        // ping, and then once every 24 hours.
        UsageReportingLevel.init();
        TempTableTracker.init();

        // Loading the PDFBox font cache can be very slow on some agents; fill it proactively. Issue 50601
        JobRunner.getDefault().execute(() -> {
            try
            {
                long start = System.currentTimeMillis();
                FontMapper mapper = FontMappers.instance();
                Method method = mapper.getClass().getMethod("getProvider");
                method.setAccessible(true);
                method.invoke(mapper);
                long duration = System.currentTimeMillis() - start;
                LOG.info("Ensuring PDFBox on-disk font cache took {} seconds", Math.round(duration / 100.0) / 10.0);
            }
            catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e)
            {
                LOG.warn("Unable to initialize PDFBox font cache", e);
            }
        });
    }

    @Override
    public List<Summary> getDetailedSummary(Container c)
    {
        int childContainerCount = ((Long) ContainerManager.getChildren(c).stream().filter(Container::isInFolderNav).count()).intValue();
        return childContainerCount > 0
                ? List.of(new Summary(childContainerCount, "Subfolder"))
                : new ArrayList<>();
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        JSONObject json = new JSONObject(getDefaultPageContextJson(context.getContainer()));
        json.put("productFeatures", ProductRegistry.getProductFeatureSet());
        json.put(EXPERIMENTAL_CALCULATED_FIELDS, OptionalFeatureService.get().isFeatureEnabled(EXPERIMENTAL_CALCULATED_FIELDS));
        return json;
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
            AdminController.WorkbookDeleteTestCase.class,
            AttachmentServiceImpl.TestCase.class,
            CoreController.TestCase.class,
            DataRegion.TestCase.class,
            DavController.TestCase.class,
            EmailServiceImpl.TestCase.class,
            FilesSiteSettingsAction.TestCase.class,
            LoggerController.TestCase.class,
            LoggingTestCase.class,
            LoginController.TestCase.class,
            ModuleInfoTestCase.class,
            ModulePropertiesTestCase.class,
            ModuleStaticResolverImpl.TestCase.class,
            NotificationServiceImpl.TestCase.class,
            PortalJUnitTest.class,
            PostgreSqlInClauseTest.class,
            ProductRegistry.TestCase.class,
            RadeoxRenderer.RadeoxRenderTest.class,
            RhinoService.TestCase.class,
            SchemaXMLTestCase.class,
            SecurityApiActions.TestCase.class,
            SecurityController.TestCase.class,
            SqlDialect.DialectTestCase.class,
            SqlScriptController.TestCase.class,
            TableViewFormTestCase.class,
            UnknownSchemasTest.class,
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
            ApiJsonWriter.TestCase.class,
            CopyFileRootPipelineJob.TestCase.class,
            OutOfRangeDisplayColumn.TestCase.class,
            PostgreSqlVersion.TestCase.class,
            ScriptEngineManagerImpl.TestCase.class,
            StatsServiceImpl.TestCase.class,


            // Radeox tests
            SimpleListTest.class,
            ExampleListFormatterTest.class,
            AtoZListFormatterTest.class,
            BaseRenderEngineTest.class,
            BasicRegexTest.class,
            ItalicFilterTest.class,
            BoldFilterTest.class,
            KeyFilterTest.class,
            NewlineFilterTest.class,
            LineFilterTest.class,
            TypographyFilterTest.class,
            HtmlRemoveFilterTest.class,
            StrikeThroughFilterTest.class,
            UrlFilterTest.class,
            ParamFilterTest.class,
            FilterPipeTest.class,
            EscapeFilterTest.class,
            LinkTestFilterTest.class,
            WikiLinkFilterTest.class,
            SmileyFilterTest.class,
            ListFilterTest.class,
            HeadingFilterTest.class

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
        return List.of
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

        // Add the "labkey" schema in all module data sources as well... should match application.properties
        for (String dataSourceName : ModuleLoader.getInstance().getAllModuleDataSourceNames())
        {
            DbScope scope = DbScope.getDbScope(dataSourceName);
            if (scope != null)
            {
                result.add(scope.getLabKeySchema());
            }
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
     * Handles startup props for LookAndFeelSettings resources
     */
    private void populateLookAndFeelResourcesWithStartupProps()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(WriteableLookAndFeelProperties.SCOPE_LOOK_AND_FEEL_SETTINGS, ResourceType.class)
        {
            @Override
            public void handle(Map<ResourceType, StartupPropertyEntry> map)
            {
                boolean incrementRevision = false;

                for (Map.Entry<ResourceType, StartupPropertyEntry> entry : map.entrySet())
                {
                    SiteResourceHandler handler = getResourceHandler(entry.getKey());
                    if (handler != null)
                        incrementRevision |= setSiteResource(handler, entry.getValue(), User.guest);
                }

                // Bump the look & feel revision so browsers retrieve the new logo, custom stylesheet, etc.
                if (incrementRevision)
                    WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            }
        });
    }

    /**
     * This method handles the home project settings
     */
    private void populateSiteSettingsWithStartupProps()
    {
        Map<StashedStartupProperties, StartupPropertyEntry> props = AppProps.getInstance().getStashedStartupProperties();

        StartupPropertyEntry folderTypeEntry = props.get(homeProjectFolderType);
        if (null != folderTypeEntry)
        {
            FolderType folderType = FolderTypeManager.get().getFolderType(folderTypeEntry.getValue());
            if (folderType != null)
                // using guest user since the server startup doesn't have a true user (this will be used for audit events)
                ContainerManager.getHomeContainer().setFolderType(folderType, User.guest);
            else
                LOG.error("Unable to find folder type for home project during server startup: " + folderTypeEntry.getValue());
        }

        StartupPropertyEntry resetPermissionsEntry = props.get(homeProjectResetPermissions);
        if (null != resetPermissionsEntry && Boolean.valueOf(resetPermissionsEntry.getValue()))
        {
            // reset the home project permissions to remove the default assignments given at server install
            MutableSecurityPolicy homePolicy = new MutableSecurityPolicy(ContainerManager.getHomeContainer());
            SecurityPolicyManager.savePolicy(homePolicy, User.getAdminServiceUser());
            // remove the guest role assignment from the support subfolder
            Group guests = SecurityManager.getGroup(Group.groupGuests);
            if (null != guests)
            {
                Container supportFolder = ContainerManager.getDefaultSupportContainer();
                if (supportFolder != null)
                {
                    MutableSecurityPolicy supportPolicy = new MutableSecurityPolicy(supportFolder.getPolicy());
                    for (Role assignedRole : supportPolicy.getAssignedRoles(guests))
                        supportPolicy.removeRoleAssignment(guests, assignedRole);
                    SecurityPolicyManager.savePolicy(supportPolicy, User.getAdminServiceUser());
                }
            }
        }

        StartupPropertyEntry webparts = props.get(homeProjectWebparts);
        if (null != webparts)
        {
            // Clear existing webparts added by core and wiki modules
            Container homeContainer = ContainerManager.getHomeContainer();
            Portal.saveParts(homeContainer, Collections.emptyList());

            for (String webpartName : StringUtils.split(webparts.getValue(), ';'))
            {
                WebPartFactory webPartFactory = Portal.getPortalPart(webpartName);
                if (webPartFactory != null)
                    addWebPart(webPartFactory.getName(), homeContainer, HttpView.BODY);
            }
        }
    }

    private @Nullable SiteResourceHandler getResourceHandler(@NotNull ResourceType type)
    {
        return LookAndFeelPropertiesManager.get().getResourceHandler(type);
    }

    private boolean setSiteResource(SiteResourceHandler resourceHandler, StartupPropertyEntry prop, User user)
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

    public void rerunSchemaCheck()
    {
        // Queue a job without delay. This avoids executing multiple overlapping schema checks. Not bothering with a
        // more surgical approach since this variant is likely being called during development.
        _warningProvider.startSchemaCheck(0);
    }
}
