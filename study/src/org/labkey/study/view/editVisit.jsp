<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDataSet" %>
<%@ page import="org.labkey.study.model.VisitDataSetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudyController.VisitSummaryBean> me = (JspView<StudyController.VisitSummaryBean>) HttpView.currentView();
    StudyController.VisitSummaryBean visitBean = me.getModelBean();
    VisitImpl visit = visitBean.getVisit();
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(me.getViewContext().getContainer(), me.getViewContext().getUser());
%>
<labkey:errors/>
<form action="<%=h(buildURL(StudyController.VisitSummaryAction.class))%>" method="POST">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(visit)%>">
<input type="hidden" name="id" value="<%=visit.getRowId()%>">
    <table>
<%--        <tr>
            <th align="right">Name&nbsp;<%=helpPopup("Name", "Short unique name, e.g. 'Enroll'")%></th>
            <td>
                <input type="text" size="50" name="name" value="<%= h(visit.getName()) %>">
            </td>
        </tr> --%>
        <tr>
            <td class=labkey-form-label>Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. 'Enrollment interview'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>VisitId/Sequence Number</td>
            <td>
                <input type="text" size="50" name="sequenceNumMin" value="<%= visit.getSequenceNumMin() %>">-<input type="text" size="50" name="sequenceNumMax" value="<%= visit.getSequenceNumMax() %>">
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Type</td>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            String selected = (visit.getType() == type ? "selected" : "");
                            %>
                            <option value="<%= type.getCode() %>" <%=text(selected)%>><%=h(type.getMeaning())%></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Cohort</td>
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
                    <select name="<%=text(CohortFilterFactory.Params.cohortId.name())%>">
                        <option value="">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>" <%=text(visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId() ? "SELECTED" : "")%>>
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
            <td class=labkey-form-label>Visit Date Dataset</td>
            <td>
                <select name="visitDateDatasetId">
                    <option value="0">[None]</option>
                    <%
                        for (VisitDataSet vds : visit.getVisitDataSets())
                        {
                            DataSet def = StudyManager.getInstance().getDataSetDefinition(getStudy(), vds.getDataSetId());
                            if (def == null || def.getTypeURI() == null)
                                continue;
                            String selected = (visit.getVisitDateDatasetId() == def.getDataSetId() ? "selected" : "");
                            %><option value="<%= def.getDataSetId() %>" <%= text(selected) %>><%= h(def.getLabel()) %></option><%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label>Visit Date Column Name</td>
            <td><%
                // UNDONE: use fancy javascript or AJAX here
                DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(getStudy(), visit.getVisitDateDatasetId());
                String visitDatePropertyName = (null != def && null != def.getVisitDateColumnName()) ? def.getVisitDateColumnName() : "";
                %><input disabled=true value="<%=h(visitDatePropertyName)%>">
            </td>
        </tr>
    <tr>
        <%-- UNDONE: duplicated in createVisit.jsp --%>
        <td class=labkey-form-label>Visit Handling (advanced)<%=
            helpPopup("SequenceNum handling",
                    "You may specificy that unique sequence numbers should be based on visit date.<br>"+
                    "This is for special handling of some log/unscheduled events.<p>"+
                    "Make sure that the sequence number range is adequate (e.g #.0000-#.9999)",
                    true)
        %></td>
        <td>
            <select name="sequenceNumHandling">
              <option <%=text(Visit.SequenceHandling.normal.name().equals(visit.getSequenceNumHandling())?"selected":"")%> value="<%=text(Visit.SequenceHandling.normal.name())%>">Normal</option>
              <option <%=text(Visit.SequenceHandling.logUniqueByDate.name().equals(visit.getSequenceNumHandling()) ?"selected":"")%> value="<%=text(Visit.SequenceHandling.logUniqueByDate.name())%>">Unique Log Events by Date</option>
            </select>
        </td>
    </tr>
        <tr>
            <td class=labkey-form-label>Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault" <%=text(visit.isShowByDefault() ? "checked" : "")%>>
            </td>
        </tr>
        <tr>
            <td class=labkey-form-label valign="top">Associated Datasets</td>
            <td>
                <table>
                <%
                    HashMap<Integer, VisitDataSetType> typeMap = new HashMap<>();
                    for (VisitDataSet vds : visit.getVisitDataSets())
                        typeMap.put(vds.getDataSetId(), vds.isRequired() ? VisitDataSetType.REQUIRED : VisitDataSetType.OPTIONAL);

                    for (DataSet dataSet : getDataSets())
                    {
                        VisitDataSetType type = typeMap.get(dataSet.getDataSetId());
                        if (null == type)
                            type = VisitDataSetType.NOT_ASSOCIATED;
                %>
                        <tr>
                            <td><%= h(dataSet.getDisplayString()) %></td>
                            <td>
                                <input type="hidden" name="dataSetIds" value="<%= dataSet.getDataSetId() %>">
                                <select name="dataSetStatus">
                                    <option value="<%= text(VisitDataSetType.NOT_ASSOCIATED.name()) %>"
                                        <%= text(type == VisitDataSetType.NOT_ASSOCIATED ? "selected" : "")%>></option>
                                    <option value="<%= text(VisitDataSetType.OPTIONAL.name()) %>"
                                        <%= text(type == VisitDataSetType.OPTIONAL ? "selected" : "") %>>Optional</option>
                                    <option value="<%= text(VisitDataSetType.REQUIRED.name())  %>"
                                        <%= text(type == VisitDataSetType.REQUIRED ? "selected" : "") %>>Required</option>
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
    <table>
        <tr>
            <td><%= generateSubmitButton("Save")%>&nbsp;<%= generateButton("Delete visit", buildURL(StudyController.ConfirmDeleteVisitAction.class, "id="+visit.getRowId()))%>&nbsp;<%= generateButton("Cancel", StudyController.ManageVisitsAction.class)%></td>
        </tr>
    </table>
</form>