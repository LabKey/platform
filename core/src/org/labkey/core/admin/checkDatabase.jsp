<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.DataCheckForm> me = (JspView<AdminController.DataCheckForm>) HttpView.currentView();
    AdminController.DataCheckForm bean = me.getModelBean();

    String errors = PageFlowUtil.getStrutsError(request, "main");
%>

<span class="labkey-error"><%=errors%></span>

<form action="getSchemaXmlDoc.view" method="get">
    <table class="normal">
        <tr class="wpHeader"><th colspan=2 align=center>Database Tools</th></tr>
        <tr><td class=normal>Check table consistency:&nbsp;</td>
        <td> <%=PageFlowUtil.buttonLink("Do Database Check", new ActionURL("admin", "doCheck.view", ""))%>&nbsp;</td></tr>
        <tr><td class=normal>&nbsp;</td><td></td></tr>
        <tr><td>Get schema xml doc:&nbsp;</td>
            <td>

                <select id="dbSchema" name="dbSchema" style="width:250"><%
                    for (Module m : bean.getModules())
                    {
                        Set<String> schemaNames = m.getSchemaNames();
                        for (String sn : schemaNames)
                        {
                        %>
                        <option value="<%= sn %>"><%= m.getName() + " : " + sn %></option >
                        <%
                        }
                   }
                    %>
                </select><br>
            </td></tr>
        <tr><td></td><td><%= buttonImg("Get Schema Xml") %>
        <%=PageFlowUtil.buttonLink("Cancel", new ActionURL("admin", "begin.view", ""))%>  </td></tr>
        <tr><td class=normal></td><td></td></tr>


    </table>
</form><br/><br/>

