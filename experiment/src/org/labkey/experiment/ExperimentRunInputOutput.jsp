<%@ page import="org.labkey.api.exp.api.ExpData" %>
<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.experiment.controllers.exp.RunInputOutputBean" %>
<%@ page import="org.labkey.api.exp.ExperimentDataHandler" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RunInputOutputBean> me = (JspView<RunInputOutputBean>) HttpView.currentView();
    RunInputOutputBean bean= me.getModelBean();
%>

<table>
    <% for (Map.Entry<ExpMaterial, String> entry : bean.getMaterials().entrySet()) {
        ExpMaterial material = entry.getKey(); %>
        <tr>
            <td>Material</td>
            <td><% if (entry.getValue() != null) { %>(<%= h(entry.getValue()) %>)<% } %></td>
            <td><a href="showMaterial.view?rowId=<%= material.getRowId() %>"><%= h(material.getName() == null || material.getName().trim().length() == 0 ? "[no name]" : material.getName()) %></a></td>
        </tr>
    <% }
    for (Map.Entry<ExpData, String> entry : bean.getDatas().entrySet()) {
        ExpData d = entry.getKey();
        ExperimentDataHandler handler = d.findDataHandler();
        URLHelper url = handler == null ? null : handler.getContentURL(HttpView.currentContext().getContainer(), d);
    %>
        <tr>
            <td>Data</td>
            <td><% if (entry.getValue() != null) { %>(<%= h(entry.getValue()) %>)<% } %></td>
            <td>
                <a href="showData.view?rowId=<%= d.getRowId() %>"><%= h(d.getName() == null || d.getName().trim().length() == 0 ? "[no name]" : d.getName()) %></a>
                <% if(url != null) { %> [<a href="<%= url %>">view</a>]<% } %>
            </td>
        </tr>
    <% } %>
</table>