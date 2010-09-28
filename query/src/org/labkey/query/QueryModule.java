/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.JavaScriptExportScriptFactory;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RExportScriptFactory;
import org.labkey.api.reports.LabkeyScriptEngineManager;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.*;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.StudySerializationRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartFactory;
import org.labkey.query.controllers.QueryController;
import org.labkey.query.data.SimpleTableDomainKind;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.SchemaReloadMaintenanceTask;
import org.labkey.query.reports.*;
import org.labkey.query.reports.chart.TimeSeriesRenderer;
import org.labkey.query.reports.chart.XYChartRenderer;
import org.labkey.query.reports.view.ReportUIProvider;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.view.QueryWebPartFactory;

import javax.script.ScriptEngineManager;
import java.util.*;


public class QueryModule extends DefaultModule
{
    public String getName()
    {
        return "Query";
    }

    public double getVersion()
    {
        return 10.20;
    }

    protected void init()
    {
        addController("query", QueryController.class);
        addController("reports", ReportsController.class);
        addController("viz", VisualizationController.class);

        QueryServiceImpl i = new QueryServiceImpl();
        QueryService.set(i);
        ServiceRegistry.get().registerService(QueryService.class, i);

        ContainerManager.addContainerListener(QueryManager.CONTAINER_LISTENER);

        ReportService.registerProvider(new ReportServiceImpl());
        ReportService.get().addUIProvider(new ReportUIProvider());

        ChartRendererFactory.get().addChartRenderer(XYChartRenderer.getInstance());
        ChartRendererFactory.get().addChartRenderer(TimeSeriesRenderer.getInstance());
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new ChartReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());

        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new ChartQueryReport());
        ReportService.get().registerReport(new RReport());
        ReportService.get().registerReport(new ExternalScriptEngineReport());
        ReportService.get().registerReport(new InternalScriptEngineReport());

        QueryView.register(new RExportScriptFactory());
        QueryView.register(new JavaScriptExportScriptFactory());
//		WebdavService.addProvider(new QueryWebdavprovider());
    }


    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(new QueryWebPartFactory(), new ReportsWebPartFactory()));
    }

    public boolean hasScripts()
    {
        return true;
    }

    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new QueryUpgradeCode();
    }

    public void startup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ReportsPipelineProvider(this));
        ReportsController.registerAdminConsoleLinks();
        QueryController.registerAdminConsoleLinks();

        ServiceRegistry.get().registerService(ScriptEngineManager.class, new LabkeyScriptEngineManager());

        StudySerializationRegistry registry = ServiceRegistry.get().getService(StudySerializationRegistry.class);

        if (null != registry)
        {
            registry.addFactories(new QueryWriter.Factory(), new QueryImporter.Factory());
            registry.addFactories(new CustomViewWriter.Factory(), new CustomViewImporter.Factory());
            registry.addFactories(new ReportWriter.Factory(), new ReportImporter.Factory());
        }

        SystemMaintenance.addTask(new SchemaReloadMaintenanceTask());
        ServiceRegistry.get(SearchService.class).addDocumentProvider(ExternalSchemaDocumentProvider.getInstance());
        ServiceRegistry.get(SearchService.class).addSearchCategory(ExternalSchemaDocumentProvider.externalTableCategory);

        PropertyService.get().registerDomainKind(new SimpleTableDomainKind());
    }

    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(QueryManager.get().getDbSchemaName());
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return Collections.singleton(QueryManager.get().getDbSchema());
    }

    public Set<Class> getJUnitTests()
    {
        return new HashSet<Class>(Arrays.asList(
                SqlParser.TestCase.class,
                Query.TestCase.class,
                QueryServiceImpl.TestCase.class
        ));
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        // Don't show Query nav trails to users that aren't admins or developers since they almost certainly don't want
        // to go to those links
        if (c.hasPermission(user, AdminPermission.class) || user.isDeveloper() || user.isAdministrator())
        {
            return super.getTabURL(c, user);
        }
        return null;
    }
}
