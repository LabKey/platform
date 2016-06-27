<%
/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.filecontent.message.FileContentDigestProvider" %>
<%@ page import="org.labkey.api.audit.provider.FileSystemAuditProvider" %>
<%@ page import="org.labkey.api.announcements.EmailOption" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    // Note: This is a plain text email, so we don't encode any content.

    FileContentDigestProvider.FileDigestForm form = ((JspView<org.labkey.filecontent.message.FileContentDigestProvider.FileDigestForm>)HttpView.currentView()).getModelBean();
    EmailOption pref = EmailOption.NOT_SET;//NumberUtils.stringToInt(EmailService.get().getEmailPref(user, c, new FileContentEmailPref()), -1);

    ActionURL emailPrefs = PageFlowUtil.urlProvider(FileUrls.class).urlFileEmailPreference(form.getContainer());
//    ActionURL fileBrowser = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(form.getContainer());
%>

Summary of notifications of files at <%=text(form.getContainer().getPath())%>.

    <%
        for (Map.Entry<Path, List<FileSystemAuditProvider.FileSystemAuditEvent>> record : form.getRecords().entrySet())
        {
            Path path = record.getKey();
            WebdavResource resource = WebdavService.get().getResolver().lookup(path);

    %>
<%=text(resource.isCollection() ? "Folder: " : " File: ")%><%=text(resource.getName())%>
        <%
            for (FileSystemAuditProvider.FileSystemAuditEvent event : record.getValue())
            {
                User user = event.getCreatedBy();
        %>
    <%=text(DateUtil.formatDateTime(form.getContainer(), event.getCreated()))%>, <%=text(user.getDisplayName(user))%>, <%=text(event.getComment())%> <%
            }
        }
    %>

You have received this email because <%
        switch(pref)
        {
            case NOT_SET:
            case FILES_INDIVIDUAL: %>you are signed up to receive notifications about updates to files at <%=text(form.getContainer().getPath())%>.
If you no longer wish to receive these notifications you can change your email preferences here: <%=text(emailPrefs.getURIString())%>.<%
            break;
        } %>
