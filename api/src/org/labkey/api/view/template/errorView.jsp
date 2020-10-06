<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.ErrorView" %>
<%@ page import="org.labkey.api.util.ErrorRenderer" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("core/css/core.css");
        dependencies.add("clientapi");
    }
%>
<%
    ErrorView me = (ErrorView) HttpView.currentView();
    ErrorRenderer model = me.getModelBean();

    String appId = "error-handler-app-" + UniqueID.getServerSessionScopedUID();
%>

<div id="<%=h(appId)%>"></div>

<%
    StringBuilder stackTrace = new StringBuilder();
    if (null != model.getException())
    {
        stackTrace.append(model.getException().getMessage());
        for (StackTraceElement stackTraceElement : model.getException().getStackTrace())
        {
            stackTrace.append("\n");
            stackTrace.append(stackTraceElement.toString());
        }
    }
%>

<script type="application/javascript">
    /*
         This error page may be invoked without the themes having been loaded for this container.
         We load the theme artifact for this container here to ensure the correct theme is loaded
         as this cannot be resolved during "addClientDependencies()" for this view.
     */
    LABKEY.requiresCss(<%=q("/core/css/" + PageFlowUtil.resolveThemeName(getContainer()) + ".css")%>);

    LABKEY.requiresScript('core/gen/errorHandler', function() {
    // LABKEY.requiresScript('http://localhost:3001/errorHandler.js', function() {

        LABKEY.App.__app__.isDOMContentLoaded = true;

        LABKEY.App.loadApp('errorHandler', <%=q(appId)%>, {
            errorDetails : {
                message: <%=q(model.getHeading())%>,
                errorType: <%=q(model.getErrorType())%>,
                stackTrace: <%=q(stackTrace.toString())%>,
                errorCode: <%=q(model.getErrorCode())%>
            }
        });
    });
</script>