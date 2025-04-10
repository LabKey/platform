<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.element.TextArea.TextAreaBuilder"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.controllers.BaseStudyController.StudyJspView" %>
<%@ page import="org.labkey.study.controllers.StudyController.ImportDatasetSchemaAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.ImportDatasetSchemaForm" %>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StudyJspView<ImportDatasetSchemaForm> me = HttpView.currentView();
    ImportDatasetSchemaForm bean = me.getModelBean();
%>
<labkey:errors/>
<p>
Use this page to import the schema for multiple datasets. Paste XML contents from dataset_manifest.xml and
dataset_metadata.xml files. These files are included in folder archives exported from a study folder.
</p>
<p>
For more information about the schema definition format, see <%=helpLink("DatasetBulkDefinition", "the dataset schema definition documentation page")%>
</p>
<table>
    <tr>
        <th align="left" colspan="3"><span class="labkey-error">* Both fields are required</span></th>
    </tr>
</table>

<labkey:form action="<%=urlFor(ImportDatasetSchemaAction.class)%>" method="POST" enctype="multipart/form-data">
    <table>
        <tr>
            <td>
                <%=new TextAreaBuilder().name("manifest").label("Dataset manifest XML *")
                    .required(true)
                    .formGroup(true)
                    .value(bean.getManifest())
                    .columns(160)
                    .rows(20)
                %>
            </td>
        </tr>
        <tr>
            <td>
                <%=new TextAreaBuilder().name("metadata").label("Dataset metadata XML *")
                    .required(true)
                    .formGroup(true)
                    .value(bean.getMetadata())
                    .columns(160)
                    .rows(20)
                %>
            </td>
        </tr>
        <tr>
            <td><%= button("Submit").submit(true) %>&nbsp;<%= button("Cancel").href(ManageTypesAction.class, getContainer()) %></td>
        </tr>
    </table>
</labkey:form>
