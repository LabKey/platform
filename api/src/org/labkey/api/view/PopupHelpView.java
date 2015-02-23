/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

import org.labkey.api.announcements.api.Tour;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/*
* User: adam
* Date: Dec 7, 2011
* Time: 5:27:39 PM
*/
public class PopupHelpView extends PopupMenuView
{
    public PopupHelpView(Container c, User user, HelpTopic topic)
    {
        NavTree menu = new NavTree("Help" + (AppProps.getInstance().isDevMode() && topic == HelpTopic.DEFAULT_HELP_TOPIC ? " (default)" : ""));

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);

        String reportAProblemPath = laf.getReportAProblemPath();
        if (reportAProblemPath != null && reportAProblemPath.trim().length() > 0 && !user.isGuest())
            menu.addChild("Support", reportAProblemPath);

        if (laf.isHelpMenuEnabled())
            menu.addChild(topic.getNavTree("LabKey Documentation"));

        if (c.hasPermission(user, ReadPermission.class))
        {
            TourService.Interface service = TourService.get();

            if (null != service)
            {
                List<Tour> tours = service.getApplicableTours(c);

                if (tours.size() > 0)
                {
                    NavTree toursMenu = new NavTree("Tours");
                    for (Tour t : tours)
                    {
                        NavTree tourLink = new NavTree(t.getTitle());
                        tourLink.setScript("LABKEY.help.Tour.showFromDb(" + PageFlowUtil.jsString(t.getRowId().toString()) + ", 0)");
                        toursMenu.addChild(tourLink);
                    }
                    menu.addChild(toursMenu);
                }
            }
        }

        menu.setId("helpMenu");

        setNavTree(menu);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
