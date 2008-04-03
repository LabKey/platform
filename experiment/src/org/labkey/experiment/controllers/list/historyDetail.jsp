<%@ page import="org.labkey.api.audit.AuditLogEvent"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.experiment.list.ListManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<AuditLogEvent> me = (JspView<AuditLogEvent>) HttpView.currentView();
    AuditLogEvent bean = me.getModelBean();

    Map<String, Object> dataMap = OntologyManager.getProperties(ContainerManager.getSharedContainer().getId(), bean.getLsid());
%>


<table><tr><td class="ms-searchform"><%=bean.getComment()%></td></tr></table><br/>
<%=dataMap.get(AuditLogService.get().getPropertyURI(ListManager.LIST_AUDIT_EVENT, "modifications"))%>