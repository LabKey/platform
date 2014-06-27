/*
 * Copyright (c) 2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.query.reports;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.NotificationInfoProvider;
import org.labkey.api.reports.model.NotificationInfo;
import org.labkey.api.reports.report.ReportDB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportNotificationInfoProvider extends NotificationInfoProvider
{
    @Override
    public Map<String, Map<Integer, List<NotificationInfo>>> getNotificationInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd)
    {
        if (null == _notificationInfoMap)
        {
            final Map<String, Map<Integer, List<NotificationInfo>>> reportInfoMap = new HashMap<>();
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
                        reportInfoMap.put(containerId, new HashMap<Integer, List<NotificationInfo>>());
                    Map<Integer, List<NotificationInfo>> subMap = reportInfoMap.get(containerId);
                    NotificationInfo notificationInfo = new NotificationInfo(report);
                    if (null != notificationInfo.getContainer() && !notificationInfo.isHidden() && notificationInfo.isShared())
                    {
                        // Don't include hidden reports (or if container was deleted)
                        int categoryId = notificationInfo.getCategoryId();
                        if (!subMap.containsKey(categoryId))
                            subMap.put(categoryId, new ArrayList<NotificationInfo>());
                        subMap.get(categoryId).add(notificationInfo);
                    }
                }
            }, ReportDB.class);
            _notificationInfoMap = reportInfoMap;
        }
        return _notificationInfoMap;
    }
}


