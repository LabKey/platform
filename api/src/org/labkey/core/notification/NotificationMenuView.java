/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenu;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class NotificationMenuView extends PopupMenuView
{
    public static final String EXPERIMENTAL_NOTIFICATIONMENU = "experimental-notificationmenu";

    public static HttpView createView(ViewContext context)
    {
        NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
        if (null == service || context.getUser().isGuest())
            return null;
        return new NotificationMenuView(context);
    }

    public NotificationMenuView(ViewContext context)
    {
        User user = context.getUser();
        Container c = context.getContainer();

        NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
        List<Notification> notifications = service.getNotificationsByUser(null, user.getUserId(), true);

        NavTree tree = new NavTree(String.valueOf(notifications.size()));
        tree.setImageCls("fa fa-bell");

        if (notifications.isEmpty())
        {
            tree.addChild("No notifications");
        }
        else
        {
            notifications.stream().forEach((n)->
            {
                tree.addChild(n.getDescription(), n.getActionLinkURL());
            });
        }

        tree.setId("lk-notificationMenu");

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);

        getModelBean().setIsSingletonMenu(true);
    }
}
