<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    StudyController.ImportTypeForm form = (StudyController.ImportTypeForm)HttpView.currentModel();
    String formDatasetId = form.getDatasetId() == null ? "" : form.getDatasetId().toString();
%>
<labkey:errors />
<labkey:form name="typeDefForm" action="<%=h(buildURL(StudyController.DefineDatasetTypeAction.class))%>" method="POST" enctype="multipart/form-data">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label-nowrap">Short Dataset Name <%=helpPopup("Name", "Short unique name, e.g. 'DEM1'")%></td>
            <td><input name="typeName" style="width:100%" value="<%=h(form.getTypeName())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label-nowrap">Dataset ID <%=helpPopup("Dataset ID", "The dataset id is an integer number that must be unique for each dataset in a study.")%></td>
            <td>
                <input id="datasetId" type="text" name="datasetId" value="<%=formDatasetId%>"<%=disabled(form.isAutoDatasetId())%> size="6">
                <input type="checkbox" name="autoDatasetId" onclick="+function(ck){var di=document.getElementById('datasetId');di.value='';di.disabled=ck.checked;}(this);"<%=checked(form.isAutoDatasetId())%>>Define Dataset Id Automatically
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Import from File <%=PageFlowUtil.helpPopup("Import from File", "Use this option if you have a spreadsheet that you would like uploaded as a dataset.")%></td>
            <td><input type="checkbox" name="fileImport" <%=checked(form.isFileImport())%>></td>
        </tr>
        <tr>
            <td colspan=5><%= button("Next").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>