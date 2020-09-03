<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.ErrorTemplate" %>
<%@ page import="org.labkey.api.util.ErrorRenderer" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi"); // added this for App Template
//        dependencies.add("http://localhost:3001/errorHandler.js");
        dependencies.add("core/gen/errorHandler");
    }
%>
<%
    ErrorTemplate me = (ErrorTemplate) HttpView.currentView();
    ErrorRenderer model = me.getModelBean();

    String appId = "error-handler-app-" + UniqueID.getServerSessionScopedUID();
%>

<div id="<%=h(appId)%>"></div>

<script type="application/javascript">
    LABKEY.App.loadApp('errorHandler', <%=q(appId)%>, {
        message: "<%=unsafe(model.getHeading())%>",
        errorType: "<%=unsafe(model.getErrorType())%>"
    });
</script>