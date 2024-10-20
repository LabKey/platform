/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.attachments.DocumentWriter;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.ResultSetView;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.MissingRootDirectoryException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationLogoAttachmentParent;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.DavException;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.core.admin.AdminController;
import org.labkey.core.query.AttachmentAuditProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class AttachmentServiceImpl implements AttachmentService, ContainerManager.ContainerListener
{
    private static final String UPLOAD_LOG = ".upload.log";
    private static final Map<String, AttachmentType> ATTACHMENT_TYPE_MAP = new HashMap<>();
    private static final Set<String> ATTACHMENT_COLUMNS = Set.of("Parent", "Container", "DocumentName", "DocumentSize", "DocumentType", "Created", "CreatedBy", "LastIndexed");

    public AttachmentServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }

    @Override
    public void download(HttpServletResponse response, AttachmentParent parent, String filename, @Nullable String alias, boolean inlineIfPossible) throws ServletException, IOException
    {
        if (null == filename || filename.isEmpty())
        {
            throw new NotFoundException();
        }

        boolean canInline = MimeMap.DEFAULT.canInlineFor(filename);

        // Default to rendering inline when possible, but let caller force download as an attachment
        boolean asAttachment = !canInline || !inlineIfPossible;

        response.reset();
        writeDocument(new ResponseWriter(response), parent, filename, alias, asAttachment);

        User user = null;
        try
        {
            ViewContext context = HttpView.currentContext();
            if (context != null)
                user = context.getUser();
        }
        catch (RuntimeException ignored)
        {
        }

        // Change in behavior added in 11.1:  no longer audit download events for the guest user
        if (null != user && !user.isGuest() && asAttachment)
        {
            addAuditEvent(user, parent, filename, "The attachment " + filename + " was downloaded");
        }
    }


    @Override
    public void download(HttpServletResponse response, AttachmentParent parent, String filename, boolean inlineIfPossible) throws ServletException, IOException
    {
        download(response, parent, filename, null, inlineIfPossible);
    }


    @Override
    public void addAuditEvent(User user, AttachmentParent parent, String filename, String comment)
    {
        if (user == null)
            throw new IllegalArgumentException("Cannot create attachment audit events for the null user.");

        if (parent != null)
        {
            Container c = ContainerManager.getForId(parent.getContainerId());
            AttachmentAuditProvider.AttachmentAuditEvent attachmentEvent = new AttachmentAuditProvider.AttachmentAuditEvent(c != null ? c.getId() : null, comment);

            attachmentEvent.setAttachmentParentEntityId(parent.getEntityId());
            attachmentEvent.setAttachment(filename);

            AuditLogService.get().addEvent(user, attachmentEvent);

            if (parent instanceof AttachmentDirectory)
            {
                FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(c != null ? c.getId() : null, comment);
                try
                {
                    event.setDirectory(((AttachmentDirectory)parent).getFileSystemDirectory().getPath());
                }
                catch (MissingRootDirectoryException ex)
                {
                    // UNDONE: AttachmentDirectory.getFileSystemPath()...
                    event.setDirectory("path not found");
                }
                event.setFile(filename);
                AuditLogService.get().addEvent(user, event);
            }
        }
    }

    @Override
    public HttpView getHistoryView(ViewContext context, AttachmentParent parent)
    {
        UserSchema schema = AuditLogService.getAuditLogSchema(context.getUser(), context.getContainer());
        if (schema != null)
        {
            checkSecurityPolicy(context.getUser(), parent);
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


    @Override
    public synchronized void addAttachments(AttachmentParent parent, List<AttachmentFile> files, @NotNull User user) throws IOException
    {
        if (null == user)
            throw new IllegalArgumentException("Cannot add attachments for the null user");

        if (null == files || files.isEmpty())
            return;

        List<String> duplicates = findDuplicates(files);
        if (duplicates.size() > 0)
        {
            throw new AttachmentService.DuplicateFilenameException(duplicates);
        }

        Set<String> filesToSkip = new TreeSet<>();
        File fileLocation = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;

        for (AttachmentFile file : files)
        {
            if (parent != null && exists(parent, file.getFilename()))
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
        HtmlString errorHtml = getErrorHtml(files);      // TODO: Get rid of getErrorHtml() -- use errors collection

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


    private @Nullable HtmlString getErrorHtml(List<AttachmentFile> files)
    {
        HtmlStringBuilder builder = HtmlStringBuilder.of();

        for (AttachmentFile file : files)
        {
            String error = file.getError();

            if (null != error)
                builder.append(error).unsafeAppend("<br><br>");
        }

        HtmlString html = builder.getHtmlString();

        return HtmlString.isEmpty(html) ? null : html;
    }


    public static class ErrorView extends JspView<Object>
    {
        public HtmlString errorHtml;
        public URLHelper returnURL;

        private ErrorView(HtmlString errorHtml, BindException errors, URLHelper returnURL)
        {
            super("/org/labkey/core/attachment/showErrors.jsp", new Object(), errors);
            this.errorHtml = errorHtml;
            this.returnURL = returnURL;
        }
    }


    @Override
    public void deleteAttachments(AttachmentParent parent)
    {
        deleteAttachments(Collections.singleton(parent));
    }

    @Override
    public void deleteAttachments(Collection<AttachmentParent> parents)
    {
        for (AttachmentParent parent : parents)
        {
            List<Attachment> atts = getAttachments(parent);

            // No attachments, or perhaps container doesn't match entityid
            if (atts.isEmpty())
                continue;

            checkSecurityPolicy(parent);   // Only check policy if there are attachments (a client may delete attachment and policy, but attempt to delete again)
            deleteIndexedAttachments(parent, atts);

            new SqlExecutor(coreTables().getSchema()).execute(sqlCascadeDelete(parent));
            if (parent instanceof AttachmentDirectory)
                ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), null);
            AttachmentCache.removeAttachments(parent);
        }
    }

    @Override
    public void deleteIndexedAttachments(List<String> parentIds)
    {
        TableSelector ts = new TableSelector(CoreSchema.getInstance().getTableInfoDocuments(),
                PageFlowUtil.set("Parent", "DocumentName"),
                new SimpleFilter(FieldKey.fromParts("Parent"), parentIds, CompareType.IN), null);

        try (ResultSet rs = ts.getResultSet())
        {
            while (rs.next())
            {
                deleteIndexedAttachment(rs.getString("Parent"), rs.getString("DocumentName"));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    public void clearLastIndexed(List<String> parentIds)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromParts("Parent"), parentIds))
            .addClause(new SimpleFilter.SQLClause("LastIndexed IS NOT NULL", null));
        SQLFragment sql = new SQLFragment("UPDATE core.Documents SET LastIndexed = NULL ")
            .append(filter.getSQLFragment(CoreSchema.getInstance().getSqlDialect()));
        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(sql);
    }

    private void deleteIndexedAttachment(AttachmentParent parent, String name)
    {
        deleteIndexedAttachment(parent.getEntityId(), name);
    }

    private void deleteIndexedAttachment(String parent, String name)
    {
        SearchService ss = SearchService.get();
        if (ss != null)
            ss.deleteResource(makeDocId(parent, name));
        new SqlExecutor(CoreSchema.getInstance().getSchema()).execute(new SQLFragment(
            "UPDATE core.Documents SET LastIndexed = NULL WHERE LastIndexed IS NOT NULL AND Parent = ? AND DocumentName = ?", parent, name)
        );
    }

    private void deleteIndexedAttachments(AttachmentParent parent, List<Attachment> atts)
    {
        for (Attachment att : atts)
            deleteIndexedAttachment(parent, att.getName());
    }

    @Override
    public void deleteIndexedAttachments(AttachmentParent parent)
    {
        List<Attachment> atts = getAttachments(parent);
        deleteIndexedAttachments(parent, atts);
    }

    private void _deleteAttachment(AttachmentParent parent, String name, @Nullable User auditUser)
    {
        checkSecurityPolicy(auditUser, parent);   // Only check policy if there are attachments (a client may delete attachment and policy, but attempt to delete again)
        deleteIndexedAttachment(parent, name);

        new SqlExecutor(coreTables().getSchema()).execute(sqlDelete(parent, name));
        if (parent instanceof AttachmentDirectory)
            ((AttachmentDirectory)parent).deleteAttachment(auditUser, name);

        if (null != auditUser)
            addAuditEvent(auditUser, parent, name, "The attachment " + name + " was deleted");
    }


    @Override
    public void deleteAttachment(AttachmentParent parent, String name, @Nullable User auditUser)
    {
        Attachment att = getAttachmentHelper(parent, name);

        if (null != att)
        {
            _deleteAttachment(parent, name, auditUser);
            AttachmentCache.removeAttachments(parent);
        }
    }

    @Override
    public void deleteAttachments(AttachmentParent parent, Collection<String> names, @Nullable User auditUser)
    {
        Map<String, Attachment> attachmentMap = getAttachments(parent, names);

        for (Attachment attachment : attachmentMap.values())
        {
            _deleteAttachment(parent, attachment.getName(), null);
        }

        AttachmentCache.removeAttachments(parent);
    }


    @Override
    public void renameAttachment(AttachmentParent parent, String oldName, String newName, User auditUser) throws IOException
    {
        File dir = null;
        File dest = null;
        File src = null;

        checkSecurityPolicy(auditUser, parent);
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

        // rename the file in the filesystem only if an Attachment directory and the db rename succeeded
        if (null != dir)
            src.renameTo(dest);

        AttachmentCache.removeAttachments(parent);

        addAuditEvent(auditUser, parent, newName, "The attachment " + oldName + " was renamed " + newName);
    }


    // Copies an attachment -- same container, same parent, but new name.  
    @Override
    public void copyAttachment(AttachmentParent parent, Attachment a, String newName, User auditUser) throws IOException
    {
        checkSecurityPolicy(auditUser, parent);
        a.setName(newName);
        DatabaseAttachmentFile file = new DatabaseAttachmentFile(a);
        addAttachments(parent, Collections.singletonList(file), auditUser);
    }

    @Override
    public void moveAttachments(Container newContainer, List<AttachmentParent> parents, User auditUser) throws IOException
    {
        SearchService ss = SearchService.get();
        for (AttachmentParent parent : parents)
        {
            checkSecurityPolicy(auditUser, parent);
            int rowsChanged = new SqlExecutor(coreTables().getSchema()).execute(sqlMove(parent, newContainer));
            if (rowsChanged > 0)
            {
                List<Attachment> atts = getAttachments(parent);
                String filename;
                for (Attachment att : atts)
                {
                    filename = att.getName();
                    if (parent instanceof AttachmentDirectory parentDir)
                    {
                        File currentDir = parentDir.getFileSystemDirectoryPath().toFile();
                        File newDir =  parentDir.getFileSystemDirectoryPath(newContainer, true).toFile();
                        File src = new File(currentDir, filename);
                        File dest = new File(newDir, filename);
                        if (!src.exists())
                            throw new FileNotFoundException(src.getAbsolutePath());
                        if (dest.exists())
                            throw new AttachmentService.DuplicateFilenameException(dest.getAbsolutePath());
                    }
                    deleteIndexedAttachment(parent, filename);
                    addAuditEvent(auditUser, parent, filename, "The attachment " + filename + " was moved");
                }
                AttachmentCache.removeAttachments(parent);
            }
        }
    }

    /** may return fewer AttachmentFile than Attachment, if there have been deletions */
    @Override
    public @NotNull List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException
    {
        checkSecurityPolicy(parent);
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
        return null != getAttachmentHelper(parent, filename);
    }

    private List<String> findDuplicates(List<AttachmentFile> files)
    {
        Set<String> fileNames = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (AttachmentFile file : files)
        {
            if (!fileNames.add(file.getFilename()))
            {
                duplicates.add(file.getFilename());
            }
        }
        return duplicates;
    }

    @Override
    public @NotNull List<Attachment> getAttachments(AttachmentParent parent)
    {
        checkSecurityPolicy(parent);
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

        File[] fileList = parentDir.listFiles(file -> !file.isDirectory() && !(file.getName().charAt(0) == '.') && !file.isHidden());

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
    @Override
    public List<Pair<String,String>> listAttachmentsForIndexing(Collection<String> parents, Date modifiedSince)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Parent"), parents, CompareType.IN);
        var since = new SearchService.LastIndexedClause(coreTables().getTableInfoDocuments(), modifiedSince, null);

        if (!since.isEmpty())
            filter.addClause(since);

        final ArrayList<Pair<String,String>> ret = new ArrayList<>();

        new TableSelector(coreTables().getTableInfoDocuments(),
                    PageFlowUtil.set("Parent", "DocumentName", "LastIndexed"),
                    filter,
                    new Sort("+Created")).forEach(rs -> {
                        String parent = rs.getString(1);
                        String name = rs.getString(2);
                        Date last = rs.getTimestamp(3);
                        if (last != null && last.getTime() == SearchService.failDate.getTime())
                            return;
                        ret.add(new Pair<>(parent, name));
                    });

        return ret;
    }

    /** Collection resource with all attachments for this parent */
    @Override
    public WebdavResource getAttachmentResource(Path path, AttachmentParent parent)
    {
        // NOTE parent does not supply ACL, but should?
        // acl = parent.getAcl()
        checkSecurityPolicy(parent);
        Container c = ContainerManager.getForId(parent.getContainerId());
        if (null == c)
            return null;

        return new AttachmentCollection(path, parent, c);
    }

    @Override
    public WebdavResource getDocumentResource(Path path, ActionURL downloadURL, String displayTitle, AttachmentParent parent, String name, SearchService.SearchCategory cat)
    {
        checkSecurityPolicy(parent);
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

    @Override
    public void registerAttachmentType(AttachmentType type)
    {
        ATTACHMENT_TYPE_MAP.put(type.getUniqueName(), type);
    }

    @Override
    public HttpView getAdminView(ActionURL currentUrl)
    {
        String requestedType = currentUrl.getParameter("type");
        AttachmentType attachmentType = null != requestedType ? ATTACHMENT_TYPE_MAP.get(requestedType) : null;

        if (null == attachmentType)
        {
            boolean findAttachmentParents = "1".equals(currentUrl.getParameter("find"));

            // The first query lists all the attachment types and the attachment counts for each. A separate select from
            // core.Documents for each type is needed to associate the Type values with the associated rows.
            List<SQLFragment> selectStatements = new LinkedList<>();

            for (AttachmentType type : ATTACHMENT_TYPE_MAP.values())
            {
                SQLFragment selectStatement = new SQLFragment();

                // Adding unique column RowId ensures we get the proper count
                selectStatement.append("SELECT RowId, CAST(").appendValue(type.getUniqueName()).append(" AS VARCHAR(500)) AS Type FROM ")
                    .append(CoreSchema.getInstance().getTableInfoDocuments(), "d")
                    .append(" WHERE ");
                addAndVerifyWhereSql(type, selectStatement);
                selectStatement.append("\n");

                selectStatements.add(selectStatement);
            }

            SQLFragment allSql = new SQLFragment("SELECT Type, COUNT(*) AS Count FROM (\n");
            allSql.append(SQLFragment.join(selectStatements, "UNION\n"));
            allSql.append(") u\nGROUP BY Type\nORDER BY Type");
            ActionURL linkUrl = currentUrl.clone().deleteParameters().addParameter("type", null);

            // The second query shows all attachments that we can't associate with a type. We just need to assemble a big
            // WHERE NOT clause that ORs the conditions from every registered type.
            SQLFragment whereSql = new SQLFragment();
            String sep = "";

            for (AttachmentType type : ATTACHMENT_TYPE_MAP.values())
            {
                whereSql.append(sep);
                sep = " OR";
                whereSql.append("\n(");
                addAndVerifyWhereSql(type, whereSql);
                whereSql.append(")");
            }

            SQLFragment unknownSql = new SQLFragment("SELECT d.Container, c.Name, d.Parent, d.DocumentName");

            if (findAttachmentParents)
                unknownSql.append(", e.TableName");

            unknownSql.append(" FROM core.Documents d\n");
            unknownSql.append("INNER JOIN core.Containers c ON c.EntityId = d.Container\n");

            Set<String> schemasToIgnore = Sets.newCaseInsensitiveHashSet(currentUrl.getParameterValues("ignore"));

            if (findAttachmentParents)
            {
                unknownSql.append("LEFT OUTER JOIN (\n");
                addSelectAllEntityIdsSql(unknownSql, schemasToIgnore);
                unknownSql.append(") e ON e.EntityId = d.Parent\n");
            }

            unknownSql.append("WHERE NOT (");
            unknownSql.append(whereSql);
            unknownSql.append(")\n");
            unknownSql.append("ORDER BY Container, Parent, DocumentName");

            WebPartView unknownView = getResultSetView(unknownSql, "Unknown Attachments", null);
            NavTree navMenu = new NavTree();

            if (!findAttachmentParents)
            {
                navMenu.addChild(new NavTree("Search for Attachment Parents (Be Patient)",
                    new ActionURL(AdminController.AttachmentsAction.class, ContainerManager.getRoot()).addParameter("find", 1).addParameter("ignore", "Audit"))
                );
            }
            else
            {
                navMenu.addChild(new NavTree("Remove TableName Column",
                        new ActionURL(AdminController.AttachmentsAction.class, ContainerManager.getRoot()))
                );

                if (schemasToIgnore.isEmpty())
                {
                    navMenu.addChild(new NavTree("Ignore Audit Schema",
                            new ActionURL(AdminController.AttachmentsAction.class, ContainerManager.getRoot()).addParameter("find", 1).addParameter("ignore", "Audit"))
                    );
                }
                else
                {
                    navMenu.addChild(new NavTree("Include All Schemas",
                            new ActionURL(AdminController.AttachmentsAction.class, ContainerManager.getRoot()).addParameter("find", 1))
                    );
                }
            }
            unknownView.setNavMenu(navMenu);

            return new VBox(getResultSetView(allSql, "Attachment Types and Counts", linkUrl), unknownView);
        }
        else
        {
            // This query lists all the documents associated with a single type.
            SQLFragment oneTypeSql = new SQLFragment("SELECT d.Container, c.Name, d.Parent, d.DocumentName FROM core.Documents d\n" +
                "INNER JOIN core.Containers c ON c.EntityId = d.Container\n" +
                "WHERE ");
            addAndVerifyWhereSql(attachmentType, oneTypeSql);
            oneTypeSql.append("\nORDER BY Container, Parent, DocumentName");

            return getResultSetView(oneTypeSql, attachmentType.getUniqueName() + " Attachments", null);
        }
    }

    private void addAndVerifyWhereSql(AttachmentType attachmentType, SQLFragment sql)
    {
        int initialLength = sql.length();
        attachmentType.addWhereSql(sql, "d.Parent", "d.DocumentName");
        if (initialLength == sql.length())
            throw new UnsupportedOperationException("AttachmentType: '" + attachmentType.getUniqueName() + "' did not update attachment WHERE clause.");
    }

    @Override
    // Joins each row of core.Documents to the table(s) (if any) that contain an entityid matching the document's parent
    public HttpView getFindAttachmentParentsView()
    {
        SQLFragment sql = new SQLFragment("SELECT RowId, CreatedBy, Created, ModifiedBy, Modified, Container, DocumentName, TableName FROM core.Documents LEFT OUTER JOIN (\n");
        addSelectAllEntityIdsSql(sql, Sets.newCaseInsensitiveHashSet("Audit"));
        sql.append(") c ON EntityId = Parent\nORDER BY TableName, DocumentName, Container");

        return getResultSetView(sql, "Probable Attachment Parents", null);
    }

    // Creates a two-column query of ID and table name that selects from every possible attachment parent column in the labkey database:
    // - Enumerate all tables in all schemas in the labkey scope
    // - Enumerate columns and identify potential attachment parents (currently, EntityId columns and ObjectIds extracted from LSIDs)
    // - Create a UNION query that selects the candidate ids along with a constant column that lists the table name
    private void addSelectAllEntityIdsSql(SQLFragment sql, Set<String> userRequestedSchemasToIgnore)
    {
        List<String> selectStatements = new LinkedList<>();
        Set<String> schemasToIgnore = Sets.newCaseInsensitiveHashSet(userRequestedSchemasToIgnore);

        // Temp schema causes problems because materialized tables disappear but stay in the cached list. This is probably a bug with
        // MaterializedQueryHelper... it should clear the temp DbSchema when it deletes a temp table. TODO: fix MQH & remove this workaround
        schemasToIgnore.add("temp");

        DbScope.getLabKeyScope().getSchemaNames().stream()
            .filter(schemaName->!schemasToIgnore.contains(schemaName)) // Exclude unwanted schema names
            .map(schemaName->DbSchema.get(schemaName, DbSchemaType.Bare))
            .forEach(schema-> schema.getTableNames().stream()
                .map(schema::getTable)
                .filter(table->table.getTableType() == DatabaseTableType.TABLE) // We just want the underlying tables (no views or virtual tables)
                .map(SchemaTableInfo::getColumns)
                .flatMap(Collection::stream)
                .filter(ColumnRenderProperties::isStringType)
                .forEach(c->addSelectStatement(selectStatements, c))
            );

        sql.append(StringUtils.join(selectStatements, "    UNION\n"));
    }

    private void addSelectStatement(List<String> selectStatements, ColumnInfo column)
    {
        String expression;
        String where = null;

        if (StringUtils.containsIgnoreCase(column.getName(), "EntityId"))
        {
            expression = column.getSelectName();
        }
        else if (StringUtils.endsWithIgnoreCase(column.getName(), "LSID"))
        {
            Pair<String, String> pair = Lsid.getSqlExpressionToExtractObjectId(column.getSelectName(), column.getSqlDialect());
            expression = pair.first;
            where = pair.second;
        }
        else
        {
            return;
        }

        TableInfo table = column.getParentTable();
        selectStatements.add("    SELECT " + expression + " AS EntityId, " + table.getSqlDialect().quoteStringLiteral(table.getSelectName()) + " AS TableName FROM " + table.getSelectName() + (null != where ? " WHERE " + where : "") + "\n");
    }

    private WebPartView getResultSetView(SQLFragment sql, String title, @Nullable ActionURL linkUrl)
    {
        SqlSelector selector = new SqlSelector(DbScope.getLabKeyScope(), sql);
        ResultSet rs = selector.getResultSet();

        return null != linkUrl ? new ResultSetView(rs, title, "Type", linkUrl) : new ResultSetView(rs, title);
    }

    @Override
    public @Nullable Attachment getAttachment(AttachmentParent parent, String name)
    {
        checkSecurityPolicy(parent);
        return getAttachmentHelper(parent, name);
    }

    @Override
    public Map<String, Attachment> getAttachments(AttachmentParent parent, Collection<String> names)
    {
        checkSecurityPolicy(parent);

        if (names == null || names.isEmpty())
            return Collections.emptyMap();

        Map<String, Attachment> attachments = new HashMap<>();

        if (parent instanceof AttachmentDirectory)
        {
            for (Attachment attachment : getAttachments(parent))
                if (names.contains(attachment.getName()))
                    attachments.put(attachment.getName(), attachment);
        }
        else
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Parent"), parent.getEntityId());
            filter.addCondition(FieldKey.fromParts("DocumentName"), names, CompareType.IN);
            // Note: we are intentionally skipping the AttachmentCache here. If we hit the cache here while getting
            // attachments from the global attachment parent we'll load every single attachment into the cache first,
            // which could be very expensive on servers that have a ton of attachments.
            List<Attachment> attachmentsList = new TableSelector(CoreSchema.getInstance().getTableInfoDocuments(),
                    ATTACHMENT_COLUMNS,
                    filter,
                    new Sort("+RowId")).getArrayList(Attachment.class);

            for (Attachment attachment : attachmentsList)
                attachments.put(attachment.getName(), attachment);
        }

        return attachments;
    }

    private @Nullable Attachment getAttachmentHelper(AttachmentParent parent, String name)
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


    @Override
    public void containerCreated(Container c, User user)
    {
    }


    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent)
    {
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        // TODO: do we need to get each document and remove its security policy?
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

    private void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, @Nullable String alias, boolean asAttachment) throws ServletException, IOException
    {
        checkSecurityPolicy(parent);
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        DbSchema schema = coreTables().getSchema();

        if (alias == null)
            alias = name;

        try (Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
        {
            // we don't want a RowSet, so execute directly (not Table.executeQuery())
            conn = schema.getScope().getConnection();
            if (null == parent.getEntityId())
                stmt = Table.prepareStatement(conn, sqlRootDocument(), Collections.singletonList(name), jdbcParameters);
            else
                stmt = Table.prepareStatement(conn, sqlDocument(), Arrays.asList(parent.getContainerId(), parent.getEntityId(), name), jdbcParameters);

            rs = stmt.executeQuery();

            OutputStream out;
            InputStream s;

            if (parent instanceof AttachmentDirectory)
            {
                File parentDir = ((AttachmentDirectory) parent).getFileSystemDirectory();
                if (!parentDir.exists())
                    throw new NotFoundException("No parent directory for downloaded file " + alias + ". Please contact an administrator.");
                File file = new File(parentDir, name);
                if (!file.exists())
                    throw new NotFoundException("Could not find file " + alias);

                if (asAttachment)
                    writer.setContentDisposition(ContentDisposition.attachment().filename(alias, StandardCharsets.UTF_8).build());
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
                    writer.setContentDisposition(ContentDisposition.builder("attachment").filename(alias, StandardCharsets.UTF_8).build());
                else
                    writer.setContentDisposition(ContentDisposition.builder("inline").filename(alias, StandardCharsets.UTF_8).build());

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
                IOUtils.copy(s, out);
            }
            finally
            {
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
            if (conn != null)
            {
                schema.getScope().releaseConnection(conn);
            }
        }
    }

    // CONSIDER: Return success/failure notification so caller can take action (render a default document) in all the failure scenarios.
    @Override
    public void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, boolean asAttachment) throws ServletException, IOException
    {
        writeDocument(writer, parent, name, null, asAttachment);
    }


    @Override
    @NotNull
    public InputStream getInputStream(AttachmentParent parent, String name) throws FileNotFoundException
    {
        checkSecurityPolicy(parent);
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        final DbSchema schema = coreTables().getSchema();

        try (Parameter.ParameterList jdbcParameters = new Parameter.ParameterList())
        {
            // we don't want a RowSet, so execute directly (not Table.executeQuery())
            conn = schema.getScope().getConnection();
            if (null == parent.getEntityId())
                stmt = Table.prepareStatement(conn, sqlRootDocument(), Collections.singletonList(name), jdbcParameters);
            else
                stmt = Table.prepareStatement(conn, sqlDocument(), Arrays.asList(parent.getContainerId(), parent.getEntityId(), name), jdbcParameters);

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
                    @Override
                    public void close() throws IOException
                    {
                        ResultSetUtil.close(frs);
                        ResultSetUtil.close(fstmt);
                        schema.getScope().releaseConnection(fconn);
                        super.close();
                    }

                    // slight hack here to get the size cheaply
                    @Override
                    public int available()
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
            ResultSetUtil.close(rs);
            ResultSetUtil.close(stmt);
            if (null != conn) schema.getScope().releaseConnection(conn);
        }
    }


    private CoreSchema coreTables()
    {
        return CoreSchema.getInstance();
    }

    private String sqlDocument()
    {
        return "SELECT DocumentType, DocumentSize, Document FROM " + coreTables().getTableInfoDocuments() + " WHERE Container = ? AND Parent = ? AND DocumentName = ?";
    }

    private String sqlRootDocument()
    {
        return "SELECT DocumentType, DocumentSize, Document FROM " + coreTables().getTableInfoDocuments() + " WHERE Parent IS NULL AND DocumentName = ?";
    }

    private SQLFragment sqlCascadeDelete(AttachmentParent parent)
    {
        return new SQLFragment("DELETE FROM " + coreTables().getTableInfoDocuments() + " WHERE Container = ? AND Parent = ?", parent.getContainerId(), parent.getEntityId());
    }

    private SQLFragment sqlDelete(AttachmentParent parent, String name)
    {
        return new SQLFragment("DELETE FROM " + coreTables().getTableInfoDocuments() + " WHERE Container = ? AND Parent = ? AND DocumentName = ?", parent.getContainerId(), parent.getEntityId(), name);
    }

    private SQLFragment sqlRename(AttachmentParent parent, String oldName, String newName)
    {
        return new SQLFragment("UPDATE " + coreTables().getTableInfoDocuments() + " SET DocumentName = ? WHERE Container = ? AND Parent = ? AND DocumentName = ?",
                newName, parent.getContainerId(), parent.getEntityId(), oldName);
    }

    private SQLFragment sqlMove(AttachmentParent parent, Container newContainer)
    {
        // TODO: consider an inClause
        return new SQLFragment("UPDATE " + coreTables().getTableInfoDocuments() + " SET Container = ? WHERE Container = ? AND Parent=?",
                newContainer.getEntityId(), parent.getContainerId(), parent.getEntityId());
    }

    private static class ResponseWriter implements DocumentWriter
    {
        private HttpServletResponse _response;

        public ResponseWriter(HttpServletResponse response)
        {
            _response = response;
        }

        @Override
        public void setContentType(String contentType)
        {
            _response.setContentType(contentType);
        }

        @Override
        public void setContentDisposition(ContentDisposition value)
        {
            ResponseHelper.setContentDisposition(_response, value);
        }

        @Override
        public void setContentLength(int size)
        {
            _response.setContentLength(size);
        }

        @Override
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
        private final AttachmentParent _parent;

        AttachmentCollection(Path path, AttachmentParent parent, SecurableResource resource)
        {
            super(path);
            _parent = parent;
            setSecurableResource(resource);
        }


        @Override
        public boolean exists()
        {
            FileContentService svc = FileContentService.get();
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


        @Override
        public WebdavResource find(Path.Part name)
        {
            Attachment a = getAttachment(_parent, name.toString());

            if (null != a)
                return new AttachmentResource(this, _parent, a);
            else
                return new AttachmentResource(this, _parent, name.toString());
        }


        @Override
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


        @Override
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
        return makeDocId(parent.getEntityId(), name);
    }

    private static String makeDocId(String parentId, String name)
    {
        return "attachment:/" + parentId + "/" + PageFlowUtil.encode(name);
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
                setSecurableResource(c);
            _downloadUrl = downloadURL;
            _parent = parent;
            _name = name;
            _docid = makeDocId(parent,name);
            initSearchProperties(name, displayTitle, cat);
        }

        @Override
        public boolean allowInline()
        {
            if (isFile() && "text/html".equals(getContentType()))
                return false;
            return super.allowInline();
        }

        private void initSearchProperties(String name, @Nullable String displayTitle, @Nullable SearchService.SearchCategory cat)
        {
            setSearchProperty(SearchService.PROPERTY.keywordsMed, FileUtil.getSearchKeywords(name));
            setSearchProperty(SearchService.PROPERTY.title, null != displayTitle ? displayTitle : name);

            if (null == cat)
                setSearchCategory(SearchService.fileCategory);
            else
                setSearchProperty(SearchService.PROPERTY.categories, SearchService.fileCategory.toString() + StringUtils.capitalize(cat.toString()));
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
                setSecurableResource(c);
            _name = name;
            _parent = parent;
            _docid = makeDocId(parent,name);

            initSearchProperties(name, null, null);
        }


        @Override
        public String getDocumentId()
        {
            return makeDocId(_parent, _name);
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

        @Override
        public boolean exists()
        {
            Attachment r = getAttachment();
            if (r == null)
                return false;
            if (null != r.getFile())
                return r.getFile().exists();
            return true;
        }

        @Override
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
        public boolean delete(User user)
        {
            if (user != null && !canDelete(user, true, null))
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

        @Override
        public InputStream getInputStream(User user) throws IOException
        {
            return AttachmentService.get().getInputStream(_parent, _name);
        }

        @Override
        public void moveFrom(User user, WebdavResource r) throws IOException, DavException
        {
            if (r instanceof AttachmentResource)
            {
                AttachmentResource from = (AttachmentResource) r;
                if (from._parent == _parent)
                {
                    renameAttachment(_parent, from.getName(), getName(), user);
                    return;
                }
            }
            super.moveFrom(user, r);
        }

        @Override
        public long copyFrom(User user, final FileStream in) throws IOException
        {
            try
            {
                AttachmentFile file =  new AttachmentFile()
                {
                    @Override
                    public long getSize() throws IOException
                    {
                        return in.getSize();
                    }

                    @Override
                    public String getError()
                    {
                        return null;
                    }

                    @Override
                    public String getFilename()
                    {
                        return getName();
                    }

                    @Override
                    public String getContentType()
                    {
                        return PageFlowUtil.getContentTypeFor(getFilename());
                    }

                    @Override
                    public InputStream openInputStream() throws IOException
                    {
                        return in.openInputStream();
                    }

                    @Override
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
                throw new IOException(x);
            }
            finally
            {
                in.closeInputStream();
            }
            // UNDONE return real length if anyone cares
            return 0;
        }

        @Override
        public WebdavResource parent()
        {
            return _folder;
        }

        @Override
        public long getCreated()
        {
            return _created;
        }

        @Override
        public User getCreatedBy()
        {
            return _createdBy;
        }

        @Override
        public long getLastModified()
        {
            return getCreated();
        }

        @Override
        public User getModifiedBy()
        {
            return _createdBy;
        }

        @Override
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

        @Override
        public String getAbsolutePath(User user)
        {
            Container container = null != getContainerId() ? ContainerManager.getForId(getContainerId()) : null;
            if (null != container && SecurityManager.canSeeFilePaths(container, user))
            {
                File file = getFile();
                return null != file ? file.getAbsolutePath() : null;
            }
            return null;
        }


        @Override
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

    private void checkSecurityPolicy(AttachmentParent attachmentParent) throws UnauthorizedException
    {
        // No-op: AttachmentParent no longer provides getSecurityPolicy()
    }

    private void checkSecurityPolicy(User user, AttachmentParent attachmentParent) throws UnauthorizedException
    {
        // No-op: AttachmentParent no longer provides getSecurityPolicy()
    }

    //
    //JUnit TestCase
    //

    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private static final String _testDirName = "/_jUnitAttachment";

        @Test
        public void testDirectories() throws IOException
        {
            User user = TestContext.get().getUser();
            assertNotNull("Should have access to a user", user);

            // clean up if anything was left over from last time
            if (null != ContainerManager.getForPath(_testDirName))
                ContainerManager.deleteAll(ContainerManager.getForPath(_testDirName), user);

            Container proj = ContainerManager.ensureContainer(_testDirName, TestContext.get().getUser());
            Container folder = ContainerManager.ensureContainer(_testDirName + "/Test", TestContext.get().getUser());

            FileContentService fileService = FileContentService.get();
            AttachmentService svc = AttachmentService.get();

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
            AttachmentDirectory relativeParent = fileService.registerDirectory(folder, "relative", FileUtil.getAbsoluteCaseSensitiveFile(relativeDir).getAbsolutePath(), false);
            
            AttachmentDirectory relativeParentTest = fileService.getRegisteredDirectory(folder, "relative");
            assertNotNull(relativeParentTest);
            assertSameFile(relativeParentTest.getFileSystemDirectory(), relativeParent.getFileSystemDirectory());

            svc.addAttachments(relativeParent, files, user);
            att = svc.getAttachments(relativeParent);
            assertEquals(att.size(), 1);

            File expectedFile1 = att.get(0).getFile();
            File expectedFile2 = new File(relativeDir, UPLOAD_LOG);

            assertTrue(expectedFile1.exists());
            assertEquals(new File(relativeDir, "file.txt"), expectedFile1);
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

        private void testFileAttachmentFiles(File file1, File file2, User user) throws IOException
        {
            AttachmentFile aFile1 = new FileAttachmentFile(file1);
            AttachmentFile aFile2 = new FileAttachmentFile(file2);

            AttachmentService service = AttachmentService.get();
            AttachmentParent root = AuthenticationLogoAttachmentParent.get();
			service.deleteAttachment(root, file1.getName(), user);
            service.deleteAttachment(root, file2.getName(), user);

            List<Attachment> attachments = service.getAttachments(root);
            int originalCount = attachments.size();

            service.addAttachments(root, Arrays.asList(aFile1, aFile2), user);
            attachments = service.getAttachments(root);
            assertEquals((originalCount + 2), attachments.size());

            service.deleteAttachment(root, file1.getName(), user);
            attachments = service.getAttachments(root);
            assertEquals((originalCount + 1), attachments.size());

            service.deleteAttachment(root, file2.getName(), user);
            attachments = service.getAttachments(root);
            assertEquals(originalCount, attachments.size());
        }
    }
}
