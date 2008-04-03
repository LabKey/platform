<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.apache.commons.lang.BooleanUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    ViewContext context = HttpView.currentContext();

    Map<String, String> pm = webPart.getPropertyMap();

    Map<String, String> reportMap = new LinkedHashMap<String, String>();
    for (Report report : ReportService.get().getReports(context.getUser(), context.getContainer()))
    {
        if (!StringUtils.isEmpty(report.getDescriptor().getReportName()))
        {
            if ("Study.attachmentReport".equals(report.getType()) || "Study.exportExcelReport".equals(report.getType()))
                continue;

            reportMap.put(String.valueOf(report.getDescriptor().getReportId()), report.getDescriptor().getReportName());
        }
    }
%>

<form name="frmCustomize" method="post" action="<%=h(webPart.getCustomizePostURL(context.getContainer()))%>">
    <table>
        <tr>
            <td class="ms-searchform">Web Part Title:</td>
            <td><input type="text" name="title" size="40" value="<%=h(pm.get("title"))%>"></td>
        </tr>
        <tr>
            <td class="ms-searchform">Report or View:</td>
            <td>
                <select name="reportId">
                    <labkey:options value="<%=pm.get("reportId")%>" map="<%=reportMap%>" />
                </select>
            </td>
        </tr>
        <td class="ms-searchform">Show View Tabs:</td>
        <td><input type="checkbox" name="showTabs" <%=BooleanUtils.toBoolean(pm.get("showTabs")) ? "checked" : ""%>></td>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>
