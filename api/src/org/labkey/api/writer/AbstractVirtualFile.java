/*
 * Copyright (c) 2012 LabKey Corporation
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
    @Override
    public void saveWebdavTree(WebdavResource resource) throws IOException
    {
        for (WebdavResource child : resource.list())
        {
            if (child.isCollection())
            {
                getDir(child.getName()).saveWebdavTree(child);
            }
            else
            {
                OutputStream outputStream = getOutputStream(child.getName());
                InputStream inputStream = null;
                try
                {
                    byte[] bytes = new byte[4096];
                    int length;
                    inputStream = child.getInputStream();
                    if (inputStream != null)
                    {
                        while ((length = inputStream.read(bytes)) != -1)
                        {
                            outputStream.write(bytes, 0, length);
                        }
                    }
                }
                finally
                {
                    try { outputStream.close(); } catch (IOException ignored) {}
                    if (inputStream != null) { try { inputStream.close(); } catch (IOException ignored) {} }
                }
            }
        }
    }
}
