/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserUrls;
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
        NavTree tree = new NavTree(user.getFriendlyName());
        tree.addChild("My Account", PageFlowUtil.urlProvider(UserUrls.class).getUserDetailsURL(context.getContainer(), user.getUserId(), context.getActionURL()));
        if (user.isImpersonated())
        {
            tree.addChild("Stop Impersonating", PageFlowUtil.urlProvider(LoginUrls.class).getStopImpersonatingURL(context.getContainer(), context.getRequest()));
        }
        else
        {
            tree.addChild("Sign Out", PageFlowUtil.urlProvider(LoginUrls.class).getLogoutURL(context.getContainer()));
        }

        tree.setId("userMenu");

        setNavTree(tree);
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
