<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<StatusController.EnterprisePipelineBean> view = (JspView<StatusController.EnterprisePipelineBean>) HttpView.currentView();
    StatusController.EnterprisePipelineBean bean = view.getModelBean();
%>

<p>You are running the Enterprise Pipeline.</p>
<p>
    <labkey:button text="Force Status Refresh" action="<%= new ActionURL(StatusController.ForceRefreshAction.class, ContainerManager.getRoot()) %>" />
    <labkey:helpPopup title="Force Status Refresh">Under normal operations, this should not be required, but if there were network problems
    that prevented a callback with status information, this can update a job.</labkey:helpPopup>
</p>
Your configuration references the following execution locations:
<ul>
<% for (String location : bean.getLocations())
{    %>
    <li><%= PageFlowUtil.filter(location) %></li>
<%
}
%>
</ul>
