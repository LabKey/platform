/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.labkey.api.resource.Resolver;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;

import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:02:25 PM
 */
public interface WebdavResolver extends Resolver
{
    boolean requiresLogin();
    Path getRootPath();
    WebdavResource lookup(Path path);
    WebdavResource welcome();


    public static interface History
    {
        User getUser();
        Date getDate();
        String getMessage();
        String getHref();       // optional detail link
    }


    // marker interfaces for web folder, see FtpConnectorImpl
    public static interface WebFolder
    {
        int getIntPermissions(User user);
        List<String> getWebFoldersNames(User user);
    }
    
}
