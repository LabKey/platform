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

package org.labkey.api.webdav;

import org.labkey.api.view.ViewContext;
import org.labkey.api.security.User;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 28, 2008
 * Time: 2:02:25 PM
 */
public interface WebdavResolver
{
    Resource lookup(String path);

    /**
     * Created by IntelliJ IDEA.
     * User: matthewb
     * Date: Apr 28, 2008
     * Time: 11:42:10 AM
     */
    public static interface Resource
    {
        String getPath();

        String getName();

        String getParentPath();

        boolean exists();

        boolean isCollection();

        Resource find(String name);        

        // should really be 'isResource()'
        boolean isFile();

        // TODO move more functionality into interface and remove this method
        File getFile();

        List<String> listNames();

        List<Resource> list();

        Resource parent();

        long getCreation();

        long getLastModified();

        String getContentType();

        InputStream getInputStream() throws IOException;

        OutputStream getOutputStream() throws IOException;

        long getContentLength();

        String getHref(ViewContext context);

        String getLocalHref(ViewContext context);

        String getETag();

        /** user may read properties of this resource */
        boolean canList(User user);

        /** user may read file stream of this resource */
        boolean canRead(User user);
        
        boolean canWrite(User user);
        boolean canCreate(User user);
        boolean canDelete(User user);
        boolean canRename(User user);

        // dav methods
        boolean delete(User user);
    }

    // marker interfaces for web folder, see FtpConnectorImpl
    public static interface WebFolder
    {
        int getPermissions(User user);
        List<String> getWebFoldersNames(User user);
    }
    
}
