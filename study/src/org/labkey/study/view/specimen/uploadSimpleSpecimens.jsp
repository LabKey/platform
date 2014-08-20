<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.specimen.ShowUploadSpecimensAction" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<ShowUploadSpecimensAction.UploadSpecimensForm> me = (JspView<ShowUploadSpecimensAction.UploadSpecimensForm>) HttpView.currentView();
    ShowUploadSpecimensAction.UploadSpecimensForm bean = me.getModelBean();
    Container c = getContainer();
    Study study = StudyManager.getInstance().getStudy(c);
%>
<labkey:errors/>
Use this form to insert or update specimens in the repository.<br>
<%=textLink("Download a template workbook", new ActionURL(SpecimenController.GetSpecimenExcelAction.class, c))%><br>

<div id="showExpectedDataFieldsDiv"><%= textLink("Show Expected Data Fields", (URLHelper)null, "document.getElementById('expectedDataFields').style.display = 'block'; document.getElementById('showExpectedDataFieldsDiv').style.display = 'none'; return false;", "showExpectedDataFieldsLink") %></div>

<div id="expectedDataFields" style="display: none">
    <br>
    <strong>Required Data Fields</strong>
    <table class="labkey-show-borders" cellpadding="3" cellspacing="0">
        <tr>
            <td><strong>Name</strong></td>
            <td><strong>Required</strong></td>
            <td><strong>Type</strong></td>
            <td><strong>Description</strong></td>
        </tr>
        <tr>
            <td>Global Unique Id</td>
            <td>Yes</td>
            <td>String</td>
            <td>The global unique ID of each vial. Assumed to be globally unique within a study, but duplicates can exist cross-study.</td>
        </tr>
        <tr>
            <td>Sample Id</td>
            <td>Yes</td>
            <td>String</td>
            <td>A unique id to be associated with the sample.  If null, this will be replaced by the Global Unique Id</td>
        </tr>
        <tr>
            <td><%=h(study.getSubjectNounSingular())%> Id</td>
            <td>Yes</td>
            <td>String</td>
            <td>	The ID of the subject providing each specimen.  If null, this will be replaced by the Global Unique Id</td>
        </tr>
        <%
            if (study.getTimepointType() == TimepointType.VISIT) {
        %>
        <tr>
            <td>Visit</td>
            <td>Yes</td>
            <td>Double</td>
            <td>The visit Id associated with the sample.</td>
        </tr>
        <tr>
            <td>Draw Timestamp</td>
            <td>No</td>
            <td>Date</td>
            <td>The timestamp of specimen collection.</td>
        </tr>

        <%
            } else {
        %>
        <tr>
            <td>Draw Timestamp</td>
            <td>Yes</td>
            <td>Date</td>
            <td>The timestamp of specimen collection.</td>
        </tr>
        <% } %>
        <tr>
            <td>Volume</td>
            <td>No</td>
            <td>Double</td>
            <td>The volume of each vial.</td>
        </tr>
        <tr>
            <td>Volume Units</td>
            <td>No</td>
            <td>String</td>
            <td>The units of volume for each specimen.</td>
        </tr>
        <tr>
            <td>Primary Type</td>
            <td>No</td>
            <td>String</td>
            <td>The ID of the primary type of each specimen.</td>
        </tr>
        <tr>
            <td>Derivative Type</td>
            <td>No</td>
            <td>String</td>
            <td>The ID of the derivative type of each specimen.</td>
        </tr>
        <tr>
            <td>Additive Type</td>
            <td>No</td>
            <td>String</td>
            <td>The ID of the additive type of each specimen.</td>
        </tr>

    </table>
</div>
<br>
Paste data in the area below
<labkey:form action="<%=h(buildURL(ShowUploadSpecimensAction.class))%>" method="post" enctype="multipart/form-data">
    <textarea name=tsv id="tsv" rows=20 cols="70"><%=h(bean.getTsv())%></textarea><br>

<%
    if (!bean.isNoSpecimens())
    {
%>
    <p>
    <labkey:radio id="replace" name="replaceOrMerge" value="replace" currentValue="<%=bean.getReplaceOrMerge()%>"/>
    <label for="merge"><b>Replace</b>: Replace all of the existing specimens.</label>
    <br>
    <labkey:radio id="merge" name="replaceOrMerge" value="merge" currentValue="<%=bean.getReplaceOrMerge()%>"/>
    <label for="merge"><b>Merge</b>: Insert new specimens and update existing specimens.</label>
<%
    }
    else
    {
%>
    <input type="hidden" name="replaceOrMerge" value="replace">
<%
    }
%>
    <p>
    <%= button("Submit").submit(true) %> <%= generateBackButton("Cancel") %>
</labkey:form>

