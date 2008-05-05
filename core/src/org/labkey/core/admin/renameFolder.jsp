<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String name = form.getName();

    if (null == name)
        name = c.getName();

    String containerDescription = (c.isProject() ? "Project" : "Folder");
    String containerType = containerDescription.toLowerCase();
    String errorHTML = formatMissedErrors("form");
%>
<form action="renameFolder.view" method="post">
    <table border=0 cellspacing=2 cellpadding=0 class="dataRegion"><%
        if (errorHTML.length() > 0)
        { %>
        <tr><td><%=errorHTML%></td></tr><%
        } %>
        <tr><td>Rename <%=containerType%> <b><%=h(name)%></b> to:&nbsp;<input id="name" name="name" value="<%=h(name)%>"/></td></tr>
        <tr><td><input type="checkbox" name="addAlias" checked> Add a folder alias for the folder's current name. This will make links that still target the old folder name continue to work.</td></tr>
    </table>
    <table border=0 cellspacing=2 cellpadding=0>
        <tr>
            <td><input type="image" src='<%=PageFlowUtil.buttonSrc("Rename")%>'></td>
            <td><a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
        </tr>
    </table>
</form>