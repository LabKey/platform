package org.labkey.api.query;

import org.labkey.api.reports.model.NotificationInfo;

import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class NotificationInfoProvider
{
    protected Map<String, Map<Integer, List<NotificationInfo>>> _notificationInfoMap = null;

    public abstract Map<String, Map<Integer, List<NotificationInfo>>> getNotificationInfoMap(Date modifiedRangeStart, Date modifiedRangeEnd);

    public void clearNotificationInfoMap()
    {
        _notificationInfoMap = null;
    }
}
