/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;

import java.util.Map;
import java.net.URISyntaxException;
import java.text.ParseException;

/**
 * User: adam
 * Date: Sep 11, 2008
 * Time: 10:21:48 AM
 */
public class DiscussionWebPartFactory extends BaseWebPartFactory
{
    public DiscussionWebPartFactory()
    {
        super("Discussion", true, false);
    }

    @Override
    public boolean isAvailable(Container c, String location)
    {
        return false;     // This webpart is used via JavaScript, not from the portal page.  See #7431
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new JspView<>("/org/labkey/announcements/customizeDiscussionWebPart.jsp", webPart);
    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException
    {
        Container c = portalCtx.getContainer();
        User user = portalCtx.getUser();
        Map<String, String> props = webPart.getPropertyMap();

        String entityId = webPart.getPropertyMap().get("entityId");

        if (null == entityId)
            throw new WebPartConfigurationException(this, "parameter 'entityId' is required");

        String pageUrlString = props.get("pageURL");

        ActionURL pageURL;

        try
        {
            pageURL = null != pageUrlString ? new ActionURL(pageUrlString) : portalCtx.getActionURL();
        }
        catch (Exception e)
        {
            throw new WebPartConfigurationException(this, "invalid 'pageURL' parameter");
        }

        String currentUrlString = props.get("currentURL");
        URLHelper currentURL;

        try
        {
            currentURL = (null != currentUrlString ? new URLHelper(currentUrlString) : portalCtx.getActionURL());
        }
        catch (URISyntaxException e)
        {
            throw new WebPartConfigurationException(this, "invalid 'currentURL' parameter");
        }

        String newDiscussionTitle = props.get("newDiscussionTitle");
        if (null == newDiscussionTitle)
            newDiscussionTitle = "New Discussion";

        boolean allowMultipleDiscussions;

        try
        {
            String allowMultipleDiscussionsString = props.get("allowMultipleDiscussions");
            allowMultipleDiscussions = (null != allowMultipleDiscussionsString && new BooleanFormat().parseObject(allowMultipleDiscussionsString).booleanValue());
        }
        catch (ParseException e)
        {
            throw new WebPartConfigurationException(this, "invalid 'allowMultipleDiscussions' parameter");
        }

        WebPartView view = DiscussionService.get().getDiscussionArea(c, user, currentURL, entityId, pageURL, newDiscussionTitle, allowMultipleDiscussions, true);
        view.setTitle("Discussion");
        view.setFrame(WebPartView.FrameType.PORTAL);

        return view;
    }
}
