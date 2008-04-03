<%@ page import="org.labkey.api.exp.api.ExpProtocol"%>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpRun> me = (JspView<ExpRun>) HttpView.currentView();
    ExpRun run = me.getModelBean();
    ExpProtocol protocol = run.getProtocol();
%>

<table>
    <tr>
        <td class="ms-searchform">Name</td>
        <td><%= h(run.getName()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">LSID</td>
        <td><%= h(run.getLSID()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Protocol</td>
        <td><a href="protocolDetails.view?rowId=<%= protocol.getRowId() %>"><%= h(protocol.getName()) %></a></td>
    </tr>
    <tr>
        <td class="ms-searchform">Created on</td>
        <td><%=DateUtil.formatDateTime(run.getCreated()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Comments</td>
        <td><%= h(run.getComments()) %></td>
    </tr>
</table>
