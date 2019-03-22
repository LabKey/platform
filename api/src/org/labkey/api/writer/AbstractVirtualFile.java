/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.writer;

import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * User: jeckels
 * Date: Jan 19, 2012
 */
public abstract class AbstractVirtualFile implements VirtualFile
{
    /** Writes the contents of the WebDAV resource into the virtual file */
    @Override
    public void saveWebdavTree(WebdavResource resource, User user) throws IOException
    {
        for (WebdavResource child : resource.list())
        {
            if (child.isCollection())
            {
                getDir(child.getName()).saveWebdavTree(child, user);
            }
            else
            {
                try (InputStream inputStream = child.getInputStream(user); OutputStream outputStream = getOutputStream(child.getName()))
                {
                    if (inputStream != null)
                        FileUtil.copyData(inputStream, outputStream);
                }
            }
        }
    }
}
