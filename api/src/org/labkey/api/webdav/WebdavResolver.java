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

package org.labkey.api.webdav;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.resource.Resolver;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;

import java.util.Date;

/**
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:02:25 PM
 */
public interface WebdavResolver extends Resolver
{
    class LookupResult
    {
        public final WebdavResolver resolver;
        public final WebdavResource resource;
        public LookupResult(WebdavResolver r, WebdavResource res)
        {
            this.resolver = r;
            this.resource = res;
        }
    }

    boolean requiresLogin();
    Path getRootPath();

    default @Nullable WebdavResource lookup(Path path)
    {
        LookupResult r = lookupEx(path);
        return null==r ? null : r.resource;
    }

    @Nullable LookupResult lookupEx(Path path);

    @Nullable WebdavResource welcome();
    default @Nullable String defaultWelcomePage()
    {
        return "index.html";
    }

    default boolean allowHtmlListing()
    {
        return true;
    }

    default boolean isStaticContent()
    {
        return true;
    }

    default boolean isEnabled()
    {
        return true;
    }

    interface History
    {
        User getUser();
        Date getDate();
        String getMessage();
        String getHref();       // optional detail link
    }

    // marker interfaces for web folder
    interface WebFolder {}
}
