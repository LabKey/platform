<%
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
%>
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.attachments.Attachment"%>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory"%>
<%@ page import="org.labkey.api.attachments.AttachmentService" %>
<%@ page import="org.labkey.api.attachments.DownloadURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.MissingRootDirectoryException" %>
<%@ page import="org.labkey.api.files.UnsetRootDirectoryException" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="org.labkey.filecontent.FileContentController.BeginAction" %>
<%@ page import="org.labkey.filecontent.FileContentController.DeleteAttachmentAction" %>
<%@ page import="org.labkey.filecontent.FileContentController.RenderStyle" %>
<%@ page import="org.labkey.filecontent.FileContentController.ShowAdminAction" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm) HttpView.currentModel();
    AttachmentDirectory parent = bean.getRoot();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();
    Container c = context.getContainer();

    if (!bean.isEnabled())
    {
        ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
        out.println("File sharing has been disabled for this project. Sharing can be configured from the <a href=" + projConfig + ">project settings</a> view.");
        return;
    }

    if (null == parent)
    {
        out.println("The file set for this directory is not configured properly.");
        if (me.isShowAdmin() && context.getUser().isAdministrator())
        {
            %><%=textLink("Configure Directories", new ActionURL(ShowAdminAction.class, c))%><%
        }
    return;
    }
    try
    {
        //Ensure that we can actually upload files
        File dir = parent.getFileSystemDirectory();
    }
    catch (UnsetRootDirectoryException e)
    {
            %>In order to use this module, a root directory must be set for the project.<br><%=adminError(context)%><%
    }
    catch (MissingRootDirectoryException e)
    {
        %>The root directory expected for this project does not exist.<br><%=adminError(context)%><%
         return;
    }

    String fileSetName = parent.getLabel();

    // Make a copy to modify
    List<Attachment> attachments = new ArrayList<>(AttachmentService.get().getAttachments(parent));

    Collections.sort(attachments, new Comparator<Attachment>() {
        public int compare(Attachment a1, Attachment a2)
        {
            return a1.getName().compareToIgnoreCase(a2.getName());
        }
    });

    //Need to use URLHelper instead of ActionURL so that .view is not appended to files automatically
    URLHelper fileUrl = new URLHelper(new ActionURL("files", "", context.getContainer()).toString());
    if (null != me.getFileSet())
        fileUrl.addParameter("fileSet", me.getFileSet());
    ActionURL addAttachmentUrl = new ActionURL(FileContentController.AddAttachmentAction.class, context.getContainer());
    addAttachmentUrl.addParameter("entityId", parent.getEntityId());

    if (me.isWide())
    {
        boolean canDelete = me.isShowAdmin() && context.hasPermission(DeletePermission.class);
%>
    <table>
        <tr>
            <th style="color:#003399;text-align:left;">File</th>
            <th style="color:#003399;text-align:right;">Size</th>
            <th style="color:#003399;text-align:left;">Date</th>
            <th style="color:#003399;text-align:left;">Person</th>
            <th>&nbsp;</th>
            <%if (canDelete)
            {
                %><th>&nbsp;</th><%
            }
         %></tr><%

    for (Attachment a : attachments)
    {
        DownloadURL deleteUrl = new DownloadURL(DeleteAttachmentAction.class, c, parent.getEntityId(), a.getName());
        URLHelper viewUrl = showFileUrl(fileUrl, a);
        URLHelper downloadUrl = downloadFileUrl(fileUrl, a);
        File file = a.getFile();
        boolean exists = null != file && file.exists();
    %>
            <tr>
            <td><%
            if (exists)
            {
                %><a href="<%=h(viewUrl)%>"><img src="<%=request.getContextPath()%><%=a.getFileIcon()%>" alt=""> <%=a.getName()%></a><%
            } else
            {
                %><span title="File was uploaded but is no longer available on disk"><img src="<%=request.getContextPath()%>/_images/exclaim.gif" alt=""> <%=a.getName()%></span><%
            } %>
            </td>
            <td align="right"><%if (exists){%><%=file.length()%><%}%></td>
            <td align="right"><%=a.getCreated()%></td>
            <td><%=a.getCreatedBy() != 0 ? a.getCreatedByName(context.getUser()) : ""%></td>
            <td><%if (exists){%>[<a href="<%=h(downloadUrl)%>" class="labkey-message">Download</a>]<%}%></td>
            <td><%
            if (canDelete)
            {
                %>[<a href="#deleteFile" onclick="window.open(<%=hq(deleteUrl.getLocalURIString())%>, null, 'height=200,width=450', false)" class="labkey-message">Delete&nbsp;<%=exists ? "File" : "Row"%></a>]<%
            } %></td>
            </tr><%
    }
    %></table><%
    }
    else  //Narrow
    {
        for (Attachment a : attachments)
        {
            URLHelper viewUrl = showFileUrl(fileUrl, a);
            if (null != a.getFile() && a.getFile().exists()) { %>
                <a href="<%=h(viewUrl)%>"><img src="<%=request.getContextPath()%><%=a.getFileIcon()%>" alt=""> <%=a.getName()%></a><%
            } else { %>
              <span title="File was uploaded but is no longer available on disk"><img src="<%=request.getContextPath()%>/_images/exclaim.gif" alt=""><%=a.getName()%></span><%
            } %>
            <br>
<%      }
    }%>
<br>
<% if (context.hasPermission(InsertPermission.class))
{
    // UNDONE: use Ext message box to avoid pop-under
    %><%=textLink("Upload File", "#uploadFile", "window.open('" + addAttachmentUrl + "', 'uploadFiles', 'height=200,width=550,resizable=yes', false);", "upload-file-id")%>&nbsp;<%
}
if (context.hasPermission(UpdatePermission.class))
{
    ActionURL manage = new ActionURL(BeginAction.class, c);
    if (null != fileSetName)
        manage.addParameter("fileSetName", fileSetName);
    %><%=textLink("Manage Files", manage)%>&nbsp;<%
}
if (me.isShowAdmin() && context.getUser().isAdministrator())
{
    %><%=textLink("Configure", new ActionURL(ShowAdminAction.class, c))%>&nbsp;<%
}%>
<%!
    URLHelper showFileUrl(URLHelper u, Attachment a)
    {
        URLHelper url = u.clone();
        url.setFile(a.getName());
        RenderStyle render = FileContentController.defaultRenderStyle(a.getName());
        url.replaceParameter("renderAs", render.name());
        return url;
    }

    URLHelper downloadFileUrl(URLHelper u, Attachment a)
    {
        URLHelper url = u.clone();
        url.setFile(a.getName());
        url.replaceParameter("renderAs", RenderStyle.ATTACHMENT.name());
        return url;
    }

    String adminError(ViewContext context)
    {
        StringBuilder sb = new StringBuilder();
        if (context.getUser().isAdministrator())
        {
            ActionURL url = new ActionURL(BeginAction.class, context.getContainer().getProject());
            sb.append("<a href=\"").append(url).append("\">Configure root</a>");
        }
        else
        {
            sb.append("Contact an adminsistrator");
        }
        return sb.toString();
    }
%>
