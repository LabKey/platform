<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<org.labkey.api.study.actions.AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm bean = me.getModelBean();
%>

<table>
    <tr>
        <td colspan="2">Expected Columns:
            <table>
        <%
            for (PropertyDescriptor pd : bean.getRunDataProperties())
            {
        %>
            <tr><td><strong><%= pd.getName() %><%= (pd.isRequired() ? " (Required)" : "") %></strong>:</td><td><%= pd.getPropertyType().getXarName() %></td><td><%=h(pd.getDescription())%></td></tr>
        <%
            }
        %>
            </table>
            <%= textLink("download spreadsheet template",
                    AssayService.get().getProtocolURL(bean.getContainer(), bean.getProtocol(), "template"))%>
            <br>After downloading and editing the spreadsheet template, paste it into the text area below or save the spreadsheet and upload it as a file.
        </td>
    </tr>
</table>