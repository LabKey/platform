/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.study.controllers.reports;

import com.google.gwt.user.client.rpc.SerializableException;
import gwt.client.org.labkey.study.chart.client.StudyChartService;
import gwt.client.org.labkey.study.chart.client.model.GWTPair;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.gwt.client.model.GWTChart;
import org.labkey.api.gwt.client.model.GWTChartColumn;
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
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.reports.ReportManager;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
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

        _isParticipantChart = BooleanUtils.toBoolean(Objects.toString(context.get("isParticipantChart"), ""));
    }

    public GWTChart getChart(int id)
    {
        return null;
    }

    public List<GWTPair> getStudyDatasets()
    {
        List<GWTPair> datasets = new ArrayList<>();
        final Study study = StudyManager.getInstance().getStudy(_context.getContainer());

        if (study != null)
        {
            for (Dataset def : StudyManager.getInstance().getDatasetDefinitions(study))
            {
                if (def.canRead(getUser()))
                    datasets.add(new GWTPair(def.getLabel(), Integer.toString(def.getDatasetId())));
            }
        }
        return datasets;
    }

    public String saveChart(GWTChart chart) throws SerializableException
    {
        try
        {
            if (getUser().isGuest())
                throw new SerializableException("Unable to save report, you must be logged in");

            ChartDesignerBean bean = new ChartDesignerBean();
            PropertyUtils.copyProperties(bean, chart);

            Report report = bean.getReport(getViewContext());
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
        catch (SerializableException se)
        {
            throw se;
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private String saveDatasetChart(GWTChart chart, Report report) throws Exception
    {
        for (Map.Entry<String, String> param : chart.getProperties().entrySet())
        {
            report.getDescriptor().setProperty(param.getKey(), param.getValue());
        }

        final String key = getReportKey(report.getDescriptor());
        if (!ReportService.get().reportNameExists(_context, chart.getReportName(), key))
        {
            if (!chart.isShared())
                report.getDescriptor().setOwner(_context.getUser().getUserId());

            int reportId = ReportService.get().saveReport(_context, key, report);
            int showWithDataset = NumberUtils.toInt(report.getDescriptor().getProperty("showWithDataset"));
            if (showWithDataset != 0)
            {
                ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, _context.getContainer());
                url.addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, String.valueOf(reportId));
                if (showWithDataset == ReportManager.ALL_DATASETS)
                    url.addParameter(DatasetDefinition.DATASETKEY, report.getDescriptor().getProperty("datasetId"));
                else
                    url.replaceParameter(DatasetDefinition.DATASETKEY, String.valueOf(showWithDataset));
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
        for (Map.Entry<String, String> param : chart.getProperties().entrySet())
        {
            report.getDescriptor().setProperty(param.getKey(), param.getValue());
        }
        report.getDescriptor().setProperty("filterParam", "participantId");
        String key = report.getDescriptor().getProperty("datasetId");
        if (!ReportService.get().reportNameExists(_context, chart.getReportName(), key))
        {
            ReportService.get().saveReport(_context, key, report);

            ActionURL url = new ActionURL(StudyController.ParticipantAction.class, _context.getContainer());
            url.addParameter("datasetId", key);
            url.addParameter("participantId", report.getDescriptor().getProperty("participantId"));

            return url.getLocalURIString();
        }
        else
            throw new SerializableException("There is already a report with the name of: '" + report.getDescriptor().getReportName() +
                    "'. Please specify a different name.");
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
            Dataset def = StudyManager.getInstance().getDatasetDefinition(study, showWithDataset);
            if (def != null)
                queryName = def.getName();
            return ReportUtil.getReportKey(StudySchema.getInstance().getSchemaName(), queryName);
        }
        return ReportUtil.getReportQueryKey(descriptor);
    }


    public List<GWTChartRenderer> getChartRenderers(GWTChart chart)
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), chart.getSchemaName());
        QuerySettings qs = schema.getSettings(_context, null, chart.getQueryName());
        qs.setViewName(chart.getViewName());

        QueryView view = new QueryView(schema, qs, null);
        List<GWTChartRenderer> gwtRenderers = new ArrayList<>();

        for (ChartRenderer renderer : ChartRendererFactory.get().getChartRenderers())
        {
            GWTChartRenderer cr = new GWTChartRenderer();

            cr.setName(renderer.getName());
            cr.setType(renderer.getType());
            cr.setColumnX(createColumnList(renderer.getDisplayColumns(view, true)));
            cr.setColumnY(createColumnList(renderer.getDisplayColumns(view, false)));

            gwtRenderers.add(cr);
        }
        return gwtRenderers;
    }

    private List<GWTChartColumn> createColumnList(Map<String, String> columnMap)
    {
        List<GWTChartColumn> columns = new ArrayList<>();

        for (Map.Entry<String, String> entry : columnMap.entrySet())
            columns.add(new GWTChartColumn(entry.getKey(), entry.getValue()));
        return columns;
    }

    public String getDisplayURL(GWTChart chart)
    {
        ChartDesignerBean bean = new ChartDesignerBean();
        try
        {
            PropertyUtils.copyProperties(bean, chart);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlPlotChart(_context.getContainer());

        for (Pair<String, String> param : bean.getParameters())
        {
            url.addParameter(param.getKey(), param.getValue());
        }
        url.addParameters(chart.getProperties());
        if (chart.getProperties().containsKey(ReportDescriptor.Prop.filterParam.name()))
        {
            // if this is a participant chart, just filter on the first participant in the dataset so that
            // the chart preview will look more representative.
            int datasetId = NumberUtils.toInt(chart.getProperties().get(DatasetDefinition.DATASETKEY));
            String qcState = getActionURL().getParameter(BaseStudyController.SharedFormParameters.QCState);
            List<String> participants = StudyController.getParticipantListFromCache(_context, datasetId, bean.getViewName(), null, qcState);
            if (!participants.isEmpty())
                url.addParameter("participantId", participants.get(0));
        }
        return url.toString();
    }
}
