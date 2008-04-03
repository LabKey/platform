package org.labkey.api.reports.report;

import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reports.report.view.ReportQueryView;

import java.sql.ResultSet;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class QueryReportDescriptor extends ReportDescriptor
{
    public static final String TYPE = "queryDescriptor";

    private QueryViewGenerator _qvGenerator;

    public QueryReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public void setQueryViewGenerator(QueryViewGenerator generator){_qvGenerator = generator;}
    public QueryViewGenerator getQueryViewGenerator(){return _qvGenerator;}

    public interface QueryViewGenerator
    {
        public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception;
    }
}
