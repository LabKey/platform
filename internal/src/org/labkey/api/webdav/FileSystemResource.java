/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditLogEvent;
import org.apache.commons.io.IOUtils;
import org.labkey.api.util.Path;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpData;

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
    private static final Logger _log = Logger.getLogger(FileSystemResource.class);

    protected File _file;
    String _name = null;
    Resource _folder;   // containing controller used for canList()
    protected Boolean _shouldIndex = null; // null means ask parent

    private FileType _type;
    private long _length = UNKNOWN;
    private long _lastModified = UNKNOWN;

    private static final long UNKNOWN = -1;
    private boolean _dataQueried = false;
    private ExpData _data;
    private boolean _mergeFromParent;

    private enum FileType { file, directory, notpresent }

    protected FileSystemResource(Path path)
    {
        super(path);
        setSearchCategory(SearchService.fileCategory);
        setSearchProperty(SearchService.PROPERTY.displayTitle, path.getName());
        setSearchProperty(SearchService.PROPERTY.searchTitle, path.getName());   // TODO: Add stripped version
    }

    protected FileSystemResource(Path folder, String name)
    {
        this(folder.append(name));
    }

    public FileSystemResource(Resource folder, String name, File file, SecurityPolicy policy)
    {
        this(folder, name, file, policy, false);
    }

    public FileSystemResource(Resource folder, String name, File file, SecurityPolicy policy, boolean mergeFromParent)
    {
        this(folder.getPath(), name);
        _folder = folder;
        _name = name;
        _policy = policy;
        _file = FileUtil.canonicalFile(file);
        _mergeFromParent = mergeFromParent;
    }

    public FileSystemResource(FileSystemResource folder, String relativePath)
    {
        this(folder.getPath(), relativePath);
        _folder = folder;
        _policy = folder._policy;
        _file = new File(folder._file, relativePath);
    }

    public FileSystemResource(Path path, File file, SecurityPolicy policy)
    {
        this(path);
        _file = file;
        _policy = policy;
    }

    public String getName()
    {
        return null == _name ? super.getName() : _name;
    }

    @Override
    public String getContainerId()
    {
        if (null != _containerId)
            return _containerId;
        return null==_folder ? null : _folder.getContainerId();
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
            if (!_file.getName().contains("."))
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
        FileType type = getType();
        if (null != type)
            return type == FileType.directory;
        return exists() && getPath().isDirectory();
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


    public long copyFrom(User user, FileStream is) throws IOException
    {
        if (!canWrite(user) || null == _file)
            return -1;

        if (!_file.exists())
        {
            _file.createNewFile();
            resetMetadata(FileType.file);
        }

        FileOutputStream fos = new FileOutputStream(_file);
        try
        {
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

    /**
     * To improve backwards compatibility with existing external processes, copy files from
     * the original location on the file system into the @files directory if they don't exist
     * in the @files directory or are newer.
     */
    private void mergeFiles()
    {
        if (_mergeFromParent && isCollection())
        {
            File[] parentFiles = _file.getParentFile().listFiles((FileFilter)FileFileFilter.FILE);
            if (parentFiles != null)
            {
                for (File parentFile : parentFiles)
                {
                    long lastModified = parentFile.lastModified();
                    File destFile = new File(_file, parentFile.getName());
                    // lastModified() returns 0 if the file doesn't exist, so this check works in either case
                    if (destFile.lastModified() < parentFile.lastModified())
                    {
                        _log.info("Detected updated file '" + destFile + "', moving to @files subdirectory");
                        try
                        {
                            if (destFile.exists() && !destFile.delete())
                            {
                                _log.warn("Failed to delete outdated file '" + destFile + "' from @files location");
                            }
                            FileUtils.moveFile(parentFile, destFile);
                            // Preserve the file's timestamp
                            if (!destFile.setLastModified(lastModified))
                            {
                                _log.warn("Filed to set timestamp on " + destFile);
                            }
                        }
                        catch (IOException e)
                        {
                            // Don't fail the request because of this error
                            _log.warn("Unable to copy file '" + parentFile + "' from legacy location to new @files location");
                        }
                    }
                    else
                    {
                        if (!parentFile.delete())
                        {
                            _log.warn("Failed to delete file '" + parentFile + "' from legacy location");
                        }
                    }
                }
            }
        }
    }

    @NotNull
    public List<String> listNames()
    {
        if (!isCollection())
            return Collections.emptyList();
        mergeFiles();
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
        mergeFiles();
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

    @Override
    public User getCreatedBy()
    {
        ExpData data = getExpData();
        return data == null ? super.getCreatedBy() : data.getCreatedBy();
    }

    @Override
    public User getModifiedBy()
    {
        ExpData data = getExpData();
        return data == null ? super.getCreatedBy() : data.getModifiedBy();
    }

    @NotNull @Override
    public List<NavTree> getActions()
    {
        if (_file != null)
        {
            ExpData data = getExpData();
            if (data != null)
            {
                ActionURL url = data.findDataHandler().getContentURL(data.getContainer(), data);
                List<? extends ExpRun> runs = ExperimentService.get().getRunsUsingDatas(Collections.singletonList(data));
                List<NavTree> result = new ArrayList<NavTree>();
                for (ExpRun run : runs)
                {
                    result.add(new NavTree(run.getName(), url));
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    private ExpData getExpData()
    {
        if (!_dataQueried)
        {
            _data = ExperimentService.get().getExpDataByURL(_file, getContainer());
            _dataQueried = true;
        }
        return _data;
    }

    Container getContainer()
    {
        String id = getContainerId();
        if (null == id)
            return null;
        return ContainerManager.getForId(id);
    }

    @Override
    public boolean shouldIndex()
    {
        if (null != _shouldIndex)
            return _shouldIndex.booleanValue();
        if (null != _folder)
            return _folder.shouldIndex();
        return super.shouldIndex();
    }
}
