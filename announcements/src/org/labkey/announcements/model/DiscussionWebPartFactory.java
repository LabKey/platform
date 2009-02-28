/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.announcements.model;

import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;

import java.util.Map;

/**
 * User: adam
 * Date: Sep 11, 2008
 * Time: 10:21:48 AM
 */
public class DiscussionWebPartFactory extends BaseWebPartFactory
{
    public DiscussionWebPartFactory()
    {
        super("Discussion", null, true, false);
    }

    @Override
    public boolean isAvailable(Container c, String location)
    {
        return false;     // This webpart is used via JavaScript, but from the portal page.  See #7431
    }

    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/announcements/customizeDiscussionWebPart.jsp", webPart);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        Container c = portalCtx.getContainer();
        User user = portalCtx.getUser();
        Map<String, String> props = webPart.getPropertyMap();

        // Next two props are required.  TODO: throw if null -- see #7101
        String entityId = webPart.getPropertyMap().get("entityId");

        String pageUrlString = props.get("pageURL");
        ActionURL pageURL = null != pageUrlString ? new ActionURL(pageUrlString) : portalCtx.getActionURL();

        String currentUrlString = props.get("currentURL");
        URLHelper currentURL = (null != currentUrlString ? new URLHelper(currentUrlString) : portalCtx.getActionURL());

        String newDiscussionTitle = props.get("newDiscussionTitle");
        if (null == newDiscussionTitle)
            newDiscussionTitle = "New Discussion";

        String allowMultipleDiscussionsString = props.get("allowMultipleDiscussions");
        boolean allowMultipleDiscussions = (null != allowMultipleDiscussionsString && new BooleanFormat().parseObject(allowMultipleDiscussionsString).booleanValue());

        WebPartView view = DiscussionService.get().getDisussionArea(c, user, currentURL, entityId, pageURL, newDiscussionTitle, allowMultipleDiscussions, true);
        view.setTitle("Discussion");
        view.setFrame(WebPartView.FrameType.PORTAL);

        return view;
    }
}
