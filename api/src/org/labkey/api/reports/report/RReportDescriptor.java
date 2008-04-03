package org.labkey.api.reports.report;

import java.util.List;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jul 12, 2007
 */
public class RReportDescriptor extends ReportDescriptor
{
    public static final String TYPE = "rReportDescriptor";

    public enum Prop implements ReportProperty
    {
        runInBackground,
        includedReports,
        script,
    }

    public RReportDescriptor()
    {
        setDescriptorType(TYPE);
    }

    public void setIncludedReports(List<String> reports)
    {
        _props.put(Prop.includedReports.toString(), reports);
    }

    public List<String> getIncludedReports()
    {
        Object reports = _props.get(Prop.includedReports.toString());
        if (reports != null && List.class.isAssignableFrom(reports.getClass()))
            return (List<String>)reports;

        return Collections.EMPTY_LIST;
    }
    
    public boolean isArrayType(String prop)
    {
        if (!super.isArrayType(prop))
        {
            return Prop.includedReports.toString().equals(prop);
        }
        return true;
    }
}
