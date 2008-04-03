<%@ page import="org.labkey.api.audit.AuditLogService"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    String currentView = (String)request.getAttribute("currentView");
%>
<form action="" method="get">
    <select name="view" onchange="this.form.submit()">
<%
    for (AuditLogService.AuditViewFactory factory : AuditLogService.get().getAuditViewFactories())
    {
%>
        <option value="<%=factory.getEventType()%>" <%=factory.getEventType().equals(currentView) ? "selected" : ""%>><%=h(factory.getName())%></option>
<%
    }
%>
    </select>
</form>