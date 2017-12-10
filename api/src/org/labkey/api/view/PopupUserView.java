/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.labkey.api.admin.CoreUrls;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.PageConfig;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class PopupUserView extends PopupMenuView
{
    public PopupUserView(ViewContext context)
    {
        setNavTree(createNavTree(context));
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);

        getModelBean().setIsSingletonMenu(true);
    }

    public static NavTree createNavTree(ViewContext context)
    {
        return createNavTree(context, null);
    }

    public static NavTree createNavTree(ViewContext context, PageConfig pageConfig)
    {
        User user = context.getUser();
        Container c = context.getContainer();
        ActionURL currentURL = context.getActionURL();
        NavTree tree = new NavTree();

        tree.setId("userMenu");

        NavTree account = new NavTree("My Account", PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), currentURL));
        tree.addChild(account);

        if (allowApiKeyPage(user))
        {
            NavTree apikey = new NavTree("API Keys", PageFlowUtil.urlProvider(SecurityUrls.class).getApiKeyURL(currentURL));
            tree.addChild(apikey);
        }

        // Delegate impersonate, stop impersonating, adjust impersonation, and sign out menu items to the current ImpersonationContext
        ImpersonationContext impersonationContext = user.getImpersonationContext();
        impersonationContext.addMenu(tree, c, user, currentURL);

        NavTree feedBack = new NavTree("Give UI Feedback", PageFlowUtil.urlProvider(CoreUrls.class).getFeedbackURL());
        feedBack.setTarget("_blank");

        tree.addSeparator();

        if (pageConfig != null)
        {
            NavTree help = PopupHelpView.createNavTree(context, pageConfig.getHelpTopic());

            for (NavTree child : help.getChildren())
                tree.addChild(child);
        }

        tree.addChild(feedBack);

        return tree;
    }

    public static boolean allowApiKeyPage(User user)
    {
        return AppProps.getInstance().isAllowApiKeys() || AppProps.getInstance().isAllowSessionKeys() || user.isInSiteAdminGroup();
    }
}
