/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Crosstab;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.*;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.view.CrosstabView;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 8, 2007
 */
public class StudyCrosstabReport extends AbstractReport
{
    public static final String TYPE = "ReportService.crosstabReport";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Crosstab View";
    }

    public String getDescriptorType()
    {
        return CrosstabReportDescriptor.TYPE;
    }

    private ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String visitRowId = descriptor.getProperty(Visit.VISITKEY);

        ReportQueryView view = ReportQueryViewFactory.get().generateQueryView(context, descriptor, queryName, viewName);

        if (!StringUtils.isEmpty(visitRowId))
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            if (study != null)
            {
                Visit visit = StudyManager.getInstance().getVisitForRowId(study, NumberUtils.toInt(visitRowId));
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

    public HttpView renderReport(ViewContext context)
    {
        String errorMessage = null;
        ReportDescriptor reportDescriptor = getDescriptor();
        ResultSet rs = null;

        if (reportDescriptor instanceof CrosstabReportDescriptor)
        {
            CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)reportDescriptor;
            try {
                Crosstab crosstab = createCrosstab(context);
                if (crosstab != null)
                {
                    ActionURL exportAction = null;

                    if (descriptor.getReportId() != null)
                    {
                        exportAction = new ActionURL(ReportsController.CrosstabExportAction.class, context.getContainer()).addParameter(ReportDescriptor.Prop.reportId, descriptor.getReportId().toString());
                    }
                    return new CrosstabView(crosstab, exportAction);
                }
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
                if (errorMessage == null)
                    errorMessage = e.toString();
                Logger.getLogger(StudyCrosstabReport.class).error("unexpected error in renderReport()", e);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        else
        {
            errorMessage = "Invalid report params: The ReportDescriptor must be an instance of CrosstabReportDescriptor";
        }

        if (errorMessage != null)
        {
            return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, context.getRequest(), false));
        }
        return null;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        String datasetId = getDescriptor().getProperty(DataSetDefinition.DATASETKEY);
        if (datasetId != null)
        {
            return new ActionURL(StudyController.DatasetReportAction.class, context.getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, datasetId).
                        addParameter("Dataset.reportId", getDescriptor().getReportId().toString());
        }
        return super.getRunReportURL(context);
    }

    protected Crosstab createCrosstab(ViewContext context) throws Exception
    {
        CrosstabReportDescriptor descriptor = (CrosstabReportDescriptor)getDescriptor();
        ReportQueryView qv = createQueryView(context, getDescriptor());
        if (qv != null)
        {
            String rowField = descriptor.getProperty("rowField");
            String colField = descriptor.getProperty("colField");
            String statField = descriptor.getProperty("statField");

            Set<Stats.StatDefinition> statSet = new LinkedHashSet<Stats.StatDefinition>();
            for (String stat : descriptor.getStats())
            {
                if ("Count".equals(stat))
                    statSet.add(Stats.COUNT);
                else if ("Sum".equals(stat))
                    statSet.add(Stats.SUM);
                else if ("Sum".equals(stat))
                    statSet.add(Stats.SUM);
                else if ("Mean".equals(stat))
                    statSet.add(Stats.MEAN);
                else if ("Min".equals(stat))
                    statSet.add(Stats.MIN);
                else if ("Max".equals(stat))
                    statSet.add(Stats.MAX);
                else if ("StdDev".equals(stat))
                    statSet.add(Stats.STDDEV);
                else if ("Var".equals(stat))
                    statSet.add(Stats.VAR);
                else if ("Median".equals(stat))
                    statSet.add(Stats.MEDIAN);
            }
            return new Crosstab(qv.getResultSet(0), qv.getColumnMap(), rowField, colField, statField, statSet);
        }
        return null;
    }

    public ExcelWriter getExcelWriter(ViewContext context) throws Exception
    {
        Crosstab crosstab = createCrosstab(context);
        if (crosstab != null)
        {
            return crosstab.getExcelWriter();
        }
        return null;
    }
}
