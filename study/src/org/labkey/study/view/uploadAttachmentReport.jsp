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
<%@ page import="org.labkey.study.reports.AttachmentReport"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>


<%
    JspView<ReportsController.UploadForm> me = (JspView<ReportsController.UploadForm>) HttpView.currentView();
    ReportsController.UploadForm bean = me.getModelBean();

    boolean canUseDiskFile = HttpView.currentContext().getUser().isAdministrator() && bean.getReportId() == 0;
%>

<table border=0 cellspacing=2 cellpadding=0>
<%
    for (ObjectError e : (List<ObjectError>) bean.getErrors().getAllErrors())
    {
%>      <tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
    }
%>
</table>

<form method="post" action="" enctype="multipart/form-data">
    <table border=0>
            <tr>
            <td class="ms-searchform">
                Report Name
            </td>
            <td>
                <%
                if (null == bean.getLabel()) {
                %>
                    <input name="label">
                <%
                } else {
                %>
                    <input type="hidden" name="reportId" value="<%=PageFlowUtil.filter(bean.getReportId())%>"><%=PageFlowUtil.filter(bean.getLabel())%>
             <% } %>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">
                Report Date
            </td>
            <td>
                <input type="text" name="reportDateString">
            </td>
        </tr>
        <% if (canUseDiskFile)
        {%>
        <tr>
            <td colspan=2 class="ms-searchform">
                <input type="radio" checked name="uploadType" onclick="showUpload()">Upload File &nbsp;&nbsp;
                <input type="radio" name="uploadType" onclick="showPath()">Use a file on server <%=request.getServerName()%>
            </td>
        </tr>
        <%}%>
        <tr>
            <td id="fileTitle" class="ms-searchform">Choose file to upload</td>
            <td><input id=uploadFile type="file" name="formFiles[0]">
        <% if (canUseDiskFile)
        {%>
            <input size=50 id="filePath" style="display:none" name="filePath">
      <%}%>
            </td>
        </tr>
    </table>

<br>
<input type="image" src='<%=PageFlowUtil.buttonSrc("Submit")%>'>
<img onClick="window.history.back()" alt="Cancel" src='<%=PageFlowUtil.buttonSrc("Cancel")%>'>
    </form>
<script type="text/javascript">
    function showUpload()
    {
        document.getElementById("uploadFile").style.display = "";
        document.getElementById("filePath").style.display = "none";
        document.getElementById("fileTitle").innerHTML = "Choose file to upload";
    }
    function showPath()
    {
        document.getElementById("uploadFile").style.display = "none";
        document.getElementById("filePath").style.display = "";
        document.getElementById("fileTitle").innerHTML = "Enter full path of file on server.";
    }
</script>