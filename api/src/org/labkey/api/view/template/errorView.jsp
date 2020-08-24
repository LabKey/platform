<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.ErrorTemplate" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    ErrorTemplate me = (ErrorTemplate) HttpView.currentView();
    PageConfig model = me.getModelBean();
%>

<div>
    <h3 class="labkey-error"> <%=PageFlowUtil.filter(me.getErrorRender().getHeading())%></h3>
</div>