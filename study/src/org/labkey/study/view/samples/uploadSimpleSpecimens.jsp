<%
/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ShowUploadSpecimensAction.UploadSpecimensForm> me = (JspView<ShowUploadSpecimensAction.UploadSpecimensForm>) HttpView.currentView();
    ShowUploadSpecimensAction.UploadSpecimensForm bean = me.getModelBean();
    Container c = getViewContext().getContainer();
%>
<labkey:errors/>
Use this form to insert or update specimens in the repository.<br>
<%=textLink("Download a template workbook", new ActionURL(SpecimenController.GetSpecimenExcelAction.class, c))%><br><br>
Paste data in the area below
<form action="<%=h(buildURL(ShowUploadSpecimensAction.class))%>" method="post" enctype="multipart/form-data">
    <textarea name=tsv id="tsv" rows=20 cols="70"><%=h(bean.getTsv())%></textarea><br>

    <p>
    <labkey:radio id="replace" name="replaceOrMerge" value="replace" currentValue="<%=bean.getReplaceOrMerge()%>"/>
    <label for="merge"><b>Replace</b>: Replace all of the existing specimens.</label>
    <br>
    <labkey:radio id="merge" name="replaceOrMerge" value="merge" currentValue="<%=bean.getReplaceOrMerge()%>"/>
    <label for="merge"><b>Merge</b>: Insert new specimens and update existing specimens.</label>

    <p>
    <%=generateSubmitButton("Submit")%> <%=buttonImg("Cancel", "window.history.back();return false;")%>
</form>

