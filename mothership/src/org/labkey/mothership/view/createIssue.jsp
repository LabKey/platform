<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<% Map<String, String> ctx = (Map)getModelBean(); %>

<form method="POST" id="CreateIssue" action="<%=h(ctx.get("action"))%>">
    <input type="hidden" name="callbackURL" value="<%=h(ctx.get("callbackURL"))%>"/>
    <input type="hidden" name="body" value="<%=h(ctx.get("body"))%>"/>
    <input type="hidden" name="title" value="<%=h(ctx.get("title"))%>"/>
    <input type="hidden" name="skipPost" value="true"/>
    <input type="hidden" name="assignedTo" value=""/>
</form>