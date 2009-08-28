/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.core.attachment;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.apache.commons.io.IOUtils;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.settings.AppProps;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractCollectionResource;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.core.webdav.FileSystemAuditViewFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.jetbrains.annotations.NotNull;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * User: adam
 * Date: Jan 3, 2007
 * Time: 7:13:28 PM
 */
public class AttachmentServiceImpl implements AttachmentService.Service, ContainerManager.ContainerListener
{
    private static Logger _log = Logger.getLogger(AttachmentServiceImpl.class);
    private static HashSet<String> _attachmentColumns = new HashSet<String>();
    private static MimeMap _mimeMap = new MimeMap();
    private static final String UPLOAD_LOG = ".upload.log";

    static
    {
        _attachmentColumns.add("Parent");
        _attachmentColumns.add("Container");
        _attachmentColumns.add("DocumentName");
        _attachmentColumns.add("DocumentSize");
        _attachmentColumns.add("DocumentType");
        _attachmentColumns.add("Created");
        _attachmentColumns.add("CreatedBy");
    }

    public AttachmentServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }

    public HttpView add(User user, AttachmentParent parent, List<AttachmentFile> files)
    {
        String message = null;

        if (!files.isEmpty())
        {
            try
            {
                addAttachments(user, parent, files);
                message = getErrorHtml(files);
            }
            catch (AttachmentService.DuplicateFilenameException e)
            {
                message = e.getMessage() + "<br><br>";
            }
            catch (IOException ioe)
            {
                message = ioe.getMessage() + "<br><br>";
            }
        }
        HttpView v = new RefreshParentView(message);

        DialogTemplate template = new DialogTemplate(v);
        template.getModelBean().setShowHeader(false);

        return template;
    }


    public HttpView delete(User user, AttachmentParent parent, String name) throws SQLException
    {
        Table.execute(coreTables().getSchema(), sqlDelete(), new Object[]{parent.getEntityId(), name});
        if (parent instanceof AttachmentDirectory)
        {
            boolean exists = false;
            File parentDir = null;
            try
            {
                parentDir = ((AttachmentDirectory) parent).getFileSystemDirectory();
                exists = parentDir.exists();
            }
            catch (AttachmentService.MissingRootDirectoryException e)
            {
                _log.warn(e.getMessage());
            }
            if (exists)
            {
                File file = new File(parentDir, name);
                if (file.exists())
                {
                    logFileAction(parentDir, name, FileAction.DELETE, user);
                    moveToDeleted(file);
                }
            }
        }
        addAuditEvent(user, parent, name, "The attachment: " + name + " was deleted");
        return new RefreshParentView();
    }


    public void download(HttpServletResponse response, AttachmentParent parent, String filename) throws ServletException, IOException
    {
        if (null == filename || 0 == filename.length())
            HttpView.throwNotFound();

        boolean asAttachment = true;
        String mime = _mimeMap.getContentTypeFor(filename);
        if (null != mime && mime.startsWith("image/"))
            asAttachment = false;

        response.reset();
        writeDocument(new ResponseWriter(response), parent, filename, asAttachment);
        addAuditEvent(null, parent, filename, "The attachment: " + filename + " was downloaded");
    }

    private void addAuditEvent(User user, AttachmentParent parent, String filename, String comment)
    {
        if (user == null)
        {
            try {
                ViewContext context = HttpView.currentContext();
                if (context != null)
                    user = context.getUser();
            }
            catch (RuntimeException e){}
        }
        if (user != null && parent != null)
        {
            {
            AuditLogEvent event = new AuditLogEvent();
            event.setEventType(AttachmentService.ATTACHMENT_AUDIT_EVENT);
            event.setCreatedBy(user);
            event.setEntityId(parent.getEntityId());
            event.setContainerId(parent.getContainerId());
            event.setKey1(filename);
            Container c = ContainerManager.getForId(parent.getContainerId());
            if (c != null && c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setComment(comment);
            AuditLogService.get().addEvent(event);
            }

            if (parent instanceof AttachmentDirectory)
            {
                AuditLogEvent event = new AuditLogEvent();
                event.setEventType(FileSystemAuditViewFactory.EVENT_TYPE);
                event.setCreatedBy(user);
                event.setContainerId(parent.getContainerId());
                try
                {
                    event.setKey1(((AttachmentDirectory)parent).getFileSystemDirectory().getPath());
                }
                catch (AttachmentService.MissingRootDirectoryException ex)
                {
                    // UNDONE: AttachmentDirectory.getFileSystemPath()...
                    event.setKey1("path not found");
                }
                event.setKey2(filename);
                event.setComment(comment);
                AuditLogService.get().addEvent(event);
            }
        }
    }

    public HttpView getHistoryView(ViewContext context, AttachmentParent parent)
    {
        return AttachmentAuditViewFactory.createAttachmentView(context, parent);
    }

    public HttpView getAddAttachmentView(Container container, AttachmentParent parent)
    {
        return getAddAttachmentView(container, parent, null);
    }

    public HttpView getAddAttachmentView(Container container, AttachmentParent parent, BindException errors)
    {
        HttpView view = new AddAttachmentView(parent, errors);
        DialogTemplate template = new DialogTemplate(view);
        template.getModelBean().setTitle("Add Attachment");
        template.getModelBean().setShowHeader(false);
        return template;
    }

    public HttpView getConfirmDeleteView(Container container, ActionURL currentURL, AttachmentParent parent, String filename)
    {
        HttpView view = new ConfirmDeleteView(currentURL, container, parent, filename);
        DialogTemplate template = new DialogTemplate(view);
        template.getModelBean().setTitle("Delete Attachment?");
        template.getModelBean().setShowHeader(false);
        return template;
    }

    public AttachmentDirectory getMappedAttachmentDirectory(Container c, boolean createDir)
            throws AttachmentService.UnsetRootDirectoryException, AttachmentService.MissingRootDirectoryException
    {
        if (createDir) //force create
            getMappedDirectory(c, true);
        else if (null == getMappedDirectory(c, false))
            return null;

        FileSystemAttachmentParent parent;
        parent = new FileSystemAttachmentParent(c);
        return parent;
    }

    public FileSystemAttachmentParent getRegisteredDirectory(Container c, String name)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("Name", name);
        try
        {
            return Table.selectObject(coreTables().getMappedDirectories(), filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public FileSystemAttachmentParent getRegisteredDirectoryFromEntityId(Container c, String entityId)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("EntityId", entityId);
        try
        {
            return Table.selectObject(coreTables().getMappedDirectories(), filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public FileSystemAttachmentParent[] getRegisteredDirectories(Container c)
    {
        SimpleFilter filter = new SimpleFilter("Container", c);
        try
        {
            return Table.select(coreTables().getMappedDirectories(), Table.ALL_COLUMNS, filter, null, FileSystemAttachmentParent.class);
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public FileSystemAttachmentParent registerDirectory(Container c, String name, String path, boolean relative)
    {
        FileSystemAttachmentParent parent = new FileSystemAttachmentParent();
        parent.setContainer(c);
        if (null == name)
            name = path;
        parent.setName(name);
        parent.setPath(path);
        parent.setRelative(relative);
        //We do this because insert does not return new fields
        parent.setEntityid(GUID.makeGUID());

        try
        {
            FileSystemAttachmentParent ret = Table.insert(HttpView.currentContext().getUser(), coreTables().getMappedDirectories(), parent);
            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    c, ContainerManager.Property.AttachmentDirectory, null, ret);
            ContainerManager.firePropertyChangeEvent(evt);
            return ret;
        } catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void unregisterDirectory(Container c, String name)
    {
        FileSystemAttachmentParent parent = getRegisteredDirectory(c, name);
        SimpleFilter filter = new SimpleFilter("Container", c);
        filter.addCondition("Name", name);
        try
        {
            Table.delete(coreTables().getMappedDirectories(), filter);
            ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                    c, ContainerManager.Property.AttachmentDirectory, parent, null);
            ContainerManager.firePropertyChangeEvent(evt);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public AttachmentParent[] getNamedAttachmentDirectories(Container c) throws SQLException
    {
        return Table.select(coreTables().getMappedDirectories(), Table.ALL_COLUMNS, new SimpleFilter("Container", c), null, FileSystemAttachmentParent.class);
    }


    private static class RefreshParentView extends JspView<String>
    {
        private RefreshParentView()
        {
            this(null);
        }

        private RefreshParentView(String message)
        {
            super("/org/labkey/core/attachment/refreshParent.jsp", message);
            setFrame(FrameType.NONE);
        }
    }


    public static class AddAttachmentView extends JspView<String>
    {
        private AddAttachmentView(AttachmentParent parent, BindException errors)
        {
            super("/org/labkey/core/attachment/addAttachment.jsp", parent.getEntityId(), errors);
            setFrame(FrameType.NONE);
        }
    }


    public static class ConfirmDeleteView extends JspView
    {
        public DownloadURL deleteURL;
        public String name;

        private ConfirmDeleteView(ActionURL currentURL, Container c, AttachmentParent parent, String name)
        {
            super("/org/labkey/core/attachment/confirmDelete.jsp");
            String pageFlow = currentURL.getPageFlow();
            deleteURL = new DownloadURL(pageFlow, c.getPath(), parent.getEntityId(), name);
            deleteURL.setAction("deleteAttachment");
            this.name = name;
            setFrame(FrameType.NONE);
        }
    }


    public synchronized void addAttachments(User user, AttachmentParent parent, List<AttachmentFile> files) throws IOException
    {
        try
        {
            if (null == files || files.isEmpty())
                return;

            Set<String> filesToSkip = new TreeSet<String>();
            File fileLocation = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;

            for (AttachmentFile file : files)
            {
                if (exists(parent, file.getFilename()))
                {
                    filesToSkip.add(file.getFilename());
                    continue;
                }

                HashMap<String, Object> hm = new HashMap<String, Object>();
                if (null == fileLocation)
                    hm.put("Document", file);
                else
                {
                    FileOutputStream fos = null;
                    InputStream is = file.openInputStream();
                    try
                    {
                        File saveFile = new File(fileLocation, file.getFilename());
                        fos = new FileOutputStream(saveFile);
                        FileUtil.copyData(is, fos);
                        logFileAction(fileLocation, file.getFilename(), FileAction.UPLOAD, user);
                    }
                    finally
                    {
                        IOUtils.closeQuietly(fos);
                        file.closeInputStream();
                    }
                }
                hm.put("DocumentName", file.getFilename());
                hm.put("DocumentSize", file.getSize());
                hm.put("DocumentType", file.getContentType());
                hm.put("Parent", parent.getEntityId());
                hm.put("Container", parent.getContainerId());
                Table.insert(user, coreTables().getTableInfoDocuments(), hm);

                addAuditEvent(user, parent, file.getFilename(), "The attachment: " + file.getFilename() + " was added");
            }

            setAttachments(Collections.singletonList(parent));

            if (!filesToSkip.isEmpty())
                throw new AttachmentService.DuplicateFilenameException(filesToSkip);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public synchronized void insertAttachmentRecord(User user, AttachmentDirectory parent, AttachmentFile file)
            throws SQLException
    {
        long size;
        try
        {
            size = file.getSize();
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
        HashMap<String, Object> hm = new HashMap<String, Object>();
        hm.put("DocumentName", file.getFilename());
        hm.put("DocumentSize", size);
        hm.put("DocumentType", file.getContentType());
        hm.put("Parent", parent.getEntityId());
        hm.put("Container", parent.getContainerId());
        Table.insert(user, coreTables().getTableInfoDocuments(), hm);
    }
    

    public HttpView getErrorView(List<AttachmentFile> files, BindException errors, URLHelper returnURL)
    {
        boolean hasErrors = null != errors && errors.hasErrors();
        String errorHtml = getErrorHtml(files);      // TODO: Get rid of getErrorHtml() -- use errors collection

        if (null == errorHtml && !hasErrors)
            return null;

        try
        {
            return new ErrorView(errorHtml, errors, returnURL);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    private String getErrorHtml(List<AttachmentFile> files)
    {
        StringBuilder errorHtml = new StringBuilder();

        for (AttachmentFile file : files)
        {
            String error = file.getError();

            if (null != error)
                errorHtml.append(error).append("<br><br>");
        }

        if (errorHtml.length() > 0)
            return errorHtml.toString();
        else
            return null;
    }


    public static class ErrorView extends JspView<Object>
    {
        public String errorHtml;
        public URLHelper returnURL;

        private ErrorView(String errorHtml, BindException errors, URLHelper returnURL)
        {
            super("/org/labkey/core/attachment/showErrors.jsp", new Object(), errors);
            this.errorHtml = errorHtml;
            this.returnURL = returnURL;
        }
    }


    public void deleteAttachments(AttachmentParent parent) throws SQLException
    {
        Table.execute(coreTables().getSchema(), sqlCascadeDelete(), new Object[]{parent.getEntityId()});
        File parentDir = null;
        try
        {
            parentDir = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;
        }
        catch (AttachmentService.MissingRootDirectoryException ex)
        {
            /* */
        }
        if (null != parentDir && parentDir.exists())
        {
            File[] files = parentDir.listFiles();
            if (null != files)
                for (File attachmentFile : files)
                {
                    if (!attachmentFile.isDirectory() && !attachmentFile.getName().startsWith(".") && attachmentFile.exists())
                    {
                        moveToDeleted(attachmentFile);
                        logFileAction(parentDir, attachmentFile.getName(), FileAction.DELETE, HttpView.currentContext().getUser());
                        addAuditEvent(null, parent, attachmentFile.getName(), "The attachment: " + attachmentFile.getName() + " was deleted");
                    }
                }
        }
    }


    public void deleteAttachment(AttachmentParent parent, String name)
    {
        try
        {
            Table.execute(coreTables().getSchema(), sqlDelete(), new Object[]{parent.getEntityId(), name});
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        File parentDir = null;
        try
        {
            parentDir = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;
        }
        catch (AttachmentService.MissingRootDirectoryException ex)
        {
            /* no problem */
        }
        if (null != parentDir && parentDir.exists())
        {
            File attachmentFile = new File(parentDir, name);
            if (attachmentFile.exists())
            {
                moveToDeleted(attachmentFile);
                logFileAction(parentDir, name, FileAction.DELETE, HttpView.currentContext().getUser());
                addAuditEvent(null, parent, attachmentFile.getName(), "The attachment: " + attachmentFile.getName() + " was deleted");
            }
        }
    }

    private void moveToDeleted(File fileToMove)
    {
        if (!fileToMove.exists())
            return;
        File parent = fileToMove.getParentFile();

        File deletedDir = new File(parent, ".deleted");
        if (!deletedDir.exists())
            deletedDir.mkdir();

        File newLocation = new File(deletedDir, fileToMove.getName());
        if (newLocation.exists())
            recursiveDelete(newLocation);

        fileToMove.renameTo(newLocation);
    }

    private void recursiveDelete(File file)
    {
        if (file.isDirectory())
        {
            File[] files = file.listFiles();
            if (null != files)
                for (File child : files)
                    recursiveDelete(child);
        }

        file.delete();
    }


    public void renameAttachment(AttachmentParent parent, String oldName, String newName) throws IOException
    {
        File dir = null;
        File dest = null;
        File src = null;

        if (parent instanceof AttachmentDirectory)
        {
            dir = ((AttachmentDirectory)parent).getFileSystemDirectory();
            src = new File(dir,oldName);
            dest = new File(dir,newName);
            if (!src.exists())
                throw new FileNotFoundException(oldName);
            if (dest.exists())
                throw new AttachmentService.DuplicateFilenameException(newName);

            // make sure newName attachment doesn't exist. if it does exist, it's already orphaned
            deleteAttachment(parent, newName);
        }

        if (exists(parent, newName))
            throw new AttachmentService.DuplicateFilenameException(newName);

        try
        {
            Table.execute(coreTables().getSchema(), sqlRename(parent, oldName, newName));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        if (null != dir)
            src.renameTo(dest);
    }


    // Copies an attachment -- same container, same parent, but new name.  
    public void copyAttachment(User user, AttachmentParent parent, Attachment a, String newName) throws IOException
    {
        DatabaseAttachmentFile file = new DatabaseAttachmentFile(a);
        file.setFilename(newName);
        addAttachments(user, parent, Arrays.asList((AttachmentFile)file));
    }


    public List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException
    {
        List<AttachmentFile> files = new ArrayList<AttachmentFile>(attachments.size());

        for (Attachment attachment : attachments)
        {
            if (parent instanceof AttachmentDirectory)
            {
                File f = new File(((AttachmentDirectory)parent).getFileSystemDirectory(), attachment.getName());
                files.add(new FileAttachmentFile(f));
            }
            else
            {
                files.add(new DatabaseAttachmentFile(attachment));
            }
        }
        return files;
    }


    private boolean exists(AttachmentParent parent, String filename)
    {
        try
        {
            Integer count = Table.executeSingleton(coreTables().getSchema(), sqlExists(), new Object[]{parent.getEntityId(), filename}, Integer.class);
            return count.intValue() > 0;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public Attachment[] getAttachments(AttachmentParent parent)
    {
        Attachment[] attachments;

        try
        {
            attachments = Table.select(coreTables().getTableInfoDocuments(),
                    _attachmentColumns,
                    new SimpleFilter("Parent", parent.getEntityId()),
                    new Sort("+Created"),
                    Attachment.class
            );
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        File parentDir = null;
        try
        {
            parentDir = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;
        }
        catch (AttachmentService.MissingRootDirectoryException ex)
        {
            /* no problem */
        }
        if (null == parentDir || !parentDir.exists())
            return attachments;

        for (Attachment att : attachments)
            att.setFile(new File(parentDir, att.getName()));

        //OK, make sure that the list really reflects what is in the file system.
        List<Attachment> attList = new ArrayList<Attachment>();
        File[] fileList = parentDir.listFiles(new FileFilter()
        {
            public boolean accept(File file)
            {
                return !file.isDirectory() && !(file.getName().charAt(0) == '.') && !file.isHidden();
            }
        });

        Set<String> attachmentNames = new CaseInsensitiveHashSet();
        for (Attachment attachment : attachments)
        {
            attachmentNames.add(attachment.getName());
            attList.add(attachment);
        }

        if (null != fileList)
            for (File file : fileList)
            {
                if (!attachmentNames.contains(file.getName()))
                    attList.add(attachmentFromFile(parent, file));
            }

        return attList.toArray(new Attachment[attList.size()]);
    }


    public WebdavResolver.Resource getAttachmentResource(String path, AttachmentParent parent)
    {
        // NOTE parent does not supply ACL, but should?
        // acl = parent.getAcl()
        Container c = ContainerManager.getForId(parent.getContainerId());
        if (null == c)
            return null;

        return new AttachmentCollection(path, parent, c.getPolicy());
    }


    private Attachment attachmentFromFile(AttachmentParent parent, File file)
    {
        Attachment attachment = new Attachment();
        attachment.setParent(parent.getEntityId());
        attachment.setContainer(parent.getContainerId());
        attachment.setDocumentName(file.getName());
        attachment.setCreated(new Date(file.lastModified()));
        attachment.setFile(file);

        return attachment;
    }


    public Attachment getAttachment(AttachmentParent parent, String name)
    {
        Attachment[] attachments = getAttachments(parent);

        for (Attachment attachment : attachments)
            if (name.equals(attachment.getName()))
                return attachment;

        return null;
    }


    public void setAttachments(Collection<AttachmentParent> parents) throws SQLException
    {
        if (parents.size() == 0)
            return;
        
        List<String> parentIds = new ArrayList<String>(parents.size());

        for (AttachmentParent parent : parents)
            parentIds.add(parent.getEntityId());

        SimpleFilter filter = new SimpleFilter();
        filter.addInClause("Parent", parentIds);

        Attachment[] attachments;
        attachments = Table.select(coreTables().getTableInfoDocuments(),
                _attachmentColumns,
                filter,
                new Sort("Parent,Created"),
                Attachment.class
        );

        Map<String, List<Attachment>> parentToAttachments = new HashMap<String, List<Attachment>>();

        for (Attachment attachment : attachments)
        {
            List<Attachment> list = parentToAttachments.get(attachment.getParent());

            if (null == list)
            {
                list = new ArrayList<Attachment>();
                parentToAttachments.put(attachment.getParent(), list);
            }

            list.add(attachment);
        }

        for (AttachmentParent parent : parents)
        {
            List<Attachment> attachmentsForParent = parentToAttachments.get(parent.getEntityId());

            if (null != attachmentsForParent)
                parent.setAttachments(attachmentsForParent);
            else
                parent.setAttachments(Collections.<Attachment>emptyList());
        }
    }

    // Not used currently... formerly used by NAB.  Removed from AttachmentService interface but left here just in case...
    public InputStream getInputStream(Attachment attachment) throws ServletException, SQLException
    {
        InputStream attachmentStream;
        DbSchema schema = null;
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        boolean success = false;

        try
        {
            // we don't want a RowSet, so execute directly (not Table.executeQuery())
            schema = coreTables().getSchema();
            conn = schema.getScope().getConnection();
            stmt = Table.prepareStatement(conn, sqlDocument(), new Object[]{attachment.getParent(), attachment.getName()});
            rs = stmt.executeQuery();

            if (!rs.next())
                HttpView.throwNotFound();

            attachmentStream = rs.getBinaryStream("Document");

            success = true;
        }
        finally
        {
            // If anything above has failed (UnauthorizedException, SQLException, etc.) we close all resources now.
            // Otherwise, it's the responsibility of the caller to close the input stream that we return (which will
            // close all the resources).

            if (!success)
            {
                try { if (null != rs) rs.close(); } catch (SQLException e) {}
                try { if (null != stmt) stmt.close(); } catch (SQLException e) {}
                try { if (null != conn) schema.getScope().releaseConnection(conn); } catch (SQLException e) {}
            }
        }

        final DbSchema finalSchema = schema;
        final Connection finalConn = conn;
        final PreparedStatement finalStmt = stmt;
        final ResultSet finalRs = rs;
        return new ResourceInputStream(attachmentStream, new ResourceInputStream.ResourceCloser()
        {
            public void close()
            {
                try { if (null != finalRs) finalRs.close(); } catch (SQLException x) {}
                try { if (null != finalStmt) finalStmt.close(); } catch (SQLException x) {}
                try { if (null != finalConn) finalSchema.getScope().releaseConnection(finalConn); } catch (SQLException x) {}
            }
        });
    }


    public void containerCreated(Container c)
    {
        try
        {
            File dir = getMappedDirectory(c, false);
            //Don't try to create dir if root not configured.
            //But if we should have a directory, create it
            if (null != dir && !dir.exists())
                getMappedDirectory(c, true);
        }
        catch (AttachmentService.MissingRootDirectoryException ex)
        {
            /* */
        }
    }


    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
        ContainerManager.ContainerPropertyChangeEvent evt = (ContainerManager.ContainerPropertyChangeEvent)propertyChangeEvent;
        Container c = evt.container;

        switch (evt.property)
        {
        case Name:
        {
            //We don't rename webroots for a project. They are set manually per-project
            //If we move to a site-wide webroot option, projects below that root should be renamed
            if (c.isProject())
                return;

            String oldValue = (String) propertyChangeEvent.getOldValue();
            String newValue = (String) propertyChangeEvent.getNewValue();

            File location = null;
            try
            {
                location = getMappedDirectory(c, false);
            }
            catch (AttachmentService.MissingRootDirectoryException ex)
            {
                /* */
            }
            if (location == null)
                return;
            //Don't rely on container object. Seems not to point to the
            //new location even AFTER rename. Just construct new file paths
            File parentDir = location.getParentFile();
            File oldLocation = new File(parentDir, oldValue);
            File newLocation = new File(parentDir, newValue);
            if (newLocation.exists())
                moveToDeleted(newLocation);

            if (oldLocation.exists())
                oldLocation.renameTo(newLocation);
            break;
        }
        case Parent:
        {
            Container oldParent = (Container) propertyChangeEvent.getOldValue();
            File oldParentFile = null;
            try
            {
                oldParentFile = getMappedDirectory(oldParent, false);
            }
            catch (AttachmentService.MissingRootDirectoryException ex)
            {
                /* */
            }
            if (null == oldParentFile)
                return;
            File oldDir = new File(oldParentFile, c.getName());
            if (!oldDir.exists())
                return;

            File newDir = null;
            try
            {
                newDir = getMappedDirectory(c, false);
            }
            catch (AttachmentService.MissingRootDirectoryException ex)
            {
            }
            //Move stray content out of the way
            if (null != newDir && newDir.exists())
               moveToDeleted(newDir);

            oldDir.renameTo(newDir);
            break;
        }
        }
    }


    public File getWebRoot(Container c)
    {
        if (c == null)
            return null;
        
        Container project = c.getProject();
        if (null == project)
            return null;

        Map<String,String> m = PropertyManager.getProperties(project.getId(), "staticFile", false);
        if (null == m)
            return null;

        return null == m.get("root") ? null : new File(m.get("root"));
    }


    public void setWebRoot(Container c, File root)
    {
        Map<String,String> m = PropertyManager.getWritableProperties(0, c.getProject().getId(), "staticFile", true);
        String oldValue = m.get("root");
        try
        {
            if (null == root)
                m.remove("root");
            else
                m.put("root", root.getCanonicalPath());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        String newValue = m.get("root");
        PropertyManager.saveProperties(m);
        
        ContainerManager.ContainerPropertyChangeEvent evt = new ContainerManager.ContainerPropertyChangeEvent(
                c, ContainerManager.Property.WebRoot, oldValue, newValue);
        ContainerManager.firePropertyChangeEvent(evt);
    }


    private File getMappedDirectory(Container c, boolean create)
            throws AttachmentService.UnsetRootDirectoryException, AttachmentService.MissingRootDirectoryException
    {
        File root = getWebRoot(c);
        if (null == root)
        {
            if (create)
                throw new AttachmentService.UnsetRootDirectoryException(c.isRoot() ? c : c.getProject());
            else
                return null;
        }

        if (!root.exists())
        {
            if (create)
                throw new AttachmentService.MissingRootDirectoryException(c.isRoot() ? c : c.getProject(), root);
            else
                return null;
        }

        File dir;
        //Don't want the Project part of the path.
        if (c.isProject())
            dir = root;
        else
        {
            //Cut off the project name
            String extraPath = c.getPath();
            extraPath = extraPath.substring(c.getProject().getName().length() + 2);
            dir = new File(root, extraPath);
        }

        if (!dir.exists() && create)
            dir.mkdirs();

        return dir;
    }

    public void containerDeleted(Container c, User user)
    {
        File dir = null;
        try
        {
            dir = getMappedDirectory(c, false);

        }
        catch (Exception e)
        {
            _log.error("containerDeleted", e);
        }

        if (null != dir && dir.exists())
        {
            moveToDeleted(dir);
        }

        try
        {
            ContainerUtil.purgeTable(coreTables().getTableInfoDocuments(), c, null);
            ContainerUtil.purgeTable(coreTables().getMappedDirectories(), c, null);
        }
        catch (SQLException x)
        {
            _log.error("Purging attachments", x);
        }
    }


    public void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, boolean asAttachment) throws ServletException, IOException
    {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        DbSchema schema = coreTables().getSchema();

        try
        {
            // we don't want a RowSet, so execute directly (not Table.executeQuery())
            conn = schema.getScope().getConnection();
            if (null == parent.getEntityId())
                stmt = Table.prepareStatement(conn, sqlRootDocument(), new Object[]{name});
            else
                stmt = Table.prepareStatement(conn, sqlDocument(), new Object[]{parent.getEntityId(), name});
            rs = stmt.executeQuery();

            OutputStream out;
            InputStream s;
            if (parent instanceof AttachmentDirectory)
            {
                File parentDir = ((AttachmentDirectory) parent).getFileSystemDirectory();
                if (!parentDir.exists())
                    throw new NotFoundException("No parent directory for downloaded file " + name + ". Please contact an administrator.");
                File file = new File(parentDir, name);
                if (!file.exists())
                    throw new NotFoundException("Could not find file " + name);

                if (asAttachment)
                    writer.setContentDisposition("attachment; filename=\"" + name + "\"");
                s = new FileInputStream(file);
            }
            else
            {
                if (!rs.next())
                    HttpView.throwNotFound();

                writer.setContentType(rs.getString("DocumentType"));
                if (asAttachment)
                    writer.setContentDisposition("attachment; filename=\"" + name + "\"");

                int size = rs.getInt("DocumentSize");
                if (size > 0)
                    writer.setContentLength(size);

                s = rs.getBinaryStream("Document");
            }
            out = writer.getOutputStream();
            try
            {
                byte[] buf = new byte[4096];
                int r;
                while (0 < (r = s.read(buf)))
                    out.write(buf, 0, r);
            }
            finally
            {
                if (null != s)
                    s.close();
            }
        }
        catch (SQLException x)
        {
            throw new ServletException(x);
        }
        finally
        {
            try
            {
                if (null != rs) rs.close();
            }
            catch (Exception x)
            {
            }
            try
            {
                if (null != stmt) stmt.close();
            }
            catch (Exception x)
            {
            }
            try
            {
                if (null != conn) schema.getScope().releaseConnection(conn);
            }
            catch (Exception x)
            {
            }
        }
    }


    public InputStream getInputStream(AttachmentParent parent, String name) throws FileNotFoundException
    {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final DbSchema schema = coreTables().getSchema();

        try
        {
            // we don't want a RowSet, so execute directly (not Table.executeQuery())
            conn = schema.getScope().getConnection();
            if (null == parent.getEntityId())
                stmt = Table.prepareStatement(conn, sqlRootDocument(), new Object[]{name});
            else
                stmt = Table.prepareStatement(conn, sqlDocument(), new Object[]{parent.getEntityId(), name});
            rs = stmt.executeQuery();

            if (parent instanceof AttachmentDirectory)
            {
                File parentDir = ((AttachmentDirectory) parent).getFileSystemDirectory();
                if (!parentDir.exists())
                    throw new FileNotFoundException("No parent directory for downloaded file " + name + ". Please contact an administrator.");
                File file = new File(parentDir, name);
                stmt.close();
                stmt = null;
                rs.close();
                rs = null;
                return new FileInputStream(file);
            }
            else
            {
                if (!rs.next())
                    throw new FileNotFoundException(name);
                final int size = rs.getInt("DocumentSize");
                InputStream is = rs.getBinaryStream("Document");

                final Connection fconn = conn;
                final PreparedStatement  fstmt = stmt;
                final ResultSet frs = rs;
                InputStream ret = new FilterInputStream(is)
                {
                    public void close() throws IOException
                    {
                        ResultSetUtil.close(fstmt);
                        ResultSetUtil.close(frs);
                        try {schema.getScope().releaseConnection(fconn);} catch (SQLException ex) {};
                        super.close();
                    }

                    // slight hack here to get the size cheaply
                    public int available() throws IOException
                    {
                        return size;
                    }
                };
                stmt = null;
                rs = null;
                conn = null;
                return ret;
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(stmt);
            ResultSetUtil.close(rs);
            if (null != conn) try {schema.getScope().releaseConnection(conn);} catch (SQLException ex) {}
        }
    }


    private CoreSchema coreTables()
    {
        return CoreSchema.getInstance();
    }

    private String sqlDocument()
    {
        return "SELECT DocumentType, DocumentSize, Document FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";
    }
    private String sqlRootDocument()
    {
        return "SELECT DocumentType, DocumentSize, Document FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent IS NULL AND DocumentName = ?";
    }
    private String sqlCascadeDelete()
    {
        return "DELETE FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent = ?";
    }
    private String sqlDelete()
    {
        return "DELETE FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";
    }

    private SQLFragment sqlRename(AttachmentParent parent, String oldName, String newName)
    {
        return new SQLFragment("UPDATE " + coreTables().getTableInfoDocuments() + " SET DocumentName = ? WHERE Container = ? AND Parent = ? AND DocumentName = ?",
                newName, parent.getContainerId(), parent.getEntityId(), oldName);
    }

    private String sqlExists()
    {
        return "SELECT COUNT(*) FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent = ? AND DocumentName = ?";
    }

    enum FileAction
    {
        UPLOAD,
        DELETE
    }

    private void logFileAction(File directory, String fileName, FileAction action, User user)
    {
        FileWriter fw = null;
        try
        {
            fw = new FileWriter(new File(directory, UPLOAD_LOG), true);
            fw.write(action.toString()  + "\t" + fileName + "\t" + new Date() + "\t" + (user == null ? "(unknown)" : user.getEmail()) + "\n");
        }
        catch (Exception x)
        {
            //Just log it.
            _log.error(x);
        }
        finally
        {
            if (null != fw)
            {
                try
                {
                    fw.close();
                }
                catch (Exception x)
                {

                }
            }
        }
    }
    private static class ResponseWriter implements DocumentWriter
    {
        private HttpServletResponse _response;

        public ResponseWriter(HttpServletResponse response)
        {
            _response = response;
        }

        public void setContentType(String contentType)
        {
            _response.setContentType(contentType);
        }

        public void setContentDisposition(String value)
        {
            _response.setHeader("Content-Disposition", value);
        }

        public void setContentLength(int size)
        {
            _response.setContentLength(size);
        }

        public OutputStream getOutputStream() throws IOException
        {
            return _response.getOutputStream();
        }
    }

    public static class FileSystemAttachmentParent implements AttachmentDirectory
    {
        private Container c;
        private String entityId;
        private String path;
        private String name;
        private boolean relative;

        public FileSystemAttachmentParent()
        {
            //For use by auto-contstruction schemes...
        }
        
        private FileSystemAttachmentParent(Container c)
        {
            this.c = c;
            this.entityId = c.getId();
        }

        public String getEntityId()
        {
            //Just use container id if no path
            return (null == entityId && null == path) ? c.getId() : entityId;
        }

        public Container getContainer()
        {
            return c;
        }

        public void setContainer(Container c)
        {
            this.c = c;
        }

        public String getContainerId()
        {
            return c.getId();
        }

        public File getFileSystemDirectory() throws AttachmentService.MissingRootDirectoryException
        {
            if (null == path)
                return ((AttachmentServiceImpl) AttachmentService.get()).getMappedDirectory(c, false);
            else if (isRelative())
            {
                File mappedDir = ((AttachmentServiceImpl) AttachmentService.get()).getMappedDirectory(c, false);
                return new File(mappedDir, path);
            }
            else
                return new File(path);
        }

        // AttachmentServiceImpl uses this to retrieve the attachments of many parents with a single query.  Implementation
        // is not necessary in most cases.
        public void setAttachments(Collection<Attachment> attachments)
        {
        }

        public String getLabel()
        {
            return name;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getPath()
        {
            return path;
        }

        public void setPath(String path)
        {
            this.path = path;
        }

        public void setEntityid(String entityid)
        {
            this.entityId = entityid;
        }

        public boolean isRelative()
        {
            return relative;
        }

        public void setRelative(boolean relative)
        {
            this.relative = relative;
        }
    }



    /** two cases
     *   regular file attachments: assume that this collection resource will be wrapped rather then exposed directly in the tree
     *   filesets: expect that this will be directly exposed in the tree
     */
    private class AttachmentCollection extends AbstractCollectionResource
    {
        AttachmentParent _parent;

        AttachmentCollection(String path, AttachmentParent parent, SecurityPolicy policy)
        {
            super(path);
            _parent = parent;
            _policy = policy;
        }


        public boolean exists()
        {
            if (_parent instanceof FileSystemAttachmentParent)
            {
                if (null == ((FileSystemAttachmentParent)_parent).getName())
                {
                    try
                    {
                        return null != getMappedAttachmentDirectory(ContainerManager.getForId(_parent.getContainerId()), false);
                    }
                    catch (AttachmentService.MissingRootDirectoryException x)
                    {
                        return false;
                    }
                }
                else
                {
                    return null != getRegisteredDirectory(ContainerManager.getForId(_parent.getContainerId()), ((FileSystemAttachmentParent)_parent).getName());
                }
            }
            return true;
        }




        public WebdavResolver.Resource find(String name)
        {
            Attachment a = getAttachment(_parent, name);
            if (null != a)
                return new AttachmentResource(this, _parent, a);
            else
                return new AttachmentResource(this, _parent, name);
        }


        public List<String> listNames()
        {
            Attachment[] attachments = getAttachments(_parent);
            ArrayList<String> names = new ArrayList<String>(attachments.length);
            for (Attachment a : attachments)
            {
                if (null != a.getFile() && !a.getFile().exists())
                    continue;
                names.add(a.getName());
            }
            return names;
        }


        @Override
        public List<WebdavResolver.Resource> list()
        {
            Attachment[] attachments = getAttachments(_parent);
            ArrayList<WebdavResolver.Resource> resources = new ArrayList<WebdavResolver.Resource>(attachments.length);
            for (Attachment a : attachments)
            {
                if (null != a.getFile() && !a.getFile().exists())
                    continue;
                resources.add(new AttachmentResource(this, _parent, a));
            }
            return resources;
        }
    }


    private class AttachmentResource extends AbstractDocumentResource
    {
        WebdavResolver.Resource _folder = null;
        AttachmentParent _parent = null;
        String _name = null;
        long _created = Long.MIN_VALUE;
        String _createdBy = null;

        Attachment _cached = null;

        
        AttachmentResource(@NotNull WebdavResolver.Resource folder, @NotNull AttachmentParent parent, @NotNull Attachment attachment)
        {
            this(folder, parent, attachment.getName());
            _created = attachment.getCreated().getTime();
            _createdBy = attachment.getCreatedByName(HttpView.currentContext());
            _cached = attachment;
        }


        AttachmentResource(@NotNull WebdavResolver.Resource folder, @NotNull AttachmentParent parent, @NotNull String name)
        {
            super(folder.getPath(), name);
            _folder = folder;
            Container c = ContainerManager.getForId(parent.getContainerId());
            _policy = c == null ? null : c.getPolicy();
            _name = name;
            _parent = parent;
        }

        Attachment getAttachment()
        {
            if (null != _cached)
                return _cached;
            return AttachmentService.get().getAttachment(_parent, _name);
        }

        public boolean exists()
        {
            Attachment r = getAttachment();
            if (r == null)
                return false;
            if (null != r.getFile())
                return r.getFile().exists();
            return true;
        }

        public boolean isCollection()
        {
            return false;
        }

        @Override
        public boolean canRename(User user)
        {
            return false;
        }

        @Override
        public boolean delete(User user) throws IOException
        {
            try
            {
                if (user != null && !canDelete(user))
                    return false;
                AttachmentService.get().delete(user, _parent, _name);
                return true;
            }
            catch (SQLException x)
            {
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }
        }

        @Override
        public FileStream getFileStream(User user) throws IOException
        {
            Attachment r = getAttachment();
            if (null == r)
                return null;
            List<AttachmentFile> files = getAttachmentFiles(_parent, Collections.singletonList(r));
            return files.get(0);
        }

        public InputStream getInputStream(User user) throws IOException
        {
            return AttachmentService.get().getInputStream(_parent, _name);
        }

        @Override
        public void moveFrom(User user, WebdavResolver.Resource r) throws IOException
        {
            if (r instanceof AttachmentResource)
            {
                AttachmentResource from = (AttachmentResource) r;
                if (from._parent == this._parent)
                {
                    renameAttachment(this._parent, from.getName(), getName());
                    return;
                }
            }
            super.moveFrom(user, r);
        }

        public long copyFrom(User user, final FileStream in) throws IOException
        {
            try
            {
                AttachmentFile file =  new AttachmentFile()
                {
                    public long getSize() throws IOException
                    {
                        return in.getSize();
                    }

                    public String getError()
                    {
                        return null;
                    }

                    public String getFilename()
                    {
                        return getName();
                    }

                    public void setFilename(String filename)
                    {
                        throw new IllegalStateException();
                    }

                    public String getContentType()
                    {
                        return PageFlowUtil.getContentTypeFor(getFilename());
                    }

                    public byte[] getBytes() throws IOException
                    {
                        throw new UnsupportedOperationException();
                    }

                    public InputStream openInputStream() throws IOException
                    {
                        return in.openInputStream();
                    }

                    public void closeInputStream() throws IOException
                    {
                        in.closeInputStream();
                    }
                };

                if (AttachmentServiceImpl.this.exists(_parent,_name))
                    deleteAttachment(_parent, _name);
                addAttachments(user, _parent, Collections.singletonList(file));
            }
            catch (AttachmentService.DuplicateFilenameException x)
            {
                IOException io = new IOException();
                io.initCause(x);
                throw io;
            }
            finally
            {
                in.closeInputStream();
            }
            // UNDONE return real length if anyone cares
            return 0;
        }

        public WebdavResolver.Resource parent()
        {
            return _folder;
        }

        public long getCreated()
        {
            return _created;
        }

        @Override
        public String getCreatedBy()
        {
            return _createdBy;
        }

        public long getLastModified()
        {
            return getCreated();
        }

        @Override
        public String getModifiedBy()
        {
            return _createdBy;
        }

        public long getContentLength()
        {
            Attachment a = getAttachment();
            if (null == a)
                return 0;

            if (_parent instanceof AttachmentDirectory)
            {
                try
                {
                    File dir = ((AttachmentDirectory)_parent).getFileSystemDirectory();
                    File file = new File(dir,a.getName());
                    return file.exists() ? file.length() : 0;
                }
                catch (AttachmentService.MissingRootDirectoryException x)
                {
                    return 0;
                }
            }
            else
            {
                // UNDONE
                // return a.getSize();
                InputStream is = null;
                try
                {
                    is = getInputStream(null);
                    if (null != is)
                    {
                        long size = 0;
                        if (is instanceof FileInputStream)
                            size = ((FileInputStream) is).getChannel().size();
                        else if (is instanceof FilterInputStream)
                            size = is.available();
                        IOUtils.closeQuietly(is);
                        is = null;
                        return size;
                    }
                }
                catch (IOException x)
                {
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                }
                return 0;
            }
        }

		@Override
        public Set<Class<? extends Permission>> getPermissions(User user)
        {
            return super.getPermissions(user);
        }

		@Override
        public File getFile()
        {
            return null;
        }

        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }
    }



    //
    //JUnit TestCase
    //

    public static class TestCase extends junit.framework.TestCase
    {
        TestCase(String name)
        {
            super(name);
        }

        public static Test suite()
        {
            TestSuite suite = new TestSuite();
            suite.addTest(new TestCase("testDirectories"));
            return suite;
        }

        void assertSameFile(File a, File b)
        {
            if (a.equals(b))
                return;
            try
            {
                a = a.getCanonicalFile();
                b = b.getCanonicalFile();
                assertEquals(a,b);
            }
            catch (IOException x)
            {
                fail(x.getMessage());
            }
        }

        public void testDirectories() throws IOException, SQLException, AttachmentService.DuplicateFilenameException
        {
            String projectRoot = AppProps.getInstance().getProjectRoot();
            if (projectRoot == null || projectRoot.equals("")) projectRoot = "C:/Labkey";
            File buildDir = new File(projectRoot, "build");
            File testDir = new File(buildDir, "AttachmentTest");
            File webRoot = new File(testDir, "JUnitWebFileRoot");

            if (null != ContainerManager.getForPath("/JUnitAttachmentProject/Test"))
                ContainerManager.delete(ContainerManager.getForPath("/JUnitAttachmentProject/Test"), null);
            if (null != ContainerManager.getForPath("/JUnitAttachmentProject/newName"))
                ContainerManager.delete(ContainerManager.getForPath("/JUnitAttachmentProject/newName"), null);
            if (null != ContainerManager.getForPath("/JUnitAttachmentProject"))
                ContainerManager.delete(ContainerManager.getForPath("/JUnitAttachmentProject"), null);
            Container proj = ContainerManager.ensureContainer("/JUnitAttachmentProject");
            Container folder = ContainerManager.ensureContainer("/JUnitAttachmentProject/Test");
            webRoot.mkdirs();

            AttachmentService.Service svc = AttachmentService.get();
            File curRoot = svc.getWebRoot(proj);
            if (null == curRoot)
                svc.setWebRoot(proj, webRoot);
            assertSameFile(svc.getWebRoot(proj), webRoot);
            AttachmentDirectory attachParent = svc.getMappedAttachmentDirectory(folder, true);
            File attachDir = attachParent.getFileSystemDirectory();
            assertTrue(attachDir.exists());
            assertSameFile(attachDir, new File(webRoot, "Test"));

            MultipartFile f = new MockMultipartFile("file.txt", "file.txt", "text/plain", "Hello World".getBytes());
            Map<String, MultipartFile> fileMap = new HashMap<String, MultipartFile>();
            fileMap.put("file.txt", f);
            List<AttachmentFile> files = SpringAttachmentFile.createList(fileMap);

            svc.addAttachments(HttpView.currentContext().getUser(), attachParent, files);
            Attachment[] att = svc.getAttachments(attachParent);
            assertEquals(att.length, 1);
            assertTrue(att[0].getFile().exists());

            assertTrue(new File(attachDir, UPLOAD_LOG).exists());

            File otherDir = new File(testDir, "subdir");
            otherDir.mkdir();
            AttachmentDirectory namedParent = svc.registerDirectory(folder, "test", otherDir.getCanonicalPath(), false);

            AttachmentDirectory namedParentTest = svc.getRegisteredDirectory(folder, "test");
            assertNotNull(namedParentTest);
            assertSameFile(namedParentTest.getFileSystemDirectory(), namedParent.getFileSystemDirectory());

            svc.addAttachments(HttpView.currentContext().getUser(), namedParent, files);
            att = svc.getAttachments(namedParent);
            assertEquals(att.length, 1);
            assertTrue(att[0].getFile().exists());
            assertSameFile(new File(otherDir, "file.txt"), att[0].getFile());
            assertTrue(new File(otherDir, UPLOAD_LOG).exists());

            svc.unregisterDirectory(folder, "test");
            namedParentTest = svc.getRegisteredDirectory(folder, "test");
            assertNull(namedParentTest);

            File relativeDir = new File(attachDir, "subdir2");
            //registering a directory doesn't make sure it exists
            relativeDir.mkdirs();
            AttachmentDirectory relativeParent = svc.registerDirectory(folder, "relative", "subdir2", true);
            AttachmentDirectory relativeParentTest = svc.getRegisteredDirectory(folder, "relative");
            assertNotNull(relativeParentTest);
            assertSameFile(relativeParentTest.getFileSystemDirectory(), relativeParent.getFileSystemDirectory());

            svc.addAttachments(HttpView.currentContext().getUser(), relativeParent, files);
            att = svc.getAttachments(relativeParent);
            assertEquals(att.length, 1);

            File expectedFile1 = att[0].getFile();
            File expectedFile2 = new File(relativeDir, UPLOAD_LOG);

            assertTrue(expectedFile1.exists());
            assertTrue(new File(relativeDir, "file.txt").equals(expectedFile1));
            assertTrue(expectedFile2.exists());

            // Slight detour... test FileAttachmentFile using these just-created files before we delete them
            testFileAttachmentFiles(expectedFile1, expectedFile2);

            ContainerManager.rename(folder, "newName");
            //Should be moved, so no longer exists
            assertFalse(attachDir.getAbsolutePath() + " shouldn't exist", attachDir.exists());
            attachDir = new File(webRoot, "newName");
            assertTrue(attachDir.exists());

            //Need to reselect folder or its name is wrong!
            folder = ContainerManager.getForId(folder.getId());
            ContainerManager.delete(folder, null);
            assertFalse(attachDir.exists());
            ContainerManager.delete(proj, null);
        }

        public void testFileAttachmentFiles(File file1, File file2) throws IOException, SQLException
        {
            AttachmentFile aFile1 = new FileAttachmentFile(file1);
            AttachmentFile aFile2 = new FileAttachmentFile(file2);

            AttachmentService.Service service = AttachmentService.get();
            AttachmentParent root = ContainerManager.RootContainer.get();
			service.deleteAttachment(root, file1.getName());
            service.deleteAttachment(root, file2.getName());

            Attachment[] attachments = service.getAttachments(root);
            int originalCount = attachments.length;

            service.add(HttpView.currentContext().getUser(), root, Arrays.asList(aFile1, aFile2));
            attachments = service.getAttachments(root);
            assertTrue((originalCount + 2) == attachments.length);

            service.deleteAttachment(root, file1.getName());
            attachments = service.getAttachments(root);
            assertTrue((originalCount + 1) == attachments.length);

            service.deleteAttachment(root, file2.getName());
            attachments = service.getAttachments(root);
            assertTrue(originalCount == attachments.length);
        }
    }
}
