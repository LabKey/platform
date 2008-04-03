<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<org.labkey.api.study.actions.AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
%>
<table>
    <tr class="wpHeader">
        <td class="wpTitle" colspan="2">Assay Properties</td>
    </tr>
    <tr>
        <td class="ms-searchform" nowrap="true">Name</td>
        <td width="100%"><%= h(bean.getProtocol().getName()) %></td>
    </tr>
    <tr>
        <td class="ms-searchform" nowrap="true">Description</td>
        <td><%= h(bean.getProtocol().getProtocolDescription()) %></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
</table>
