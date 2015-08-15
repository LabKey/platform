/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.study.reports;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.RunChartReportView;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.view.*;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Feb 8, 2008
 */
public class StudyRunChartReportView extends RunChartReportView
{
    public static final String PARTICIPANT_KEY = "participantId";
    private Collection<Report> _reports;

    StudyRunChartReportView(Report report)
    {
        super(report);
    }

    StudyRunChartReportView(Collection<Report> reports)
    {
        super(reports.iterator().next());
        _reports = reports;
    }

    public HttpView getTabView(String tabId) throws Exception
    {
        if (TAB_VIEW.equals(tabId))
        {
            VBox view = new VBox();
            Collection<Report> reports = _reports != null ? _reports : Collections.singleton(getReport());
            boolean isParticipantChart = PARTICIPANT_KEY.equals(getReport().getDescriptor().getProperty(ReportDescriptor.Prop.filterParam));
            if (isParticipantChart)
            {
                ViewContext context = getViewContext();
                int datasetId = 0;
                DatasetDefinition def = getDatasetDefinition();
                if (def != null)
                    datasetId = def.getRowId();

                String qcState = getViewContext().getActionURL().getParameter(BaseStudyController.SharedFormParameters.QCState);
                List<String> participants = StudyController.getParticipantListFromCache(context, datasetId,
                        getReport().getDescriptor().getProperty(ReportDescriptor.Prop.viewName), null, qcState);

                view.addView(ReportsController.getParticipantNavTrail(context, participants));

                String participantId = (String)context.get(PARTICIPANT_KEY);
                addChartView(view, reports, participantId);
            }
            else
                addChartView(view, reports, null);
            return view;
        }
        return super.getTabView(tabId);
    }

    private void addChartView(VBox view, Collection<Report> reports, String participantId)
    {
        for (Report report : reports)
        {
            ActionURL url = ReportUtil.getPlotChartURL(getViewContext(), report);
            if (participantId != null)
                url.addParameter(PARTICIPANT_KEY, participantId);

            view.addView(new HtmlView("<img src='" + url.getLocalURIString() + "'>"));
        }
    }

    protected DatasetDefinition getDatasetDefinition()
    {
        try
        {
            int datasetId = NumberUtils.toInt(getReport().getDescriptor().getProperty(DatasetDefinition.DATASETKEY));
            return StudyManager.getInstance().getDatasetDefinition(StudyManager.getInstance().getStudy(getViewContext().getContainer()), datasetId);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
