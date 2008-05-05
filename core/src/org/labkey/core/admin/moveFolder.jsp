<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    MoveFolderTreeView me = (MoveFolderTreeView) HttpView.currentView();
    ManageFoldersForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String name = form.getName();
    if (null == name)
        name = c.getName();
    String containerType = (c.isProject() ? "project" : "folder");
%>

<table class="dataRegion">
    <%=formatMissedErrors("form")%>
    <tr><td><form name="moveAddAlias" action="showMoveFolderTree.view">
        <input type="checkbox" onchange="document.forms.moveAddAlias.submit()" name="addAlias" <% if (form.isAddAlias()) { %>checked<% } %>> Add a folder alias for the folder's current location. This will make links that still target the old folder location continue to work.
        <% if (form.isShowAll()) { %>
            <input type="hidden" name="showAll" value="1" />
        <% } %>
    </form></td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>Move <%=containerType%> <b><%=h(name)%></b> to:</td></tr>
    <tr><td>&nbsp;</td></tr>
    <%
        boolean showAll = form.isShowAll() || c.isProject();
        ActionURL moveURL = AdminController.getMoveFolderURL(c, form.isAddAlias());    // Root is placeholder -- will get replaced by container tree
        AdminController.MoveContainerTree ct = new AdminController.MoveContainerTree(showAll ? "/" : form.getProjectName(), HttpView.currentContext().getUser(), ACL.PERM_ADMIN, moveURL);
        ct.setIgnore(c);        // Can't set a folder's parent to itself or its children
        ct.setInitialLevel(1);  // Use as left margin
    %>
    <%=ct.render()%>
    <tr><td>&nbsp;</td></tr>
    </table>

    <table border=0 cellspacing=2 cellpadding=0><tr>
    <td><a href="<%=h(AdminController.getManageFoldersURL(c))%>"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td><%
    if (form.isShowAll())
    {
        if (!c.isProject())
        {
            %><td><a href="<%=h(AdminController.getShowMoveFolderTreeURL(c, form.isAddAlias(), false))%>"><img border=0 src='<%=PageFlowUtil.buttonSrc("Show Current Project Only")%>'></a></td><%
        }
    }
    else
    {
        %><td><a href="<%=h(AdminController.getShowMoveFolderTreeURL(c, form.isAddAlias(), true))%>"><img border=0 src='<%=PageFlowUtil.buttonSrc("Show All Projects")%>'></a></td><%
    }
    %></tr>
</table>