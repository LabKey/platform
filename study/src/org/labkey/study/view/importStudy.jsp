<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
    boolean reload = ((Boolean) HttpView.currentModel()).booleanValue();

    String lowerCaseVerb = (reload ? "reload" : "import");
    String initialCapVerb = (reload ? "Reload" : "Import");
%>
<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>
<tr><td><%
    if (reload)
    {
%>
You can reload a study archive to update an existing study with new settings and data.  A study archive is a .study.zip file
or a collection of individual files that comforms to the LabKey study export conventions and formats.  A study archive can be
created using the study export feature or via scripts that write data from a master repository into the correct formats.
Note: Reloading a study will replace existing study data with the data in the archive.
<%
    }
    else
    {
%>
You can import a study archive to create and populate a new study.  A study archive is a .study.zip file or a collection of
individual files that comforms to the LabKey study export conventions and formats.  In most cases, a study archive is created
using the study export feature.  Using export and import, a study can be moved from one server to another or a new study can
be created using a standard template.
<%
    }
%>
<p>For more information about exporting, importing, and reloading studies, see <a href="<%=new HelpTopic("importExportStudy", HelpTopic.Area.STUDY)%>">the study documentation</a>.</p>
</td></tr>
<tr><td class="labkey-announcement-title" align=left><span><%=initialCapVerb%> Study From Local Zip Archive</span></td></tr>
<tr><td class="labkey-title-area-line"><img height=1 width=1 src="/labkey/_.gif"></td></tr>
<tr><td>To <%=lowerCaseVerb%> a study from a zip archive on your local machine (for example, a study that you have exported and saved
        to your local hard drive), browse to a .study.zip file, open it, and click the "<%=initialCapVerb%> Study From Local Zip Archive" button below.</td></tr>
<tr><td><input type="file" name="studyZip" size="50"></td></tr>
<tr>
    <td><%=PageFlowUtil.generateSubmitButton(initialCapVerb + " Study From Local Zip Archive")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr><td class="labkey-announcement-title" align=left><span><%=initialCapVerb%> Study From Server-Accessible Archive</span></td></tr>
<tr><td class="labkey-title-area-line"><img height=1 width=1 src="/labkey/_.gif"></td></tr>
    <tr><td>To <%=lowerCaseVerb%> a study from a server-accessible archive, click the "<%=initialCapVerb%> Study Using Pipeline"
            button below, navigate to a .study.zip archive or a study.xml file, and click the "<%=initialCapVerb%> Study" button.<%

            if (reload) { %> You can also reload study archives via the "Data Pipeline" link in the study overview.<% } %></td></tr>
<tr>
    <td><%=PageFlowUtil.generateButton(initialCapVerb + " Study Using Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
</tr>
</table>
</form>
