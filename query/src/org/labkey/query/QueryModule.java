/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.query;

import org.apache.commons.collections4.Factory;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.DefaultAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.views.DataViewService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.digest.ReportAndDatasetChangeDigestProvider;
import org.labkey.api.module.AdminLinkManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.JavaExportScriptFactory;
import org.labkey.api.query.JavaScriptExportScriptFactory;
import org.labkey.api.query.PerlExportScriptFactory;
import org.labkey.api.query.PythonExportScriptFactory;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RExportScriptFactory;
import org.labkey.api.query.SasExportScriptFactory;
import org.labkey.api.query.SimpleTableDomainKind;
import org.labkey.api.query.URLExportScriptFactory;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ExternalScriptEngineReport;
import org.labkey.api.reports.report.InternalScriptEngineReport;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.JavaScriptReportDescriptor;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.QueryReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.python.IpynbReport;
import org.labkey.api.reports.report.python.IpynbReportDescriptor;
import org.labkey.api.reports.report.r.RReport;
import org.labkey.api.reports.report.r.RReportDescriptor;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.writer.ContainerUser;
import org.labkey.query.analytics.AggregatesCountNonBlankAnalyticsProvider;
import org.labkey.query.analytics.AggregatesMaxAnalyticsProvider;
import org.labkey.query.analytics.AggregatesMeanAnalyticsProvider;
import org.labkey.query.analytics.AggregatesMinAnalyticsProvider;
import org.labkey.query.analytics.AggregatesSumAnalyticsProvider;
import org.labkey.query.analytics.RemoveColumnAnalyticsProvider;
import org.labkey.query.analytics.SummaryStatisticsAnalyticsProvider;
import org.labkey.query.audit.QueryExportAuditProvider;
import org.labkey.query.audit.QueryUpdateAuditProvider;
import org.labkey.query.controllers.OlapController;
import org.labkey.query.controllers.QueryController;
import org.labkey.query.controllers.SqlController;
import org.labkey.query.jdbc.QueryDriver;
import org.labkey.query.olap.MemberSet;
import org.labkey.query.olap.ServerManager;
import org.labkey.query.olap.metadata.MetadataElementBase;
import org.labkey.query.olap.rolap.RolapReader;
import org.labkey.query.olap.rolap.RolapTestCase;
import org.labkey.query.olap.rolap.RolapTestSchema;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.reports.AttachmentReport;
import org.labkey.query.reports.LinkReport;
import org.labkey.query.reports.ModuleReportCache;
import org.labkey.query.reports.ReportAndDatasetChangeDigestProviderImpl;
import org.labkey.query.reports.ReportAuditProvider;
import org.labkey.query.reports.ReportImporter;
import org.labkey.query.reports.ReportNotificationInfoProvider;
import org.labkey.query.reports.ReportServiceImpl;
import org.labkey.query.reports.ReportViewProvider;
import org.labkey.query.reports.ReportWriter;
import org.labkey.query.reports.ReportsController;
import org.labkey.query.reports.ReportsPipelineProvider;
import org.labkey.query.reports.ReportsWebPartFactory;
import org.labkey.query.reports.ViewCategoryImporter;
import org.labkey.query.reports.ViewCategoryWriter;
import org.labkey.query.reports.getdata.AggregateQueryDataTransform;
import org.labkey.query.reports.getdata.FilterClauseBuilder;
import org.labkey.query.reports.view.ReportAndDatasetChangeDigestEmailTemplate;
import org.labkey.query.reports.view.ReportUIProvider;
import org.labkey.query.sql.Method;
import org.labkey.query.sql.QNode;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.view.InheritedQueryDataViewProvider;
import org.labkey.query.view.QueryDataViewProvider;
import org.labkey.query.view.QueryWebPartFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.labkey.api.query.QueryService.USE_ROW_BY_ROW_UPDATE;

public class QueryModule extends DefaultModule
{
    public QueryModule()
    {
        QueryService.setInstance(new QueryServiceImpl());
        BuiltInColumnTypes.registerStandardColumnTransformers();

        QueryDriver.register();
        ReportAndDatasetChangeDigestProvider.set(new ReportAndDatasetChangeDigestProviderImpl());
    }

    @Override
    public String getName()
    {
        return "Query";
    }

    @Override
    public Double getSchemaVersion()
    {
        return 24.000;
    }

