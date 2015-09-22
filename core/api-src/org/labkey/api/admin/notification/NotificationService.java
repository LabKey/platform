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
package org.labkey.api.admin.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
         * Remove a single notification, if it exists, for a specific user based on the specified objectId and types,
         * rr if no objectId provided, removes all notifications for a specific user based on the specified types.
         */
        int removeNotifications(Container container, @Nullable String objectId, @NotNull List<String> types, int notifyUserId);
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
