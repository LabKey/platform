<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.query.QueryView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%
JspView<QueryView.ExcelExportOptionsBean> me = (JspView<QueryView.ExcelExportOptionsBean>) HttpView.currentView();
QueryView.ExcelExportOptionsBean model = me.getModelBean();
String guid = GUID.makeGUID();
String onClickScript = null;
if (model.getIqyURL() != null)
{
    onClickScript = "window.location = document.getElementById('" + guid + "').checked ? " + PageFlowUtil.jsString(model.getXlsURL().getLocalURIString()) + " : " + PageFlowUtil.jsString(model.getIqyURL().getLocalURIString()) + "; return false;";
}
%>

<table cellspacing="4">
    <tr>
        <td valign="center">Export to Excel as:</td>
        <td valign="center"><input type="radio" id="<%= guid %>" name="excelExportType" value="<%= PageFlowUtil.filter(model.getXlsURL()) %>" checked="true" /></td>
        <td valign="center">Standard File (.xls)</td>
    </tr>
    <% if (model.getIqyURL() != null) { %>
    <tr>
        <td/>
        <td valign="center"><input type="radio" name="excelExportType" value="<%= PageFlowUtil.filter(model.getIqyURL()) %>" /></td>
        <td valign="center">Web Query -- Automatically Updates (.iqy)</td>
    </tr>
    <% } %>

    <tr>
        <td colspan="2" />
        <td><%=
            PageFlowUtil.generateButton("Export to Excel", model.getXlsURL(), onClickScript) %>
        </td>
    </tr>
</table>