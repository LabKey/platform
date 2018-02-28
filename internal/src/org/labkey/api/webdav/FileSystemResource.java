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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.flow.api.FlowService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.SecurityLogger;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.CanSeeAuditLogRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* User: matthewb
* Date: Oct 21, 2008
* Time: 10:00:49 AM
*
*   Base class for file-system based resources
*/
public class FileSystemResource extends AbstractWebdavResource
{
    private static final Logger _log = Logger.getLogger(FileSystemResource.class);
    public static final String URL_COL_PREFIX = "_labkeyurl_";

    protected List<FileInfo> _files;
    String _name = null;
    WebdavResource _folder;   // containing controller used for canList()
    protected Boolean _shouldIndex = null; // null means ask parent

    private boolean _dataQueried = false;
    private List<ExpData> _data;
    private Map<String, String> _customProperties;

    private enum FileType { file, directory, notpresent }

    protected FileSystemResource(Path path)
    {
        super(path);
        setSearchCategory(SearchService.fileCategory);
        setSearchProperty(SearchService.PROPERTY.title, path.getName());
        setSearchProperty(SearchService.PROPERTY.keywordsMed, FileUtil.getSearchKeywords(path.getName()));
    }

    protected FileSystemResource(Path folder, String name)
    {
        this(folder.append(name));
    }

    public FileSystemResource(WebdavResource folder, String name, File file, SecurityPolicy policy)
    {
        this(folder.getPath(), name);
        _folder = folder;
        _name = name;
        setPolicy(policy);
        _files = Collections.singletonList(new FileInfo(FileUtil.getAbsoluteCaseSensitiveFile(file)));
    }

    public FileSystemResource(FileSystemResource folder, String relativePath)
    {
        this(folder.getPath(), relativePath);
        _folder = folder;
        setPolicy(folder.getPolicy());

        _files = new ArrayList<>(folder._files.size());
        _files.addAll(folder._files.stream()
                .map(file -> new FileInfo(new File(file.getFile(), relativePath)))
                .collect(Collectors.toList()));
    }

