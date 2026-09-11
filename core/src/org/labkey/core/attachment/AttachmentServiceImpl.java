/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.attachments.DocumentWriter;
import org.labkey.api.attachments.FileAttachmentFile;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.FileSystemAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilter.AllFolders;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.PropertySchema;
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
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.AuthenticationLogoAttachmentParent;
import org.labkey.api.security.ElevatedUser;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.TroubleshooterRole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.AbstractDocumentResource;
import org.labkey.api.webdav.AbstractWebdavResourceCollection;
import org.labkey.api.webdav.DavException;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.core.query.AttachmentAuditProvider;
import org.labkey.core.query.AttachmentAuditProvider.AttachmentAuditEvent;
import org.labkey.core.query.CoreQuerySchema;
import org.springframework.http.ContentDisposition;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class AttachmentServiceImpl implements AttachmentService
{
    private static final Logger LOG = LogHelper.getLogger(AttachmentServiceImpl.class, "Orphaned attachments");
    private static final String UPLOAD_LOG = ".upload.log";
    private static final Map<String, AttachmentParentType> ATTACHMENT_TYPE_MAP = new HashMap<>();
    private static final Set<String> ATTACHMENT_COLUMNS = Set.of("Parent", "Container", "DocumentName", "DocumentSize", "DocumentType", "Created", "CreatedBy", "LastIndexed");

    // Maximum number of deferred AttachmentCache invalidations one transaction will accumulate before falling
    // back to clearing the whole cache. See invalidateCache().
    private static final int MAX_DEFERRED_CACHE_INVALIDATIONS = 1000;

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
            AttachmentAuditEvent attachmentEvent = new AttachmentAuditEvent(c == null ? ContainerManager.getRoot() : c, comment);

            attachmentEvent.setAttachmentParentEntityId(parent.getEntityId());
            attachmentEvent.setParentType(parent.getAttachmentParentType().getUniqueName());
            attachmentEvent.setAttachment(filename);

            AuditLogService.get().addEvent(user, attachmentEvent);

            if (parent instanceof AttachmentDirectory adParent)
            {
                FileSystemAuditProvider.FileSystemAuditEvent event = new FileSystemAuditProvider.FileSystemAuditEvent(c, comment);
                event.setDirectory(adParent.getFileSystemDirectory().getPath());

                event.setFile(filename);
                AuditLogService.get().addEvent(user, event);
            }
        }
    }

    @Override
    public HttpView<?> getHistoryView(ViewContext context, AttachmentParent parent, BindException errors)
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

            QueryView view = schema.createView(context, settings, errors);
            view.setTitle("Attachments History:");

            return view;
        }
        return null;
    }

    private void validateAttachmentSize(AttachmentFile file) throws IOException
    {
        int maxSize = AppProps.getInstance().getMaxBLOBSize();
        if (file.getSize() > maxSize)
        {
            throw new AttachmentService.FileTooLargeException(file, maxSize);
        }
    }

    @Override
    public void validateAttachmentSizes(AttachmentParent parent, List<AttachmentFile> files) throws IOException
    {
        File fileLocation = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;

        // Only validate if we're putting the file in the database
        if (null == fileLocation)
        {
            for (AttachmentFile file : files)
            {
                validateAttachmentSize(file);
            }
        }
    }


    @Override
    public synchronized void addAttachments(AttachmentParent parent, List<AttachmentFile> files, @NotNull User user) throws IOException
    {
        if (null == user)
            throw new IllegalArgumentException("Cannot add attachments for the null user");

        if (null == files || files.isEmpty())
            return;

        List<String> duplicates = findDuplicates(files);
        if (!duplicates.isEmpty())
        {
            throw new AttachmentService.DuplicateFilenameException(duplicates);
        }

        Set<String> filesToSkip = new TreeSet<>();
        File fileLocation = parent instanceof AttachmentDirectory dir ? dir.getFileSystemDirectory() : null;

        // Resolve which of these files already exist with a single query instead of a cached read per file.
        Map<String, Attachment> existingAttachments = null == parent ? Collections.emptyMap() :
                getAttachments(parent, files.stream().map(AttachmentFile::getFilename).toList());

        for (AttachmentFile file : files)
        {
            if (existingAttachments.containsKey(file.getFilename()))
            {
                filesToSkip.add(file.getFilename());
                continue;
            }

            HashMap<String, Object> hm = new HashMap<>();
            if (null == fileLocation)
            {
                validateAttachmentSize(file);
                hm.put("Document", file);
            }
            else
                ((AttachmentDirectory)parent).addAttachment(user, file);

            hm.put("DocumentName", file.getFilename());
            hm.put("DocumentSize", file.getSize());
            hm.put("DocumentType", file.getContentType());
            hm.put("Parent", parent.getEntityId());
            hm.put("ParentType", parent.getAttachmentParentType().getUniqueName());
            hm.put("Container", parent.getContainerId());
            Table.insert(user, coreTables().getTableInfoDocuments(), hm);

            addAuditEvent(user, parent, file.getFilename(), "The attachment " + file.getFilename() + " was added");
        }

        invalidateCache(parent);

        if (!filesToSkip.isEmpty())
            throw new AttachmentService.DuplicateFilenameException(filesToSkip);
    }

    @Override
    public ErrorView getErrorView(List<AttachmentFile> files, BindException errors, URLHelper returnUrl)
    {
        boolean hasErrors = null != errors && errors.hasErrors();
        HtmlString errorHtml = getErrorHtml(files);      // TODO: Get rid of getErrorHtml() -- use errors collection

        if (null == errorHtml && !hasErrors)
            return null;

        try
        {
            return new ErrorView(errorHtml, errors, returnUrl);
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
        public URLHelper returnUrl;

        private ErrorView(HtmlString errorHtml, BindException errors, URLHelper returnUrl)
        {
            super("/org/labkey/core/attachment/showErrors.jsp", new Object(), errors);
            this.errorHtml = errorHtml;
            this.returnUrl = returnUrl;
        }
    }


    @Override
    public void deleteAttachments(AttachmentParent parent)
    {
        deleteAttachments(Collections.singleton(parent));
    }

    /**
     * Returns a parent's attachments without reading through the AttachmentCache. Bulk deletes call this once per
     * parent inside a single transaction -- ExperimentServiceImpl.truncateDataClass builds one AttachmentParent per
     * data class row, and IssueManager one per comment -- so reading through the cache there fills the transaction's
     * private cache and queues a post-commit reload task for every parent, work that the removal below immediately
     * discards. AttachmentDirectory parents still take the cached path, which reconciles the database rows against
     * the file system.
     */
    private @NotNull List<Attachment> getAttachmentsForDelete(AttachmentParent parent)
    {
        if (parent instanceof AttachmentDirectory)
            return getAttachments(parent);

        checkSecurityPolicy(parent);

        return new TableSelector(CoreSchema.getInstance().getTableInfoDocuments(),
                ATTACHMENT_COLUMNS,
                new SimpleFilter(FieldKey.fromParts("Parent"), parent.getEntityId()),
                new Sort("+RowId")).getArrayList(Attachment.class);
    }

    @Override
    public void deleteAttachments(Collection<AttachmentParent> parents)
    {
        for (AttachmentParent parent : parents)
        {
            List<Attachment> atts = getAttachmentsForDelete(parent);

            // No attachments, or perhaps container doesn't match entityid
            if (atts.isEmpty())
                continue;

            checkSecurityPolicy(parent);   // Only check policy if there are attachments (a client may delete attachment and policy, but attempt to delete again)
            deleteIndexedAttachments(parent, atts);

            new SqlExecutor(coreTables().getSchema()).execute(sqlCascadeDelete(parent));
            if (parent instanceof AttachmentDirectory)
                ((AttachmentDirectory)parent).deleteAttachment(HttpView.currentContext().getUser(), null);
            invalidateCache(parent);
        }
    }

    /**
     * Invalidates a mutated parent's AttachmentCache entry, bounding how many deferred invalidations a single
     * transaction can accumulate. Inside a transaction, DatabaseCache defers each key removal to the commit as its
     * own task (see TransactionCache.remove()), and several callers reach this once per parent inside a single
     * transaction: ExperimentServiceImpl.truncateDataClass builds one AttachmentParent per data class row,
     * IssueManager one per comment, and AttachmentDataIterator adds and deletes attachments per imported row. A
     * large operation would otherwise retain a task per parent for the life of the transaction. Past
     * MAX_DEFERRED_CACHE_INVALIDATIONS we clear the whole cache once instead, trading precision for a bound on
     * memory; over-invalidating only costs cache warmth, never correctness.
     */
    private void invalidateCache(AttachmentParent parent)
    {
        DbScope.Transaction tx = CoreSchema.getInstance().getScope().getCurrentTransaction();

        // Outside a transaction each removal happens immediately and retains nothing, so there's nothing to bound
        if (null == tx)
        {
            AttachmentCache.removeAttachments(parent);
            return;
        }

        DeferredInvalidationCounter counter = tx.addCommitTask(new DeferredInvalidationCounter(), DbScope.CommitTaskOption.POSTCOMMIT);

        if (counter.removeIndividually())
            AttachmentCache.removeAttachments(parent);
        else if (counter.shouldClear())
            AttachmentCache.clear();
    }

    /**
     * Transaction-scoped counter of deferred AttachmentCache removals. Coalesced via addCommitTask()'s equals()-based
     * dedup, which hands back the task already registered for this transaction; the same approach
     * DatabaseCache.BlockingDatabaseCache.CacheReloadCounterTask uses to bound its own post-commit work. run() is a
     * no-op -- the task exists only to carry the count.
     */
    private static class DeferredInvalidationCounter implements Runnable
    {
        private int _count;
        private boolean _cleared;

        @Override
        public void run()
        {
            // No-op: this task exists only to hold a transaction-scoped count
        }

        /** @return true if the caller should invalidate this parent's key individually */
        private boolean removeIndividually()
        {
            return !_cleared && ++_count <= MAX_DEFERRED_CACHE_INVALIDATIONS;
        }

        /** @return true if the caller should clear the whole cache, i.e., only on the call that crosses the limit */
        private boolean shouldClear()
        {
            if (_cleared)
                return false;

            _cleared = true;
            return true;
        }

        @Override
        public boolean equals(Object o)
        {
            return null != o && getClass() == o.getClass();
        }

        @Override
        public int hashCode()
        {
            return getClass().hashCode();
        }

        // CommitTaskOption.add() logs this eagerly on every duplicate, so keep it allocation-free
        @Override
        public String toString()
        {
            return "AttachmentCache deferred invalidation counter";
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
        SearchService.get().deleteResource(makeDocId(parent, name));
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
            invalidateCache(parent);
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

        invalidateCache(parent);
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

        invalidateCache(parent);

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
    public int moveAttachments(Container newContainer, List<AttachmentParent> parents, User auditUser) throws IOException
    {
        int totalRowsChanged = 0;

        for (AttachmentParent parent : parents)
        {
            checkSecurityPolicy(auditUser, parent);
            int rowsChanged = new SqlExecutor(coreTables().getSchema()).execute(sqlMove(parent, newContainer));
            if (rowsChanged > 0)
            {
                totalRowsChanged += rowsChanged;
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
                invalidateCache(parent);
            }
        }

        return totalRowsChanged;
    }

    /** may return fewer AttachmentFile than Attachment, if there have been deletions */
    @Override
    public @NotNull List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException
    {
        checkSecurityPolicy(parent);
        List<AttachmentFile> files = new ArrayList<>(attachments.size());

        for (Attachment attachment : attachments)
        {
            if (parent instanceof AttachmentDirectory adParent)
            {
                File f = new File((adParent).getFileSystemDirectory(), attachment.getName());
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

        parentDir = parent instanceof AttachmentDirectory ? ((AttachmentDirectory) parent).getFileSystemDirectory() : null;

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
    public void registerAttachmentParentType(AttachmentParentType type)
    {
        String uniqueName = type.getUniqueName();

        if (uniqueName.contains("."))
            throw new IllegalArgumentException("Invalid attachment parent type name: " + uniqueName);

        ATTACHMENT_TYPE_MAP.put(uniqueName, type);
    }

    @Override
    public Collection<AttachmentParentType> getAttachmentParentTypes()
    {
        return ATTACHMENT_TYPE_MAP.values();
    }

    @Override
    // Joins each row of core.Documents to the table(s) (if any) that contain an EntityId matching the document's parent
    public WebPartView<?> getFindAttachmentParentsView()
    {
        SQLFragment sql = new SQLFragment("SELECT RowId, CreatedBy, Created, ModifiedBy, Modified, Container, DocumentName, TableName FROM core.Documents LEFT OUTER JOIN (\n");
        addSelectAllEntityIdsSql(sql, null, Sets.newCaseInsensitiveHashSet("Audit"), false);
        sql.append(") c ON EntityId = Parent\nORDER BY TableName, DocumentName, Container");

        return getResultSetView(sql, "Probable Attachment Parents", null);
    }

    // Creates a two-column query of ID and table name that selects from every possible attachment parent column in the labkey database:
    // - Enumerate all tables in all schemas in the labkey scope
    // - Enumerate columns and identify potential attachment parents (currently, EntityId columns and ObjectIds extracted from LSIDs)
    // - Create a UNION query that selects the candidate ids along with a constant column that lists the table name
    private void addSelectAllEntityIdsSql(SQLFragment sql, @Nullable Set<String> userRequestedSchemas, Set<String> userRequestedSchemasToIgnore, boolean lsidsOnly)
    {
        Set<String> schemasToIgnore = Sets.newCaseInsensitiveHashSet(userRequestedSchemasToIgnore);
        String documentsSelectName = CoreSchema.getInstance().getTableInfoDocuments().getSelectName();
        String propertySetsSelectName = PropertySchema.getInstance().getTableInfoPropertySets().getSelectName();

        // Temp schema causes problems because materialized tables disappear but stay in the cached list. This is probably a bug with
        // MaterializedQueryHelper... it should clear the temp DbSchema when it deletes a temp table. TODO: fix MQH & remove this workaround
        schemasToIgnore.add("temp");

        sql.append((userRequestedSchemas != null ? userRequestedSchemas.stream() : DbScope.getLabKeyScope().getSchemaNames().stream())
            .filter(schemaName->!schemasToIgnore.contains(schemaName)) // Exclude unwanted schema names
            .map(schemaName->DbSchema.get(schemaName, DbSchemaType.Bare))
            .flatMap(schema->schema.getTableNames().stream().map(schema::getTable))
            .filter(table->table.getTableType() == DatabaseTableType.TABLE) // We just want the underlying tables (no views or virtual tables)
            .filter(table->!Objects.equals(documentsSelectName, table.getSelectName())) // Don't join to the Documents table!
            .filter(table->!Objects.equals(propertySetsSelectName, table.getSelectName())) // Nor to prop.PropertySets
            .map(SchemaTableInfo::getColumns)
            .flatMap(Collection::stream)
            .filter(ColumnRenderProperties::isStringType)
            .map(c->getSelectStatement(c, lsidsOnly))
            .filter(Objects::nonNull)
            .collect(LabKeyCollectors.joining(new SQLFragment("    UNION\n"))));
    }

    private static final Set<String> EXCLUDED_COLUMNS = CaseInsensitiveHashSet.of("Container", "ContainerId", "LookupContainer", "OwnerId", "Project", "ResourceId");

    private SQLFragment getSelectStatement(ColumnInfo column, boolean lsidsOnly)
    {
        SQLFragment expression;
        SQLFragment where = null;

        // GUID columns that don't have an "excluded" name, otherwise attachments associated with core.Containers rows
        // will appear to be attached to many tables in the database.
        if (column.getJdbcType() == JdbcType.GUID && !lsidsOnly && !EXCLUDED_COLUMNS.contains(column.getName()))
        {
            expression = column.getSelectIdentifier().getSql();
        }
        else if (Strings.CI.endsWith(column.getName(), "LSID"))
        {
            Pair<SQLFragment, SQLFragment> pair = Lsid.getSqlExpressionToExtractObjectId(column.getSelectIdentifier().getSql());
            expression = pair.first;
            where = pair.second;
        }
        else
        {
             return null;
        }

        TableInfo table = column.getParentTable();
        SQLFragment sql = new SQLFragment("    SELECT ")
            .append(expression)
            .append(" AS EntityId, ? AS TableName FROM ")
            .add(table.getSelectName())
            .append(table);

        if (null != where)
            sql.append(" WHERE ").append(where);

        sql.append("\n");

        return sql;
    }

    private WebPartView<?> getResultSetView(SQLFragment sql, String title, @Nullable ActionURL linkUrl)
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

            if (parent instanceof AttachmentDirectory adParent)
            {
                java.nio.file.Path parentDir = adParent.getFileSystemDirectoryPath();
                if (!Files.exists(parentDir))
                    throw new FileNotFoundException("No parent directory for downloaded file " + name + ". Please contact an administrator.");
                java.nio.file.Path file = FileUtil.appendName(parentDir, name);
                stmt.close();
                stmt = null;
                rs.close();
                rs = null;
                try
                {
                    return Files.newInputStream(file);
                }
                catch (FileNotFoundException e)
                {
                    throw e;
                }
                catch (IOException e)
                {
                    throw new FileNotFoundException(e.getMessage());
                }
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

    private static final int MAX_ORPHANS_TO_LOG = 20;

    private record Orphan(String documentName, String parentType){}

    @Override
    public int logOrphanedAttachments()
    {
        int ret = 0;
        User user = ElevatedUser.getElevatedUser(User.getSearchUser(), TroubleshooterRole.class);
        UserSchema core = DefaultSchema.get(user, ContainerManager.getRoot()).getUserSchema(CoreQuerySchema.NAME);

        if (core != null)
        {
            TableInfo documents = core.getTable(CoreQuerySchema.DOCUMENTS_TABLE_NAME, new AllFolders(user));
            if (null != documents)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Orphaned"), true);
                List<Orphan> orphans = new TableSelector(documents, new CsvSet("DocumentName, ParentType"), filter, null).getArrayList(Orphan.class);
                if (!orphans.isEmpty())
                {
                    ret = orphans.size();
                    LOG.error("Found {}, which likely indicates a problem with a delete method or a container listener.", StringUtilsLabKey.pluralize(ret, "orphaned attachment"));

                    final String message;
                    if (orphans.size() > MAX_ORPHANS_TO_LOG)
                    {
                        orphans = orphans.subList(0, MAX_ORPHANS_TO_LOG);
                        message = "The first " + MAX_ORPHANS_TO_LOG;
                    }
                    else
                    {
                        message = "All";
                    }

                    LOG.error("{} detected orphans are listed below:\n{}", message, orphans.stream().map(Record::toString).collect(Collectors.joining("\n")));
                }
            }
        }

        return ret;
    }

    record OrphanedAttachment(String container, String parent, String parentType, String documentName)
    {
        AttachmentParent getAttachmentParent()
        {
            return new AttachmentParent()
            {
                @Override
                public String getEntityId()
                {
                    return parent;
                }

                @Override
                public String getContainerId()
                {
                    return container;
                }

                @Override
                public @NotNull AttachmentParentType getAttachmentParentType()
                {
                    // Attempt to resolve the parent type. This will get written to the audit log.
                    AttachmentParentType type = ATTACHMENT_TYPE_MAP.get(parentType());
                    return type != null ? type : AttachmentParentType.UNKNOWN;
                }
            };
        }
    }

    @Override
    public void deleteOrphanedAttachments()
    {
        // TroubleShooterRole provides ability to read the Documents table. deleteAttachments() does not check perms.
        User user = ElevatedUser.getElevatedUser(User.getSearchUser(), TroubleshooterRole.class);
        UserSchema core = DefaultSchema.get(user, ContainerManager.getRoot()).getUserSchema(CoreQuerySchema.NAME);
        if (core != null)
        {
            // Use "unsafe everything" container filter because it's possible that orphaned attachments have a container
            // that no longer exists.
            TableInfo documents = core.getTable(CoreQuerySchema.DOCUMENTS_TABLE_NAME, ContainerFilter.getUnsafeEverythingFilter());
            if (null != documents)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Orphaned"), true);
                MutableInt count = new MutableInt(0);
                new TableSelector(documents, new CsvSet("Container, Parent, ParentType, DocumentName"), filter, null).forEach(OrphanedAttachment.class, orphan -> {
                    LOG.info("Deleting orphaned attachment: {}", orphan);
                    try
                    {
                        deleteAttachment(orphan.getAttachmentParent(), orphan.documentName(), user);
                        count.increment();
                    }
                    catch (Exception e)
                    {
                        LOG.error("Exception while deleting orphaned attachment: {}", orphan, e);
                    }
                });
                AttachmentAuditEvent event = new AttachmentAuditEvent(ContainerManager.getRoot(), "Deleted " + StringUtilsLabKey.pluralize(count.intValue(), "orphaned attachment"));
                event.setAttachment("All orphaned attachments");
                AuditLogService.get().addEvent(user, event);
            }
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
        private final HttpServletResponse _response;

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
                    return null != svc.getMappedAttachmentDirectory(ContainerManager.getForId(_parent.getContainerId()), false);
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
            _containerId = new GUID(parent.getContainerId());
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
                setSearchProperty(SearchService.PROPERTY.categories, SearchService.fileCategory + StringUtils.capitalize(cat.toString()));
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
            _containerId = new GUID(parent.getContainerId());
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
            return files.getFirst();
        }

        @Override
        public InputStream getInputStream(User user) throws IOException
        {
            return AttachmentService.get().getInputStream(_parent, _name);
        }

        @Override
        public void moveFrom(User user, WebdavResource r) throws IOException, DavException
        {
            if (r instanceof AttachmentResource from)
            {
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
                File dir = ((AttachmentDirectory)_parent).getFileSystemDirectory();
                File file = new File(dir,a.getName());
                return file.exists() ? file.length() : 0;
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
                File dir = ((AttachmentDirectory)_parent).getFileSystemDirectory();
                return new File(dir,getName());
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
            assertEquals(1, att.size());
            assertTrue(att.getFirst().getFile().exists());

            // test rename
            String oldName = f.getName();
            String newName = "newname.txt";
            svc.renameAttachment(attachParent, oldName, newName, user);
            assertNull(svc.getAttachment(attachParent, oldName));
            assertNotNull(svc.getAttachment(attachParent, newName));
            // put things back as we found them...
            svc.renameAttachment(attachParent, newName, oldName, user);


            assertTrue(FileUtil.appendName(attachDir, UPLOAD_LOG).exists());

            File otherDir = FileUtil.appendName(attachDir, "subdir");
            otherDir.mkdir();
            AttachmentDirectory namedParent = fileService.registerDirectory(folder, "test", otherDir.getCanonicalPath(), false);

            AttachmentDirectory namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNotNull(namedParentTest);
            assertSameFile(namedParentTest.getFileSystemDirectory(), namedParent.getFileSystemDirectory());

            svc.addAttachments(namedParent, files, user);
            att = svc.getAttachments(namedParent);
            assertEquals(1, att.size());
            assertTrue(att.getFirst().getFile().exists());
            assertSameFile(FileUtil.appendName(otherDir, "file.txt"), att.getFirst().getFile());
            assertTrue(FileUtil.appendName(otherDir, UPLOAD_LOG).exists());

            fileService.unregisterDirectory(folder, "test");
            namedParentTest = fileService.getRegisteredDirectory(folder, "test");
            assertNull(namedParentTest);

            File relativeDir = FileUtil.appendName(attachDir, "subdir2");
            relativeDir.mkdirs();
            AttachmentDirectory relativeParent = fileService.registerDirectory(folder, "relative", FileUtil.getAbsoluteCaseSensitiveFile(relativeDir).getAbsolutePath(), false);
            
            AttachmentDirectory relativeParentTest = fileService.getRegisteredDirectory(folder, "relative");
            assertNotNull(relativeParentTest);
            assertSameFile(relativeParentTest.getFileSystemDirectory(), relativeParent.getFileSystemDirectory());

            svc.addAttachments(relativeParent, files, user);
            att = svc.getAttachments(relativeParent);
            assertEquals(1, att.size());

            File expectedFile1 = att.getFirst().getFile();
            File expectedFile2 = FileUtil.appendName(relativeDir, UPLOAD_LOG);

            assertTrue(expectedFile1.exists());
            assertEquals(FileUtil.appendName(relativeDir, "file.txt"), expectedFile1);
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

        /**
         * Attachments are routinely added and deleted inside a transaction while other threads -- most notably the
         * SearchService indexing threads -- read the same parent's attachments. Those readers have no transaction, so
         * they must see the pre-commit state until the delete commits, and must never be handed a cached snapshot that
         * outlives the commit. This is the deterministic version of the race that made
         * DeleteJobAttachmentsApiTest.testMultipleAttachments flaky.
         */
        @Test
        public void testCacheConsistencyAcrossUncommittedDelete() throws Exception
        {
            User user = TestContext.get().getUser();
            AttachmentService svc = AttachmentService.get();
            AttachmentParent parent = new TestAttachmentParent(JunitUtil.getTestContainer());

            try
            {
                svc.addAttachments(parent, List.of(testFile("one.txt"), testFile("two.txt"), testFile("three.txt")), user);

                // Warm the shared cache; this is what threads without a transaction read below
                assertEquals("Should start with three attachments", 3, svc.getAttachments(parent).size());

                try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
                {
                    svc.deleteAttachment(parent, "one.txt", user);
                    svc.deleteAttachment(parent, "two.txt", user);

                    // The deleting thread sees its own uncommitted deletes
                    assertEquals(List.of("three.txt"), getNames(svc.getAttachments(parent)));

                    // A thread with no transaction must still see the pre-commit state. Two ways this can break: the
                    // cache hands it the uncommitted list the line above just loaded, or the cache was evicted
                    // eagerly and it reloads the pre-delete rows on its own connection and caches them past the
                    // commit. A transaction-aware cache keeps the transaction's loads private and defers the eviction.
                    assertEquals("Thread without a transaction must see the pre-commit state",
                            List.of("one.txt", "two.txt", "three.txt"), getNamesOnNewThread(parent));

                    tx.commit();
                }

                // The commit must have invalidated the shared cache in both directions
                assertEquals(List.of("three.txt"), getNames(svc.getAttachments(parent)));
                assertEquals("Thread without a transaction must see the committed state",
                        List.of("three.txt"), getNamesOnNewThread(parent));
            }
            finally
            {
                svc.deleteAttachments(parent);
            }
        }

        /**
         * The bulk delete path bounds how many deferred cache removals one transaction accumulates (see
         * invalidateCache()). Below that bound each parent is still invalidated individually, so verify that
         * a multi-parent delete defers its removals and invalidates every parent on commit.
         */
        @Test
        public void testBulkDeleteCacheInvalidation() throws Exception
        {
            User user = TestContext.get().getUser();
            AttachmentService svc = AttachmentService.get();
            Container c = JunitUtil.getTestContainer();
            List<AttachmentParent> parents = List.of(new TestAttachmentParent(c), new TestAttachmentParent(c), new TestAttachmentParent(c));

            try
            {
                for (AttachmentParent parent : parents)
                    svc.addAttachments(parent, List.of(testFile("bulk.txt")), user);

                // Warm the shared cache for every parent; this is what threads without a transaction read below
                for (AttachmentParent parent : parents)
                    assertEquals(List.of("bulk.txt"), getNames(svc.getAttachments(parent)));

                try (DbScope.Transaction tx = CoreSchema.getInstance().getScope().ensureTransaction())
                {
                    svc.deleteAttachments(parents);

                    // The deleting thread sees its own uncommitted deletes
                    for (AttachmentParent parent : parents)
                        assertTrue("Deleting thread should see its own uncommitted deletes", svc.getAttachments(parent).isEmpty());

                    // ...while the removals stay deferred for everyone else
                    for (AttachmentParent parent : parents)
                        assertEquals("Thread without a transaction must see the pre-commit state",
                                List.of("bulk.txt"), getNamesOnNewThread(parent));

                    tx.commit();
                }

                // Every parent's entry must be gone from the shared cache, not just the last one
                for (AttachmentParent parent : parents)
                {
                    assertTrue("Bulk delete must invalidate the shared cache", svc.getAttachments(parent).isEmpty());
                    assertEquals(List.of(), getNamesOnNewThread(parent));
                }
            }
            finally
            {
                svc.deleteAttachments(parents);
            }
        }

        private static AttachmentFile testFile(String name)
        {
            return new ByteArrayAttachmentFile(name, name.getBytes(StringUtilsLabKey.DEFAULT_CHARSET), "text/plain");
        }

        private static List<String> getNames(Collection<Attachment> attachments)
        {
            return attachments.stream().map(Attachment::getName).toList();
        }

        // Reads the parent's attachments on a thread that has never had a transaction, so it always resolves to the
        // shared cache
        private static List<String> getNamesOnNewThread(AttachmentParent parent) throws InterruptedException
        {
            AtomicReference<List<String>> names = new AtomicReference<>();
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread thread = new Thread(() -> {
                try
                {
                    names.set(getNames(AttachmentService.get().getAttachments(parent)));
                }
                catch (Throwable t)
                {
                    failure.set(t);
                }
            }, "AttachmentServiceImpl.TestCase reader");

            thread.start();
            thread.join(30_000);

            if (null != failure.get())
                throw new RuntimeException("Reader thread failed", failure.get());
            assertFalse("Reader thread did not finish; it is likely blocked on the open transaction", thread.isAlive());

            return names.get();
        }

        private static class TestAttachmentParent implements AttachmentParent
        {
            private final String _containerId;
            private final String _entityId = GUID.makeGUID();

            private TestAttachmentParent(Container c)
            {
                _containerId = c.getId();
            }

            @Override
            public String getEntityId()
            {
                return _entityId;
            }

            @Override
            public String getContainerId()
            {
                return _containerId;
            }

            @Override
            public @NotNull AttachmentParentType getAttachmentParentType()
            {
                return AttachmentParentType.UNKNOWN;
            }
        }

        // Tests the ability to extract EntityIds from data class LSIDs
        @Test
        public void testLsidGuidExtraction()
        {
            SQLFragment sql = new SQLFragment();
            new AttachmentServiceImpl().addSelectAllEntityIdsSql(sql, Set.of("expdataclass"), Set.of(), true);
            if (!sql.isEmpty())
            {
                new SqlSelector(DbScope.getLabKeyScope(), sql).forEach(rs -> {
                    String entityId = rs.getString("entityid");
                    if (!GUID.isGUID(entityId))
                        fail(entityId + " from " + rs.getString("tablename") + " is not a valid GUID");
                });
            }
        }
    }
}
