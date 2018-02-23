/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ExternalScriptEngineReport;
import org.labkey.api.reports.report.InternalScriptEngineReport;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.JavaScriptReportDescriptor;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.QueryReportDescriptor;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.stats.AnalyticsProviderRegistry;
import org.labkey.api.stats.SummaryStatisticRegistry;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.WebPartFactory;
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
import org.labkey.query.reports.ReportImporter;
import org.labkey.query.reports.ReportNotificationInfoProvider;
import org.labkey.query.reports.ReportServiceImpl;
import org.labkey.query.reports.ReportWriter;
import org.labkey.query.reports.ReportsController;
import org.labkey.query.reports.ReportsPipelineProvider;
import org.labkey.query.reports.ReportsWebPartFactory;
import org.labkey.query.reports.chart.TimeSeriesRenderer;
import org.labkey.query.reports.chart.XYChartRenderer;
import org.labkey.query.reports.getdata.AggregateQueryDataTransform;
import org.labkey.query.reports.getdata.FilterClauseBuilder;
import org.labkey.query.reports.view.ReportAndDatasetChangeDigestEmailTemplate;
import org.labkey.query.reports.view.ReportUIProvider;
import org.labkey.query.sql.QNode;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.view.InheritedQueryDataViewProvider;
import org.labkey.query.view.QueryDataViewProvider;
import org.labkey.query.view.QueryWebPartFactory;

import javax.script.ScriptEngineManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


public class QueryModule extends DefaultModule
{
    public QueryModule()
    {
        QueryServiceImpl i = new QueryServiceImpl();
        QueryService.set(i);
        QueryDriver.register();
        ReportAndDatasetChangeDigestProvider.set(new ReportAndDatasetChangeDigestProviderImpl());
    }

    public String getName()
    {
        return "Query";
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        DefaultSchema.registerProvider("rolap_test", new DefaultSchema.SchemaProvider(this)
        {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                return schema.getContainer().getParsedPath().equals(JunitUtil.getTestContainerPath());
            }

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

        ContainerManager.addContainerListener(QueryManager.CONTAINER_LISTENER, ContainerManager.ContainerListener.Order.Last);

        QueryService.get().addQueryListener(new CustomViewQueryChangeListener());
        QueryService.get().addQueryListener(new QuerySnapshotQueryChangeListener());

        ReportService.registerProvider(ReportServiceImpl.getInstance());
        ReportService.get().addUIProvider(new ReportUIProvider());

        ChartRendererFactory.get().addChartRenderer(XYChartRenderer.getInstance());
        ChartRendererFactory.get().addChartRenderer(TimeSeriesRenderer.getInstance());
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new ChartReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());
        ReportService.get().registerDescriptor(new JavaScriptReportDescriptor());

        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new ChartQueryReport());
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

        DataViewService.get().registerProvider(QueryDataViewProvider.TYPE, new QueryDataViewProvider());
        DataViewService.get().registerProvider(InheritedQueryDataViewProvider.TYPE, new InheritedQueryDataViewProvider());

        AdminConsole.addExperimentalFeatureFlag(QueryView.EXPERIMENTAL_GENERIC_DETAILS_URL, "Generic [details] link in grids/queries",
                "This feature will turn on generating a generic [details] URL link in most grids.", false);
    }


    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
                new QueryWebPartFactory(),
                new ReportsWebPartFactory(),
                new DataViewsWebPartFactory()
//                new QueryBrowserWebPartFactory()
        ));
    }

    public boolean hasScripts()
    {
        return true;
    }

    public void doStartup(ModuleContext moduleContext)
    {
        if (null != PipelineService.get())
            PipelineService.get().registerPipelineProvider(new ReportsPipelineProvider(this));
        ReportsController.registerAdminConsoleLinks();
        QueryController.registerAdminConsoleLinks();

        ServiceRegistry.get().registerService(ScriptEngineManager.class, new LabKeyScriptEngineManager());

        FolderSerializationRegistry folderRegistry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != folderRegistry)
        {
            folderRegistry.addFactories(new QueryWriter.Factory(), new QueryImporter.Factory());
            folderRegistry.addFactories(new CustomViewWriter.Factory(), new CustomViewImporter.Factory());
            folderRegistry.addFactories(new ReportWriter.Factory(), new ReportImporter.Factory());
            folderRegistry.addFactories(new ExternalSchemaDefWriterFactory(), new ExternalSchemaDefImporterFactory());
        }

        // support importing Queries, Custom Views, and Reports from the study archive for backwards compatibility
        StudySerializationRegistry studyRegistry = ServiceRegistry.get().getService(StudySerializationRegistry.class);
        if (null != studyRegistry)
        {
            studyRegistry.addImportFactory(new QueryImporter.Factory());
            studyRegistry.addImportFactory(new CustomViewImporter.Factory());
            studyRegistry.addImportFactory(new ReportImporter.Factory());
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

        ReportAndDatasetChangeDigestProvider.get().addNotificationInfoProvider(new ReportNotificationInfoProvider());
        DailyMessageDigest.getInstance().addProvider(ReportAndDatasetChangeDigestProvider.get());
        // Note: DailyMessageDigest timer is initialized by the AnnouncementModule

        CacheManager.addListener(new ServerManager.CacheListener());

        AdminLinkManager.getInstance().addListener((adminNavTree, container, user) -> {
            if (container.hasPermission(user, ReadPermission.class))
                adminNavTree.addChild(new NavTree("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(container)));
        });

        AnalyticsProviderRegistry analyticsProviderRegistry = ServiceRegistry.get().getService(AnalyticsProviderRegistry.class);
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

        SummaryStatisticRegistry summaryStatisticRegistry = ServiceRegistry.get().getService(SummaryStatisticRegistry.class);
        if (null != summaryStatisticRegistry)
        {
            summaryStatisticRegistry.register(Aggregate.BaseType.SUM);
            summaryStatisticRegistry.register(Aggregate.BaseType.MEAN);
            summaryStatisticRegistry.register(Aggregate.BaseType.COUNT);
            summaryStatisticRegistry.register(Aggregate.BaseType.MIN);
            summaryStatisticRegistry.register(Aggregate.BaseType.MAX);
        }
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
        return new HashSet<>(Arrays.asList(
                Query.QueryTestCase.class,
                QueryServiceImpl.TestCase.class,
                RolapReader.RolapTest.class,
                RolapTestCase.class,
                MultiValueTest.class,
                OlapController.TestCase.class,
                QueryController.TestCase.class,
                ModuleReportCache.TestCase.class,
                ServerManager.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<Class> getUnitTests()
    {
        return new HashSet<>(Arrays.asList(
                SqlParser.SqlParserTestCase.class,
                JdbcType.TestCase.class,
                QNode.TestCase.class,
                TableWriter.TestCase.class,
                AggregateQueryDataTransform.TestCase.class,
                FilterClauseBuilder.TestCase.class,
                MemberSet.TestCase.class,
                MetadataElementBase.TestCase.class,
                AttachmentReport.TestCase.class
        ));
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        // Don't show Query nav trails to users who aren't admins or developers since they almost certainly don't want
        // to go to those links
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper())
        {
            return super.getTabURL(c, user);
        }
        return null;
    }
}