    public FileSystemResource(Path path, File file, SecurityPolicy policy)
    {
        this(path);
        _files = Collections.singletonList(new FileInfo(file));
        setPolicy(policy);
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


    @Override
    protected void setPolicy(SecurityPolicy policy)
    {
        super.setPolicy(policy);
        setSearchProperty(SearchService.PROPERTY.securableResourceId, policy.getResourceId());
    }


    public boolean exists()
    {
        if (_files == null)
        {
            // Special case
            return true;
        }

        return getType() != FileType.notpresent;
    }


    private FileType getType()
    {
        if (_files == null)
        {
            return FileType.notpresent;
        }

        for (FileInfo file : _files)
        {
            if (file.getType() != FileType.notpresent)
            {
                return file.getType();
            }
        }
        return FileType.notpresent;
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
        return _files != null && getType() == FileType.file;
    }


    protected FileInfo getFileInfo()
    {
        if (_files == null || _files.isEmpty())
        {
            return null;
        }
        for (FileInfo file : _files)
        {
            if (file.getType() != FileType.notpresent)
            {
                return file;
            }
        }
        return _files.get(0);
    }


    public File getFile()
    {
        FileInfo f = getFileInfo();
        if (null == f)
            return null;
        return f.getFile();
    }


    public FileStream getFileStream(User user) throws IOException
    {
        if (!canRead(user, true))
            return null;
        if (null == _files || !exists())
            return null;
        return new FileStream.FileFileStream(getFile());
    }


    public InputStream getInputStream(User user) throws IOException
    {
        if (!canRead(user, true))
            return null;
        if (null == _files || !exists())
            return null;
        return new FileInputStream(getFile());
    }

    @Override
    public String getAbsolutePath(User user)
    {
        if (SecurityManager.canSeeFilePaths(getContainer(), user))
        {
            File file = getFile();
            return null != file ? file.getAbsolutePath() : null;
        }
        return null;
    }

    public long copyFrom(User user, FileStream is) throws IOException
    {
        File file = getFile();
        boolean created = false;
        if (!file.exists())
        {
            file.getParentFile().mkdirs();
            try
            {
                file.createNewFile();
                created = true;
            }
            catch (IOException x)
            {
                throw new ConfigurationException("Couldn't create file on server.", x);
            }
            resetMetadata();
        }

        try (FileOutputStream fos = new FileOutputStream(file))
        {
            long len = FileUtil.copyData(is.openInputStream(), fos);
            fos.getFD().sync();
            resetMetadata();
            return len;
        }
        catch (IOException x)
        {
            // if InputStream was unexpectedly closed, (e.g. browser closed) try to clean up if we created this file
            if (created)
                file.delete();
            throw x;
        }
    }


    @Override
    public void moveFrom(User user, WebdavResource src) throws IOException
    {
        super.moveFrom(user, src);
        resetMetadata();
    }


    private void resetMetadata()
    {
        if (_files != null)
        {
            for (FileInfo file : _files)
            {
                file._attributes = null;
            }
        }
    }

    @NotNull
    public Collection<String> listNames()
    {
        if (!isCollection())
            return Collections.emptyList();
        Set<String> result = new TreeSet<>();
        if (_files != null)
        {
            _files.stream()
                    .filter(file -> file.getType() == FileType.directory)
                    .forEach(file -> {
                        File[] children = file.getFile().listFiles();
                        if (null != children)
                        {
                            for (File child : children)
                                result.add(child.getName());
                        }
                    });
        }
        return result;
    }


    public Collection<WebdavResource> list()
    {
        Collection<String> names = listNames();
        ArrayList<WebdavResource> resources = new ArrayList<>(names.size());
        for (String name : names)
        {
            WebdavResource r = find(name);
            if (null != r && !(r instanceof WebdavResolverImpl.UnboundResource))
                resources.add(r);
        }
        return resources;
    }


    public WebdavResource find(String name)
    {
        return new FileSystemResource(this, name);
    }

    
    public long getCreated()
    {
        return getLastModified();
    }


    public long getLastModified()
    {
        FileInfo fi = getFileInfo();
        if (null != fi)
        {
            return fi.getLastModified();
        }
        return Long.MIN_VALUE;
    }


    public long getContentLength()
    {
        FileInfo fi = getFileInfo();
        if (null == fi || FileType.file != fi.getType())
            return 0;
        return fi.getLength();
    }


    public boolean canRead(User user, boolean forRead)
    {
        try
        {
            SecurityLogger.indent(getPath() + " FileSystemResource.canRead()");
            boolean canReadPerm = super.canRead(user, forRead);
            if (!canReadPerm || !isFile() || !forRead)
                return canReadPerm;
            File f = getFile();
            if (null == f)
                return canReadPerm;

            // for real files that we are about to actually read, we want to
            // check that the OS will allow LabKey server to read the file
            // should always return true, unless there is a configuration problem
            if (!f.canRead())
            {
                SecurityLogger.log("File.canRead()==false",user,null,false);
                _log.warn(user.getEmail() + " attempted to read file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
                return false;
            }
            return canReadPerm;
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean canWrite(User user, boolean forWrite)
    {
        try
        {
            SecurityLogger.indent(getPath() + " FileSystemResource.canWrite()");
            boolean canWritePerm = super.canWrite(user, forWrite) && hasFileSystem();
            if (!canWritePerm || !isFile() || !forWrite)
                return canWritePerm;
            File f = getFile();
            if (null == f)
                return canWritePerm;

            // for real files that we are about to actually write, we want to
            // check that the OS will allow LabKey server to write the file
            // should always return true, unless there is a configuration problem
            if (!f.canWrite())
            {
                SecurityLogger.log("File.canWrite()==false",user,null,false);
                _log.warn(user.getEmail() + " attempted to write file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
                return false;
            }
            return canWritePerm;
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean canCreate(User user, boolean forCreate)
    {
        try
        {
            SecurityLogger.indent(getPath() + " FileSystemResource.canCreate()");
            boolean canCreatePerm = super.canCreate(user, forCreate) && hasFileSystem();
            if (!canCreatePerm || !isFile() || !forCreate)
                return canCreatePerm;
            File f = getFile();
            if (null == f)
                return canCreatePerm;

            // for real files that we are about to actually write, we want to
            // check that the OS will allow LabKey server to write the file
            // should always return true, unless there is a configuration problem
            if (!f.canWrite())
            {
                SecurityLogger.log("File.canWrite()==false",user,null,false);
                _log.warn(user.getEmail() + " attempted to write file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
                return false;
            }
            return canCreatePerm;
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean canDelete(User user, boolean forDelete, @Nullable List<String> message)
    {
        try
        {
            if (!super.canDelete(user, forDelete, message) || !hasFileSystem())
                return false;
            File f = getFile();
            if (null == f)
                return false;
            if (!f.canWrite())
            {
                SecurityLogger.log("File.canWrite()==false",user,null,false);
                if (forDelete)
                {
                    if (null != message)
                        message.add("File is not writable on server");
                    _log.warn(user.getEmail() + " attempted to delete file that is not writable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
                }
                return false;
            }
            // can't delete if already processed
            if (!getActions(user).isEmpty())
            {
                if (null != message)
                    message.add("File has been imported by an assay and may not be deleted.");
                return false;
            }
            return true;
        }
        finally
        {
            SecurityLogger.outdent();
        }
    }


    public boolean canRename(User user, boolean forRename)
    {
        return super.canRename(user, forRename);
    }

    public boolean canList(User user, boolean forRead)
    {
        return super.canRead(user, forRead) || (null != _folder && _folder.canList(user, forRead));
    }    

    private boolean hasFileSystem()
    {
        return _files != null;
    }


    public boolean delete(User user)
    {
        File file = getFile();
        if (file == null || (null != user && !canDelete(user, true, null)))
            return false;

        try {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir;
            dir = svc.getMappedAttachmentDirectory(getContainer(), false);
            String fileDirPath = file.getParent();

            // 23746: If file exists in parent with the same name it was deleted instead
            if (dir != null && fileDirPath != null && dir.getFileSystemDirectoryPath().toAbsolutePath().toString().equals(fileDirPath))
            {
                // legacy support for files added through the narrow files web part but deleted through
                // the newer file browser (issue: 11396). This is the difference between files stored through
                // webdav or via the attachments service.
                //
                Attachment attach = AttachmentService.get().getAttachment(dir, file.getName());
                if (attach != null)
                {
                    AttachmentService.get().deleteAttachment(dir, file.getName(), null);
                    return (AttachmentService.get().getAttachment(dir, file.getName()) == null);
                }
            }
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        boolean deleted = file.delete();
        if (!deleted)
            _log.warn("Unexpected file system error, could not delete file: " + file.getPath());
        return deleted;
    }


    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        File file = getFile();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts(FileSystemAuditProvider.COLUMN_NAME_DIRECTORY), file.getParent());
        filter.addCondition(FieldKey.fromParts(FileSystemAuditProvider.COLUMN_NAME_FILE), file.getName());

        // Allow all users to see history in the container
        HashSet<Role> roles = new HashSet<>();
        roles.add(RoleManager.getRole(ReaderRole.class));
        roles.add(RoleManager.getRole(CanSeeAuditLogRole.class));
        User user = new LimitedUser(UserManager.getGuestUser(), new int[0], roles, true);

        List<AuditTypeEvent> logs = AuditLogService.get().getAuditEvents(getContainer(), user, FileSystemAuditProvider.EVENT_TYPE, filter, null);
        if (null == logs)
            return Collections.emptyList();

        List<WebdavResolver.History> history = new ArrayList<>(logs.size());
        history.addAll(logs.stream()
                .map(e -> new HistoryImpl(e.getCreatedBy().getUserId(), e.getCreated(), e.getComment(), null))
                .collect(Collectors.toList()));
        return history;
    }


    @Override
    public User getCreatedBy()
    {
        List<ExpData> data = getExpData();
        return data != null && data.size() == 1 ? data.get(0).getCreatedBy() : super.getCreatedBy();
    }

    public String getDescription()
    {
        List<ExpData> data = getExpData();
        return data != null && data.size() == 1 ? data.get(0).getComment() : super.getDescription();
    }

    @Override
    public User getModifiedBy()
    {
        List<ExpData> data = getExpData();
        return data != null && data.size() == 1 ? data.get(0).getCreatedBy() : super.getModifiedBy();
    }


    @NotNull @Override
    public Collection<NavTree> getActions(User user)
    {
        if (!isFile())
            return Collections.emptyList();

        return getActionsHelper(user, getExpData());
    }
    

    protected List<ExpData> getExpData()
    {
        if (!_dataQueried)
        {
            try
            {
                String fileURL = getFile().toURI().toURL().toString();
                _data = getExpDatasHelper(fileURL, getContainer());
            }
            catch (MalformedURLException e) {}
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
        boolean shouldIndexFolder = true;
        FileType ft = getType();
        if (FileType.directory == ft)
        {
            if (null != _shouldIndex)
                shouldIndexFolder = _shouldIndex.booleanValue();
        }
        else if (FileType.file == ft)
        {
            if (null != _folder)
                shouldIndexFolder = _folder.shouldIndex();
        }
        else
            return false;

        return shouldIndexFolder && super.shouldIndex();
    }


    @Override
    public Map<String, String> getCustomProperties(User user)
    {
        if (_customProperties == null)
        {
            _customProperties = new HashMap<>();
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            ExpData data = svc.getDataObject(this, getContainer());

            if (null != data)
            {
                Set<String> customPropertyNames = new CaseInsensitiveHashSet();
                for (ObjectProperty prop : data.getObjectProperties().values())
                {
                    customPropertyNames.add(prop.getName() + "_displayvalue");                    
                    customPropertyNames.add(prop.getName());
                    customPropertyNames.add(URL_COL_PREFIX + prop.getName());
                }

                TableInfo ti = ExpSchema.TableType.Data.createTable(new ExpSchema(user, getContainer()), ExpSchema.TableType.Data.toString());
                QueryUpdateService qus = ti.getUpdateService();

                try
                {
                    File canonicalFile = FileUtil.getAbsoluteCaseSensitiveFile(this.getFile());
                    String url = canonicalFile.toURI().toURL().toString();
                    Map<String, Object> keys = Collections.singletonMap(ExpDataTable.Column.DataFileUrl.name(), url);
                    List<Map<String, Object>> rows = qus.getRows(user, getContainer(), Collections.singletonList(keys));

                    assert(rows.size() <= 1);

                    if (rows.size() == 1)
                    {
                        for (Map.Entry<String, Object> entry : rows.get(0).entrySet())
                        {
                            Object value = entry.getValue();

                            if (value != null)
                            {
                                String key = entry.getKey();

                                if (customPropertyNames.contains(key))
                                   _customProperties.put(key, String.valueOf(value));
                            }
                        }
                    }
                }
                catch (MalformedURLException e)
                {
                    throw new UnexpectedException(e);
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
                catch (RuntimeException re)
                {
                    throw re;
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        }

        return _customProperties;
    }

    @Override
    public void notify(ContainerUser context, String message)
    {
        addAuditEvent(new DefaultContainerUser(getContainer(), context.getUser()), message);
    }


    static final FileTime nullTime = FileTime.from(Long.MIN_VALUE,TimeUnit.MILLISECONDS);

    static final BasicFileAttributes doesNotExist = (BasicFileAttributes)Proxy.newProxyInstance(
            FileSystemResource.class.getClassLoader(),
            new Class[] {BasicFileAttributes.class},
            (proxy, method, args) -> {
                if (method.getReturnType() == Boolean.class)
                    return false;
                if (method.getReturnType() == FileTime.class)
                    return nullTime;
                if (method.getReturnType() == Long.class)
                    return 0;
                return null;
            });



    protected static class FileInfo
    {
        private File _file;
        BasicFileAttributes _attributes;

        public FileInfo(File file)
        {
            _file = file;
        }

        public File getFile()
        {
            return _file;
        }

        /**
         * Try to determine if this entry exists on disk, and its type (file or directory) with the minimum number of
         * java.io.File method calls. Assume that entries are likely to exist, and that files are likely to have extensions.
         * In most cases this reduces the number of java.io.File method calls to one to answer exists(), isDirectory(), and
         * isFile().
         */
        private FileType getType()
        {
            _init();
            if (null == _attributes)
                return null;
            if (doesNotExist == _attributes)
                return FileType.notpresent;
            if (_attributes.isRegularFile())
                return FileType.file;
            else
                return FileType.directory;
        }



        private long getLastModified()
        {
            _init();
            return null==_attributes ? Long.MIN_VALUE : _attributes.lastModifiedTime().toMillis();
        }


        private long getLength()
        {
            _init();
            return null==_attributes ? 0 : _attributes.size();
        }


        private void _init()
        {
            if (_file != null && _attributes == null)
            {
                try
                {
                    _attributes = Files.readAttributes(_file.toPath(), BasicFileAttributes.class);
                }
                catch (FileNotFoundException|InvalidPathException|FileSystemException x)
                {
                    _attributes = doesNotExist;
                }
                catch (IOException x)
                {
                    throw new UnexpectedException(x);
                }
            }
        }
    }
}
