package org.labkey.api.query;

import org.labkey.api.reports.model.ReportInfo;

import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class ReportInfoProvider
{
    protected Map<String, Map<Integer, List<ReportInfo>>> _reportInfoMap = null;

    public abstract Map<String, Map<Integer, List<ReportInfo>>> getReportInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd);

    public void clearReportInfoMap()
    {
        _reportInfoMap = null;
    }
}
