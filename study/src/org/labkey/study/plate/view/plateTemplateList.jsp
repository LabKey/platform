<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.study.PlateTemplate" %>
<%@ page import="org.labkey.study.controllers.plate.PlateController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.study.PlateTypeHandler" %>
<%@ page import="org.labkey.study.plate.PlateManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PlateController.PlateTemplateListBean> me = (JspView<PlateController.PlateTemplateListBean>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    PlateTemplate[] plateTemplates = me.getModelBean().getTemplates();
%>
<h4>Available Plate Templates</h4>
<table class="normal">
<%
    for (PlateTemplate template : plateTemplates)
    {
%>
    <tr>
        <td><%= h(template.getName()) %></td>
        <%
            if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_UPDATE))
            {
        %>
        <td><%= textLink("edit", "designer.view?templateName=" + PageFlowUtil.encode(template.getName())) %></td>
        <%
            }
            if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_INSERT))
            {
        %>
        <td><%= textLink("edit a copy", "designer.view?copy=true&templateName=" + PageFlowUtil.encode(template.getName())) %></td>
        <td><%= textLink("copy to another folder", "copyTemplate.view?templateName=" + PageFlowUtil.encode(template.getName())) %></td>
        <%
            }
            if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_DELETE))
            {
        %>
        <td><%= ((plateTemplates !=null && plateTemplates.length > 1) ?
                textLink("delete", "delete.view?templateName=" + PageFlowUtil.encode(template.getName()),
                        "return confirm('Permanently delete this plate template?')", null) :
                "Cannot delete the final template.") %></td>
        <%
            }
        %>
    </tr>
<%
    }
    if (context.getContainer().hasPermission(context.getUser(), ACL.PERM_INSERT))
    {
%>
    <tr><td><br></td></tr>
    <% for (PlateTypeHandler handler : PlateManager.get().getPlateTypeHandlers())
    { %>
        <tr>
            <td colspan="4"><%= textLink("new " + handler.getAssayType() + " template", "designer.view?assayType=" + handler.getAssayType())%></td>
        </tr>
        <%  for (String template : handler.getTemplateTypes())
            {  %>
        <tr>
            <td colspan="4"><%= textLink("new " + handler.getAssayType() + " " + template + " template", "designer.view?assayType=" + handler.getAssayType() + "&templateType=" + template)%></td>
        </tr>
    <%      }
    }%>
<%
    }
%>
</table>
