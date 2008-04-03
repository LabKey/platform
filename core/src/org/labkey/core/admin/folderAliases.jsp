<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ViewContext> me = (JspView<ViewContext>) HttpView.currentView();
    ViewContext context = me.getViewContext();
    String[] pathAliases = ContainerManager.getAliasesForContainer(context.getContainer());
%>
<table border="0" width="500">
    <tr>
        <td>Folder aliases allow you to redirect other URLs on this server to this folder.</td>
    </tr>
    <tr>
        <td>
            For example, if you enter <b>/otherproject/otherfolder</b> below,
            URLs directed to a folder with name <b>/otherproject/otherfolder</b>
            will be redirected to this folder, <b><%= context.getContainer().getPath() %></b>.
        </td>
    </tr>
    <tr>
        <td>Enter one alias per line. Each alias should start with a '/'. Aliases that are
            paths to real folders in the system will be ignored.</td>
    </tr>
    <tr>
        <td>
        <form action="saveAliases.post" method="post">
            <textarea rows="4" cols="40" name="aliases"><%
                StringBuilder sb = new StringBuilder();
                String separator = "";
                for (String path : pathAliases)
                {
                    sb.append(separator);
                    separator = "\r\n";
                    sb.append(path);
                }%><%= sb.toString() %></textarea><br><br>
            <%= buttonImg("Save Aliases") %>
            <%= buttonLink("Cancel", "manageFolders.view") %>
        </form>
        </td>
    </tr>
</table>
