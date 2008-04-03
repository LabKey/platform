<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.ftp.FtpPage" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = getViewContext();
    String contextPath = context.getContextPath();
    FtpPage dropPage = (FtpPage)getModelBean();
%>
<html>
<head>
<title>FTP Instructions</title>
<%=org.labkey.api.util.PageFlowUtil.getStandardIncludes()%>
<style type="text/css">
div.content { margin:10px; }
</style>
<script type="text/javascript">
<!--
function openftp()
{
    window.open("<%=dropPage.getURL(context.getContainer(), context.getUser())%>","ftpwindow",
        "width=800,height=600,location=yes,menubar=yes,resizeable=yes,scrollbars=yes,status=yes,titlebar=yes,toolbar=yes");
}
//-->
</script>
</head>
<body onload="document.getElementById('username').select();">
<div class="content">
Use the following FTP URL to upload files:<br/>
<a target="ftpwindow" href="<%=dropPage.getURL(context.getContainer(), context.getUser())%>" onclick="openftp(); return false"><%=dropPage.getURL(context.getContainer())%></a><br/><br/>

If you are prompted for a username, enter:<br/>
<input id="username" type="text" readonly="true" value="<%=PageFlowUtil.filter(context.getUser().getEmail())%>" size="60"><br/><br/>
The password is your web site password.<br/>
</div>
</body>
</html>