<%@ page import="org.labkey.api.exp.AbstractParameter"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Map" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Map<String, ? extends AbstractParameter>> me = (JspView<Map<String, ? extends AbstractParameter>>) HttpView.currentView();
    Map<String, ? extends AbstractParameter> params = me.getModelBean();
%>

<% if (params.isEmpty())
{ %>
<em>No data to show.</em>
<% } %>
<table>
<% for (String name : params.keySet())
{
    AbstractParameter param = params.get(name); %>
    <tr>
        <td class="ms-searchform"><%= h(param.getName()) %></td>
        <td>
            <%= h(param.getValue()) %>
        </td>
    </tr>
<% } %>
</table>