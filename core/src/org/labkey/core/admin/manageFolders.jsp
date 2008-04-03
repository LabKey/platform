<%@ page import="org.labkey.core.admin.AdminController"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.util.ContainerTreeSelected"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    final AdminController.ManageFoldersForm form = (AdminController.ManageFoldersForm) __form;
    final Container c = getContainer();
    final ViewContext context = HttpView.currentContext();
    final ActionURL currentUrl = context.cloneActionURL();
    final ContainerTreeSelected ct = new ContainerTreeSelected(form.getProjectName(), getUser(), ACL.PERM_ADMIN, currentUrl, "managefolders");
    Container parent = c.getParent();
    List<Container> siblings = parent == null ? null : parent.getChildren();
    boolean hasSiblings = siblings != null  && siblings.size() > 1;
    List<Container> children = c.getChildren();
    boolean hasChildren = children != null && children.size() > 0;

    ct.setCurrent(c);
    ct.setInitialLevel(1);
%>

<table class="dataRegion">
    <tr><td style="padding-left:0">Pick a folder to modify:</td></tr>
    <tr><td>&nbsp;</td></tr>
    <%= ct.render()%>
</table><br>

<table border=0 cellspacing=2 cellpadding=0><tr>
<%
    ActionURL modify = new ActionURL("admin", "modifyFolder", context.getContainer());
    ActionURL rename = modify.clone().addParameter("action", "rename");
    ActionURL move   = modify.clone().addParameter("action", "move");
    ActionURL create = modify.clone().addParameter("action", "create");
    ActionURL delete = modify.clone().addParameter("action", "delete");
    ActionURL reorder = modify.clone().setAction("reorderFolders");
    ActionURL aliases = modify.clone().setAction("folderAliases");

    if (!ContainerManager.getHomeContainer().equals(c) && !ContainerManager.getSharedContainer().equals(c))
    {
%>
        <td><%= buttonLink("Rename", rename)%></td>
        <td><%= buttonLink("Move", move)%></td>
        <td><%= buttonLink("Create Subfolder", create)%></td>
        <td><%= buttonLink("Delete", delete)%></td><%

        if (hasSiblings && !context.getContainer().isRoot() && !context.getContainer().getParent().isRoot())
        {
            %><td><%= buttonLink("Change Display Order", reorder)%></td><%
        }
    }
    else 
    {
        %><td><%= buttonLink("Create Subfolder", create)%></td><%
    }
%>
    <td><%= buttonLink("Aliases", aliases)%></td>
</tr></table>
