<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
Before uploading datasets, an administrator must set up a "pipeline"
directory where uploaded data will be stored.<br><br>

<%
    ViewContext context = HttpView.currentContext();
    if (context.hasPermission(ACL.PERM_ADMIN))
    {
        ActionURL pipelineUrl = context.cloneActionURL();
        pipelineUrl.setPageFlow("Pipeline").setAction("setup.view");
        pipelineUrl.addParameter("referer", context.getActionURL().getLocalURIString());
        out.print(textLink("Pipeline Setup", pipelineUrl));
        out.print(" ");
    }
    if (null == HttpView.currentModel() || (Boolean) HttpView.currentModel())
        out.print(textLink("Go Back", "#", "window.history.back();return false;", "goback"));
%>