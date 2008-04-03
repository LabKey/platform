<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.demo.view.BulkUpdatePage" %>
<%@ page import="org.labkey.demo.model.Person" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext context = HttpView.currentContext();
    org.labkey.demo.view.BulkUpdatePage pageInfo = (BulkUpdatePage) (HttpView.currentModel());
    List<Person> people = pageInfo.getList();
%>
This container contains <%= people.size() %> people.<br>
<%= buttonLink("View Grid", new ActionURL("demo", "begin", context.getContainer())) %>