<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    CohortImpl[] cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getViewContext().getUser());
    JspView<Map<Integer,StudyController.DatasetVisibilityData>> me = (JspView<Map<Integer,StudyController.DatasetVisibilityData>>) HttpView.currentView();
    Map<Integer,StudyController.DatasetVisibilityData> bean = me.getModelBean();
%>

<labkey:errors/>

<%

    if (bean.entrySet().size() == 0)
    {
        ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, getViewContext().getContainer());
        createURL.addParameter("autoDatasetId", "true");
%>
    No datasets have been created in this study.<br><br>
    <%= generateButton("Create New Dataset", createURL) %>&nbsp;<%= generateButton("Cancel", "manageTypes.view")%>
<%
    }
    else
    {
%>

<form action="dataSetVisibility.post" method="POST">

<p>Datasets can be hidden on the study overview screen.</p>
<p>Hidden data can always be viewed, but is not shown by default.</p>
    <table>
        <tr>
            <th align="left">ID</th>
            <th align="left">Label</th>
            <th align="left">Category</th>
            <th align="left">Cohort</th>
            <th align="left">Visible</th>
        </tr>
    <%
        for (Map.Entry<Integer, StudyController.DatasetVisibilityData> entry : bean.entrySet())
        {
            int id = entry.getKey().intValue();
            StudyController.DatasetVisibilityData data = entry.getValue();
    %>
        <tr>
            <td><%= id %></td>
            <td>
                <input type="text" size="20" name="label" value="<%= data.label != null ? data.label : "" %>">
            </td>
            <td>
                <input type="text" size="20" name="extraData" value="<%= data.category != null ? data.category : "" %>">
            </td>
            <td>
                <%
                    if (cohorts == null || cohorts.length == 0)
                    {
                %>
                    <em>No cohorts defined</em>
                <%
                    }
                    else
                    {
                    %>
                    <select name="cohort">
                        <option value="-1">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%= data.cohort != null && data.cohort.intValue() == cohort.getRowId() ? "SELECTED" : ""%>>
                            <%= h(cohort.getLabel())%>
                        </option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
            <td align="center">
                <input type="checkbox" name="visible" <%= data.visible ? "Checked" : "" %> value="<%= id %>">
                <input type="hidden" name="ids" value="<%= id %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <%= generateSubmitButton("Save") %>&nbsp;<%= generateButton("Cancel", "manageTypes.view")%>
</form>
<%
    }
%>