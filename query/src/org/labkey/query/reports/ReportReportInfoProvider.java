package org.labkey.query.reports;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ReportInfoProvider;
import org.labkey.api.reports.model.ReportInfo;
import org.labkey.api.reports.report.ReportDB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportReportInfoProvider extends ReportInfoProvider
{
    @Override
    public Map<String, Map<Integer, List<ReportInfo>>> getReportInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd)
    {
        if (null == _reportInfoMap)
        {
            final Map<String, Map<Integer, List<ReportInfo>>> reportInfoMap = new HashMap<>();
            TableInfo reportTableInfo = CoreSchema.getInstance().getTableInfoReport();
            SimpleFilter filter = new SimpleFilter();
            filter.addBetween(FieldKey.fromString("Modified"), modifiedRangeStart, modifiedRangeEnd);
            Sort sort = new Sort("DisplayOrder");
            TableSelector selector = new TableSelector(reportTableInfo, filter, sort);
            selector.forEach(new Selector.ForEachBlock<ReportDB>()
            {
                @Override
                public void exec(ReportDB report) throws SQLException
                {
                    String containerId = report.getContainerId();
                    if (!reportInfoMap.containsKey(containerId))
                        reportInfoMap.put(containerId, new HashMap<Integer, List<ReportInfo>>());
                    Map<Integer, List<ReportInfo>> subMap = reportInfoMap.get(containerId);
                    ReportInfo reportInfo = new ReportInfo(report);
                    int categoryId = reportInfo.getCategoryId();
                    if (!subMap.containsKey(categoryId))
                        subMap.put(categoryId, new ArrayList<ReportInfo>());
                    subMap.get(categoryId).add(reportInfo);
                }
            }, ReportDB.class);
            _reportInfoMap = reportInfoMap;
        }
        return _reportInfoMap;
    }
}


