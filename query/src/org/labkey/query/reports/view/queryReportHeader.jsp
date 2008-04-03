<%@ page import="org.labkey.api.reports.report.QueryReport"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<QueryReport.HeaderBean> me = (JspView<QueryReport.HeaderBean>) HttpView.currentView();
    QueryReport.HeaderBean bean = me.getModelBean();
%>
<%--
<%= textLink("Reports and Views", "begin.view") %>
--%>

<%
    if (bean.showCustomizeLink())
    {
%>
        [<a href="<%= bean.getCustomizeURL().getEncodedLocalURIString() %>">Customize View</a>]
<%
    }
%>