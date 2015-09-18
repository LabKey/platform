package org.labkey.core.notification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.AbstractContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.view.NotFoundException;

import java.util.List;

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
    public Notification addNotification(Container container, User user, @NotNull String objectId, @NotNull String type, int notifyUserId) throws ValidationException
    {
        if (getNotification(container, objectId, type, notifyUserId) != null)
        {
            throw new ValidationException("Notification already exists for the specified user, object, and type.");
        }

        Notification notification = new Notification();
        notification.setContainer(container.getEntityId());
        notification.setUserId(notifyUserId);
        notification.setObjectId(objectId);
        notification.setType(type);
        return Table.insert(user, getTable(), notification);
    }

    @Override
    public List<Notification> getNotificationsByType(Container container, @NotNull String type, int notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        TableSelector selector = new TableSelector(getTable(), filter, null);
        return selector.getArrayList(Notification.class);
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
    public int removeNotificationsByType(Container container, @NotNull String type, int notifyUserId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Type"), type);
        filter.addCondition(FieldKey.fromParts("UserID"), notifyUserId);
        return Table.delete(getTable(), filter);
    }

    @Override
    public void removeNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId)
    {
        Notification notification = getNotification(container, objectId, type, notifyUserId);
        if (notification == null)
        {
            throw new NotFoundException("Notification does not exist for the specified user, object, and type.");
        }
        Table.delete(getTable(), notification.getRowId());
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
