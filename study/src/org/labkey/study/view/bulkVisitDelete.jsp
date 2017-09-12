<%
/*
 * Copyright (c) 2017 LabKey Corporation
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

<%@ page import="org.labkey.api.study.TimepointType"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.DeleteVisitsForm" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.SpecimenManager" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="org.labkey.study.model.VisitMapKey" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<DeleteVisitsForm> me = (JspView<DeleteVisitsForm>)HttpView.currentView();
    DeleteVisitsForm form = me.getModelBean();

    StudyManager studyManager = StudyManager.getInstance();
    StudyImpl study = studyManager.getStudy(getContainer());
    VisitManager visitManager = studyManager.getVisitManager(study);
    boolean isDateBased = study != null && study.getTimepointType() == TimepointType.DATE;
    String noun = isDateBased ? "Timepoint" : "Visit";

    ActionURL returnURL;
    if (getActionURL().getParameter("returnUrl") != null)
        returnURL = new ActionURL(getActionURL().getParameter("returnUrl"));
    else
        returnURL = new ActionURL(StudyController.ManageVisitsAction.class, getContainer());

    Map<VisitMapKey, VisitManager.VisitStatistics> visitSummaryMap = visitManager.getVisitSummary(getUser(), null, null, Collections.singleton(VisitManager.VisitStatistic.RowCount), true);
    Map<Integer, Integer> visitRowCountMap = new HashMap<>();
    for (Map.Entry<VisitMapKey, VisitManager.VisitStatistics> e : visitSummaryMap.entrySet())
    {
        VisitMapKey key = e.getKey();
        if (!visitRowCountMap.containsKey(key.visitRowId))
            visitRowCountMap.put(key.visitRowId, 0);

        int newRowCount = visitRowCountMap.get(key.visitRowId) + e.getValue().get(VisitManager.VisitStatistic.RowCount);
        visitRowCountMap.put(key.visitRowId, newRowCount);
    }
%>

<p>
    Select the <%=h(noun.toLowerCase())%>s you want to delete.
    <span style="font-weight: bold;">Note: this will also delete any related dataset and specimen rows.</span>
</p>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(StudyController.BulkDeleteVisitsAction.class))%>" name="bulkDeleteVisits" method="POST">
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header"><input type="checkbox" onchange="toggleAllRows(this);"></td>
        <td class="labkey-column-header">Label</td>
        <td class="labkey-column-header"><%=h(isDateBased ? "Day" : "Sequence")%> Range</td>
        <td class="labkey-column-header">Cohort</td>
        <td class="labkey-column-header">Type</td>
        <td class="labkey-column-header"># of Dataset Rows</td>
        <td class="labkey-column-header"># of Specimen Rows</td>
    </tr>
    <%
        int rowCount = 0;
        List<VisitImpl> allVisits = studyManager.getVisits(study, Visit.Order.DISPLAY);
        for (VisitImpl visit : allVisits)
        {
            ActionURL visitSummaryURL = new ActionURL(StudyController.VisitSummaryAction.class, study.getContainer());
            visitSummaryURL.addParameter("id", visit.getRowId());
            int dataCount = visitRowCountMap.containsKey(visit.getRowId()) ? visitRowCountMap.get(visit.getRowId()) : 0;
            int vialCount = SpecimenManager.getInstance().getSampleCountForVisit(visit);

            rowCount++;
    %>
        <tr class="visit-row <%=h(rowCount % 2 == 1 ? "labkey-alternate-row" : "labkey-row")%>">
            <td><input type="checkbox" name="visitIds" value="<%=visit.getRowId()%>"></td>
            <td class="visit-label"><a href="<%=visitSummaryURL.getLocalURIString()%>"><%= h(visit.getDisplayString()) %></a></td>
            <td class="visit-range"><%= visit.getSequenceNumMin() %><%= h(visit.getSequenceNumMin()!= visit.getSequenceNumMax() ? " - " + visit.getSequenceNumMax() : "") %></td>
            <td><%= h(visit.getCohort() != null ? h(visit.getCohort().getLabel()) : "All") %></td>
            <td><%= h(visit.getType() != null ? visit.getType().getMeaning() : "[Not defined]") %></td>
            <td align="right" class="visit-dataset-count"><%=h(Formats.commaf0.format(dataCount))%></td>
            <td align="right" class="visit-specimen-count"><%=h(Formats.commaf0.format(vialCount))%></td>
        </tr>
    <%
        }
    %>
</table>
<br/>
<%= button("Delete Selected").id("delete_btn").submit(true).onClick(
    "if (confirm('Are you sure you want to delete the selected visit and all related dataset/specimen data? This action cannot be undone.')){" +
        "Ext4.get(this).replaceCls('labkey-button', 'labkey-disabled-button');" +
        "return true;" +
    "} " +
    "else return false;")%>
<%= button("Cancel").href(returnURL) %>
</labkey:form>

<script type="text/javascript">
    function toggleAllRows(checkbox)
    {
        var i;
        var checked = checkbox.checked;
        var elements = document.getElementsByName("visitIds");
        for (i in elements)
        {
            var e = elements[i];
            e.checked = checked;
        }
    }
</script>