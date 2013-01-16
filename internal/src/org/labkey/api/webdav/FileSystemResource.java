/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
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
import org.labkey.api.files.FileContentEmailPref;
import org.labkey.api.files.FileContentEmailPrefFilter;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FileUrls;
import org.labkey.api.flow.api.FlowService;
import org.labkey.api.notification.EmailMessage;
import org.labkey.api.notification.EmailService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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

    private long _length = UNKNOWN;
    private long _lastModified = UNKNOWN;

    private static final long UNKNOWN = -1;
    private boolean _dataQueried = false;
    private List<ExpData> _data;
    private boolean _mergeFromParent;
    private Map<String, String> _customProperties;

    private enum FileType { file, directory, notpresent }

    protected FileSystemResource(Path path)
    {
        super(path);
        setSearchCategory(SearchService.fileCategory);
        setSearchProperty(SearchService.PROPERTY.displayTitle, path.getName());
        setSearchProperty(SearchService.PROPERTY.searchTitle, FileUtil.getSearchTitle(path.getName()));
    }

    protected FileSystemResource(Path folder, String name)
    {
        this(folder.append(name));
    }

    public FileSystemResource(WebdavResource folder, String name, File file, SecurityPolicy policy, boolean mergeFromParent)
    {
        this(folder.getPath(), name);
        _folder = folder;
        _name = name;
        setPolicy(policy);
        _files = Collections.singletonList(new FileInfo(FileUtil.canonicalFile(file)));
        _mergeFromParent = mergeFromParent;
    }

    public FileSystemResource(FileSystemResource folder, String relativePath)
    {
        this(folder.getPath(), relativePath);
        _folder = folder;
        setPolicy(folder.getPolicy());

        _files = new ArrayList<FileInfo>(folder._files.size());
        for (FileInfo file : folder._files)
        {
            _files.add(new FileInfo(new File(file.getFile(), relativePath)));
        }
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

    public File getFile()
    {
        if (_files == null)
        {
            return null;
        }
        for (FileInfo file : _files)
        {
            if (file.getType() != FileType.notpresent)
            {
                return file.getFile();
            }
        }
        return _files.get(0).getFile();
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


    public long copyFrom(User user, FileStream is) throws IOException
    {
        File file = getFile();
        if (!file.exists())
        {
            file.getParentFile().mkdirs();
            file.createNewFile();
            resetMetadata();
        }

        FileOutputStream fos = new FileOutputStream(file);
        try
        {
            long len = FileUtil.copyData(is.openInputStream(), fos);
            fos.getFD().sync();
            resetMetadata();
            return len;
        }
        finally
        {
            IOUtils.closeQuietly(fos);
        }
    }

    private void resetMetadata()
    {
        if (_files != null)
        {
            for (FileInfo file : _files)
            {
                file._type = null;
            }
        }
        _length = UNKNOWN;
        _lastModified = UNKNOWN;
    }

    /**
     * To improve backwards compatibility with existing external processes, copy files from
     * the original location on the file system into the @files directory if they don't exist
     * in the @files directory or are newer.
     */
    final protected void mergeFilesIfNeeded()
    {
        if (_mergeFromParent && isCollection())
        {
            mergeFiles(getFile());
        }
    }

    public static void mergeFiles(File atFilesDirectory)
    {
        File[] parentFiles = atFilesDirectory.getParentFile().listFiles((FileFilter) FileFileFilter.FILE);
        if (parentFiles != null)
        {
            for (File parentFile : parentFiles)
            {
                long lastModified = parentFile.lastModified();
                File destFile = new File(atFilesDirectory, parentFile.getName());
                // lastModified() returns 0 if the file doesn't exist, so this check works in either case
                if (destFile.lastModified() < parentFile.lastModified())
                {
                    _log.info("Detected updated file '" + parentFile + "', moving to @files subdirectory");
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

    @NotNull
    public Collection<String> listNames()
    {
        if (!isCollection())
            return Collections.emptyList();
        mergeFilesIfNeeded();
        ArrayList<String> list = new ArrayList<String>();
        if (_files != null)
        {
            for (FileInfo file : _files)
            {
                if (file.getType() == FileType.directory)
                {
                    File[] children = file.getFile().listFiles();
                    if (null != children)
                    {
                        for (File child: children)
                            list.add(child.getName());
                    }
                }
            }
        }
        Collections.sort(list);
        return list;
    }


    public Collection<WebdavResource> list()
    {
        Collection<String> names = listNames();
        ArrayList<WebdavResource> infos = new ArrayList<WebdavResource>(names.size());
        for (String name : names)
        {
            WebdavResource r = find(name);
            if (null != r && !(r instanceof WebdavResolverImpl.UnboundResource))
                infos.add(r);
        }
        return infos;
    }


    public WebdavResource find(String name)
    {
        mergeFilesIfNeeded();
        return new FileSystemResource(this, name);
    }

    
    public long getCreated()
    {
        return getLastModified();
    }


    public long getLastModified()
    {
        File file = getFile();
        if (null != file)
        {
            if (_lastModified == UNKNOWN)
            {
                _lastModified = file.lastModified();
            }
            return _lastModified;
        }
        return Long.MIN_VALUE;
    }


    public long getContentLength()
    {
        if (!isFile() || getFile() == null)
            return 0;
        if (_length == UNKNOWN)
        {
            _length = getFile().length();
        }
        return _length;
    }

    public boolean canRead(User user, boolean forRead)
    {
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
            _log.warn(user.getEmail() + " attempted to read file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
            return false;
        }
        return canReadPerm;
    }


    public boolean canWrite(User user, boolean forWrite)
    {
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
            _log.warn(user.getEmail() + " attempted to write file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
            return false;
        }
        return canWritePerm;
    }


    public boolean canCreate(User user, boolean forCreate)
    {
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
            _log.warn(user.getEmail() + " attempted to write file that is not readable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
            return false;
        }
        return canCreatePerm;
    }


    public boolean canDelete(User user, boolean forDelete)
    {
        if (!super.canDelete(user, forDelete) || !hasFileSystem())
            return false;
        File f = getFile();
        if (null == f)
            return false;
        if (!f.canWrite())
        {
            if (forDelete)
                _log.warn(user.getEmail() + " attempted to delete file that is not writable by LabKey Server.  This may be a configuration problem. file: " + f.getPath());
            return false;
        }
        // can't delete if already processed
        return getActions(user).isEmpty();
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
        if (file == null || (null != user && !canDelete(user, true)))
            return false;

        try {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentDirectory dir;
            dir = svc.getMappedAttachmentDirectory(getContainer(), false);

            if (dir != null)
            {
                // legacy support for files added through the narrow files web part but deleted through
                // the newer file browser (issue: 11396). This is the difference between files stored through
                // webdav or via the attachments service.
                //
                Attachment attach = AttachmentService.get().getAttachment(dir, file.getName());
                if (attach != null)
                {
                    AttachmentService.get().deleteAttachment(dir, file.getName(), null);
                    return true;
                }
            }
        }
        catch (Exception e)
        {
            _log.error(e);
        }
        return file.delete();
    }

    @NotNull
    public Collection<WebdavResolver.History> getHistory()
    {
        File file = getFile();
        SimpleFilter filter = new SimpleFilter("EventType",  "FileSystem"); // FileSystemAuditViewFactory.EVENT_TYPE);
        filter.addCondition("Key1", file.getParent());
        filter.addCondition("Key2", file.getName());
        List<AuditLogEvent> logs = AuditLogService.get().getEvents(filter);
        if (null == logs)
            return Collections.emptyList();
        List<WebdavResolver.History> history = new ArrayList<WebdavResolver.History>(logs.size());
        for (AuditLogEvent e : logs)
            history.add(new HistoryImpl(e.getCreatedBy().getUserId(), e.getCreated(), e.getComment(), null));
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

        List<ExpData> datas = getExpData();

        List<NavTree> result = new ArrayList<NavTree>();
        Set<Integer> runids = new HashSet<Integer>();

        for (ExpData data : datas)
        {
            if (data == null || !data.getContainer().hasPermission(user, ReadPermission.class))
                continue;

            ActionURL dataURL = data.findDataHandler().getContentURL(data.getContainer(), data);
            List<? extends ExpRun> runs = ExperimentService.get().getRunsUsingDatas(Collections.singletonList(data));

            for (ExpRun run : runs)
            {
                if (!run.getContainer().hasPermission(user, ReadPermission.class))
                    continue;
                if (!runids.add(run.getRowId()))
                    continue;

                String runURL = dataURL == null ? LsidManager.get().getDisplayURL(run.getLSID()) : dataURL.toString();
                String actionName;

                if (!run.getName().equals(data.getName()))
                {
                    actionName = run.getName() + " (" + run.getProtocol().getName() + ")";
                }
                else
                {
                    actionName = run.getProtocol().getName();
                }

                result.add(new NavTree(actionName, runURL));
            }
        }
        return result;
    }
    

    private List<ExpData> getExpData()
    {
        if (!_dataQueried)
        {
            try
            {
                String fileURL = getFile().toURI().toURL().toString();
                List<ExpData> list = new LinkedList<ExpData>();

                FlowService fs = ServiceRegistry.get(FlowService.class);
                if (null != fs)
                {
                    List<ExpData> f = fs.getExpDataByURL(fileURL, getContainer());
                    list.addAll(f);
                }
                ExperimentService.Interface es = ExperimentService.get();
                if (null != es)
                {
                    ExpData d = es.getExpDataByURL(fileURL, null);
                    if (null != d)
                        list.add(d);
                }
                _data = list;
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
        if (null != _shouldIndex)
            return _shouldIndex.booleanValue();
        if (null != _folder)
            return _folder.shouldIndex();
        return super.shouldIndex();
    }

    @Override
    public Map<String, String> getCustomProperties(User user)
    {
        if (_customProperties == null)
        {
            _customProperties = new HashMap<String, String>();
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
                    Map<String, Object> keys = Collections.singletonMap(ExpDataTable.Column.DataFileUrl.name(), (Object)url);
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
        String dir;
        String name;
        String state = message;
        File f = getFile();
        if (f != null)
        {
            dir = f.getParent();
            name = f.getName();
        }
        else
        {
            Resource parent = parent();
            dir = parent == null ? "" : parent.getPath().toString();
            name = getName();
        }

        // translate the actions into a more meaningful message
        if ("created".equalsIgnoreCase(message))
        {
            message = "File uploaded to " + getContainer().getContainerNoun() + ": " + getContainer().getPath();
        }
        else if ("deleted".equalsIgnoreCase(message))
        {
            message = "File deleted from " + getContainer().getContainerNoun() + ": " + getContainer().getPath();
        }
        else if ("replaced".equalsIgnoreCase(message))
            message = "File replaced in " + getContainer().getContainerNoun() + ": " + getContainer().getPath();

//        String subject = "File Management Tool notification: " + message;

        AuditLogEvent event = new AuditLogEvent();

        event.setCreatedBy(context.getUser());
        event.setContainerId(getContainer().getId());
        event.setEventType(FileSystemAuditViewFactory.EVENT_TYPE);
        event.setKey1(dir);
        event.setKey2(name);
        event.setKey3(getPath().toString());
        event.setComment(message);

        AuditLogService.get().addEvent(event);

        if ("created".equalsIgnoreCase(state))
        {
            WebdavService.get().fireWebdavCreated(this, getContainer(), context.getUser());
        }
        else if ("deleted".equalsIgnoreCase(state))
        {
            WebdavService.get().fireWebdavDeleted(this, getContainer(), context.getUser());
        }

        //AuditLogService.get().addEvent(context.getUser(), getContainer(), FileSystemAuditViewFactory.EVENT_TYPE, dir, name, message);
/*
        if (context instanceof ViewContext)
            sendNotificationEmail((ViewContext)context, message, subject);
        else
        {
            ViewContext viewContext = HttpView.currentContext();
            if (viewContext != null)
                sendNotificationEmail(viewContext, message, subject);
        }
*/
    }

    private void sendNotificationEmail(ViewContext context, String message, String subject)
    {
        try {
            EmailService.I svc = EmailService.get();
            User[] users = svc.getUsersWithEmailPref(getContainer(), new FileContentEmailPrefFilter(FileContentEmailPref.INDIVIDUAL));

            if (users != null && users.length > 0)
            {
                FileEmailForm form = new FileEmailForm(this, message);
                List<EmailMessage> messages = new ArrayList<EmailMessage>();

                form.setUrlEmailPrefs(PageFlowUtil.urlProvider(FileUrls.class).urlFileEmailPreference(getContainer()));
                form.setUrlFileBrowser(PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer()));
                form.setContainerPath(getContainer().getPath());

                for (User user : users)
                {
                    EmailMessage msg = svc.createMessage(LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress(),
                            new String[]{user.getEmail()}, subject);

                    msg.addContent(EmailMessage.contentType.HTML, context,
                            new JspView<FileEmailForm>("/org/labkey/api/webdav/view/fileEmailNotify.jsp", form));
                    msg.addContent(EmailMessage.contentType.PLAIN, context,
                            new JspView<FileEmailForm>("/org/labkey/api/webdav/view/fileEmailNotifyPlain.jsp", form));

                    messages.add(msg);
                }
                // send messages in bulk
                svc.sendMessage(messages.toArray(new EmailMessage[messages.size()]), context == null ? null : context.getUser(), getContainer());
             }
       }
        catch (Exception e)
        {
            // Don't fail the request because of this error
            _log.warn("Unable to send email for the file notification: " + e.getMessage());
        }
    }

    public static class FileEmailForm
    {
        private String _action;
        private WebdavResource _resource;
        private ActionURL _urlEmailPrefs;
        private ActionURL _urlFileBrowser;
        private String _containerPath;

        public ActionURL getUrlEmailPrefs()
        {
            return _urlEmailPrefs;
        }

        public void setUrlEmailPrefs(ActionURL urlEmailPrefs)
        {
            _urlEmailPrefs = urlEmailPrefs;
        }

        public ActionURL getUrlFileBrowser()
        {
            return _urlFileBrowser;
        }

        public void setUrlFileBrowser(ActionURL urlFileBrowser)
        {
            _urlFileBrowser = urlFileBrowser;
        }

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }

        public FileEmailForm(WebdavResource resource, String action)
        {
            _resource = resource;
            _action = action;
        }

        public String getAction()
        {
            return _action;
        }

        public WebdavResource getResource()
        {
            return _resource;
        }
    }

    protected static class FileInfo
    {
        private File _file;
        private FileType _type;
        private long _length = UNKNOWN;
        private long _lastModified = UNKNOWN;

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
    }
}
