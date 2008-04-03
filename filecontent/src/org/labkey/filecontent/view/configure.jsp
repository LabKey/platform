<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.filecontent.FileContentController.FileContentForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.attachments.AttachmentService" %>
<%@ page import="java.io.File" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<FileContentForm> me = (JspView<FileContentForm>) HttpView.currentView();
    FileContentForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    AttachmentDirectory[] attachmentDirs = AttachmentService.get().getRegisteredDirectories(ctx.getContainer());

    String fileSetHelp = "A file set enables web file sharing of data in subdirectories that do not correspond " +
    "exactly to LabKey containers. It is important to remember that when you request a file from a file set, " +
    "you must specify the file set name in the <code>fileSet</code> parameter of the request URL.<br/><br/>" +
    "For example, if a file set was configured with a name of: <code>test</code> and a path of: <code>c:/examples</code>. " +
    "The file: <code>c:/examples/index.htm</code> could be served with a request of: <code>.../labkey/files/home/index.htm? fileSet=test</code>";
%>
<% if (null != form.getMessage())
    {
%>
    <div style="color:red"><%=form.getMessage()%></div>
<%
    }
    if (ctx.getUser().isAdministrator())
    {
        if (ctx.getContainer().isProject())
        {
%>
<b>Web Root</b><br>
    Set web root for files in this project. Leave blank to turn off automatic web file sharing for folders.<br>
When a web root is set,
each folder in the project has a corresponding subdirectory in the file system.<br><br>
<form action="saveRoot.post" method="POST">
    Web Root <input name=rootPath size=50 value="<%=h(form.getRootPath())%>"><br>
    <%=buttonImg("Submit")%>
</form>
<br><br>
<%      }
        else
        {
            File rootFile = AttachmentService.get().getWebRoot(ctx.getContainer().getProject());
            ActionURL configureHelper = new ActionURL("FileContent", "showAdmin.view", ctx.getContainer().getProject());
            if (null == rootFile)
            { %>
                    There is no web root for this project.
        <%  }
            else
            {   %>
                The web root for this project is <br><blockquote><%=h(rootFile.getCanonicalPath())%></blockquote>
                The directory containing web files for this folder is
                <blockquote>
                             <%=h(AttachmentService.get().getMappedAttachmentDirectory(ctx.getContainer(), false).getFileSystemDirectory().getCanonicalPath())%>
                </blockquote>
            <%}
            %>
 <a href="<%=h(configureHelper)%>">Configure Project Settings</a><br><br>
<%      }
    } //site administrator
%>


<b>File Sets<%=PageFlowUtil.helpPopup("File Sets", fileSetHelp, true)%></b><br>
Each file set is an additional directory that stores files accessible to users of this folder.<br/>
<%
    for (AttachmentDirectory attDir : attachmentDirs)
    {%>
    <form action="deleteAttachmentDirectory.post" method="POST">
    <table>
        <tr>
            <td class="ms-searchform">Name</td>
            <td><%=h(attDir.getLabel())%><input type="hidden" name="fileSetName" value="<%=h(attDir.getLabel())%>"></td>
        </tr>
        <tr>
            <td class="ms-searchform">Path</td>
            <td><%=h(attDir.getFileSystemDirectory().getPath())%> <%=attDir.getFileSystemDirectory().exists() ? "" : "Directory does not exist. An administrator must create it."%></td>
        </tr>
        <tr>
            <td colspan=2><%=buttonLink("Show Files", "begin.view?fileSetName=" + h(attDir.getLabel()))%> <%=buttonImg("Remove")%> (Files will not be deleted)</td>
        </tr>
    </table>
        </form>
<%  } %>

<form action="addAttachmentDirectory.post" method="POST">
<table>                                        
    <tr>
        <td class="ms-searchform">Name</td>
        <td><input name="fileSetName" value="<%=h(form.getFileSetName())%>"></td>
    </tr>
    <tr>
        <td class="ms-searchform">Path</td>
        <td><input name="path" size="60" value="<%=h(form.getPath())%>"></td>
    </tr>
    <tr>
        <td><%=buttonImg("Add File Set")%> </td>
    </tr>
</table>
</form>
<%
if (ctx.getUser().isAdministrator())
{
%>
<br><b>Additional Information</b><br>
When you set a web root for a project, you can use your LabKey Server installation as a secure web content server.<br>
For each project you can define a parallel file-system tree containing files you would like LabKey Server to return<br>
You can then use LabKey URLs to download those files.
If, for example, you set the content root for the Home project to<br>
<pre>
    C:\content\homeProject\
</pre>
<br>and that directory contained test.html, the link<br>
<pre>
    http://<%=request.getServerName()%><%=request.getContextPath()%>/FileContent/home/sendFile.view?fileName=test.html
    </pre>
    will return the file. This is useful, but of limited utility if the file contains relative links and img tags.<br>
    However, if you configure the FileServlet as outlined below, standard urls like this<br>
    <pre>
    http://<%=request.getServerName()%><%=request.getContextPath()%>/files/home/test.html
</pre>
would return the file, after first checking security on the home project.<br>You could also use links like this
<pre>
    http://<%=request.getServerName()%><%=request.getContextPath()%>/files/home/subdir/other.html
</pre>
to serve the file<br>
<pre>
    C:\content\homeProject\subdir\other.html
</pre>
Files returned this way will be returned to the browser
as is with no frame. To render content within the standard LabKey user interface you can set the renderAs parameter on your URL to one of two values.
<ul>
    <li><b>?renderAs=FRAME</b> will cause the file to be rendered within an IFRAME. This is useful for returning standard HTML files</li>
    <li><b>?renderAs=INLINE</b> will render the content of the file directly into a page. This is only useful if you have files containing fragments of HTML,  and those
    files link to other resources on the LabKey Server, and links within the HTML will also need the renderAs=INLINE to maintain the look.</li>
    <li><b>?renderAs=TEXT</b> renders text into a page, preserves line breaks in text files</li>
    <li><b>?renderAs=IMAGE</b> for rendering an image in a page</li>
    <li><b>?renderAs=PAGE</b> force the file to be downloaded (e.g. not framed)</li>
</ul>

<%  }%>
