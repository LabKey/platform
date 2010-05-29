/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.*;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.DialogTemplate;
import org.labkey.api.webdav.*;
import org.labkey.core.query.AttachmentAuditViewFactory;
import org.labkey.api.webdav.FileSystemAuditViewFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
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
        _attachmentColumns.add("LastIndexed");
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
            ((AttachmentDirectory)parent).deleteAttachment(user, name);
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

    public void addAuditEvent(User user, AttachmentParent parent, String filename, String comment)
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

                int maxSize = AppProps.getInstance().getMaxBLOBSize();
                if (file.getSize() > maxSize)
                {
                    throw new IOException(file.getFilename() + " is larger than the maximum allowed size, " + NumberFormat.getIntegerInstance().format(maxSize) + " bytes");
                }

                HashMap<String, Object> hm = new HashMap<String, Object>();
                if (null == fileLocation)
                    hm.put("Document", file);
                else
                    ((AttachmentDirectory)parent).addAttachment(user, file);

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
        Attachment[] atts = getAttachments(parent);
        if (atts != null && atts.length > 0)
        {
            SearchService ss = ServiceRegistry.get(SearchService.class);
            for (Attachment att : atts)
                ss.deleteResource(makeDocId(parent,att.getName()));
        }

        Table.execute(coreTables().getSchema(), sqlCascadeDelete(), new Object[]{parent.getEntityId()});
        if (parent instanceof AttachmentDirectory)
            ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), null);
    }


    public void deleteAttachment(AttachmentParent parent, String name)
    {
        try
        {
            Attachment att = getAttachment(parent,name);
            if (null != att)
            {
                SearchService ss = ServiceRegistry.get(SearchService.class);
                ss.deleteResource(makeDocId(parent,att.getName()));
            }

            Table.execute(coreTables().getSchema(), sqlDelete(), new Object[]{parent.getEntityId(), name});
            if (parent instanceof AttachmentDirectory)
                ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), name);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
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
        catch (MissingRootDirectoryException ex)
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
//                return !(file.getName().charAt(0) == '.') && !file.isHidden();
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


    /** Does not work for file system parents */
    public List<Pair<String,String>> listAttachmentsForIndexing(Collection<String> parents, Date modifiedSince)
    {
        ResultSet rs = null;

        try
        {
            SimpleFilter filter = new SimpleFilter("Parent", parents, CompareType.IN);
            if (null != modifiedSince)
                filter.addCondition("Modified", modifiedSince, CompareType.GTE);
            SimpleFilter.FilterClause since = new SearchService.LastIndexedClause(coreTables().getTableInfoDocuments(), modifiedSince, null);
            filter.addClause(since);
            
            rs = Table.select(coreTables().getTableInfoDocuments(),
                    PageFlowUtil.set("Parent","DocumentName","LastIndexed"),
                    filter,
                    new Sort("+Created"));

            ArrayList<Pair<String,String>> ret = new ArrayList<Pair<String, String>>();
            while (rs.next())
            {
                String parent = rs.getString(1);
                String name = rs.getString(2);
                java.util.Date last = rs.getTimestamp(3);
                if (last != null && last.getTime() == SearchService.failDate.getTime())
                    continue;
                ret.add(new Pair<String,String>(parent, name));
            }
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
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

    public void containerCreated(Container c)
    {
    }


    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        try
        {
            ContainerUtil.purgeTable(coreTables().getTableInfoDocuments(), c, null);
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
            {
/*
                if (a.getFile() != null && a.getFile().isDirectory())
                {

                    return new FileSystemResource(this, name, a.getFile(), org.labkey.api.security.SecurityManager.getPolicy(ContainerManager.getForId(_parent.getContainerId())));
                }
                else
*/
                    return new AttachmentResource(this, _parent, a);
            }
            else
                return new AttachmentResource(this, _parent, name);
        }


        public Collection<String> listNames()
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


        public Collection<? extends WebdavResource> list()
        {
            Attachment[] attachments = getAttachments(_parent);
            ArrayList<WebdavResource> resources = new ArrayList<WebdavResource>(attachments.length);
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
            initSearch(name, displayTitle, cat);
        }


        private void initSearch(String name, @Nullable String displayTitle, @Nullable SearchService.SearchCategory cat)
        {
            setSearchProperty(SearchService.PROPERTY.searchTitle, FileUtil.getSearchTitle(name));
            setSearchProperty(SearchService.PROPERTY.displayTitle, null != displayTitle ? displayTitle : name);

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

            initSearch(name, null, null);
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
            return super.getExecuteHref(context);
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
        public void moveFrom(User user, WebdavResource r) throws IOException
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

        @Override
        public void setLastIndexed(long ms, long modified)
        {
            try
            {
            Table.execute(CoreSchema.getInstance().getSchema(), new SQLFragment(
                    "UPDATE core.Documents SET LastIndexed=? WHERE Parent=? and DocumentName=?",
                    new Object[]{new Date(ms), _parent.getEntityId(), _name}));
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
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

            FileContentService fileService = ServiceRegistry.get().getService(FileContentService.class);
            AttachmentService.Service svc = AttachmentService.get();

            File curRoot = fileService.getFileRoot(proj);
            if (null == curRoot)
            {
                fileService.setFileRoot(proj, webRoot);
                assertSameFile(fileService.getFileRoot(proj), webRoot);
            }
            AttachmentDirectory attachParent = fileService.getMappedAttachmentDirectory(folder, true);
            File attachDir = attachParent.getFileSystemDirectory();
            assertTrue(attachDir.exists());
            //assertSameFile(attachDir, new File(webRoot, "Test"));

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
            AttachmentDirectory namedParent = fileService.registerDirectory(folder, "test", otherDir.getCanonicalPath(), false);

            AttachmentDirectory namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNotNull(namedParentTest);
            assertSameFile(namedParentTest.getFileSystemDirectory(), namedParent.getFileSystemDirectory());

            svc.addAttachments(HttpView.currentContext().getUser(), namedParent, files);
            att = svc.getAttachments(namedParent);
            assertEquals(att.length, 1);
            assertTrue(att[0].getFile().exists());
            assertSameFile(new File(otherDir, "file.txt"), att[0].getFile());
            assertTrue(new File(otherDir, UPLOAD_LOG).exists());

            fileService.unregisterDirectory(folder, "test");
            namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNull(namedParentTest);

            //registering a directory doesn't make sure it exists
            AttachmentDirectory relativeParent = fileService.registerDirectory(folder, "relative", "subdir2", true);
            File relativeDir = relativeParent.getFileSystemDirectory();
            relativeDir.mkdirs();
            
            AttachmentDirectory relativeParentTest = fileService.getRegisteredDirectory(folder, "relative");
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

            //Need to reselect folder or its name is wrong!
            folder = ContainerManager.getForId(folder.getId());
            ContainerManager.delete(folder, null);
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
