<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<SecurityController.PermissionsForm> me = (JspView<SecurityController.PermissionsForm>) HttpView.currentView();
    SecurityController.PermissionsForm bean = me.getModelBean();

    ActionURL url = HttpView.currentContext().cloneActionURL();
    String currentTab = StringUtils.defaultString(bean.getTabId(), SecurityController.TAB_REPORT);
    String nextTab = SecurityController.TAB_REPORT.equals(currentTab) ? SecurityController.TAB_STUDY : SecurityController.TAB_REPORT;
%>
    <input type="hidden" name="reportId" value="<%=bean.getReportId()%>">
    <table cellspacing=0 cellpadding=0 width="100%">
        <tr>
            <td class="navtab" style="border-top:none;border-left:none;border-right:none;">
                <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
            </td>
            <td class=<%=SecurityController.TAB_REPORT.equals(currentTab) ? "navtab-selected" : "navtab-inactive"%>><a <%=SecurityController.TAB_REPORT.equals(currentTab) ? "" : "href=\"" + url.replaceParameter("tabId", SecurityController.TAB_REPORT) + "\""%>>Permissions&nbsp;</a></td>
            <td class=<%=SecurityController.TAB_STUDY.equals(currentTab) ? "navtab-selected" : "navtab-inactive"%>><a <%=SecurityController.TAB_STUDY.equals(currentTab) ? "" : "href=\"" + url.replaceParameter("tabId", SecurityController.TAB_STUDY) + "\""%>>Study&nbsp;Security&nbsp;</a></td>
            <td class="navtab" style="border-top:none;border-left:none;border-right:none;text-align:right;" width=100%>
                <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>
            </td>
        </tr>
        <tr>
            <td colspan="4" class="navtab" style="border-top:none;text-align:left;" width=100%>
                <img src="<%=request.getContextPath()%>/_.gif" height=1 width=5>

