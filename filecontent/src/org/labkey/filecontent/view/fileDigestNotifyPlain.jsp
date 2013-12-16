<%
/*
 * Copyright (c) 2011 LabKey Corporation
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
<%@ page import="org.labkey.api.audit.AuditLogEvent"%>
<%@ page import="org.labkey.api.files.FileContentEmailPref" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    org.labkey.filecontent.message.FileContentDigestProvider.FileDigestForm form = ((JspView<org.labkey.filecontent.message.FileContentDigestProvider.FileDigestForm>)HttpView.currentView()).getModelBean();
    int pref = FileContentEmailPref.FOLDER_DEFAULT;//NumberUtils.stringToInt(EmailService.get().getEmailPref(user, c, new FileContentEmailPref()), -1);

    ActionURL emailPrefs = PageFlowUtil.urlProvider(FileUrls.class).urlFileEmailPreference(form.getContainer());
    ActionURL fileBrowser = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(form.getContainer());
%>

Summary of notifications of files at <%=form.getContainer().getPath()%>.

    <%
        for (Map.Entry<Path, List<AuditLogEvent>> record : form.getRecords().entrySet())
        {
            Path path = record.getKey();
            WebdavResource resource = WebdavService.get().getResolver().lookup(path);

    %>
        <%=resource.isCollection() ? "Folder: " : " File: "%><%=h(resource.getName())%>
        <%
            for (AuditLogEvent event : record.getValue())
            {
                User user = event.getCreatedBy();
        %>
                <%=DateUtil.formatDateTime(getContainer(), event.getCreated())%>, <%=user.getDisplayName(user)%>, <%=event.getComment()%>
        <%
            }
        %>
    <%
        }
    %>

    You have received this email because <%
        switch(pref)
        {
            case FileContentEmailPref.FOLDER_DEFAULT:
            case FileContentEmailPref.INDIVIDUAL: %>
            you are signed up to receive notifications about updates to files at <%=form.getContainer().getPath()%>.
            If you no longer wish to receive these notifications you can change your email preferences here: <%=emailPrefs.getURIString()%>. <%
            break;
        } %>
