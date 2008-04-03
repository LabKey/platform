<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.exp.api.ExpProtocol" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExpProtocol> me = (JspView<ExpProtocol>) HttpView.currentView();
    ExpProtocol protocol = me.getModelBean();
%>

<table>
    <tr>
        <td class="ms-searchform">Name</td>
        <td><%= h(protocol.getName()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">LSID</td>
        <td><%= h(protocol.getLSID()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Contact</td>
        <td><%= h(protocol.getContact()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Instrument</td>
        <td><%= h(protocol.getInstrument()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Software</td>
        <td><%= h(protocol.getSoftware()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform">Description</td>
        <td><%= h(protocol.getProtocolDescription()) %></td>
    </tr>
</table>