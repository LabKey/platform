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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getViewContext().getContainer();
    boolean reload = ((Boolean) HttpView.currentModel()).booleanValue();
%>
<form action="" name="import" enctype="multipart/form-data" method="post">
<table cellpadding=0>
    <%=formatMissedErrorsInTable("form", 2)%>
<tr>
    <td><%=(reload ? "Reload" : "Import")%> from a .zip file</td>
    <td><input type="file" name="studyZip" size="50"></td>
</tr>
<tr>
    <td><%=PageFlowUtil.generateSubmitButton((reload ? "Reload" : "Import") + " Study")%></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>
<tr>
    <td><%=reload ? "Reload" : "Import"%> from pipeline</td>
</tr>
<tr>
    <td><%=PageFlowUtil.generateButton((reload ? "Reload" : "Import") + " From Pipeline", urlProvider(PipelineUrls.class).urlBrowse(c, "pipeline"))%></td>
</tr>
</table>
</form>