    @Override
    protected void init()
    {
        DefaultSchema.registerProvider("rolap_test", new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return schema.getContainer().getParsedPath().equals(JunitUtil.getTestContainerPath());
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new RolapTestSchema(schema.getUser(), schema.getContainer());
            }
        });

        addController("query", QueryController.class);
        addController("sql", SqlController.class);
        addController("reports", ReportsController.class);
        addController("olap", OlapController.class);

        ExternalSchema.register();
        LinkedSchema.register();

        QueryService.get().addQueryListener(new CustomViewQueryChangeListener());
        QueryService.get().addQueryListener(new QuerySnapshotQueryChangeListener());
        QueryService.get().addQueryListener(new QueryDefQueryChangeListener());

        ReportService.registerProvider(ReportServiceImpl.getInstance());
        ReportService.get().addUIProvider(new ReportUIProvider());
        ReportService.get().addGlobalItemFilterType(JavaScriptReport.TYPE);
        ReportService.get().addGlobalItemFilterType(QuerySnapshotService.TYPE);
        ReportService.get().addGlobalItemFilterType(IpynbReport.TYPE);

        ReportService.get().registerDescriptor(new IpynbReportDescriptor());
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());
        ReportService.get().registerDescriptor(new JavaScriptReportDescriptor());

        ReportService.get().registerReport(new IpynbReport());
        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new RReport());
        ReportService.get().registerReport(new ExternalScriptEngineReport());
        ReportService.get().registerReport(new InternalScriptEngineReport());
        ReportService.get().registerReport(new JavaScriptReport());
        ReportService.get().registerReport(new AttachmentReport());
        ReportService.get().registerReport(new LinkReport());
        EmailTemplateService.get().registerTemplate(ReportAndDatasetChangeDigestEmailTemplate.class);

        QueryView.register(new RExportScriptFactory());
        QueryView.register(new JavaScriptExportScriptFactory());
        QueryView.register(new PerlExportScriptFactory());
        QueryView.register(new JavaExportScriptFactory());
        QueryView.register(new URLExportScriptFactory());
        QueryView.register(new PythonExportScriptFactory());
        QueryView.register(new SasExportScriptFactory());

        DataViewService.get().registerProvider(ReportViewProvider.TYPE, new ReportViewProvider());

        DataViewService.get().registerProvider(QueryDataViewProvider.TYPE, new QueryDataViewProvider());
        DataViewService.get().registerProvider(InheritedQueryDataViewProvider.TYPE, new InheritedQueryDataViewProvider());

        AdminConsole.addExperimentalFeatureFlag(QueryView.EXPERIMENTAL_GENERIC_DETAILS_URL, "Generic [details] link in grids/queries",
                "This feature will turn on generating a generic [details] URL link in most grids.", false);
        AdminConsole.addExperimentalFeatureFlag(QueryServiceImpl.EXPERIMENTAL_LAST_MODIFIED, "Include Last-Modified header on query metadata requests",
                "For schema, query, and view metadata requests include a Last-Modified header such that the browser can cache the response. " +
                "The metadata is invalidated when performing actions such as creating a new List or modifying the columns on a custom view", false);
        AdminConsole.addExperimentalFeatureFlag(USE_ROW_BY_ROW_UPDATE, "Use row-by-row update", "For Query.updateRows api, do row-by-row update, instead of using a prepared statement that updates rows in batches.", false);
        AdminConsole.addExperimentalFeatureFlag(QueryServiceImpl.EXPERIMENTAL_PRODUCT_ALL_FOLDER_LOOKUPS, "Less restrictive product folder lookups",
                "Allow for lookup fields in product folders to query across all folders within the top-level folder.", false);
        AdminConsole.addExperimentalFeatureFlag(QueryServiceImpl.EXPERIMENTAL_PRODUCT_PROJECT_DATA_LISTING_SCOPED, "Product folders display folder-specific data",
                "Only list folder-specific data within product folders.", false);
    }


    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return List.of(
            new DataViewsWebPartFactory(),
            new QueryWebPartFactory(),
            new ReportsWebPartFactory()
//            new QueryBrowserWebPartFactory()
        );
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(QueryManager.CONTAINER_LISTENER, ContainerManager.ContainerListener.Order.Last);

        if (null != PipelineService.get())
            PipelineService.get().registerPipelineProvider(new ReportsPipelineProvider(this));
        QueryController.registerAdminConsoleLinks();

        FolderSerializationRegistry folderRegistry = FolderSerializationRegistry.get();
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new QueryWriter.Factory(), new QueryImporter.Factory());
            folderRegistry.addFactories(new CustomViewWriter.Factory(), new CustomViewImporter.Factory());
            folderRegistry.addFactories(new ReportWriter.Factory(), new ReportImporter.Factory());
            folderRegistry.addFactories(new ViewCategoryWriter.Factory(), new ViewCategoryImporter.Factory());
            folderRegistry.addFactories(new ExternalSchemaDefWriterFactory(), new ExternalSchemaDefImporterFactory());
        }

        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addDocumentProvider(ExternalSchemaDocumentProvider.getInstance());
            ss.addSearchCategory(ExternalSchemaDocumentProvider.externalTableCategory);
        }
        if (null != PropertyService.get())
            PropertyService.get().registerDomainKind(new SimpleTableDomainKind());

        if (null != AuditLogService.get() && AuditLogService.get().getClass() != DefaultAuditProvider.class)
        {
            AuditLogService.get().registerAuditType(new QueryExportAuditProvider());
            AuditLogService.get().registerAuditType(new QueryUpdateAuditProvider());
        }
        AuditLogService.get().registerAuditType(new ReportAuditProvider());

        ReportAndDatasetChangeDigestProvider.get().addNotificationInfoProvider(new ReportNotificationInfoProvider());
        DailyMessageDigest.getInstance().addProvider(ReportAndDatasetChangeDigestProvider.get());
        // Note: DailyMessageDigest timer is initialized by the AnnouncementModule

        CacheManager.addListener(new ServerManager.CacheListener());
        CacheManager.addListener(new QueryServiceImpl.CacheListener());

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            if (container.hasPermission(user, ReadPermission.class))
                adminNavTree.addChild(new NavTree("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(container)));
        });

        AnalyticsProviderRegistry analyticsProviderRegistry = AnalyticsProviderRegistry.get();
        if (null != analyticsProviderRegistry)
        {
            analyticsProviderRegistry.registerProvider(new AggregatesCountNonBlankAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new AggregatesSumAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new AggregatesMeanAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new AggregatesMinAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new AggregatesMaxAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new SummaryStatisticsAnalyticsProvider());
            analyticsProviderRegistry.registerProvider(new RemoveColumnAnalyticsProvider());
        }

        SummaryStatisticRegistry summaryStatisticRegistry = SummaryStatisticRegistry.get();
        if (null != summaryStatisticRegistry)
        {
            summaryStatisticRegistry.register(Aggregate.BaseType.SUM);
            summaryStatisticRegistry.register(Aggregate.BaseType.MEAN);
            summaryStatisticRegistry.register(Aggregate.BaseType.COUNT);
            summaryStatisticRegistry.register(Aggregate.BaseType.MIN);
            summaryStatisticRegistry.register(Aggregate.BaseType.MAX);
        }

        QueryManager.registerUsageMetrics(getName());
        ReportServiceImpl.registerUsageMetrics(getName());

        // Administrators, Platform Developers, and Trusted Analysts can edit queries, if they also have edit permissions in the current folder
        RoleManager.registerPermission(new EditQueriesPermission());
        Role platformDeveloperRole = RoleManager.getRole(PlatformDeveloperRole.class);
        platformDeveloperRole.addPermission(EditQueriesPermission.class);
        Role trustedAnalystRole = RoleManager.getRole("org.labkey.api.security.roles.TrustedAnalystRole");
        if (null != trustedAnalystRole)
            trustedAnalystRole.addPermission(EditQueriesPermission.class);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(QueryManager.get().getDbSchemaName(), "junit");
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
            ModuleReportCache.TestCase.class,
            OlapController.TestCase.class,
            QueryController.TestCase.class,
            QueryController.SaveRowsTestCase.class,
            QueryServiceImpl.TestCase.class,
            RolapReader.RolapTest.class,
            RolapTestCase.class,
            ServerManager.TestCase.class
        );
    }

    @Override
    public @NotNull List<Factory<Class<?>>> getIntegrationTestFactories()
    {
        List<Factory<Class<?>>> ret = new ArrayList<>(super.getIntegrationTestFactories());
        ret.add(new JspTestCase("/org/labkey/query/MultiValueTest.jsp"));
        ret.add(new JspTestCase("/org/labkey/query/olap/OlapTestCase.jsp"));
        ret.add(new JspTestCase("/org/labkey/query/QueryServiceImplTestCase.jsp"));
        ret.add(new JspTestCase("/org/labkey/query/QueryTestCase.jsp"));
        ret.add(new JspTestCase("/org/labkey/query/sql/CalculatedColumnTestCase.jsp"));

        return ret;
    }


    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return Set.of(
            AggregateQueryDataTransform.TestCase.class,
            AttachmentReport.TestCase.class,
            FilterClauseBuilder.TestCase.class,
            JdbcType.TestCase.class,
            MemberSet.TestCase.class,
            MetadataElementBase.TestCase.class,
            Method.TestCase.class,
            QNode.TestCase.class,
            Query.TestCase.class,
            ReportsController.SerializationTest.class,
            SqlParser.SqlParserTestCase.class,
            TableWriter.TestCase.class
        );
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        // Don't show Query nav trails to users who aren't admins or developers since they almost certainly don't want
        // to go to those links
        if (c.hasOneOf(user, AdminPermission.class, PlatformDeveloperPermission.class))
        {
            return super.getTabURL(c, user);
        }
        return null;
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        JSONObject json = super.getPageContextJson(context);
        boolean hasEditQueriesPermission = context.getContainer().hasPermission(context.getUser(), EditQueriesPermission.class);
        json.put("hasEditQueriesPermission", hasEditQueriesPermission);
        Container container = context.getContainer();
        boolean isProductFoldersEnabled = container != null && container.isProductFoldersEnabled();  // TODO: should these be moved to CoreModule?
        json.put(QueryService.PRODUCT_FOLDERS_ENABLED, isProductFoldersEnabled);
        json.put(QueryService.PRODUCT_FOLDERS_EXIST, isProductFoldersEnabled && container.hasProductFolders());
        json.put(QueryService.EXPERIMENTAL_PRODUCT_ALL_FOLDER_LOOKUPS, QueryService.get().isProductFoldersAllFolderScopeEnabled());
        json.put(QueryService.EXPERIMENTAL_PRODUCT_PROJECT_DATA_LISTING_SCOPED, QueryService.get().isProductFoldersDataListingScopedToProject());
        return json;
    }
}
