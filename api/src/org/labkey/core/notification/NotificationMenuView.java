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
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class NotificationMenuView extends JspView<List<Notification>>
{
    public static final String EXPERIMENTAL_NOTIFICATIONMENU = "experimental-notificationmenu";

    public static HttpView createView(ViewContext context)
    {
        NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
        if (null == service || context.getUser().isGuest())
            return null;
        HttpView view = new NotificationMenuView();
        view.setViewContext(context);
        return view;
    }


    public NotificationMenuView()
    {
        // NOTE: this .jsp is in the core module so that jsp recompile works
        super(NotificationMenuView.class, "notificationpanel.jsp", null);
        setFrame(FrameType.NONE);

        ViewContext context = getViewContext();
        User user = context.getUser();
        if (!user.isGuest())
        {
            NotificationService.Service service = ServiceRegistry.get(NotificationService.Service.class);
            setModelBean(service.getNotificationsByUser(null, user.getUserId(), true));
        }
    }


    @Override
    protected void renderView(List<Notification> model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_NOTIFICATIONMENU))
            return;
        if (getViewContext().getUser().isGuest())
            return;
        super.renderView(model, request, response);
    }
}
