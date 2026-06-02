/*
 * Copyright (c) 2011-2026 LabKey Corporation
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

package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;

/**
 * The Help menu item that appears in the user menu and lets the user navigate to relevant documentation and other resources.
 */
public class PopupHelpView extends PopupMenuView
{
    public PopupHelpView(ViewContext context, HelpTopic topic)
    {
        setNavTree(createNavTree(context, topic));
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }

    public static NavTree createNavTree(ViewContext context, HelpTopic topic)
    {
        NavTree menu = new NavTree("Help" + (AppProps.getInstance().isDevMode() && topic == HelpTopic.DEFAULT_HELP_TOPIC ? " (default)" : ""));
        menu.setId("helpMenu");

        Container c = context.getContainer();
        User user = context.getUser();
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);

        if (ModuleLoader.getInstance().isStartupComplete())
        {
            String reportAProblemPath = laf.getReportAProblemPath();
            if (reportAProblemPath != null && !reportAProblemPath.trim().isEmpty() && !user.isGuest())
                menu.addChild("Support", reportAProblemPath);
        }

        if (laf.isHelpMenuEnabled())
            menu.addChild(topic.getNavTree("LabKey Documentation"));

        return menu;
    }
}
