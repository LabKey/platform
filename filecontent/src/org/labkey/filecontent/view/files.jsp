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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.MissingRootDirectoryException" %>
<%@ page import="org.labkey.api.files.UnsetRootDirectoryException" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="org.labkey.filecontent.FileContentController.BeginAction" %>
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

    assert(!me.isWide());

    if (!bean.isEnabled())
    {
        ActionURL projConfig = urlProvider(AdminUrls.class).getProjectSettingsFileURL(c);
        out.println("File sharing has been disabled for this project. Sharing can be configured from the <a href=" + projConfig + ">project settings</a> view.");
        return;
    }

    if (null == parent)
    {
        out.println("The file set for this directory is not configured properly.");
        if (me.isShowAdmin() && context.getUser().isSiteAdmin())
        {
            %><%=textLink("Configure Directories", new ActionURL(ShowAdminAction.class, c))%><%
        }
    return;
    }
    try
    {
        //Ensure that we can actually upload files
        parent.getFileSystemDirectory();
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

    for (Attachment a : attachments)
    {
        URLHelper viewUrl = showFileUrl(fileUrl, a);
        if (null != a.getFile() && a.getFile().exists()) {
%>
            <a href="<%=h(viewUrl)%>"><img src="<%=h(request.getContextPath())%><%=h(a.getFileIcon())%>" alt=""> <%=h(a.getName())%></a>
<%
        } else {
%>
          <span title="File was uploaded but is no longer available on disk"><img src="<%=h(request.getContextPath())%>/_images/exclaim.gif" alt=""><%=h(a.getName())%></span>
<%
        }
%>
        <br>
<%
    }
%>
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
if (me.isShowAdmin() && context.getUser().isSiteAdmin())
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

    String adminError(ViewContext context)
    {
        StringBuilder sb = new StringBuilder();
        if (context.getUser().isSiteAdmin())
        {
            ActionURL url = new ActionURL(BeginAction.class, context.getContainer().getProject());
            sb.append("<a href=\"").append(url).append("\">Configure root</a>");
        }
        else
        {
            sb.append("Contact an administrator");
        }
        return sb.toString();
    }
%>
