<%@ page import="org.labkey.study.model.Visit"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<table class="normal">
<%
    if (getVisits().length > 0)
    {
%>
    <tr>
        <td>Visits can be displayed in any order.</td>
        <td><%= textLink("Change Display Order", "visitDisplayOrder.view")%></td>
    </tr>
    <tr>
        <td>Visit visibility and label can be changed.</td>
        <td><%= textLink("Change Properties", "visitVisibility.view")%></td>
    </tr>
<%
    }
%>
    <tr>
        <td>New visits can be defined for this study at any time.</td>
        <td><%= textLink("Create New Visit", "createVisit.view")%></td>
    </tr>
    <tr>
        <td>Recalculate visit dates</td>
        <td><%= textLink("Recompute Visit Dates", "updateParticipantVisits.view")%></td>
    </tr>
    <tr>
        <td>Import a visit map to quickly define a study</td>
        <td><%= textLink("Import Visit Map", "uploadVisitMap.view") %></td>
    </tr>

</table>

<%
    if (getVisits().length > 0)
    {
%>
<p>
<table class="normal">
    <th>&nbsp;</th>
    <th>Label</th>
    <th>Sequence</th>
    <th>Cohort</th>
    <th>Type</th>
    <th>Show By Default</th>
    <th>&nbsp;</th>
    <%
        for (Visit visit : getVisits())
        {
    %>
        <tr>
            <td><%= textLink("edit", "visitSummary.view?id=" + visit.getRowId()) %></td>
            <th align=left><%= visit.getDisplayString() %></th>
            <td><%= visit.getSequenceNumMin() %><%= visit.getSequenceNumMin()!= visit.getSequenceNumMax() ? "-" + visit.getSequenceNumMax() : ""%></td>
            <td><%= visit.getCohort() != null ? h(visit.getCohort().getLabel()) : "All"%></td>
            <td><%= visit.getType() != null ? visit.getType().getMeaning() : "[Not defined]"%></td>
            <td><%= visit.isShowByDefault()%></td>
        </tr>
    <%
        }
    %>
</table>
<%
    }
%>