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
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.CohortFilterFactory" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDataset" %>
<%@ page import="org.labkey.study.model.VisitDatasetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StudyController.VisitSummaryBean> me = (JspView<StudyController.VisitSummaryBean>) HttpView.currentView();
    StudyController.VisitSummaryBean visitBean = me.getModelBean();
    VisitImpl visit = visitBean.getVisit();

    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
    boolean isDateBased = study != null && study.getTimepointType() == TimepointType.DATE;
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(getContainer(), getUser());
%>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(StudyController.VisitSummaryAction.class))%>" method="POST">
<input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(visit)%>">
<input type="hidden" name="id" value="<%=visit.getRowId()%>">
    <table class="lk-fields-table">
        <tr>
            <td class="labkey-form-label">Label&nbsp;<%=helpPopup("Label", "Descriptive label, e.g. 'Enrollment interview' or 'Week 2'")%></td>
            <td>
                <input type="text" size="50" name="label" value="<%= h(visit.getLabel()) %>">
            </td>
        </tr>
<%
    if (isDateBased)
    {
%>
        <tr>
            <td class="labkey-form-label">Day Range&nbsp;<%=helpPopup("Day Range", "Days from start date encompassing this visit. E.g. 11-17 for Week 2")%></td>
            <td>
                <input type="text" size="26" name="sequenceNumMin" value="<%=(int) visit.getSequenceNumMin()%>">-<input type="text" size="26" name="sequenceNumMax" value="<%=(int) visit.getSequenceNumMax()%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Protocol Day&nbsp;<%=helpPopup("Protocol Day", "The expected day for this visit according to the protocol, used for study alignment.")%></td>
            <td>
                <input type="text" size="26" name="protocolDay" value="<%= null != visit.getProtocolDay() ? (int)(double)visit.getProtocolDay() : ""%>">
            </td>
        </tr>
<%
    }
    else
    {
%>
        <tr>
            <td class="labkey-form-label">VisitId/Sequence Range</td>
            <td>
                <input type="text" size="26" name="sequenceNumMin" value="<%=visit.getSequenceNumMin()%>">-<input type="text" size="26" name="sequenceNumMax" value="<%=visit.getSequenceNumMax()%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Protocol Day&nbsp;<%=helpPopup("Protocol Day", "The expected day for this visit according to the protocol, used for study alignment.")%></td>
            <td>
                <input type="text" size="26" name="protocolDay" value="<%=null != visit.getProtocolDay() ? visit.getProtocolDay() : ""%>">
            </td>
        </tr>
<%
    }
%>
        <tr>
            <td class="labkey-form-label">Description&nbsp;<%=helpPopup("Description", "A short description of the visit, appears as hovertext on visit headers in study navigator and visit column in datasets.")%></td>
            <td>
                <textarea name="description" cols="50" rows="3"><%= h(visit.getDescription()) %></textarea>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Type</td>
            <td>
                <select name="typeCode">
                    <option value="">[None]</option>
                    <%
                        for (Visit.Type type : Visit.Type.values())
                        {
                            boolean selected = (visit.getType() == type);
                            %>
                            <option value="<%= type.getCode() %>"<%=selected(selected)%>><%=h(type.getMeaning())%></option>
                            <%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Cohort</td>
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
                    <select name="<%=h(CohortFilterFactory.Params.cohortId.name())%>">
                        <option value="">All</option>
                    <%

                        for (CohortImpl cohort : cohorts)
                        {
                    %>
                        <option value="<%= cohort.getRowId()%>"<%=selected(visit.getCohortId() != null && visit.getCohortId() == cohort.getRowId())%>><%= h(cohort.getLabel())%></option>
                    <%
                        }
                    %>
                    </select>
                    <%
                    }
                %>
            </td>
        </tr>
