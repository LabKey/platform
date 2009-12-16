<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%
JspView<ActionURL> me = (JspView<ActionURL>) HttpView.currentView();
%>

<table cellspacing="4">
    <tr>
        <td valign="center">Export as tab-separated values</td>
        <td><%= PageFlowUtil.generateButton("Export to Text", me.getModelBean()) %></td>
    </tr>
</table>