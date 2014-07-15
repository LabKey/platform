<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.AttachmentDirectory"%>
<%@ page import="org.labkey.api.files.FileContentService"%>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.filecontent.FileContentController" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<FileContentController.FileContentForm> me = (JspView<FileContentController.FileContentForm>) HttpView.currentView();
    FileContentController.FileContentForm form = me.getModelBean();
    FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
    AttachmentDirectory[] attachmentDirs = service.getRegisteredDirectories(getContainer());

    String fileSetHelp = "A file set enables web file sharing of data in subdirectories that do not correspond " +
    "exactly to LabKey containers. It is important to remember that when you request a file from a file set, " +
    "you must specify the file set name in the <code>fileSet</code> parameter of the request URL.<br/><br/>" +
    "For example, if a file set was configured with a name of: <code>test</code> and a path of: <code>c:/examples</code>. " +
    "The file: <code>c:/examples/index.htm</code> could be served with a request of: <code>.../labkey/files/home/index.htm? fileSet=test</code>";

    %><labkey:errors/><%
    if (null != form.getMessage())
    {
        // UNDONE: do we have a message style?
        %><div style="color:green;"><%=text(form.getMessage())%></div><%
    }

    if (getUser().isSiteAdmin())
    {
        File rootFile = service.getFileRoot(getContainer());
        ActionURL configureHelper = urlProvider(AdminUrls.class).getProjectSettingsURL(getContainer()).addParameter("tabId", "files");
        if (null == rootFile)
        { %>
            There is no file root for this folder.
     <% }
        else
        { %>
            The file root for this folder is <br><blockquote><%=h(org.labkey.api.util.FileUtil.getAbsoluteCaseSensitiveFile(rootFile).getAbsolutePath())%></blockquote>
            The directory containing files for this folder is
        <%
            String path = "<unset>";
            AttachmentDirectory attachDir = service.getMappedAttachmentDirectory(getContainer(), false);
            if (attachDir != null)
            {
                File fileSystemDir = attachDir.getFileSystemDirectory();
                if (fileSystemDir != null)                {
                    path = FileUtil.getAbsoluteCaseSensitiveFile(fileSystemDir).getAbsolutePath();
                }
            }
        %>
            <blockquote>
                <%=h(path)%>
            </blockquote>
     <% } %>
        <a href="<%=h(configureHelper)%>">Configure file root for Project.</a><br><br>
<%  } //site administrator %>


<b>File Sets<%=PageFlowUtil.helpPopup("File Sets", fileSetHelp, true)%></b><br>
Each file set is an additional directory that stores files accessible to users of this folder.<br/>
<%
    for (AttachmentDirectory attDir : attachmentDirs)
    {%>
    <form action="deleteAttachmentDirectory.post" method="POST">
        <labkey:csrf/>
     <table>
        <tr>
            <td class="labkey-form-label">Name</td>
            <td><%=h(attDir.getLabel())%><input type="hidden" name="fileSetName" value="<%=h(attDir.getLabel())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Path</td>
            <td><%=h(attDir.getFileSystemDirectory().getPath())%> <%=h(attDir.getFileSystemDirectory().exists() ? "" : "Directory does not exist. An administrator must create it.")%></td>
        </tr>
        <tr>
            <td colspan=2><%= button("Show Files").href(buildURL(FileContentController.BeginAction.class, "fileSetName=" + h(attDir.getLabel()))) %> <%= button("Remove").submit(true) %> (Files will not be deleted)</td>
        </tr>
    </table>
        </form>
<%  } %>

<form action="addAttachmentDirectory.post" method="POST">
    <labkey:csrf/>
<table>
    <tr>
        <td class="labkey-form-label">Name</td>
        <td><input name="fileSetName" value="<%=h(form.getFileSetName())%>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Path</td>
        <td><input name="path" size="60" value="<%=h(form.getPath())%>"></td>
    </tr>
    <tr>
        <td><%= button("Add File Set").submit(true) %> </td>
    </tr>
</table>
</form>
<%
if (getUser().isSiteAdmin())
{
%>
<br><b>Additional Information</b><br>
When you set a file root for a folder, you can use your LabKey Server installation as a secure web content server.<br>
For each folder you can define a parallel file-system tree containing files you would like LabKey Server to return<br>
You can then use LabKey URLs to download those files.
If, for example, you set the content root for the Home project to<br>
<pre>
    C:\content\homeProject\
</pre>
<br>and that directory contained test.html, the link<br>
<pre>
http://<%=text(request.getServerName())%><%=text(request.getContextPath())%>/files/home/test.html
</pre>
    will return the file. You could also use links like this
<pre>
    http://<%=text(request.getServerName())%><%=text(request.getContextPath())%>/files/home/subdir/other.html
</pre>
to serve the file<br>
<pre>
    C:\content\homeProject\subdir\other.html
</pre>
assuming that subdir is the name of a folder on your LabKey server.<br><br>
Files returned this way will be returned to the browser inside the standard LabKey template. To render content as a bare file or within an IFRAME can set the renderAs parameter on your URL to one of several values.
<ul>
    <li><b>?renderAs=FRAME</b> will cause the file to be rendered within an IFRAME. This is useful for returning standard HTML files</li>
    <li><b>?renderAs=INLINE</b> will render the content of the file directly into a page. This is only useful if you have files containing fragments of HTML,  and those
    files link to other resources on the LabKey Server, and links within the HTML will also need the renderAs=INLINE to maintain the look.</li>
    <li><b>?renderAs=TEXT</b> renders text into a page, preserves line breaks in text files</li>
    <li><b>?renderAs=IMAGE</b> for rendering an image in a page</li>
    <li><b>?renderAs=PAGE</b> force the file to be downloaded (e.g. not framed)</li>
</ul>

<%  }%>
