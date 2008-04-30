package org.labkey.query;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.QueryService;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.*;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.controllers.QueryControllerSpring;
import org.labkey.query.controllers.dbuserschema.DbUserSchemaController;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.reports.ReportsController;
import org.labkey.query.reports.ReportsPipelineProvider;
import org.labkey.query.reports.ReportServiceImpl;
import org.labkey.query.reports.ReportsWebPartFactory;
import org.labkey.query.reports.chart.TimeSeriesRenderer;
import org.labkey.query.reports.chart.XYChartRenderer;
import org.labkey.query.view.QueryWebPartFactory;

import java.util.Set;


public class QueryModule extends DefaultModule
{
    public static final String NAME = "Query";
    public QueryModule()
    {
        super(NAME, 8.11, null, true, new QueryWebPartFactory(), new ReportsWebPartFactory());
        addController("query", QueryControllerSpring.class);
        addController("reports", ReportsController.class);
        addController("dbuserschema", DbUserSchemaController.class);
        
        QueryService.set(new QueryServiceImpl());
        ContainerManager.addContainerListener(QueryManager.CONTAINER_LISTENER);

        ReportService.registerProvider(new ReportServiceImpl());
        
        ChartRendererFactory.get().addChartRenderer(XYChartRenderer.getInstance());
        ChartRendererFactory.get().addChartRenderer(TimeSeriesRenderer.getInstance());
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new ChartReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());

        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new ChartQueryReport());
        ReportService.get().registerReport(new RReport());
    }


    public void startup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new ReportsPipelineProvider());
        super.startup(moduleContext);

/*
        ReportService.get().registerDescriptor(new ReportDescriptor());
        ReportService.get().registerDescriptor(new ChartReportDescriptor());
        ReportService.get().registerDescriptor(new QueryReportDescriptor());
        ReportService.get().registerDescriptor(new RReportDescriptor());

        ReportService.get().registerReport(new QueryReport());
        ReportService.get().registerReport(new ChartQueryReport());
        ReportService.get().registerReport(new RReport());
*/
    }


    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(QueryManager.get().getDbSchemaName());
    }
}
