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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyController.VisitSummaryBean> me = (JspView<StudyController.VisitSummaryBean>) HttpView.currentView();
    StudyController.VisitSummaryBean visitBean = me.getModelBean();
    Visit visit = visitBean.getVisit();
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(me.getViewContext().getContainer(), me.getViewContext().getUser());
%>

<table border=0 cellspacing=2 cellpadding=0>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<form action="visitSummary.post" method="POST">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(visit)%>">
<input type="hidden" name="id" value="<%=visit.getRowId()%>">
    <table class="normal">
<%--        <tr>
            <th align="right">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></th>
            <td>
                <input type="text" size="50" name="name" value="<%= h(visit.getName()) %>">
            </td>
        </tr> --%>
        <tr>
            <th align="right">Label&nbsp;<%=helpPopup("Label", "Descriptive Label e.g. Week 2'")%></th>
            <td>
                <input type="text" size="50" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
        </tr>
        <tr>
            <th align="right">Day Range&nbsp;<%=helpPopup("Day Range", "Days from start date encompassing this visit. E.g. 11-17 for Week 2")%></th>
            <td>
                <input type="text" size="50" name="sequenceNumMin" value="<%= (int) visit.getSequenceNumMin() %>">-<input type="text" size="50" name="sequenceNumMax" value="<%= (int) visit.getSequenceNumMax() %>">
            </td>
        </tr>
        <tr>
            <th align="right">Cohort</th>
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
                    <select name="cohortId">
                        <option value="">All</option>
                    <%

                        for (Cohort cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%= visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId() ? "SELECTED" : ""%>>
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
        </tr>
        <tr>
            <th align="right">Show By Default</th>
            <td>
                <input type="checkbox" name="showByDefault" <%= visit.isShowByDefault() ? "checked" : "" %>>
            </td>
        </tr>
        <tr>
            <th valign="top">Associated Datasets</th>
            <td>
                <table>
                <%
                    HashMap<Integer, VisitDataSetType> typeMap = new HashMap<Integer, VisitDataSetType>();
                    for (VisitDataSet vds : visit.getVisitDataSets())
                        typeMap.put(vds.getDataSetId(), vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);

                    for (DataSetDefinition dataSet : getDataSets())
                    {
                        VisitDataSetType type = typeMap.get(dataSet.getDataSetId());
                        if (null == type)
                            type = VisitDataSetType.NOT_ASSOCIATED;
                %>
                        <tr>
                            <td><%= dataSet.getDisplayString() %></td>
                            <td>
                                <input type="hidden" name="dataSetIds" value="<%= dataSet.getDataSetId() %>">
                                <select name="dataSetStatus">
                                    <option value="<%= VisitDataSetType.NOT_ASSOCIATED.name() %>"
                                        <%= type == VisitDataSetType.NOT_ASSOCIATED ? "selected" : ""%>></option>
                                    <option value="<%= VisitDataSetType.OPTIONAL.name() %>"
                                        <%= type == VisitDataSetType.OPTIONAL ? "selected" : ""%>>Optional</option>
                                    <option value="<%= VisitDataSetType.REQUIRED.name() %>"
                                        <%= type == VisitDataSetType.REQUIRED ? "selected" : ""%>>Required</option>
                                </select>
                            </td>
                        </tr>
                <%
                    }
                %>
                </table>
            </td>
        </tr>
    </table>
    <table class="normal">
        <tr>
            <td><%= buttonImg("Save")%>&nbsp;<%= buttonLink("Delete visit", "confirmDeleteVisit.view?id="+visit.getRowId())%>&nbsp;<%= buttonLink("Cancel", "manageVisits.view")%></td>
        </tr>
    </table>
</form>