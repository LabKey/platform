<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainerNoTab();
%>

<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>
<tr><td>
You can import a folder archive to create and populate a new folder.  A folder archive is a .folder.zip file or a collection of
individual files that comforms to the LabKey folder export conventions and formats.  In most cases, a folder archive is created
using the folder export feature.  Using export and import, a folder can be moved from one server to another or a new folder can
be created using a standard template. You can also populate a new folder from a template folder on the current server using the "Create
Folder From Template" option from the folder creation page.

<%--<p>For more information about exporting, importing, and reloading folders, see <%=helpLink("importExportFolder", "the folder documentation")%>.</p>--%>
</td></tr>
<tr><td class="labkey-announcement-title" align=left><span>Import Folder From Local Zip Archive</span></td></tr>
<tr><td class="labkey-title-area-line"></td></tr>
<tr><td>To import a folder from a zip archive on your local machine (for example, a folder that you have exported and saved
        to your local hard drive), browse to a .folder.zip archive, open it, and click the "Import Folder From Local Zip Archive" button below.</td></tr>
<tr><td><input type="file" name="folderZip" size="50"></td></tr>
<tr>
    <td><%=generateSubmitButton("Import Folder From Local Zip Archive")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr><td class="labkey-announcement-title" align=left><span>Import Folder From Server-Accessible Archive</span></td></tr>
<tr><td class="labkey-title-area-line"></td></tr>
    <tr><td>
        To import a folder from a server-accessible archive, click the "Import Folder Using Pipeline"
        button below, navigate to a .folder.zip archive or a folder.xml file, and click the "Import Data" button.
    </td></tr>
<tr>
    <td><%=generateButton("Import Folder Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
</table>
</form>

