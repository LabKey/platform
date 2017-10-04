/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
public interface AttachmentService
{
    String ATTACHMENT_AUDIT_EVENT = "AttachmentAuditEvent";

    static void register(AttachmentService serviceImpl)
    {
        ServiceRegistry.get().registerService(AttachmentService.class, serviceImpl);
    }

    static AttachmentService get()
    {
        return ServiceRegistry.get(AttachmentService.class);
    }

    void download(HttpServletResponse response, AttachmentParent parent, String name) throws ServletException, IOException;

    HttpView getHistoryView(ViewContext context, AttachmentParent parent);

    HttpView getErrorView(List<AttachmentFile> files, BindException errors, URLHelper returnUrl);

    void addAttachments(AttachmentParent parent, List<AttachmentFile> files, @NotNull User user) throws IOException;

    void deleteAttachments(AttachmentParent parent);

    void deleteAttachments(Collection<AttachmentParent> parents);

    /**
     * @param auditUser set to null to skip audit
     */
    void deleteAttachment(AttachmentParent parent, String name, @Nullable User auditUser);

    void renameAttachment(AttachmentParent parent, String oldName, String newName, User auditUser) throws IOException;

    void copyAttachment(AttachmentParent parent, Attachment a, String newName, User auditUser) throws IOException;

    void moveAttachments(Container newContainer, List<AttachmentParent> parents, User auditUser) throws IOException;

    @NotNull
    List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException;

    // Returns an unmodifiable list of attachments for this parent
    @NotNull
    List<Attachment> getAttachments(AttachmentParent parent);

    List<Pair<String, String>> listAttachmentsForIndexing(Collection<String> parents, Date modifiedSince);

    WebdavResource getAttachmentResource(Path path, AttachmentParent parent);

    WebdavResource getDocumentResource(Path path, ActionURL downloadURL, String displayTitle, AttachmentParent parent, String name, SearchService.SearchCategory cat);

    @Nullable
    Attachment getAttachment(AttachmentParent parent, String name);

    void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, boolean asAttachment) throws ServletException, IOException;

    @NotNull
    InputStream getInputStream(AttachmentParent parent, String name) throws FileNotFoundException;

    void addAuditEvent(User user, AttachmentParent parent, String filename, String comment);

    void deleteAttachmentIndex(AttachmentParent parent);

    void registerAttachmentType(AttachmentType type);

    HttpView getAdminView(ActionURL currentUrl);

    HttpView getFindAttachmentParentsView();

    class DuplicateFilenameException extends IOException
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

    class FileTooLargeException extends IOException
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
