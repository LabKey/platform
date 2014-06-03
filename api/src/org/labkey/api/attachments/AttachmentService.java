/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.attachments;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.WebdavResource;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * User: adam
 * Date: Jan 3, 2007
 * Time: 7:03:21 PM
 */
public class AttachmentService
{
    public static final String ATTACHMENT_AUDIT_EVENT = "AttachmentAuditEvent";
    private static Service _serviceImpl = null;

    public interface Service
    {
        public void download(HttpServletResponse response, AttachmentParent parent, String name) throws ServletException, IOException;

        // Use the void-returning methods addAttachments and deleteAttachments instead
        @Deprecated
        public HttpView add(AttachmentParent parent, List<AttachmentFile> files, User auditUser);
        @Deprecated
        public HttpView delete(AttachmentParent parent, String name, User auditUser) throws SQLException;

        public HttpView getAddAttachmentView(Container container, AttachmentParent parent, BindException errors);
        public HttpView getHistoryView(ViewContext context, AttachmentParent parent);
        public HttpView getErrorView(List<AttachmentFile> files, BindException errors, URLHelper returnUrl);

        public void addAttachments(AttachmentParent parent, List<AttachmentFile> files, @NotNull User user) throws IOException;
        public void deleteAttachments(AttachmentParent... parent);
        public void deleteAttachments(Collection<AttachmentParent> parents);

        /**
         * @param auditUser set to null to skip audit
         */
        public void deleteAttachment(AttachmentParent parent, String name, @Nullable User auditUser);
        public void renameAttachment(AttachmentParent parent, String oldName, String newName, User auditUser) throws IOException;
        public void copyAttachment(AttachmentParent parent, Attachment a, String newName, User auditUser) throws IOException;

        public void moveAttachments(Container newContainer, List<AttachmentParent> parents, User auditUser) throws IOException;

        public @NotNull List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException;
        // Returns an unmodifiable list of attachments for this parent
        public @NotNull List<Attachment> getAttachments(AttachmentParent parent);
        public List<Pair<String, String>> listAttachmentsForIndexing(Collection<String> parents, Date modifiedSince);

        public WebdavResource getAttachmentResource(Path path, AttachmentParent parent);
        public WebdavResource getDocumentResource(Path path, ActionURL downloadURL, String displayTitle, AttachmentParent parent, String name, SearchService.SearchCategory cat);
        public @Nullable Attachment getAttachment(AttachmentParent parent, String name);
        public void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, boolean asAttachment) throws ServletException, IOException;
        public @NotNull InputStream getInputStream(AttachmentParent parent, String name) throws FileNotFoundException;

        public void addAuditEvent(User user, AttachmentParent parent, String filename, String comment);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
        ServiceRegistry.get().registerService(AttachmentService.Service.class, serviceImpl);
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }

    public static class DuplicateFilenameException extends IOException
    {
        private List<String> _errors = new ArrayList<>();

        public DuplicateFilenameException(Collection<String> filenames)
        {
            for (String filename : filenames)
                addError(filename);
        }

        public DuplicateFilenameException(String filename)
        {
            addError(filename);
        }

        private void addError(String filename)
        {
            _errors.add("New file " + filename + " was not attached; A duplicate file was detected.");
        }

        public List<String> getErrors()
        {
            return _errors;
        }

        @Override
        public String getMessage()
        {
            return StringUtils.join(_errors, " ");
        }
    }

    public static class FileTooLargeException extends IOException
    {
        private List<String> _errors = new ArrayList<>();

        public FileTooLargeException(Collection<AttachmentFile> files, int maxSize) throws IOException
        {
            for (AttachmentFile file : files)
                addError(file, maxSize);
        }

        public FileTooLargeException(AttachmentFile file, int maxSize) throws IOException
        {
            addError(file, maxSize);
        }

        private void addError(AttachmentFile file, int maxSize) throws IOException
        {
            _errors.add("File " + file.getFilename() + " is larger than the maximum allowed size. " + NumberFormat.getIntegerInstance().format(file.getSize()) + " vs " + NumberFormat.getIntegerInstance().format(maxSize) + " bytes");
        }

        public List<String> getErrors()
        {
            return _errors;
        }

        @Override
        public String getMessage()
        {
            return StringUtils.join(_errors, " ");
        }
    }
}
