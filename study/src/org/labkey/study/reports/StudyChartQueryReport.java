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
import org.labkey.api.reports.report.ChartQueryReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.controllers.StudyController;

/**
 * User: Karl Lum
 * Date: Apr 24, 2007
 */
public class StudyChartQueryReport extends ChartQueryReport
{
    public static final String TYPE = "Study.chartQueryReport";

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

    public ActionURL getRunReportURL(ViewContext context)
    {
        String datasetId = getDescriptor().getProperty(DatasetDefinition.DATASETKEY);
        if (datasetId != null)
        {
            return new ActionURL(StudyController.DatasetReportAction.class, context.getContainer()).
                        addParameter(DatasetDefinition.DATASETKEY, datasetId).
                        addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, getDescriptor().getReportId().toString());
        }
        return super.getRunReportURL(context);
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new StudyRunChartReportView(this);
    }
}
