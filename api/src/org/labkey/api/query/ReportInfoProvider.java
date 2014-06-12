package org.labkey.api.query;

import org.labkey.api.reports.model.ReportInfo;

import java.util.Date;
import java.util.Map;
import java.util.Set;

public abstract class ReportInfoProvider
{
    protected Map<String, Map<Integer, Set<ReportInfo>>> _reportInfoMap = null;

    public abstract Map<String, Map<Integer, Set<ReportInfo>>> getReportInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd);

    public void clearReportInfoMap()
    {
        _reportInfoMap = null;
    }
}
