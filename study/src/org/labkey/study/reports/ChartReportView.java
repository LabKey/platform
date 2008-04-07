package org.labkey.study.reports;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.data.*;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.RunChartReportView;
import org.labkey.api.security.ACL;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Nov 17, 2006
 */
public class ChartReportView extends AbstractReportView
{
    public static final String TYPE = "Study.chartReportView";
    public static final String REPORT_GUID = "reportGuid";

    public ChartReportView()
    {
        _reportType = ChartReportView.class.getName();
    }

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Study Chart Report View";
    }

    public String getDescriptorType()
    {
        return ChartReportViewDescriptor.TYPE;
    }

    public void setDescriptor(ReportDescriptor descriptor)
    {
        if (descriptor instanceof ChartReportViewDescriptor)
            ((ChartReportViewDescriptor)descriptor).setParent(this);

        super.setDescriptor(descriptor);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public void init(ViewContext context)
    {
        final ActionURL url = context.getActionURL();
        final String dataset = url.getParameter("datasetId");
        final String chartsPerRow = url.getParameter("chartsPerRow");

        if (dataset != null)
            setShowWithDataset(Integer.parseInt(dataset));
        
        List<Pair<String, String>> params = new ArrayList();
        params.add(new Pair("datasetId", dataset));
        if (!StringUtils.isEmpty(chartsPerRow))
            params.add(new Pair("chartsPerRow", chartsPerRow));

        ReportDescriptor descriptor = getDescriptor();
        descriptor.setProperty(QueryParam.schemaName.toString(), StudyManager.getSchemaName());
        descriptor.setProperty(QueryParam.queryName.toString(), url.getParameter(QueryParam.queryName.toString()));

        String viewName = url.getParameter(QueryParam.viewName.toString());
        if (!StringUtils.isEmpty(viewName))
            descriptor.setProperty(QueryParam.viewName.toString(), viewName);

        setParams(PageFlowUtil.toQueryString(params));
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        Report[] reports = getChildReports(context);
        if (reports.length == 1)
            return reports[0].getRunReportView(context);
        // Issue #5634, Backwards compatibility for when multiple charts could be embedded into a parent chart.
        // this is no longer supported directly in the designer, instead the multi chart option is used.
        //
        else if (reports.length > 1)
            return new StudyRunChartReportView(reports);

        return super.getRunReportView(context);
    }

    public void deleteView(ViewContext context) throws SQLException
    {
    }

    public void saveView(ViewContext context) throws Exception {
    }

    public void beforeSave(ViewContext context)
    {
        String reportGuid = getDescriptor().getProperty(REPORT_GUID);
        if (StringUtils.isEmpty(reportGuid))
        {
            final String key = GUID.makeGUID();
            getDescriptor().setProperty(REPORT_GUID, key);

            final String datasetId = getDescriptor().getProperty("datasetId");
            final String chartsPerRow = getDescriptor().getProperty("chartsPerRow");

            try {
                if (datasetId != null && _reports != null)
                {
                    //Map<Integer, Report> map = ReportsController.getCachedReports(context, Integer.parseInt(datasetId));
                    //for (Report report : map.values())
                    for (Report report : _reports)
                    {
                        report.getDescriptor().setProperty("chartsPerRow", chartsPerRow != null ? chartsPerRow : "3");
                        report.getDescriptor().setOwner(getDescriptor().getOwner());
                        ReportService.get().saveReport(context, key, report);
                    }
                }
            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
        {
            Integer owner = getDescriptor().getOwner();
            for (Report report : getChildReports(context))
            {
                if (owner != report.getDescriptor().getOwner())
                {
                    try {
                        report.getDescriptor().setOwner(owner);
                        ReportService.get().saveReport(context, report.getDescriptor().getReportKey(), report);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public void beforeDelete(ViewContext context)
    {
        try {
            for (Report report : getChildReports(context))
                ReportService.get().deleteReport(context, report);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        for (Report report : getChildReports(context))
        {
            return report.renderDataView(context);
        }
        return super.renderDataView(context);
    }

    Report[] getChildReports(ViewContext context)
    {
        if (_reports == null)
        {
            try {
                final String key = getDescriptor().getProperty(REPORT_GUID);
                _reports = ReportService.get().getReports(context.getUser(), context.getContainer(), key);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return _reports;
    }

    public HttpView renderReport(ViewContext context)
    {
        Report[] reports = getChildReports(context);
        if (reports.length > 0)
        {
            final Report report = reports[0];
            final String dataset = report.getDescriptor().getProperty("datasetId");
            final String chartsPerRow = report.getDescriptor().getProperty("chartsPerRow");
            String participantId = context.getActionURL().getParameter("participantId");
            if (participantId == null)
                participantId = (String)context.get("participantId");
            if (dataset != null)
            {
                final ReportsController.PlotForm form = new ReportsController.PlotForm();
                form.setDatasetId(Integer.parseInt(dataset));
                form.setContext(context);
                form.setContainer(context.getContainer());
                form.setReports(reports);
                form.setAction("datasetView.plot");
                if (participantId != null)
                    form.setParticipantId(participantId);
                if (chartsPerRow != null)
                    form.setChartsPerRow(Integer.parseInt(chartsPerRow));

                return new JspView<ReportsController.PlotForm>("/org/labkey/study/view/datasetCharts.jsp", form);
            }
        }
        return null;
    }

    public static class ChartReportViewDescriptor extends ChartReportDescriptor
    {
        public static final String TYPE = "Study.chartViewDescriptor";
        private ChartReportView _parent;

        public ChartReportViewDescriptor()
        {
            setDescriptorType(TYPE);
        }

        public void setParent(ChartReportView parent)
        {
            _parent = parent;
        }


        public void updateACL(ViewContext context, ACL acl)
        {
            super.updateACL(context, acl);
            if (_parent != null)
            {
                for (Report child : _parent.getChildReports(context))
                {
                    child.getDescriptor().updateACL(context, acl);
                }
            }
        }
    }

    public static class DatasetChartReport extends ChartQueryReport
    {
        public static final String TYPE = "Study.datasetChart";

        public String getType()
        {
            return TYPE;
        }

        private TableInfo getTable(ViewContext context, ReportDescriptor descriptor) throws Exception
        {
            final int datasetId = Integer.parseInt(descriptor.getProperty("datasetId"));
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);

            return def.getTableInfo(context.getUser());
        }

        public ResultSet generateResultSet(ViewContext context) throws Exception
        {
            ReportDescriptor descriptor = getDescriptor();
            final TableInfo tableInfo = getTable(context, descriptor);
            DataRegion dr = new DataRegion();
            dr.setTable(tableInfo);
            RenderContext ctx = new RenderContext(context);
            ctx.setContainer(context.getContainer());

            //if (BooleanUtils.toBoolean(descriptor.getProperty("isPlotView")))
            {
                final String participantId = context.getActionURL().getParameter("participantId");
                if (participantId != null && NumberUtils.isNumber(participantId))
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition("participantId", participantId, CompareType.EQUAL);

                    ctx.setBaseFilter(filter);
                }
            }
            return dr.getResultSet(ctx);
        }

        public ChartReportDescriptor.LegendItemLabelGenerator getLegendItemLabelGenerator()
        {
            return new ChartReportDescriptor.LegendItemLabelGenerator() {
                public String generateLabel(ViewContext context, ReportDescriptor descriptor, String itemName) throws Exception
                {
                    TableInfo table = getTable(context, descriptor);
                    if (table != null)
                    {
                        ColumnInfo info = table.getColumn(itemName);
                        return info != null ? info.getCaption() : itemName;
                    }
                    return itemName;
                }
            };
        }
    }
}
