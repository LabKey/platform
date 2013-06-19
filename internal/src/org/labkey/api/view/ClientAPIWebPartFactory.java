/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.labkey.api.security.User;

import java.util.Map;

/**
 * Base class for Web Parts that are essentially wrappers around client api calls.
 *
 * User: jgarms
 * Date: Jul 29, 2008
 * Time: 2:45:24 PM
 */
public abstract class ClientAPIWebPartFactory extends BaseWebPartFactory
{
    public static final String CONTENT_KEY = "content";
    public static final String DEFAULT_CONTENT_KEY = "defaultContent";

    public ClientAPIWebPartFactory(String name)
    {
        this(name, false);
    }

    public ClientAPIWebPartFactory(String name, boolean showCustomizeOnInsert)
    {
        super(name, null, true, showCustomizeOnInsert);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        String content = webPart.getPropertyMap().get(CONTENT_KEY);
        if (content == null)
        {
            content = getDefaultContent();
        }
        WebPartView view = new HtmlView(content);
        view.setFrame(WebPartView.FrameType.PORTAL);
        view.setTitle(getTitle());
        return view;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        String defaultContent = getDefaultContent();
        Map<String,String> propertyMap = webPart.getPropertyMap();
        propertyMap.put(DEFAULT_CONTENT_KEY, defaultContent);
        if (propertyMap.get(CONTENT_KEY) == null)
        {
           propertyMap.put(CONTENT_KEY, defaultContent);
        }
        return new JspView<>("/org/labkey/api/view/customizeClientAPIWebPart.jsp", webPart);
    }

    protected abstract String getDefaultContent();

    protected String getTitle()
    {
        return name;
    }

    public boolean isEditable()
    {
        // We're only editable if the user can write scripts
        User user = HttpView.currentContext().getUser();
        return user.isDeveloper();
    }
}
