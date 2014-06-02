/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.DocumentWriter;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: adam
 * Date: Jan 3, 2007
 * Time: 7:13:28 PM
 */
public class AttachmentServiceImpl implements AttachmentService.Service, ContainerManager.ContainerListener
{
    private static Logger _log = Logger.getLogger(AttachmentServiceImpl.class);
    private static MimeMap _mimeMap = new MimeMap();
    private static final String UPLOAD_LOG = ".upload.log";

    static final Set<String> ATTACHMENT_COLUMNS = new CsvSet("Parent, Container, DocumentName, DocumentSize, DocumentType, Created, CreatedBy, LastIndexed");

    public AttachmentServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }


    @Override
    @Deprecated
    public HttpView add(AttachmentParent parent, List<AttachmentFile> files, User auditUser)
    {
        String message = null;

        if (!files.isEmpty())
        {
            try
            {
                addAttachments(parent, files, auditUser);
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

    @Override
    @Deprecated
    public HttpView delete(AttachmentParent parent, String name, User auditUser) throws SQLException
    {
        deleteAttachment(parent, name, auditUser);
        return new RefreshParentView();
    }

    @Override
    public void download(HttpServletResponse response, AttachmentParent parent, String filename) throws ServletException, IOException
    {
        if (null == filename || 0 == filename.length())
        {
            throw new NotFoundException();
        }

        boolean isInlineImage = _mimeMap.isInlineImageFor(filename);
        boolean asAttachment = !isInlineImage;

        response.reset();
        writeDocument(new ResponseWriter(response), parent, filename, asAttachment);

        User user = null;
        try
        {
            ViewContext context = HttpView.currentContext();
            if (context != null)
                user = context.getUser();
        }
        catch (RuntimeException e)
        {
        }

        // Change in behavior added in 11.1:  no longer audit download events for the guest user
        if (null != user && !user.isGuest() && !isInlineImage)
        {
            addAuditEvent(user, parent, filename, "The attachment " + filename + " was downloaded");
        }
    }


    @Override
    public void addAuditEvent(User user, AttachmentParent parent, String filename, String comment)
    {
        if (user == null)
            throw new IllegalArgumentException("Cannot create attachment audit events for the null user.");

        if (parent != null)
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
                catch (MissingRootDirectoryException ex)
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

    @Override
    public HttpView getHistoryView(ViewContext context, AttachmentParent parent)
    {
        if (AuditLogService.get().isMigrateComplete() || AuditLogService.get().hasEventTypeMigrated(AttachmentService.ATTACHMENT_AUDIT_EVENT))
        {
            UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
            if (schema != null)
            {
                QuerySettings settings = new QuerySettings(context, QueryView.DATAREGIONNAME_DEFAULT);

                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(AttachmentAuditProvider.COLUMN_NAME_CONTAINER), parent.getContainerId());
                filter.addCondition(FieldKey.fromParts(AttachmentAuditProvider.COLUMN_NAME_ATTACHMENT_PARENT_ENTITY_ID), parent.getEntityId());

                settings.setBaseFilter(filter);
                settings.setQueryName(AttachmentService.ATTACHMENT_AUDIT_EVENT);

                QueryView view = schema.createView(context, settings);
                view.setTitle("Attachments History:");

                return view;
            }
            return null;
        }
        else
            return AttachmentAuditViewFactory.createAttachmentView(context, parent);
    }

    @Override
    public HttpView getAddAttachmentView(Container container, AttachmentParent parent, BindException errors)
    {
        HttpView view = new AddAttachmentView(parent, errors);
        DialogTemplate template = new DialogTemplate(view);
        template.getModelBean().setTitle("Add Attachment");
        template.getModelBean().setShowHeader(false);
        return template;
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


    @Override
    public synchronized void addAttachments(AttachmentParent parent, List<AttachmentFile> files, @NotNull User user) throws IOException
    {
        if (null == user)
            throw new IllegalArgumentException("Cannot add attachments for the null user");

        if (files != null)
        {
            List<String> duplicates = findDuplicates(files);

            if (duplicates.size() > 0)
            {
                throw new AttachmentService.DuplicateFilenameException(duplicates);
            }
        }

        if (null == files || files.isEmpty())
            return;

        Set<String> filesToSkip = new TreeSet<>();
        File fileLocation = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;

        for (AttachmentFile file : files)
        {
            if (exists(parent, file.getFilename()))
            {
                filesToSkip.add(file.getFilename());
                continue;
            }

            HashMap<String, Object> hm = new HashMap<>();
            if (null == fileLocation)
            {
                int maxSize = AppProps.getInstance().getMaxBLOBSize();
                if (file.getSize() > maxSize)
                {
                    throw new AttachmentService.FileTooLargeException(file, maxSize);
                }

                hm.put("Document", file);
            }
            else
                ((AttachmentDirectory)parent).addAttachment(user, file);

            hm.put("DocumentName", file.getFilename());
            hm.put("DocumentSize", file.getSize());
            hm.put("DocumentType", file.getContentType());
            hm.put("Parent", parent.getEntityId());
            hm.put("Container", parent.getContainerId());
            Table.insert(user, coreTables().getTableInfoDocuments(), hm);

            addAuditEvent(user, parent, file.getFilename(), "The attachment " + file.getFilename() + " was added");
        }

        AttachmentCache.removeAttachments(parent);

        if (!filesToSkip.isEmpty())
            throw new AttachmentService.DuplicateFilenameException(filesToSkip);
    }

    @Override
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


    @Override
    public void deleteAttachments(AttachmentParent... parents)
    {
        for(AttachmentParent parent : parents)
        {
            List<Attachment> atts = getAttachments(parent);
            SearchService ss = ServiceRegistry.get(SearchService.class);

            for (Attachment att : atts)
                ss.deleteResource(makeDocId(parent, att.getName()));

            new SqlExecutor(coreTables().getSchema()).execute(sqlCascadeDelete(), parent.getEntityId());
            if (parent instanceof AttachmentDirectory)
                ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), null);
            AttachmentCache.removeAttachments(parent);
        }
    }

    @Override
    public void deleteAttachments(Collection<AttachmentParent> parents)
    {
        for(AttachmentParent parent : parents)
        {
            List<Attachment> atts = getAttachments(parent);
            SearchService ss = ServiceRegistry.get(SearchService.class);

            for (Attachment att : atts)
                ss.deleteResource(makeDocId(parent, att.getName()));

            new SqlExecutor(coreTables().getSchema()).execute(sqlCascadeDelete(), parent.getEntityId());
            if (parent instanceof AttachmentDirectory)
                ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), null);
            AttachmentCache.removeAttachments(parent);
        }
    }

    @Override
    public void deleteAttachment(AttachmentParent parent, String name, @Nullable User auditUser)
    {
        Attachment att = getAttachment(parent,name);

        if (null != att)
        {
            SearchService ss = ServiceRegistry.get(SearchService.class);
            ss.deleteResource(makeDocId(parent, att.getName()));
        }

        new SqlExecutor(coreTables().getSchema()).execute(sqlDelete(), parent.getEntityId(), name);
        if (parent instanceof AttachmentDirectory)
            ((AttachmentDirectory)parent).deleteAttachment(auditUser, name);

        AttachmentCache.removeAttachments(parent);

        if (null != auditUser)
            addAuditEvent(auditUser, parent, name, "The attachment " + name + " was deleted");
    }


    @Override
    public void renameAttachment(AttachmentParent parent, String oldName, String newName, User auditUser) throws IOException
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
            deleteAttachment(parent, newName, null);
        }

        if (exists(parent, newName))
            throw new AttachmentService.DuplicateFilenameException(newName);

        new SqlExecutor(coreTables().getSchema()).execute(sqlRename(parent, oldName, newName));

        // rename the file in the filesystem only if an Attachment director and the db rename succeded
        if (null != dir)
            src.renameTo(dest);

        AttachmentCache.removeAttachments(parent);

        addAuditEvent(auditUser, parent, newName, "The attachment " + oldName + " was renamed " + newName);
    }


    // Copies an attachment -- same container, same parent, but new name.  
    @Override
    public void copyAttachment(AttachmentParent parent, Attachment a, String newName, User auditUser) throws IOException
    {
        a.setName(newName);
        DatabaseAttachmentFile file = new DatabaseAttachmentFile(a);
        addAttachments(parent, Collections.singletonList((AttachmentFile)file), auditUser);
    }


    /** may return fewer AttachmentFile than Attachment, if there have been deletions */
    @Override
    public @NotNull List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException
    {
        List<AttachmentFile> files = new ArrayList<>(attachments.size());

        for (Attachment attachment : attachments)
        {
            if (parent instanceof AttachmentDirectory)
            {
                File f = new File(((AttachmentDirectory)parent).getFileSystemDirectory(), attachment.getName());
                files.add(new FileAttachmentFile(f));
            }
            else
            {
                try
                {
                    files.add(new DatabaseAttachmentFile(attachment));
                }
                catch (FileNotFoundException x)
                {
                    //
                }
            }
        }
        return files;
    }


    private boolean exists(AttachmentParent parent, String filename)
    {
        return null != getAttachment(parent, filename);
    }

    private List<String> findDuplicates(List<AttachmentFile> files)
    {
        Set fileNames = new HashSet<String>();
        List<String> duplicates = new ArrayList<>();
        for (AttachmentFile file : files)
        {
            if(fileNames.add(file.getFilename()) == false)
            {
                duplicates.add(file.getFilename());
            }
        }
        return duplicates;
    }

    @Override
    public @NotNull List<Attachment> getAttachments(AttachmentParent parent)
    {
        Map<String, Attachment> mapFromDatabase = AttachmentCache.getAttachments(parent);
        List<Attachment> attachmentsFromDatabase = Collections.unmodifiableList(new ArrayList<>(mapFromDatabase.values()));

        File parentDir = null;

        try
        {
            parentDir = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;
        }
        catch (MissingRootDirectoryException ex)
        {
            /* no problem */
        }

        if (null == parentDir || !parentDir.exists())
            return attachmentsFromDatabase;

        for (Attachment att : attachmentsFromDatabase)
            att.setFile(new File(parentDir, att.getName()));

        //OK, make sure that the list really reflects what is in the file system.
        List<Attachment> attList = new ArrayList<>();

        File[] fileList = parentDir.listFiles(new FileFilter()
        {
            public boolean accept(File file)
            {
                return !file.isDirectory() && !(file.getName().charAt(0) == '.') && !file.isHidden();
            }
        });

        Set<String> attachmentNames = new CaseInsensitiveHashSet();

        for (Attachment attachment : attachmentsFromDatabase)
        {
            attachmentNames.add(attachment.getName());
            attList.add(attachment);
        }

        if (null != fileList)
        {
            for (File file : fileList)
            {
                if (!attachmentNames.contains(file.getName()))
                    attList.add(attachmentFromFile(parent, file));
            }
        }

        return Collections.unmodifiableList(attList);
    }


    /** Does not work for file system parents */
    public List<Pair<String,String>> listAttachmentsForIndexing(Collection<String> parents, Date modifiedSince)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Parent"), parents, CompareType.IN);
        if (null != modifiedSince)
            filter.addCondition(FieldKey.fromParts("Modified"), modifiedSince, CompareType.GTE);
        SimpleFilter.FilterClause since = new SearchService.LastIndexedClause(coreTables().getTableInfoDocuments(), modifiedSince, null);
        filter.addClause(since);

        final ArrayList<Pair<String,String>> ret = new ArrayList<>();

        new TableSelector(coreTables().getTableInfoDocuments(),
                    PageFlowUtil.set("Parent", "DocumentName", "LastIndexed"),
                    filter,
                    new Sort("+Created")).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String parent = rs.getString(1);
                String name = rs.getString(2);
                java.util.Date last = rs.getTimestamp(3);
                if (last != null && last.getTime() == SearchService.failDate.getTime())
                    return;
                ret.add(new Pair<>(parent, name));
            }
        });

        return ret;
    }


    /** Collection resource with all attachments for this parent */
    public WebdavResource getAttachmentResource(Path path, AttachmentParent parent)
    {
        // NOTE parent does not supply ACL, but should?
        // acl = parent.getAcl()
        Container c = ContainerManager.getForId(parent.getContainerId());
        if (null == c)
            return null;

        return new AttachmentCollection(path, parent, c.getPolicy());
    }


    public WebdavResource getDocumentResource(Path path, ActionURL downloadURL, String displayTitle, AttachmentParent parent, String name, SearchService.SearchCategory cat)
    {
        return new AttachmentResource(path, downloadURL, displayTitle, parent, name, cat);
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


    public @Nullable Attachment getAttachment(AttachmentParent parent, String name)
    {
        if (parent instanceof AttachmentDirectory)
        {
            for (Attachment attachment : getAttachments(parent))
                if (name.equals(attachment.getName()))
                    return attachment;

            return null;
        }
        else
        {
            return AttachmentCache.getAttachments(parent).get(name);
        }
    }


    public void containerCreated(Container c, User user)
    {
    }


    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(coreTables().getTableInfoDocuments(), c, null);
        AttachmentCache.removeAttachments(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {        
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    // CONSIDER: Return success/failure notification so caller can take action (render a default document) in all the failure scenarios.
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
                {
                    throw new NotFoundException();
                }

                writer.setContentType(rs.getString("DocumentType"));
                if (asAttachment)
                    writer.setContentDisposition("attachment; filename=\"" + name + "\"");

                int size = rs.getInt("DocumentSize");
                if (size > 0)
                    writer.setContentLength(size);

                s = rs.getBinaryStream("Document");
                if (null == s)
                    return;
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
            ResultSetUtil.close(rs);
            ResultSetUtil.close(stmt);
            schema.getScope().releaseConnection(conn);
        }
    }


    @NotNull
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
                        schema.getScope().releaseConnection(fconn);
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
            if (null != conn) schema.getScope().releaseConnection(conn);
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

    /** two cases
     *   regular file attachments: assume that this collection resource will be wrapped rather then exposed directly in the tree
     *   filesets: expect that this will be directly exposed in the tree
     */
    private class AttachmentCollection extends AbstractWebdavResourceCollection
    {
        AttachmentParent _parent;

        AttachmentCollection(Path path, AttachmentParent parent, SecurityPolicy policy)
        {
            super(path);
            _parent = parent;
            setPolicy(policy);
        }


        public boolean exists()
        {
            FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
            if (_parent instanceof AttachmentDirectory)
            {
                if (null == ((AttachmentDirectory)_parent).getName())
                {
                    try
                    {
                        return null != svc.getMappedAttachmentDirectory(ContainerManager.getForId(_parent.getContainerId()), false);
                    }
                    catch (MissingRootDirectoryException x)
                    {
                        return false;
                    }
                }
                else
                {
                    return null != svc.getRegisteredDirectory(ContainerManager.getForId(_parent.getContainerId()), ((AttachmentDirectory)_parent).getName());
                }
            }
            return true;
        }


        public WebdavResource find(String name)
        {
            Attachment a = getAttachment(_parent, name);

            if (null != a)
                return new AttachmentResource(this, _parent, a);
            else
                return new AttachmentResource(this, _parent, name);
        }


        public Collection<String> listNames()
        {
            List<Attachment> attachments = getAttachments(_parent);
            ArrayList<String> names = new ArrayList<>(attachments.size());

            for (Attachment a : attachments)
            {
                if (null != a.getFile() && !a.getFile().exists())
                    continue;
                names.add(a.getName());
            }

            return names;
        }


        public Collection<? extends WebdavResource> list()
        {
            List<Attachment> attachments = getAttachments(_parent);
            ArrayList<WebdavResource> resources = new ArrayList<>(attachments.size());

            for (Attachment a : attachments)
            {
                if (null != a.getFile() && !a.getFile().exists())
                    continue;
                resources.add(new AttachmentResource(this, _parent, a));
            }

            return resources;
        }
    }


    private static String makeDocId(AttachmentParent parent, String name)
    {
        return "attachment:/" + parent.getEntityId() + "/" + PageFlowUtil.encode(name);
    }
    

    private class AttachmentResource extends AbstractDocumentResource
    {
        WebdavResource _folder;
        final AttachmentParent _parent;
        final String _name;
        long _created = Long.MIN_VALUE;
        User _createdBy = null;
        ActionURL _downloadUrl = null;
        Attachment _cached = null;
        final String _docid;

        AttachmentResource(Path path, ActionURL downloadURL, String displayTitle, AttachmentParent parent, String name, SearchService.SearchCategory cat)
        {
            super(path);

            Container c = ContainerManager.getForId(parent.getContainerId());
            _containerId = parent.getContainerId();
            if (null != c)
                setPolicy(c.getPolicy());
            _downloadUrl = downloadURL;
            _parent = parent;
            _name = name;
            _docid = makeDocId(parent,name);
            initSearchProperties(name, displayTitle, cat);
        }


        private void initSearchProperties(String name, @Nullable String displayTitle, @Nullable SearchService.SearchCategory cat)
        {
            setSearchProperty(SearchService.PROPERTY.keywordsMed, FileUtil.getSearchTitle(name));
            setSearchProperty(SearchService.PROPERTY.title, null != displayTitle ? displayTitle : name);

            if (null == cat)
                setSearchCategory(SearchService.fileCategory);
            else
                setSearchProperty(SearchService.PROPERTY.categories, SearchService.fileCategory.toString() + " " + cat.toString());
        }

        AttachmentResource(@NotNull WebdavResource folder, @NotNull AttachmentParent parent, @NotNull Attachment attachment)
        {
            this(folder, parent, attachment.getName());

            _created = attachment.getCreated().getTime();
            _createdBy = UserManager.getUser(attachment.getCreatedBy());
            _cached = attachment;
        }


        AttachmentResource(@NotNull WebdavResource folder, @NotNull AttachmentParent parent, @NotNull String name)
        {
            super(folder.getPath(), name);
            _containerId = parent.getContainerId();
            _folder = folder;
            Container c = ContainerManager.getForId(parent.getContainerId());
            if (c != null)
                setPolicy(c.getPolicy());
            _name = name;
            _parent = parent;
            _docid = makeDocId(parent,name);

            initSearchProperties(name, null, null);
        }


        @Override
        public String getDocumentId()
        {
            return "attachment:/" + _parent.getEntityId() + "/" + PageFlowUtil.encode(_name);
        }

        Attachment getAttachment()
        {
            if (null != _cached)
                return _cached;
            return AttachmentService.get().getAttachment(_parent, _name);
        }


        @Override
        public String getContentType()
        {
            return super.getContentType();
        }

        @Override
        public String getExecuteHref(ViewContext context)
        {
            if (null != _downloadUrl)
                return _downloadUrl.getLocalURIString();
            String download = _parent.getDownloadURL(context, getName());
            if (null != download)
                return download;
            download = super.getExecuteHref(context);
            return download;
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
        public boolean canRename(User user, boolean forRename)
        {
            return false;
        }

        @Override
        public boolean delete(User user) throws IOException
        {
            if (user != null && !canDelete(user, true))
                return false;
            AttachmentService.get().deleteAttachment(_parent, _name, user);
            return true;
        }

        @Override
        public FileStream getFileStream(User user) throws IOException
        {
            Attachment r = getAttachment();
            if (null == r)
                return null;
            List<AttachmentFile> files = getAttachmentFiles(_parent, Collections.singletonList(r));
            if (files.isEmpty())
                throw new FileNotFoundException(r.getName());
            return files.get(0);
        }

        public InputStream getInputStream(User user) throws IOException
        {
            return AttachmentService.get().getInputStream(_parent, _name);
        }

        @Override
        public void moveFrom(User user, WebdavResource r) throws IOException
        {
            if (r instanceof AttachmentResource)
            {
                AttachmentResource from = (AttachmentResource) r;
                if (from._parent == this._parent)
                {
                    renameAttachment(this._parent, from.getName(), getName(), user);
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

                    public String getContentType()
                    {
                        return PageFlowUtil.getContentTypeFor(getFilename());
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
                    deleteAttachment(_parent, _name, user);
                addAttachments(_parent, Collections.singletonList(file), user);
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

        public WebdavResource parent()
        {
            return _folder;
        }

        public long getCreated()
        {
            return _created;
        }

        @Override
        public User getCreatedBy()
        {
            return _createdBy;
        }

        public long getLastModified()
        {
            return getCreated();
        }

        @Override
        public User getModifiedBy()
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
                catch (MissingRootDirectoryException x)
                {
                    return 0;
                }
            }
            else
            {
                // UNDONE
                // return a.getSize();
                try (InputStream is = getInputStream(null))
                {
                    if (null != is)
                    {
                        long size = 0;
                        if (is instanceof FileInputStream)
                            size = ((FileInputStream) is).getChannel().size();
                        else if (is instanceof FilterInputStream)
                            size = is.available();

                        return size;
                    }
                }
                catch (IOException ignored)
                {
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
            if (_parent instanceof AttachmentDirectory)
            {
                try
                {
                    File dir = ((AttachmentDirectory)_parent).getFileSystemDirectory();
                    return new File(dir,getName());
                }
                catch (MissingRootDirectoryException x)
                {
                    return null;
                }
            }
            return null;
        }


        @NotNull
        public List<WebdavResolver.History> getHistory()
        {
            //noinspection unchecked
            return Collections.EMPTY_LIST;
        }

        @Override
        public void setLastIndexed(long ms, long modified)
        {
            new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(new SQLFragment(
                    "UPDATE core.Documents SET LastIndexed=? WHERE Parent=? and DocumentName=?",
                    new Date(ms), _parent.getEntityId(), _name));
        }
    }



    //
    //JUnit TestCase
    //

    public static class TestCase extends Assert
    {
        private static final String _testDirName = "/_jUnitAttachment";

        @Test
        public void testDirectories() throws IOException, SQLException, AttachmentService.DuplicateFilenameException
        {
            User user = TestContext.get().getUser();
            assertTrue(null != user);

            String projectRoot = AppProps.getInstance().getProjectRoot();
            if (projectRoot == null || projectRoot.equals("")) projectRoot = "C:/Labkey";

            // clean up if anything was left over from last time
            if (null != ContainerManager.getForPath(_testDirName))
                ContainerManager.deleteAll(ContainerManager.getForPath(_testDirName), user);

            Container proj = ContainerManager.ensureContainer(_testDirName);
            Container folder = ContainerManager.ensureContainer(_testDirName + "/Test");

            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentService.Service svc = AttachmentService.get();

            File curRoot = fileService.getFileRoot(proj);
            assertTrue(curRoot.isDirectory());

            AttachmentDirectory attachParent = fileService.getMappedAttachmentDirectory(folder, true);
            File attachDir = attachParent.getFileSystemDirectory();
            assertTrue(attachDir.exists());

            MultipartFile f = new MockMultipartFile("file.txt", "file.txt", "text/plain", "Hello World".getBytes());
            Map<String, MultipartFile> fileMap = new HashMap<>();
            fileMap.put("file.txt", f);
            List<AttachmentFile> files = SpringAttachmentFile.createList(fileMap);

            svc.addAttachments(attachParent, files, user);
            List<Attachment> att = svc.getAttachments(attachParent);
            assertEquals(att.size(), 1);
            assertTrue(att.get(0).getFile().exists());

            // test rename
            String oldName = f.getName();
            String newName = "newname.txt";
            svc.renameAttachment(attachParent, oldName, newName, user);
            assertNull(svc.getAttachment(attachParent, oldName));
            assertNotNull(svc.getAttachment(attachParent, newName));
            // put things back as we found them...
            svc.renameAttachment(attachParent, newName, oldName, user);


            assertTrue(new File(attachDir, UPLOAD_LOG).exists());

            File otherDir = new File(attachDir, "subdir");
            otherDir.mkdir();
            AttachmentDirectory namedParent = fileService.registerDirectory(folder, "test", otherDir.getCanonicalPath(), false);

            AttachmentDirectory namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNotNull(namedParentTest);
            assertSameFile(namedParentTest.getFileSystemDirectory(), namedParent.getFileSystemDirectory());

            svc.addAttachments(namedParent, files, user);
            att = svc.getAttachments(namedParent);
            assertEquals(att.size(), 1);
            assertTrue(att.get(0).getFile().exists());
            assertSameFile(new File(otherDir, "file.txt"), att.get(0).getFile());
            assertTrue(new File(otherDir, UPLOAD_LOG).exists());

            fileService.unregisterDirectory(folder, "test");
            namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNull(namedParentTest);

            File relativeDir = new File(attachDir, "subdir2");
            relativeDir.mkdirs();
            AttachmentDirectory relativeParent = fileService.registerDirectory(folder, "relative", relativeDir.getCanonicalPath(), false);
            
            AttachmentDirectory relativeParentTest = fileService.getRegisteredDirectory(folder, "relative");
            assertNotNull(relativeParentTest);
            assertSameFile(relativeParentTest.getFileSystemDirectory(), relativeParent.getFileSystemDirectory());

            svc.addAttachments(relativeParent, files, user);
            att = svc.getAttachments(relativeParent);
            assertEquals(att.size(), 1);

            File expectedFile1 = att.get(0).getFile();
            File expectedFile2 = new File(relativeDir, UPLOAD_LOG);

            assertTrue(expectedFile1.exists());
            assertTrue(new File(relativeDir, "file.txt").equals(expectedFile1));
            assertTrue(expectedFile2.exists());


            // Slight detour... test FileAttachmentFile using these just-created files before we delete them
            testFileAttachmentFiles(expectedFile1, expectedFile2, user);

            // clean up
            ContainerManager.deleteAll(proj, user);
        }



        private void assertSameFile(File a, File b)
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

        private void testFileAttachmentFiles(File file1, File file2, User user) throws IOException, SQLException
        {

            AttachmentFile aFile1 = new FileAttachmentFile(file1);
            AttachmentFile aFile2 = new FileAttachmentFile(file2);

            AttachmentService.Service service = AttachmentService.get();
            AttachmentParent root = ContainerManager.RootContainer.get();
			service.deleteAttachment(root, file1.getName(), user);
            service.deleteAttachment(root, file2.getName(), user);

            List<Attachment> attachments = service.getAttachments(root);
            int originalCount = attachments.size();

            service.addAttachments(root, Arrays.asList(aFile1, aFile2), user);
            attachments = service.getAttachments(root);
            assertTrue((originalCount + 2) == attachments.size());

            service.deleteAttachment(root, file1.getName(), user);
            attachments = service.getAttachments(root);
            assertTrue((originalCount + 1) == attachments.size());

            service.deleteAttachment(root, file2.getName(), user);
            attachments = service.getAttachments(root);
            assertTrue(originalCount == attachments.size());
        }
    }
}
