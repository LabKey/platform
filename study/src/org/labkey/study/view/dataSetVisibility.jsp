<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.study.model.Cohort" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getViewContext().getUser());
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
        for (DataSetDefinition def : getDataSets())
        {
    %>
        <tr>
            <td><%= def.getDataSetId() %></td>
            <td>
                <input type="text" size="20" name="label" value="<%= def.getLabel() != null ? def.getLabel() : "" %>">
            </td>
            <td>
                <input type="text" size="20" name="extraData" value="<%= def.getCategory() != null ? def.getCategory() : "" %>">
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

                        for (Cohort cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%= def.getCohortId() != null && def.getCohortId() == cohort.getRowId() ? "SELECTED" : ""%>>
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
                <input type="checkbox" name="visible" <%= def.isShowByDefault() ? "Checked" : "" %> value="<%= def.getDataSetId() %>">
                <input type="hidden" name="ids" value="<%= def.getDataSetId() %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <%= generateSubmitButton("Save") %>&nbsp;<%= generateButton("Cancel", "manageTypes.view")%>
</form>
