/*
 * Copyright (c) 2011-2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
import org.labkey.api.security.impersonation.ImpersonationContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.PageFlowUtil;

/**
 * Popup menu for upper-right corner of main frame
 * User: jeckels
 * Date: Oct 20, 2011
 */
public class PopupUserView extends PopupMenuView
{
    public PopupUserView(ViewContext context)
    {
        User user = context.getUser();
        Container c = context.getContainer();
        ActionURL currentURL = context.getActionURL();

        NavTree tree = new NavTree(user.getFriendlyName());
        tree.addChild("My Account", PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(c, user.getUserId(), currentURL));

        if (user.isImpersonated())
        {
            tree.addChild("Stop Impersonating", PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL()));
        }
        else
        {
            ImpersonationContext impersonationContext = user.getImpersonationContext();
            @Nullable Container project = c.getProject();

            // If user is already impersonating then we need to check permissions on the actual admin user
            User adminUser = impersonationContext.isImpersonating() ? impersonationContext.getAdminUser() : user;

            // Must be site or project admin (folder admins can't impersonate)
            if (adminUser.isSiteAdmin() || (null != project && project.hasPermission(adminUser, AdminPermission.class)))
            {
                NavTree impersonateMenu = new NavTree("Impersonate");
                impersonationContext.addMenu(impersonateMenu, c, user, currentURL);

                if (impersonateMenu.hasChildren())
                    tree.addChild(impersonateMenu);
            }

            tree.addChild("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(c));
        }

        tree.setId("userMenu");

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
