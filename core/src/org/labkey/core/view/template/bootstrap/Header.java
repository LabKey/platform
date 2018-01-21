/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.core.view.template.bootstrap;

import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.JspView;
import org.labkey.api.view.template.PageConfig;


/**
 * Created by Nick Arnold on 3/7/2017.
 */
public class Header extends JspView<PageConfig>
{
    public Header(PageConfig page)
    {
        super("/org/labkey/core/view/template/bootstrap/header.jsp", page);
        setFrame(FrameType.NONE);
        displayNotifications();
    }

    private void displayNotifications()
    {
        if (AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATION_MENU))
        {
            this.setView("notifications", NotificationMenuView.createView(getViewContext()));
        }
    }
}
