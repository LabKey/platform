<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.SampleManager" %>
<%@ page import="org.labkey.study.model.SampleRequestStatus" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.study.controllers.samples.SpringSpecimenController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpringSpecimenController.ViewRequestsHeaderBean> me = (JspView<SpringSpecimenController.ViewRequestsHeaderBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    SpringSpecimenController.ViewRequestsHeaderBean bean = me.getModelBean();
    ActionURL userLink = context.cloneActionURL();
    if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_ADMIN) || context.getUser().isAdministrator())
    {
%>
<%= textLink("Customize View", bean.getView().getCustomizeURL().getLocalURIString()) %>
<%
    }
%>
<%= textLink("All User Requests", userLink.deleteParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY).getLocalURIString()) %>
<%= textLink("My Requests", userLink.replaceParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY,
        context.getUser().getDisplayName(context)).getLocalURIString()) %>
Filter by status: <select onChange="document.location=options[selectedIndex].value">
<%
    ActionURL current = context.cloneActionURL();
    current.deleteParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL);
%>
    <option value="<%= current.getLocalURIString() %>">All Statuses</option>
<%
    for (SampleRequestStatus status : bean.getStauses())
    {
        current.replaceParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_STATUSLABEL, status.getLabel());
%>
    <option value="<%= current.getLocalURIString() %>" <%= bean.isFilteredStatus(status) ? "SELECTED" : "" %>><%= h(status.getLabel()) %></option>
<%
    }
%>
</select>
<%
    String userFilter = context.getActionURL().getParameter(SpringSpecimenController.ViewRequestsHeaderBean.PARAM_CREATEDBY);
    if (userFilter != null)
    {
%>
<b>Showing requests from user <%= userFilter %></b>
<%
    }
%>
