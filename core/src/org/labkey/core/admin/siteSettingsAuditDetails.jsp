<%@ page import="org.labkey.core.admin.SiteSettingsAuditDetailsModel" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView<SiteSettingsAuditDetailsModel> me = (JspView<SiteSettingsAuditDetailsModel>) HttpView.currentView();
    SiteSettingsAuditDetailsModel model = me.getModelBean();
%>

<p>On <%=PageFlowUtil.filter(model.getWhen())%>,
    <b><%=null == model.getUser() ? "user id " + model.getEvent().getCreatedBy() : PageFlowUtil.filter(model.getUser().getFriendlyName())%></b>
    modified the site settings in the following way:</p>
<%=model.getDiff()%>
