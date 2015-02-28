/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.study.dataset;

import org.labkey.api.data.ContainerManager;
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
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DatasetNotificationInfoProvider extends NotificationInfoProvider
{
    @Override
    public Map<String, Map<Integer, List<NotificationInfo>>> getNotificationInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd)
    {
        final Map<String, Map<Integer, List<NotificationInfo>>> notificationInfoMap = new HashMap<>();
        TableInfo reportTableInfo = StudySchema.getInstance().getTableInfoDataset();
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
                if (null != report.getContainer())
                {
                    Study study = StudyManager.getInstance().getStudy(ContainerManager.getForId(report.getContainer()));
                    if (null != study)
                    {
                        DatasetDefinition datasetDefinition = StudyManager.getInstance().getDatasetDefinition(study, report.getDatasetId());
                        if (null != datasetDefinition)
                        {
                            NotificationInfo notificationInfo = new NotificationInfo(report, !datasetDefinition.isShowByDefault());
                            if (null != notificationInfo.getContainer() && !notificationInfo.isHidden() && notificationInfo.isShared())
                            {
                                // Don't include hidden reports (or if container was deleted)
                                int categoryId = null != report.getCategoryId() ? report.getCategoryId() : ViewCategoryManager.UNCATEGORIZED_ROWID;
                                if (!subMap.containsKey(categoryId))
                                    subMap.put(categoryId, new ArrayList<NotificationInfo>());
                                subMap.get(categoryId).add(notificationInfo);
                            }
                        }
                    }
                }
            }
        }, DatasetDB.class);
        return notificationInfoMap;
    }
}


