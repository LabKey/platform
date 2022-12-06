package org.labkey.api.reports.report.python;

import org.labkey.api.reports.report.ScriptReportDescriptor;

public class IpynbReportDescriptor extends ScriptReportDescriptor
{
    public static final String DESCRIPTOR_TYPE = "ipynbReportDescriptor";

    public IpynbReportDescriptor()
    {
        this(DESCRIPTOR_TYPE);
    }

    public IpynbReportDescriptor(String type)
    {
        super(type);
        setReportType(IpynbReport.TYPE);
    }
}
