<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.filecontent.FilesWebPart" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.io.File" %>
<%@ page import="org.labkey.api.attachments.AttachmentService" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="org.labkey.filecontent.CustomizeFilesWebPartView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.attachments.AttachmentParent" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    CustomizeFilesWebPartView me = (CustomizeFilesWebPartView) HttpView.currentView();
    FileContentController.CustomizeWebPartForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    ActionURL postUrl = new ActionURL("Project", "customizeWebPart.post", ctx.getContainer());
    AttachmentDirectory [] attDirs = AttachmentService.get().getRegisteredDirectories(ctx.getContainer());

%>
<form action="<%=postUrl%>" method="post">
    <input type="hidden" name="pageId" value="<%=form.getPageId()%>">
    <input type="hidden" name="index" value="<%=form.getIndex()%>">
    You can configure this web part to show files from the default directory for this folder or
    from a directory configured by a site administrator.  <br>
    Directory: <select name="fileSet">
        <option value="" <%=null == form.getFileSet() ? "SELECTED" : ""%>>&lt;Default&gt;</option>
<% for (AttachmentDirectory attDir : attDirs)
{   %>
        <option value="<%=h(attDir.getLabel())%>" <%=attDir.getLabel().equals(form.getFileSet()) ? "SELECTED" : ""%>><%=h(attDir.getLabel())%></option>

<%} %>
    </select>
    <% if (ctx.getUser().isAdministrator())
{
    ActionURL configUrl = new ActionURL(FileContentController.ShowAdminAction.class, ctx.getContainer());
    %>
    <a href="<%=h(configUrl)%>">Configure Directories</a>
    <%
}
%><br>
    <%=buttonImg("Submit")%>
</form>