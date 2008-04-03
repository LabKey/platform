package org.labkey.study.reports;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QueryParam;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.view.CrosstabView;

import javax.servlet.http.HttpServletResponse;
import java.sql.ResultSet;
import java.sql.SQLException;

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
        return "Study Crosstab View";
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
                ReportQueryView qv = createQueryView(context, descriptor);
                if (qv != null)
                {
                    rs = qv.getResultSet(0);
                    return new CrosstabView(rs, qv.getColumnMap(), descriptor);
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
}
