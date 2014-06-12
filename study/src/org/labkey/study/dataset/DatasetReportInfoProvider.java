package org.labkey.study.dataset;

import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ReportInfoProvider;
import org.labkey.api.reports.model.ReportInfo;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.study.DatasetDB;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DatasetReportInfoProvider extends ReportInfoProvider
{
    private Map<String, Map<Integer, Set<ReportInfo>>> _reportInfoMap = null;

    @Override
    public Map<String, Map<Integer, Set<ReportInfo>>> getReportInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd)
    {
        if (null == _reportInfoMap)
        {
            final Map<String, Map<Integer, Set<ReportInfo>>> reportInfoMap = new HashMap<>();
            TableInfo reportTableInfo = StudySchema.getInstance().getTableInfoDataSet();
            SimpleFilter filter = new SimpleFilter();
            filter.addBetween(FieldKey.fromString("Modified"), modifiedRangeStart, modifiedRangeEnd);
            TableSelector selector = new TableSelector(reportTableInfo, filter, null);
            selector.forEach(new Selector.ForEachBlock<DatasetDB>()
            {
                @Override
                public void exec(DatasetDB report) throws SQLException
                {
                    String containerId = report.getContainer();
                    if (!reportInfoMap.containsKey(containerId))
                        reportInfoMap.put(containerId, new HashMap<Integer, Set<ReportInfo>>());
                    Map<Integer, Set<ReportInfo>> subMap = reportInfoMap.get(containerId);
                    int categoryId = null != report.getCategoryId() ? report.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID;
                    if (!subMap.containsKey(categoryId))
                        subMap.put(categoryId, new HashSet<ReportInfo>());
                    subMap.get(categoryId).add(new ReportInfo(report));
                }
            }, DatasetDB.class);
            _reportInfoMap = reportInfoMap;
        }
        return _reportInfoMap;
    }
}


