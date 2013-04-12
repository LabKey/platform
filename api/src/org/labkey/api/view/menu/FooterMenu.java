/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.view.menu;

import org.labkey.api.data.Container;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.PageConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:39 AM
 */
public class FooterMenu extends NavTreeMenu
{
    public FooterMenu(ViewContext context, PageConfig page)
    {
        super(context, "leftNavFooter", getNavTree(context, page));
        setHighlightSelection(false);
    }

    private static NavTree[] getNavTree(ViewContext context, PageConfig page)
    {
        List<NavTree> menu = new ArrayList<NavTree>();
        User user = context.getUser();
        Container c = context.getContainer();

        //
        // LOGIN
        //
        if (user.isGuest())
            menu.add(new NavTree("Sign In", PageFlowUtil.urlProvider(LoginUrls.class).getLoginURL()));

        menu.add(new NavTree("Home", AppProps.getInstance().getHomePageActionURL()));
        
        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
        String reportAProblemPath = laf.getReportAProblemPath();
        if (reportAProblemPath != null && reportAProblemPath.trim().length() > 0 && !user.isGuest())
            menu.add(new NavTree("Support", reportAProblemPath));

        //
        // HELP
        //
        if (laf.isHelpMenuEnabled())
        {
            HelpTopic topic = null != page ? page.getHelpTopic() : HelpTopic.DEFAULT_HELP_TOPIC;
            menu.add(topic.getNavTree("Help"));
        }

        return menu.toArray(new NavTree[menu.size()]);
    }
}
