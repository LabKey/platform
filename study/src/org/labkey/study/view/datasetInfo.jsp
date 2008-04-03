<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();
%>
<table>
    <tr><td class=ms-searchform>Name</td><th class=normal align=left><%= h(dataset.getName()) %></th></tr>
    <tr><td class=ms-searchform>Label</td><td class=normal><%= h(dataset.getLabel()) %></td></tr>
    <tr><td class=ms-searchform>Display String</td><td class=normal><%= h(dataset.getDisplayString()) %></td></tr>
    <tr><td class=ms-searchform>Category</td><td class=normal><%= h(dataset.getCategory()) %></td></tr>
    <tr><td class=ms-searchform>Visit Date Column</td><td class=normal><%= h(dataset.getVisitDatePropertyName()) %></td></tr>
</table>