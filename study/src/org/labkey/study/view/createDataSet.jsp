<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    // TODO: Delete this JSP... not used!?

    JspView<StudyController.DatasetForm> me = (JspView<StudyController.DatasetForm>) HttpView.currentView();
    StudyController.DatasetForm form = me.getModelBean();
%>
<labkey:errors/>
<labkey:form action="createDataSet.post" method="POST">
    <input type="hidden" name="action" value="create">
    <table>
        <tr>
            <th align="right">Dataset Id (Integer)</th>
            <td>
                <input type="text" name="dataSetIdStr" value="<%=form.getDatasetIdStr()%>">
            </td>
        </tr>
        <tr>
            <th align="right">Dataset Label</th>
            <td><input type="text" name="label" value="<%=h(form.getLabel())%>"></td>
        </tr>
        <tr>
            <th align="right">Category</th>
            <td><input type="text" name="category" value="<%=h(form.getCategory())%>"></td>
        </tr>
        <tr>
            <th align="right">Show By Default</th>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked(form.isShowByDefault())%>>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %>
            </td>
        </tr>
    </table>
</labkey:form>