<%
    if (!isDateBased)
    {
%>
        <tr>
            <td class="labkey-form-label">Visit Date Dataset</td>
            <td>
                <select name="visitDateDatasetId">
                    <option value="0">[None]</option>
                    <%
                        for (VisitDataset vds : visit.getVisitDatasets())
                        {
                            Dataset def = StudyManager.getInstance().getDatasetDefinition(getStudy(), vds.getDatasetId());
                            if (def == null || def.getTypeURI() == null)
                                continue;
                            boolean selected = (visit.getVisitDateDatasetId() == def.getDatasetId());
                            %><option value="<%= def.getDatasetId() %>"<%=selected(selected) %>><%= h(def.getLabel()) %></option><%
                        }
                    %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Visit Date Column Name</td>
            <td><%
                // UNDONE: use fancy javascript or AJAX here
                DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(getStudy(), visit.getVisitDateDatasetId());
                String visitDatePropertyName = (null != def && null != def.getVisitDateColumnName()) ? def.getVisitDateColumnName() : "";
                %><input disabled=true value="<%=h(visitDatePropertyName)%>">
            </td>
        </tr>
    <tr>
        <%-- UNDONE: duplicated in createVisit.jsp --%>
        <td class="labkey-form-label">Visit Handling (advanced)<%=
            helpPopup("Visit Handling (advanced)",
                    "You may specify that unique sequence numbers should be based on visit date."+
                    "<p>This is for special handling of some log/unscheduled events.</p>"+
                    "<p>Make sure that the sequence number range is adequate (e.g #.0000-#.9999).</p>",
                    true)
        %></td>
        <td>
            <select name="sequenceNumHandling">
              <option<%=selected(Visit.SequenceHandling.normal == visit.getSequenceNumHandlingEnum())%> value="<%=text(Visit.SequenceHandling.normal.name())%>">Normal</option>
              <option<%=selected(Visit.SequenceHandling.logUniqueByDate == visit.getSequenceNumHandlingEnum())%> value="<%=text(Visit.SequenceHandling.logUniqueByDate.name())%>">Unique Log Events by Date</option>
            </select>
        </td>
    </tr>
<%
    }
%>
        <tr>
            <td class="labkey-form-label">Show By Default</td>
            <td>
                <input type="checkbox" name="showByDefault"<%=checked(visit.isShowByDefault())%>>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label" valign="top">Associated Datasets</td>
            <td>
                <table class="lk-fields-table">
                <%
                    HashMap<Integer, VisitDatasetType> typeMap = new HashMap<>();
                    for (VisitDataset vds : visit.getVisitDatasets())
                        typeMap.put(vds.getDatasetId(), vds.isRequired() ? VisitDatasetType.REQUIRED : VisitDatasetType.OPTIONAL);

                    for (Dataset dataset : getDatasets())
                    {
                        VisitDatasetType type = typeMap.get(dataset.getDatasetId());
                        if (null == type)
                            type = VisitDatasetType.NOT_ASSOCIATED;
                %>
                        <tr>
                            <td><%= h(dataset.getDisplayString()) %></td>
                            <td>
                                <input type="hidden" name="datasetIds" value="<%= dataset.getDatasetId() %>">
                                <select name="datasetStatus">
                                    <option value="<%= h(VisitDatasetType.NOT_ASSOCIATED.name()) %>"
                                        <%=selected(type == VisitDatasetType.NOT_ASSOCIATED)%>></option>
                                    <option value="<%= h(VisitDatasetType.OPTIONAL.name()) %>"
                                        <%=selected(type == VisitDatasetType.OPTIONAL) %>>Optional</option>
                                    <option value="<%= h(VisitDatasetType.REQUIRED.name()) %>"
                                        <%=selected(type == VisitDatasetType.REQUIRED) %>>Required</option>
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
    <br/>
    <%= button("Save").submit(true) %>&nbsp;
    <%= button(isDateBased ? "Delete Timepoint" : "Delete Visit").href(buildURL(StudyController.ConfirmDeleteVisitAction.class, "id="+visit.getRowId())) %>&nbsp;
    <%= button("Cancel").href(StudyController.ManageVisitsAction.class, getContainer()) %>
</labkey:form>