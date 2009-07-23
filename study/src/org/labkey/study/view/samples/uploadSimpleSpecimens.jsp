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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.ShowUploadSpecimensAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ShowUploadSpecimensAction.UploadSpecimensForm> me = (JspView<ShowUploadSpecimensAction.UploadSpecimensForm>) HttpView.currentView();
    ShowUploadSpecimensAction.UploadSpecimensForm bean = me.getModelBean();
%>
<labkey:errors/>
Use this form to <b>replace all specimens</b> in the repository with a new list of specimens.<br>
[<a href="getSpecimenExcel.view">Download a template workbook</a>]<br><br>
Paste data in the area below
<form action="showUploadSpecimens.post" method="post" enctype="multipart/form-data">
    <textarea name=tsv id="tsv" rows=20 cols="70"><%=h(bean.getTsv())%></textarea><br>
    <%=generateSubmitButton("Submit")%> <%=buttonImg("Cancel", "window.history.back();return false;")%>
</form>

<b>This will REPLACE all specimens in the repository</b>
