/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: Jul 25, 2008
 * Time: 11:48:29 AM
 */

public interface SecurityUrls extends UrlProvider
{
    ActionURL getBeginURL(Container container);
    ActionURL getManageGroupURL(Container container, String groupName, URLHelper returnURL);
    ActionURL getManageGroupURL(Container container, String groupName);
    ActionURL getGroupPermissionURL(Container container, int id, URLHelper returnURL);
    ActionURL getGroupPermissionURL(Container container, int id);
    ActionURL getPermissionsURL(Container container);
    ActionURL getPermissionsURL(Container container, URLHelper returnURL);
    ActionURL getSiteGroupsURL(Container container, URLHelper returnURL);
    ActionURL getContainerURL(Container container);
    ActionURL getShowRegistrationEmailURL(Container container, ValidEmail email, String mailPrefix);
    ActionURL getAddUsersURL();
    ActionURL getFolderAccessURL(Container container);
    ActionURL getApiKeyURL(URLHelper returnURL); // Always root
    String getCompleteUserURLPrefix(Container container);
    String getCompleteUserReadURLPrefix(Container container);
}
