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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: Jul 30, 2008
 * Time: 5:26:28 PM
 */
public interface UserUrls extends UrlProvider
{
    ActionURL getSiteUsersURL();
    ActionURL getProjectUsersURL(Container container);
    ActionURL getUserAccessURL(Container container, int userId);
    ActionURL getUserDetailsURL(Container container, int userId, @Nullable URLHelper returnURL);
    ActionURL getUserDetailsURL(Container c, @Nullable URLHelper returnURL);
    ActionURL getUserUpdateURL(Container c, URLHelper returnURL, int userId);
    ActionURL getUserAttachmentDownloadURL(User user, String name);

    /**
     * Does this user's profile need to be updated? In other words, are there required fields that are blank? Doesn't
     * return a URL, so perhaps it should be on a different service, but there aren't any obvious choices.
     */
    boolean requiresProfileUpdate(User user);
}
