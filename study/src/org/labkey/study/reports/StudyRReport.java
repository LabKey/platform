/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.report.RReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.study.Study;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.TabStripView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;

import java.util.List;

/**
 * User: Karl Lum
 * Date: Jul 25, 2007
 */
public class StudyRReport extends RReport
{
    public static final String TYPE = "Study.rReport";
    public static final String PARTICIPANT_KEY = "participantId";

    public String getType()
    {
        return TYPE;
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());

        return ReportQueryViewFactory.get().generateQueryView(context, descriptor, queryName, viewName);
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        // Special handling for study R report -- from old StudyRunRReportView
        HttpView reportView = super.getRunReportView(context);

        boolean isParticipantChart = PARTICIPANT_KEY.equals(getDescriptor().getProperty(ReportDescriptor.Prop.filterParam));

        if (!isParticipantChart)
            return reportView;

        int datasetId = 0;
        DatasetDefinition def = getDatasetDefinition(context);
        if (def != null)
            datasetId = def.getRowId();

        String qcState = context.getActionURL().getParameter(BaseStudyController.SharedFormParameters.QCState);
        List<String> participants = StudyController.getParticipantListFromCache(context, datasetId,
                getDescriptor().getProperty(ReportDescriptor.Prop.viewName), null, qcState);

        VBox vBox = new VBox();
        vBox.addView(ReportsController.getParticipantNavTrail(context, participants));
        vBox.addView(reportView);

        return vBox;
    }

    protected DatasetDefinition getDatasetDefinition(ViewContext context)
    {
        try
        {
            final Study study = StudyManager.getInstance().getStudy(context.getContainer());

            if (study != null)
            {
                return StudyManager.getInstance().
                        getDatasetDefinitionByQueryName(study, getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        return null;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        if (getDescriptor().getReportId() != null)
            return new ActionURL(ReportsController.RunRReportAction.class, context.getContainer()).
                            addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, getDescriptor().getReportId().toString());
        else
            return super.getRunReportURL(context);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        if (canEdit(context.getUser(), context.getContainer()))
        {
            return getRunReportURL(context).
                    addParameter(TabStripView.TAB_PARAM, TAB_SOURCE).
                    addParameter(ActionURL.Param.redirectUrl, PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(context.getContainer()).getLocalURIString());
        }
        return null;
    }
}
