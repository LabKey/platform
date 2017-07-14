package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;

/**
 * Created by klum on 7/13/2017.
 */
public class AssayProgressReport extends AbstractReport
{
    public static final String TYPE = "ReportService.ParticipantReport";
    public static final String MEASURES_PROP = "measures";
    public static final String GROUPS_PROP = "groups";

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Assay Progress Report";
    }

    @Override
    public HttpView renderReport(ViewContext context) throws Exception
    {
        return null;
    }
}
