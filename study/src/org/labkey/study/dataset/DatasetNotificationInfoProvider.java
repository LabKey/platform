package org.labkey.study.dataset;

import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.NotificationInfoProvider;
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.study.DatasetDB;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetNotificationInfoProvider extends NotificationInfoProvider
{
    @Override
    public Map<String, Map<Integer, List<NotificationInfo>>> getNotificationInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd)
    {
        if (null == _notificationInfoMap)
        {
            final Map<String, Map<Integer, List<NotificationInfo>>> notificationInfoMap = new HashMap<>();
            TableInfo reportTableInfo = StudySchema.getInstance().getTableInfoDataSet();
            SimpleFilter filter = new SimpleFilter();
            filter.addBetween(FieldKey.fromString("Modified"), modifiedRangeStart, modifiedRangeEnd);
            Sort sort = new Sort("DisplayOrder");
            TableSelector selector = new TableSelector(reportTableInfo, filter, sort);
            selector.forEach(new Selector.ForEachBlock<DatasetDB>()
            {
                @Override
                public void exec(DatasetDB report) throws SQLException
                {
                    String containerId = report.getContainer();
                    if (!notificationInfoMap.containsKey(containerId))
                        notificationInfoMap.put(containerId, new HashMap<Integer, List<NotificationInfo>>());
                    Map<Integer, List<NotificationInfo>> subMap = notificationInfoMap.get(containerId);
                    int categoryId = null != report.getCategoryId() ? report.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID;
                    if (!subMap.containsKey(categoryId))
                        subMap.put(categoryId, new ArrayList<NotificationInfo>());
                    subMap.get(categoryId).add(new NotificationInfo(report));
                }
            }, DatasetDB.class);
            _notificationInfoMap = notificationInfoMap;
        }
        return _notificationInfoMap;
    }
}


