package org.labkey.api.admin.notification;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

/**
 * User: cnathe
 * Date: 9/14/2015
 *
 * Service for adding/getting/removing user notifications attached to specific objects.
 *
 */
public class NotificationService
{
    private static Service _serviceImpl = null;

    public interface Service
    {
        /*
         * Insert a new notification in the specified container for the given user/object/type.
         */
        Notification addNotification(Container container, User user, @NotNull String objectId, @NotNull String type, int notifyUserId) throws ValidationException;

        /*
         * Returns a list of notifications for a specific user based on the specified type.
         */
        List<Notification> getNotificationsByType(Container container, @NotNull String type, int notifyUserId);

        /*
         * Returns a notification for a specific user based on the specified objectId and type.
         */
        Notification getNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId);

        /*
         * Removes all notifications for a specific user based on the specified type.
         */
        int removeNotificationsByType(Container container, @NotNull String type, int notifyUserId);

        /*
         * Remove a single notification for a specific user based on the specified objectId and type.
         */
        void removeNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
        ServiceRegistry.get().registerService(NotificationService.Service.class, serviceImpl);
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}
