/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.core.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.AbstractContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: cnathe
 * Date: 9/14/2015
 */
public class NotificationServiceImpl extends AbstractContainerListener implements NotificationService.Service
{
    private final static NotificationServiceImpl INSTANCE = new NotificationServiceImpl();

    public static NotificationServiceImpl getInstance()
    {
        return INSTANCE;
    }

    private NotificationServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }

    @Override
    public Notification addNotification(Container container, User user, @NotNull Notification notification) throws ValidationException
    {
        if (notification.getObjectId() == null || notification.getType() == null || notification.getUserId() <= 0)
        {
            throw new ValidationException("Notification missing one of the required fields: objectId, type, or notifyUserId.");
        }
        if (getNotification(container, notification) != null)
        {
            throw new ValidationException("Notification already exists for the specified user, object, and type.");
        }

        notification.setContainer(container.getEntityId());
        return Table.insert(user, getTable(), notification);
    }

    @Override
    public List<Notification> getNotificationsByType(Container container, @NotNull String type, int notifyUserId, boolean unreadOnly)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        if (unreadOnly)
            filter.addCondition(FieldKey.fromParts("ReadOn"), null, CompareType.ISBLANK);

        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getArrayList(Notification.class);
    }

    @Override
    public Notification getNotification(Container container, @NotNull Notification notification)
    {
        return getNotification(container, notification.getObjectId(), notification.getType(), notification.getUserId());
    }

    @Override
    public Notification getNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("ObjectId"), objectId);
        filter.addCondition(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getObject(Notification.class);
    }

    @Override
    public int markAsRead(Container container, User user, @Nullable String objectId, @NotNull List<String> types, int notifyUserId)
    {
        SimpleFilter filter = getNotificationUpdateFilter(container, objectId, types, notifyUserId);
        filter.addCondition(FieldKey.fromParts("ReadOn"), null, CompareType.ISBLANK);

        TableSelector selector = new TableSelector(getTable(), filter, null);
        List<Notification> notifications = selector.getArrayList(Notification.class);

        // update the ReadOn date to current date
        Map<String, Object> fields = new HashMap<>();
        fields.put("ReadOn", new Date());

        try(DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            for (Notification notification : notifications)
            {
                Table.update(user, getTable(), fields, notification.getRowId());
            }
            transaction.commit();
        }

        return notifications.size();
    }

    @Override
    public int removeNotifications(Container container, @Nullable String objectId, @NotNull List<String> types, int notifyUserId)
    {
        SimpleFilter filter = getNotificationUpdateFilter(container, objectId, types, notifyUserId);
        return Table.delete(getTable(), filter);
    }

    private SimpleFilter getNotificationUpdateFilter(Container container, @Nullable String objectId, @NotNull List<String> types, int notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        filter.addInClause(FieldKey.fromParts("Type"), types);
        if (objectId != null)
            filter.addCondition(FieldKey.fromParts("ObjectId"), objectId);
        return filter;
    }

    private TableInfo getTable()
    {
        return CoreSchema.getInstance().getTableInfoNotifications();
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTable(), c, "Container");
    }
}
