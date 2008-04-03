package org.labkey.study.controllers.reports;

import com.google.gwt.user.client.rpc.SerializableException;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.model.GWTChartRenderer;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.chart.ChartRenderer;
import org.labkey.api.reports.chart.ChartRendererFactory;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;
import org.labkey.study.chart.client.StudyChartService;
import org.labkey.study.chart.client.model.GWTPair;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.reports.ChartReportView;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.controllers.StudyController;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 7, 2007
 */
public class StudyChartServiceImpl extends BaseRemoteService implements StudyChartService
{
    // determines whether this is a chart embedded in the participant view
    private boolean _isParticipantChart;

    public StudyChartServiceImpl(ViewContext context)
    {
        super(context);

        _isParticipantChart = BooleanUtils.toBoolean(ObjectUtils.toString(context.get("isParticipantChart")));
    }

    public GWTChart getChart(int id) throws Exception
    {
        return null;
    }

    public List getStudyDatasets() throws Exception
    {
        List<GWTPair> datasets = new ArrayList<GWTPair>();
        final Study study = StudyManager.getInstance().getStudy(_context.getContainer());

        if (study != null)
        {
            datasets.add(new GWTPair("All Datasets", Integer.toString(ReportManager.ALL_DATASETS)));

            for (DataSetDefinition def : StudyManager.getInstance().getDataSetDefinitions(study))
            {
                datasets.add(new GWTPair(def.getLabel(), Integer.toString(def.getDataSetId())));
            }
        }
        return datasets;
    }

    public List saveCharts(GWTChart[] charts, Map properties) throws Exception
    {
        List<String> errors = new ArrayList<String>();

        return errors.isEmpty() ? null : errors;
    }

    public String saveChart(GWTChart chart) throws Exception
    {
        if (getUser().isGuest())
            throw new SerializableException("Unable to save report, you must be logged in");

        ChartDesignerBean bean = new ChartDesignerBean();
        PropertyUtils.copyProperties(bean, chart);

        Report report = bean.getReport();
        if (report != null)
        {
            if (_isParticipantChart)
                return saveParticipantChart(chart, report);
            else
                return saveDatasetChart(chart, report);
        }
        else
            throw new SerializableException("Unable to save report, the report could not be instantiated");
    }

    private String saveDatasetChart(GWTChart chart, Report report) throws Exception
    {
        ChartReportView reportView = new ChartReportView();
        for (Map.Entry<String, String> param : (Set<Map.Entry<String, String>>)chart.getProperties().entrySet())
        {
            reportView.getDescriptor().setProperty(param.getKey(), param.getValue());
            report.getDescriptor().setProperty(param.getKey(), param.getValue());
        }

        final String key = getReportKey(report.getDescriptor());
        if (!reportNameExists(_context, chart.getReportName(), key))
        {
            // A chart report view has the ability to render multiple 'child' reports
            if (!chart.isShared())
                reportView.getDescriptor().setOwner(_context.getUser().getUserId());

            reportView.getDescriptor().setReportName(report.getDescriptor().getReportName());
            reportView.getDescriptor().setReportDescription(report.getDescriptor().getReportDescription());
            report.getDescriptor().setReportName(null);
            reportView.setReports(new Report[]{report});

            int reportId = ReportService.get().saveReport(_context, key, reportView);
            int showWithDataset = NumberUtils.toInt(reportView.getDescriptor().getProperty("showWithDataset"));
            if (showWithDataset != 0)
            {
                ActionURL url = new ActionURL("Study", "datasetReport", _context.getContainer());
                url.addParameter("Dataset.viewName", String.valueOf(reportId));
                if (showWithDataset == ReportManager.ALL_DATASETS)
                    url.addParameter(DataSetDefinition.DATASETKEY,  reportView.getDescriptor().getProperty("datasetId"));
                else
                    url.replaceParameter(DataSetDefinition.DATASETKEY,  String.valueOf(showWithDataset));
                return url.toString();
            }
        }
        else
            throw new SerializableException("There is already a report with the name of: '" + report.getDescriptor().getReportName() +
                    "'. Please specify a different name.");

        return null;
    }

    private String saveParticipantChart(GWTChart chart, Report report) throws Exception
    {
        for (Map.Entry<String, String> param : (Set<Map.Entry<String, String>>)chart.getProperties().entrySet())
        {
            report.getDescriptor().setProperty(param.getKey(), param.getValue());
        }
        report.getDescriptor().setProperty("filterParam", "participantId");
        String key = report.getDescriptor().getProperty("datasetId");
        ReportService.get().saveReport(_context, key, report);

        ActionURL url = new ActionURL("Study", "participant", _context.getContainer());
        url.addParameter("datasetId", key);
        url.addParameter("participantId", report.getDescriptor().getProperty("participantId"));

        return url.getLocalURIString();
    }

    private String getReportKey(ReportDescriptor descriptor) throws ServletException
    {
        int showWithDataset = NumberUtils.toInt(descriptor.getProperty("showWithDataset"));
        if (showWithDataset != 0)
        {
            if (ReportManager.ALL_DATASETS == showWithDataset)
                return ReportManager.ALL_DATASETS_KEY;

            String queryName = null;
            Study study = StudyManager.getInstance().getStudy(_context.getContainer());
            DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, showWithDataset);
            if (def != null)
                queryName = def.getLabel();
            return ChartUtil.getReportKey(StudyManager.getSchemaName(), queryName);
        }
        return ChartUtil.getReportQueryKey(descriptor);
    }

    private boolean reportNameExists(ViewContext context, String reportName, String key)
    {
        try {
            for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer(), key))
            {
                if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
                    return true;
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public GWTChartRenderer[] getChartRenderers(GWTChart chart) throws Exception
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), chart.getSchemaName());
        QuerySettings qs = new QuerySettings(_context.getActionURL(), null);
        qs.setSchemaName(schema.getSchemaName());
        qs.setQueryName(chart.getQueryName());

        QueryView view = new QueryView(schema, qs);
        List<GWTChartRenderer> gwtRenderers = new ArrayList<GWTChartRenderer>();

        for (ChartRenderer renderer : ChartRendererFactory.get().getChartRenderers())
        {
            GWTChartRenderer cr = new GWTChartRenderer();

            cr.setName(renderer.getName());
            cr.setType(renderer.getType());
            cr.setColumnX(renderer.getDisplayColumns(view, true));
            cr.setColumnY(renderer.getDisplayColumns(view, false));

            gwtRenderers.add(cr);
        }
        return gwtRenderers.toArray(new GWTChartRenderer[0]);
    }

    public String getDisplayURL(GWTChart chart) throws Exception
    {
        ChartDesignerBean bean = new ChartDesignerBean();
        PropertyUtils.copyProperties(bean, chart);
        ActionURL url = new ActionURL("reports", "plotChart", _context.getContainer());

        for (Pair<String, String> param : bean.getParameters())
        {
            url.addParameter(param.getKey(), param.getValue());
        }
        url.addParameters(chart.getProperties());
        if (chart.getProperties().containsKey(ReportDescriptor.Prop.filterParam.name()))
        {
            // if this is a participant chart, just filter on the first participant in the dataset so that
            // the chart preview will look more representative.
            int datasetId = NumberUtils.toInt((String)chart.getProperties().get(DataSetDefinition.DATASETKEY));
            List<String> participants = StudyController.getParticipantListFromCache(_context, datasetId, bean.getViewName(), null);
            if (!participants.isEmpty())
                url.addParameter("participantId", participants.get(0));
        }
        return url.toString();
    }
}
