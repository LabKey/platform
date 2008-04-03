package org.labkey.study.reports;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.RunChartReportView;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.view.*;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 8, 2008
 */
public class StudyRunChartReportView extends RunChartReportView
{
    public static final String PARTICIPANT_KEY = "participantId";

    StudyRunChartReportView(Report report)
    {
        super(report);
    }

    protected HttpView getTabView(String tabId) throws Exception
    {
        if (TAB_VIEW.equals(tabId))
        {
            VBox view = new VBox();
            boolean isParticipantChart = PARTICIPANT_KEY.equals(getReport().getDescriptor().getProperty(ReportDescriptor.Prop.filterParam));
            if (isParticipantChart)
            {
                ViewContext context = getViewContext();
                int datasetId = 0;
                DataSetDefinition def = getDataSetDefinition();
                if (def != null)
                    datasetId = def.getRowId();

                List<String> participants = StudyController.getParticipantListFromCache(context, datasetId,
                        getReport().getDescriptor().getProperty(ReportDescriptor.Prop.viewName), null);

                view.addView(ReportsController.getParticipantNavTrail(context, participants));

                String participantId = (String)context.get(PARTICIPANT_KEY);
                ActionURL url = ChartUtil.getPlotChartURL(context, getReport());
                if (participantId != null)
                    url.addParameter(PARTICIPANT_KEY, participantId);
                view.addView(new HtmlView("<img border=0 src='" + url.getLocalURIString() + "'>"));
            }
            else
                view.addView(super.getTabView(tabId));
            return view;
        }
        return super.getTabView(tabId);
    }

    protected DataSetDefinition getDataSetDefinition()
    {
        try {
            int datasetId = NumberUtils.toInt(getReport().getDescriptor().getProperty(DataSetDefinition.DATASETKEY));
            return StudyManager.getInstance().getDataSetDefinition(StudyManager.getInstance().getStudy(getViewContext().getContainer()), datasetId);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
