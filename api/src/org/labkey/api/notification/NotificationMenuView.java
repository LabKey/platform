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
package org.labkey.api.notification;

import org.labkey.api.settings.AppProps;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class NotificationMenuView extends JspView<Object>
{
    public static final String EXPERIMENTAL_NOTIFICATION_MENU = "experimental-notificationmenu";

    public static HttpView createView(ViewContext context)
    {
        if (context.getUser().isGuest())
            return null;
        HttpView view = new NotificationMenuView();
        view.setViewContext(context);
        return view;
    }


    public NotificationMenuView()
    {
        super(NotificationMenuView.class, "notificationpanel.jsp", null);
        setFrame(FrameType.NONE);
    }


    @Override
    protected void renderView(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_NOTIFICATION_MENU))
            return;
        if (getViewContext().getUser().isGuest())
            return;
        super.renderView(model, request, response);
    }
}
