package org.labkey.study.reports;

import org.labkey.api.query.*;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.QueryReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataSetQueryView;
import org.labkey.study.query.StudyQuerySchema;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: brittp
 * Date: Aug 29, 2006
 * Time: 10:15:10 AM
 */
public class StudyQueryReport extends QueryReport
{
    public static final String TYPE = "Study.queryReport";

    public String getType()
    {
        return TYPE;
    }

    public void beforeSave(ViewContext context)
    {
        ReportDescriptor reportDescriptor = getDescriptor();
        if (reportDescriptor instanceof QueryReportDescriptor)
        {
            try {
                final QueryReportDescriptor descriptor = (QueryReportDescriptor)reportDescriptor;
                CrosstabReportDescriptor.QueryViewGenerator qvGen = getQueryViewGenerator();
                if (qvGen == null)
                {
                    qvGen = descriptor.getQueryViewGenerator();
                }

                if (qvGen != null)
                {
                    ReportQueryView qv = qvGen.generateQueryView(context, descriptor);
                    if (qv != null)
                    {
                        QueryDefinition queryDef = qv.getQueryDef();
                        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
                        if (queryDef.getCustomView(null, context.getRequest(), viewName) == null)
                        {
                            CustomView view = queryDef.createCustomView(null, viewName);
                            view.setIsHidden(true);
                            view.save(context.getUser(), context.getRequest());
                        }
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    public int renameReport(ViewContext context, String newKey, String newName) throws SQLException
    {
        int reportId = -1;
        // delete the wrapped custom query before creating the new one
        CustomView oldView = getCustomView(context);
        List<FieldKey> columns = null;
        ActionURL filterOrSort = new ActionURL();
        if (oldView != null)
        {
            columns = oldView.getColumns();
            oldView.applyFilterAndSortToURL(filterOrSort, DataSetQueryView.DATAREGION);

            User user = oldView.getOwner();
            if (user != null)
                getDescriptor().setOwner(user.getUserId());
            oldView.delete(context.getUser(), context.getRequest());
        }
        getDescriptor().setReportName(newName);
        reportId = ReportService.get().saveReport(context, newKey, this);

        CustomView newView = getCustomView(context);
        if (columns != null && columns.size() > 0)
        {
            newView.setColumns(columns);
            newView.setFilterAndSortFromURL(filterOrSort, DataSetQueryView.DATAREGION);
            newView.save(context.getUser(), context.getRequest());
        }
        return reportId;
    }

    protected CustomView getCustomView(ViewContext context)
    {
        try {
            StudyQuerySchema schema = getStudyQuerySchema(context.getUser(), ACL.PERM_READ, context);
            String viewName = getDescriptor().getProperty(QueryParam.viewName.toString());
            QuerySettings qs = new QuerySettings(context.getActionURL(), getDescriptor().getProperty(QueryParam.dataRegionName.toString()));
            qs.setSchemaName(schema.getSchemaName());
            qs.setQueryName(getDescriptor().getProperty(QueryParam.queryName.toString()));
            QueryDefinition queryDef = qs.getQueryDef(schema);
            if (queryDef != null)
                return queryDef.getCustomView(context.getUser(), context.getRequest(), viewName);
            return null;
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
    }

    protected StudyQuerySchema getStudyQuerySchema(User user, int perm, ViewContext context) throws ServletException
    {
        if (perm != ACL.PERM_READ)
            throw new IllegalArgumentException("only PERM_READ supported");
        Study study = StudyManager.getInstance().getStudy(context.getContainer());
        return new StudyQuerySchema(study, user, true);
    }

    public void beforeDelete(ViewContext context)
    {
        CustomView view = getCustomView(context);
        if (view != null)
            view.delete(context.getUser(), context.getRequest());
    }

    public CrosstabReportDescriptor.QueryViewGenerator getQueryViewGenerator()
    {
        return new CrosstabReportDescriptor.QueryViewGenerator() {
            public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
            {
                return ReportQueryViewFactory.get().generateQueryView(context, descriptor, 
                        descriptor.getProperty(QueryParam.queryName.toString()),
                        descriptor.getProperty(QueryParam.viewName.toString()));
            }
        };
    }
}
