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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditLogEvent;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.*;

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

    public FileSystemResource(WebdavResolver.Resource folder, String name, File file, SecurityPolicy policy)
    {
        super(folder.getPath(), name);
        _folder = folder;
        _name = name;
        _policy = policy;
        _file = FileUtil.canonicalFile(file);
    }

    public FileSystemResource(FileSystemResource folder, String relativePath)
    {
        super(folder, relativePath);
        _folder = folder;
        _policy = folder._policy;
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


    public FileStream getFileStream(User user) throws IOException
    {
        if (!canRead(user))
            return null;
        if (null == _file || !_file.exists())
            return null;
        return new FileStream.FileFileStream(_file);
    }


    public InputStream getInputStream(User user) throws IOException
    {
        if (!canRead(user))
            return null;
        if (null == _file || !_file.exists())
            return null;
        return new FileInputStream(_file);
    }


    public OutputStream getOutputStream(User user) throws IOException
    {
        if (!canWrite(user))
            return null;
        if (null == _file)
            return null;
        if (!_file.exists())
            _file.createNewFile();
        return new FileOutputStream(_file);
    }


    public long copyFrom(User user, FileStream is) throws IOException
    {
        FileOutputStream fos=null;
        try
        {
            fos = (FileOutputStream)getOutputStream(user);
            if (null == fos)
                return -1;
            long len = FileUtil.copyData(is.openInputStream(), fos);
            fos.getFD().sync();
            return len;
        }
        finally
        {
            IOUtils.closeQuietly(fos);
        }
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

    
    public long getCreated()
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
        return Long.MIN_VALUE;
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
        if (_file == null || (null != user && !canDelete(user)))
            return false;
        return _file.delete();
    }

    @NotNull
    public List<WebdavResolver.History> getHistory()
    {
        SimpleFilter filter = new SimpleFilter("EventType",  "FileSystem"); // FileSystemAuditViewFactory.EVENT_TYPE);
        filter.addCondition("Key1", _file.getParent());
        filter.addCondition("Key2", _file.getName());
        List<AuditLogEvent> logs = AuditLogService.get().getEvents(filter);
        if (null == logs)
            return Collections.EMPTY_LIST;
        List<WebdavResolver.History> history = new ArrayList<WebdavResolver.History>(logs.size());
        for (AuditLogEvent e : logs)
            history.add(new HistoryImpl(e.getCreatedBy(), e.getCreated(), e.getComment(), null));
        return history;
    }


    /*
    * not part of Resource interface, but used by FtpConnector
    */

    public Container getContainer()
    {
        return _c;
    }
}
