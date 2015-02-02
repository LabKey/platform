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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.study.Study;
import org.labkey.api.study.reports.CrosstabReport;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

/**
 * User: Karl Lum
 * Date: May 8, 2007
 */
public class StudyCrosstabReport extends CrosstabReport
{
    public static final String TYPE = "ReportService.crosstabReport";

    public String getType()
    {
        return TYPE;
    }

    @Override
    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String visitRowId = descriptor.getProperty(VisitImpl.VISITKEY);

        ReportQueryView view = ReportQueryViewFactory.get().generateQueryView(context, descriptor, queryName, viewName);

        if (!StringUtils.isEmpty(visitRowId))
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            if (study != null)
            {
                VisitImpl visit = StudyManager.getInstance().getVisitForRowId(study, NumberUtils.toInt(visitRowId));
                if (visit != null)
                {
                    SimpleFilter filter = new SimpleFilter();
                    visit.addVisitFilter(filter);
                    view.setFilter(filter);
                }
            }
        }
        return view;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        int datasetId = NumberUtils.toInt(getDescriptor().getProperty(DatasetDefinition.DATASETKEY), -1);
        if (datasetId == -1)
        {
            String queryName = getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
            if (queryName != null)
            {
                Study study = StudyManager.getInstance().getStudy(context.getContainer());
                if (study != null)
                {
                    DatasetDefinition def = StudyManager.getInstance().getDatasetDefinitionByQueryName(study, queryName) ;
                    if (def != null)
                        datasetId = def.getDatasetId();
                }
            }
        }

        if (datasetId != -1)
        {
            return new ActionURL(StudyController.DatasetReportAction.class, context.getContainer()).
                        addParameter(DatasetDefinition.DATASETKEY, datasetId).
                        addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, getDescriptor().getReportId().toString());
        }
        return super.getRunReportURL(context);
    }
}
