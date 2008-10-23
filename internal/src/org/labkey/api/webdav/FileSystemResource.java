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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.util.FileUtil;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
* User: matthewb
* Date: Oct 21, 2008
* Time: 10:00:49 AM
*
*   Base class for file-system based resources
*/
public class FileSystemResource extends AbstractResource
{
    Container _c;
    protected File _file;
    String _name = null;
    WebdavResolver.Resource _folder;   // containing controller used for canList()

    protected FileSystemResource(String path)
    {
        super(path);
        assert getPath().equals("/") || !getPath().endsWith("/");
    }

    protected FileSystemResource(String folder, String name)
    {
        this(WebdavResolverImpl.c(folder,name));
    }

    public FileSystemResource(WebdavResolver.Resource folder, String name, File file, ACL acl)
    {
        super(folder.getPath(), name);
        _folder = folder;
        _name = name;
        _acl = acl;
        _file = FileUtil.canonicalFile(file);
    }

    public FileSystemResource(FileSystemResource folder, String relativePath)
    {
        super(folder, relativePath);
        _folder = folder;
        _acl = folder._acl;
        _file = FileUtil.canonicalFile(new File(folder._file, relativePath));
    }

    public String getName()
    {
        return null == _name ? super.getName() : _name;
    }
    
    public boolean exists()
    {
        return _file == null || _file.exists();
    }


    public boolean isCollection()
    {
        if (null != _file && _file.isDirectory())
            return true;
        return getPath().endsWith("/");
    }


    // cannot create objects in a virtual collection
    public boolean isVirtual()
    {
        return _file == null;
    }


    public boolean isFile()
    {
        return _file != null && _file.isFile();
    }


    public File getFile()
    {
        return _file;
    }


    public InputStream getInputStream() throws IOException
    {
        if (null == _file || !_file.exists())
            return null;
        return new FileInputStream(_file);
    }


    public OutputStream getOutputStream() throws IOException
    {
        if (null == _file || !_file.exists())
            return null;
        return new FileOutputStream(_file);
    }

    @NotNull
    public List<String> listNames()
    {
        if (!isCollection())
            return Collections.emptyList();
        ArrayList<String> list = new ArrayList<String>();
        if (_file != null && _file.isDirectory())
        {
            File[] files = _file.listFiles();
            if (null != files)
            {
                for (File file: files)
                    list.add(file.getName());
            }
        }
        Collections.sort(list);
        return list;
    }


    public List<WebdavResolver.Resource> list()
    {
        List<String> names = listNames();
        ArrayList<WebdavResolver.Resource> infos = new ArrayList<WebdavResolver.Resource>(names.size());
        for (String name : names)
        {
            WebdavResolver.Resource r = find(name);
            if (null != r && !(r instanceof WebdavResolverImpl.UnboundResource))
                infos.add(r);
        }
        return infos;
    }


    public WebdavResolver.Resource find(String name)
    {
        return new FileSystemResource(this, name);
    }

    
    public long getCreation()
    {
        if (_c != null && _c.getCreated() != null)
            return _c.getCreated().getTime();
        if (null != _file)
            return _file.lastModified();
        return getLastModified();
    }


    public long getLastModified()
    {
        if (null != _file)
            return _file.lastModified();
        if (_c != null && _c.getCreated() != null)
            return _c.getCreated().getTime();
        return 0;
    }


    public long getContentLength()
    {
        if (!isFile() || _file == null)
            return 0;
        return _file.length();
    }


    public boolean canWrite(User user)
    {
        return super.canWrite(user) && hasFileSystem();
    }


    public boolean canCreate(User user)
    {
        return super.canCreate(user) && hasFileSystem();
    }


    public boolean canDelete(User user)
    {
        return super.canDelete(user) && hasFileSystem();
    }


    public boolean canRename(User user)
    {
        return super.canRename(user);
    }

    public boolean canList(User user)
    {
        return canRead(user) || (null != _folder && _folder.canList(user));
    }    

    private boolean hasFileSystem()
    {
        return _file != null;
    }


    public boolean delete(User user)
    {
        if (_file == null || !canDelete(user))
            return false;
        return _file.delete();
    }

    /*
     * not part of Resource interface, but used by FtpConnector
     */

    public Container getContainer()
    {
        return _c;
    }
}
