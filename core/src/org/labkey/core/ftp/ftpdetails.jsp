<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.ftp.FtpPage" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = getViewContext();
    FtpPage dropPage = (FtpPage)getModelBean();
    Container c = context.getContainer();
%>
<html>
<head>
<title>FTP Instructions</title>
<%=org.labkey.api.util.PageFlowUtil.getStandardIncludes(c)%>
<style type="text/css">
div.content { margin:10px; }
</style>
<script type="text/javascript">
<!--
function openftp()
{
    window.open("<%=dropPage.getUserURL()%>","ftpwindow",
        "width=800,height=600,location=yes,menubar=yes,resizeable=yes,scrollbars=yes,status=yes,titlebar=yes,toolbar=yes");
}
//-->
</script>
</head>
<body onload="document.getElementById('username').select();">
<div class="content">
Use the following FTP URL to upload files:<br/>
<a target="ftpwindow" href="<%=dropPage.getUserURL()%>" onclick="openftp(); return false"><%=dropPage.getURL()%></a><br/><br/>

If you are prompted for a username, enter:<br/>
<input id="username" type="text" readonly="true" value="<%=PageFlowUtil.filter(context.getUser().getEmail())%>" size="60"><br/><br/>
The password is your web site password.<br/>
</div>
</body>
</html>