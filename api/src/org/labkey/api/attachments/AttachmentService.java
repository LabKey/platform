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

package org.labkey.api.attachments;

import org.labkey.api.data.Container;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.Pair;
import org.labkey.api.webdav.Resource;
import org.springframework.validation.BindException;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Date;

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
        public HttpView delete(User user, AttachmentParent parent, String name) throws SQLException;
        public void download(HttpServletResponse response, AttachmentParent parent, String name) throws ServletException, IOException;
        public HttpView getAddAttachmentView(Container container, AttachmentParent parent);
        public HttpView getAddAttachmentView(Container container, AttachmentParent parent, BindException errors);
        public HttpView getConfirmDeleteView(Container container, ActionURL currentUrl, AttachmentParent parent, String filename);
        public HttpView getHistoryView(ViewContext context, AttachmentParent parent);

        public HttpView add(User user, AttachmentParent parent, List<AttachmentFile> files);
        public HttpView getErrorView(List<AttachmentFile> files, BindException errors, URLHelper returnUrl);

        public void addAttachments(User user, AttachmentParent parent, List<AttachmentFile> files) throws IOException;
        public void insertAttachmentRecord(User user, AttachmentDirectory parent, AttachmentFile file) throws SQLException;
        public void deleteAttachments(AttachmentParent parent) throws SQLException;
        public void deleteAttachment(AttachmentParent parent, String name);
        public void renameAttachment(AttachmentParent parent, String oldName, String newName) throws IOException;
        public void copyAttachment(User user, AttachmentParent parent, Attachment a, String newName) throws IOException;
        public List<AttachmentFile> getAttachmentFiles(AttachmentParent parent, Collection<Attachment> attachments) throws IOException;
        public Attachment[] getAttachments(AttachmentParent parent);
        public List<Pair<String,String>> listAttachments(Collection<String> parents, Date modifiedSince);
        public Resource getAttachmentResource(Path path, AttachmentParent parent);
        public Resource getDocumentResource(Path path, ActionURL downloadURL, String title, AttachmentParent parent, String name, SearchService.SearchCategory cat);
        public Attachment getAttachment(AttachmentParent parent, String name);
        public void setAttachments(Collection<AttachmentParent> parents) throws SQLException;
        public void writeDocument(DocumentWriter writer, AttachmentParent parent, String name, boolean asAttachment) throws ServletException, IOException;
        public InputStream getInputStream(AttachmentParent parent, String name) throws FileNotFoundException;
        public void addAuditEvent(User user, AttachmentParent parent, String filename, String comment);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }

    public static class DuplicateFilenameException extends IOException
    {
        private List<String> _errors = new ArrayList<String>();

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
            _errors.add("New file " + filename + " was not attached; a file by that name was previously attached.");
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
