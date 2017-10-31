/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
import org.labkey.api.util.MailHelper;
import org.labkey.api.view.ActionURL;

import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/**
 * Service for adding/getting/removing user notifications attached to specific objects.
 * User: cnathe
 * Date: 9/14/2015
 */
public interface NotificationService
{
    static void register(NotificationService serviceImpl)
    {
        ServiceRegistry.get().registerService(NotificationService.class, serviceImpl);
    }

    static NotificationService get()
    {
        return ServiceRegistry.get(NotificationService.class);
    }

    /*
     * Insert a new notification in the specified container.
     */
    Notification addNotification(Container container, User user, @NotNull Notification notification) throws ValidationException;

    /* backward compatible version of addNotification(), send email as well */
    Notification sendMessage(Container c, User createdByUser, User notifyUser, MailHelper.MultipartMessage m,
                             String linkText, String linkURL, String id, String type, boolean useSubjectAsContent)
        throws IOException, MessagingException, ValidationException;

    /**
     * Create a notification, and send email, for the recipient user.
     * @return the generated Notification object
     */
    Notification sendMessageForRecipient(Container c, User createdByUser, User recipient, String subject, String body,
                                 ActionURL linkUrl, String id, String type) throws MessagingException, ValidationException, IOException;

    /*
     * Returns a list of notifications for a specific user. Sorted descending by created date.
     */
    List<Notification> getNotificationsByUser(Container container, int notifyUserId, boolean unreadOnly);

    /*
     * Returns a list of notifications for a specific user based on the specified type.
     */
    List<Notification> getNotificationsByType(Container container, @NotNull String type, int notifyUserId, boolean unreadOnly);

    /*
     * Returns a notification based on the notification's RowId.
     */
    Notification getNotification(int rowId);

    /*
     * Returns a notification (used for checking if a record already exists for the given user, objectId, and type).
     */
    Notification getNotification(Container container, @NotNull Notification notification);

    /*
     * Returns a notification for a specific user based on the specified objectId and type.
     */
    Notification getNotification(Container container, @NotNull String objectId, @NotNull String type, int notifyUserId);

    /*
     * Mark a single notification, if it exists, as read for a specific user based on the specified objectId and types,
     * or if no objectId provided, mark all notifications as read for a specific user based on the specified types.
     * Return a count of the number of notification records updated.
     */
    int markAsRead(Container container, User user, @Nullable String objectId, @NotNull List<String> types, int notifyUserId);

    int markAsRead(@NotNull User user, int rowid);

    /*
     * Remove a single notification, if it exists, for a specific user based on the specified objectId and types,
     * or if no objectId provided, removes all notifications for a specific user based on the specified types.
     * Return a count of the number of notification records removed.
     */
    int removeNotifications(Container container, @Nullable String objectId, @NotNull List<String> types, int notifyUserId);

    /*
     * Remove all notifications for the specific objectId and types
     * or if no objectId provided, removes all notifications for a specific types.
     * Return a count of the number of notification records removed.
     */
    int removeNotificationsByType(Container container, @Nullable String objectId, @NotNull List<String> types);

    /*
     * Register a mapping from a String notification type to a display label and font-awesome icon for that type.
     */
    void registerNotificationType(@NotNull String type, String label, @Nullable String iconCls);

    /*
     * Get the registered display label for a given notification type String. Default "Other".
     */
    String getNotificationTypeLabel(@NotNull String type);

    /*
     * Get the registered font-awesome icon class for a given notification type String. Default "fa-bell".
     */
    String getNotificationTypeIconCls(@NotNull String type);

    /*
     * send event to browser
     */
    void sendServerEvent(int userId, Class clazz);
    /*
     * send event to browser
     */
    void sendServerEvent(int userId, Enum e);
    /**
     * cleanly close any websockets associated with the userId
     * If session is provided, only WebSockets associated with the HttpSession will close.
     */
    void closeServerEvents(int userId, @Nullable HttpSession session, Enum e);
}
