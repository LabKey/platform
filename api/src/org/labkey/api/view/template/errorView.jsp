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
        dependencies.add("http://localhost:3001/errorHandler.js");
//        dependencies.add("core/gen/errorHandler");
    }
%>
<%
    ErrorTemplate me = (ErrorTemplate) HttpView.currentView();
    ErrorRenderer model = me.getModelBean();

    String uniqueId = "" + UniqueID.getServerSessionScopedUID();
    String appId = "error-handler-app-" + uniqueId;
%>

<div>
    <div id="<%=h(appId)%>"></div>
</div>

<script type="application/javascript">
    LABKEY.App.loadApp('errorHandler', <%=q(appId)%>, {
        message: "<%=unsafe(model.getHeading())%>",
    });
</script>