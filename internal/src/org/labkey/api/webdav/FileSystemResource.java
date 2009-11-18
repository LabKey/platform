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
    Resource _folder;   // containing controller used for canList()

    private FileType _type;
    private long _length = UNKNOWN;
    private long _lastModified = UNKNOWN;

    private static final long UNKNOWN = -1;

    private enum FileType { file, directory, notpresent }

    protected FileSystemResource(String path)
    {
        super(path);
        assert getPath().equals("/") || !getPath().endsWith("/");
    }

    protected FileSystemResource(String folder, String name)
    {
        this(WebdavResolverImpl.c(folder,name));
    }

    public FileSystemResource(Resource folder, String name, File file, SecurityPolicy policy)
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
        _file = new File(folder._file, relativePath);
    }

    public String getName()
    {
        return null == _name ? super.getName() : _name;
    }

    public boolean exists()
    {
        return _file == null || getType() != FileType.notpresent;
    }

    /**
     * Try to determine if this entry exists on disk, and its type (file or directory) with the minimum number of
     * java.io.File method calls. Assume that entries are likely to exist, and that files are likely to have extensions.
     * In most cases this reduces the number of java.io.File method calls to one to answer exists(), isDirectory(), and
     * isFile().
     */
    private FileType getType()
    {
        if (_file == null)
        {
            return null;
        }
        if (_type == null)
        {
            if (_file.getName().indexOf(".") == -1)
            {
                // With no extension, first guess that it's a directory
                if (_file.isDirectory())
                {
                    _type = FileType.directory;
                }
                else if (_file.isFile())
                {
                    _type = FileType.file;
                }
            }
            else
            {
                // If it has an extension, guess that it's a file
                if (_file.isFile())
                {
                    _type = FileType.file;
                }
                else if (_file.isDirectory())
                {
                    _type = FileType.directory;
                }
            }

            if (_type == null)
            {
                _type = FileType.notpresent;
            }
        }
        return _type;
    }

    public boolean isCollection()
    {
        if (null != _file && getType() == FileType.directory)
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
        return _file != null && getType() == FileType.file;
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
        {
            _file.createNewFile();
            resetMetadata(FileType.file);
        }
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
            resetMetadata(FileType.file);
            return len;
        }
        finally
        {
            IOUtils.closeQuietly(fos);
        }
    }

    private void resetMetadata(FileType type)
    {
        _length = UNKNOWN;
        _lastModified = UNKNOWN;
        _type = type;
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


    public List<Resource> list()
    {
        List<String> names = listNames();
        ArrayList<Resource> infos = new ArrayList<Resource>(names.size());
        for (String name : names)
        {
            Resource r = find(name);
            if (null != r && !(r instanceof WebdavResolverImpl.UnboundResource))
                infos.add(r);
        }
        return infos;
    }


    public Resource find(String name)
    {
        return new FileSystemResource(this, name);
    }

    
    public long getCreated()
    {
        return getLastModified();
    }


    public long getLastModified()
    {
        if (null != _file)
        {
            if (_lastModified == UNKNOWN)
            {
                _lastModified = _file.lastModified();
//                _lastModified = 0;
            }
            return _lastModified;
        }
        if (_c != null && _c.getCreated() != null)
            return _c.getCreated().getTime();
        return Long.MIN_VALUE;
    }


    public long getContentLength()
    {
        if (!isFile() || _file == null)
            return 0;
        if (_length == UNKNOWN)
        {
            _length = _file.length();
//            _length = 0;
        }
        return _length;
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
            return Collections.emptyList();
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
