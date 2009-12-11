<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.attachments.Attachment"%>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory"%>
<%@ page import="org.labkey.api.attachments.AttachmentService" %>
<%@ page import="org.labkey.api.attachments.DownloadURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="org.labkey.filecontent.FilesWebPart" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Comparator" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    AttachmentDirectory parent = (AttachmentDirectory) HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();
    Container c = context.getContainer();
    if (null == parent)
    {
        out.println("The file set for this directory is not configured properly.");
        if (me.isShowAdmin() && context.getUser().isAdministrator())
        {
            %>[<a href="<%=h(new ActionURL(FileContentController.ShowAdminAction.class, c))%>" >Configure Directories</a>]<%
        }
    return;
    }
    try
    {
        //Ensure that we can actually upload files
        File dir = parent.getFileSystemDirectory();
    }
    catch (AttachmentService.UnsetRootDirectoryException e)
    {
            %>In order to use this module, a root directory must be set for the project.<br><%=adminError(context)%><%
    }
    catch (AttachmentService.MissingRootDirectoryException e)
    {
        %>The root directory expected for this project does not exist.<br><%=adminError(context)%><%
         return;
    }

    String fileSetName = parent.getLabel();
    Attachment[] attachments = AttachmentService.get().getAttachments(parent);

    Arrays.sort(attachments, new Comparator<Attachment>() {
        public int compare(Attachment a1, Attachment a2)
        {
            return a1.getName().compareToIgnoreCase(a2.getName());
        }
    });

    //Need to use URLHelper insetad of ViewURLHelper so that .view is not appended to files automatically
    URLHelper fileUrl = new URLHelper(new ActionURL("files", "", context.getContainer()).toString());
    if (null != me.getFileSet())
        fileUrl.addParameter("fileSet", me.getFileSet());
    ActionURL addAttachmentUrl = new ActionURL("FileContent", "addAttachment.view", context.getContainer());
    addAttachmentUrl.addParameter("entityId", parent.getEntityId());

    if (me.isWide())
    {
        boolean canDelete = me.isShowAdmin() && context.hasPermission(ACL.PERM_DELETE);
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
        DownloadURL deleteUrl = new DownloadURL("FileContent", c.getPath(), parent.getEntityId(), a.getName());
        deleteUrl.setAction("deleteAttachment.view");
        URLHelper viewUrl = showFileUrl(fileUrl, a);
        URLHelper downloadUrl = downloadFileUrl(fileUrl, a);
        File file = a.getFile();
        boolean exists = null != file && file.exists();
        String contentType = PageFlowUtil.getContentTypeFor(a.getName());
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
            <td><%=a.getCreatedBy() != 0 ? a.getCreatedByName(context) : ""%></td>
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
<% if (context.hasPermission(ACL.PERM_INSERT))
{
    ActionURL dropURL = new ActionURL("ftp","drop", context.getContainer());
    // UNDONE: use yahoo dialog to avoid pop-under
    %>[<a href="#uploadFile" onclick="window.open('<%=addAttachmentUrl%>','uploadFiles','height=200,width=550,resizable=yes', false);">Upload&nbsp;File</a>]&nbsp;<%
    if (0==1){
    if (null == StringUtils.trimToNull(parent.getLabel()))
    {
    %>[<a href="#uploadFiles" onclick="window.open('<%=h(dropURL.getLocalURIString())%>', '_blank', 'height=600,width=1000,resizable=yes');">Upload Multiple Files</a>]&nbsp;<%
    }
    else
    {
    %>[<a href="#uploadFiles" onclick="window.open('<%=h(dropURL.addParameter("fileSetName",parent.getLabel()).getLocalURIString())%>', '_blank', 'height=600,width=1000,resizable=yes');">Upload Multiple Files</a>]&nbsp;<%
    }}
}
if (context.hasPermission(ACL.PERM_UPDATE))
{
    ActionURL manage = new ActionURL("FileContent", "begin", c);
    if (null != fileSetName)
        manage.addParameter("fileSetName",fileSetName);
    %>[<a href="<%=h(manage)%>" >Manage&nbsp;Files</a>]&nbsp;<%
}
if (me.isShowAdmin() && context.getUser().isAdministrator())
{
    %>[<a href="<%=h(new ActionURL(FileContentController.ShowAdminAction.class, c))%>" >Configure</a>]&nbsp;<%
}%>
<%!
    URLHelper showFileUrl(URLHelper u, Attachment a)
    {
        URLHelper url = u.clone();
        url.setFile(a.getName());
        FileContentController.RenderStyle render = FileContentController.defaultRenderStyle(a.getName());
        url.replaceParameter("renderAs", render.name());
        return url;
    }

    URLHelper downloadFileUrl(URLHelper u, Attachment a)
    {
        URLHelper url = u.clone();
        url.setFile(a.getName());
        url.replaceParameter("renderAs", FileContentController.RenderStyle.ATTACHMENT.name());
        return url;
    }

    String adminError(ViewContext context)
    {
        StringBuilder sb = new StringBuilder();
        if (context.getUser().isAdministrator())
        {
            ActionURL url = new ActionURL("FileContent", "begin.view", context.getContainer().getProject());
            sb.append("<a href=\"").append(url).append("\">Configure root</a>");
        }
        else
        {
            sb.append("Contact an adminsistrator");
        }
        return sb.toString();
    }
%>
