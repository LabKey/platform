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
<%@ page import="org.labkey.study.model.VisitImpl"%>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getStudy().getContainer(), getUser());
%>
<labkey:form action="<%=h(buildURL(StudyController.VisitVisibilityAction.class))%>" method="POST">
    <table class="lk-fields-table">
        <tr>
            <th align="left" style="font-weight: bold;">ID</th>
            <th align="left" style="font-weight: bold;">Label</th>
            <th align="left" style="font-weight: bold;">Cohort</th>
            <th align="left" style="font-weight: bold;">Type</th>
            <th align="left" style="font-weight: bold;">Show By Default</th>
        </tr>
    <%
        for (VisitImpl visit : getVisits(Visit.Order.DISPLAY))
        {
    %>
        <tr>
            <td><%= visit.getRowId() %></td>
            <td>
                <input type="text" size="40" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
            <td>
                <%
                    if (cohorts == null || cohorts.size() == 0)
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
                        <option value="<%= cohort.getRowId()%>"<%=selected(visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId()) %>>
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
            <td>
                <select name="extraData">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            boolean selected = (visit.getType() == type);
                            %>
                            <option value="<%= type.getCode() %>"<%=selected(selected)%>><%= h(type.getMeaning()) %></option>
                            <%
                        }
                    %>
                </select>
            </td>
            <td>
                <input type="checkbox" name="visible"<%=checked(visit.isShowByDefault())%> value="<%= visit.getRowId() %>">
                <input type="hidden" name="ids" value="<%= visit.getRowId() %>">
            </td>
        </tr>
    <%
        }
    %>
    </table>
    <br/>
    <%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(StudyController.ManageVisitsAction.class, getContainer()) %>
</labkey:form>
