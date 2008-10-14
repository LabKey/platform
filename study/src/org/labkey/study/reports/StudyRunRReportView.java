/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HtmlView;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Feb 8, 2008
 */
public class StudyRunRReportView extends RunRReportView
{
    public static final String PARTICIPANT_KEY = "participantId";

    public StudyRunRReportView(Report report)
    {
        super(report);
    }

    public HttpView getTabView(String tabId) throws Exception
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

                String qcState = getViewContext().getActionURL().getParameter(BaseStudyController.SharedFormParameters.QCState);
                List<String> participants = StudyController.getParticipantListFromCache(context, datasetId,
                        getReport().getDescriptor().getProperty(ReportDescriptor.Prop.viewName), null, qcState);

                view.addView(ReportsController.getParticipantNavTrail(context, participants));
            }
            view.addView(super.getTabView(tabId));
            return view;
        }
        return super.getTabView(tabId);
    }

    protected DataSetDefinition getDataSetDefinition()
    {
        try {
            final Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
            if (study != null)
            {
                return StudyManager.getInstance().
                        getDataSetDefinition(study, getReport().getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }
}
