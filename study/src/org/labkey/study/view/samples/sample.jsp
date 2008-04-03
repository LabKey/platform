<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page import="org.labkey.study.model.Site" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Specimen> me = (JspView<Specimen>) HttpView.currentView();
    Specimen sample = me.getModelBean();
    Site originatingLocation = SampleManager.getInstance().getOriginatingSite(sample);
%>
<table class="normal">
    <tr>
        <th align="right">Specimen Number</th>
        <td><%= h(sample.getSpecimenNumber()) %></td>
    </tr>
    <tr>
        <th align="right">Globally Unique ID</th>
        <td><%= h(sample.getGlobalUniqueId()) %></td>
    </tr>
    <tr>
        <th align="right">Participant</th>
        <td><%= sample.getPtid() %></td>
    </tr>
    <tr>
        <th align="right">Visit</th>
        <td><%= h(sample.getVisitDescription()) %>&nbsp;<%= sample.getVisitValue() %></td>
    </tr>
    <tr>
        <th align="right">Volume</th>
        <td><%= sample.getVolume() %>&nbsp;<%= h(sample.getVolumeUnits()) %></td>
    </tr>
    <tr>
        <th align="right">Collection Date</th>
        <td><%= h(sample.getDrawTimestamp() != null ? formatDateTime(sample.getDrawTimestamp()) : "Unknown") %></td>
    </tr>
    <tr>
        <th align="right">Collection Location</th>
        <td><%= h(originatingLocation != null ? originatingLocation.getDisplayName() : "Unknown") %></td>
    </tr>
</table>