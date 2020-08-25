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
//        dependencies.add("xxxx");
//        dependencies.add("http://localhost:3001/errorHandler.js");
        dependencies.add("core/gen/errorHandler");
    }
%>
<%
    ErrorTemplate me = (ErrorTemplate) HttpView.currentView();
    PageConfig model = me.getModelBean();
    String appId = "error-handler";
%>

<div>
    <h3 class="labkey-error"> <%=PageFlowUtil.filter(me.getErrorRender().getHeading())%></h3>
    <div id="<%=q(appId)%>"></div>
</div>

<script type="application/javascript">
    console.log(LABKEY.App);

    (function () {
        try {
            LABKEY.App.loadApp('errorHandler', <%=q(appId)%>, {
                message: "",
            });
        }
        catch (e) {

        }
    })();

</script>