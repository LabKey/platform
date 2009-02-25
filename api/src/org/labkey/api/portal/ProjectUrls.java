/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
package org.labkey.api.portal;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.Portal;

/**
 * <code>ProjectUrls</code> a UrlProvider for the project UI.
 */
public interface ProjectUrls extends UrlProvider 
{
    ActionURL getStartURL(Container container);
    ActionURL getHomeURL();
    ActionURL getCustomizeWebPartURL(Container c);
    ActionURL getAddWebPartURL(Container c);
    ActionURL getCustomizeWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL);
    ActionURL getMoveWebPartURL(Container c, Portal.WebPart webPart, int direction, ActionURL returnURL);
    ActionURL getDeleteWebPartURL(Container c, Portal.WebPart webPart, ActionURL returnURL);
}
