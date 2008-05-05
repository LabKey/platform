<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.*" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageFoldersForm> me = (JspView<ManageFoldersForm>) HttpView.currentView();
    ManageFoldersForm form = me.getModelBean();
    Container c = HttpView.currentContext().getContainer();
    User user = HttpView.currentContext().getUser();

    String name = form.getName();
    if (null == name)
        name = c.getName();

    String containerType = (c.isProject() ? "project" : "folder");
    String childrenDescription = (c.isProject() ? "folder" : "subfolder");
%>
<table class="dataRegion"><%
    if (c.equals(ContainerManager.getHomeContainer()))
    {
        %><tr><td>You cannot delete the home project.</td></tr>
        <tr><td class="normal">
        <a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("OK")%>'></a>
        </td></tr>
        </table><%

        return;
    }

    // Attempting recursive delete.  Could be first or second confirmation page.  Either way, user must have
    // admin permissions to the entire tree.
    if (c.hasChildren() && !ContainerManager.hasTreePermission(c, user, ACL.PERM_ADMIN))
    {
        %><tr><td>This <%=h(containerType)%> has <%=childrenDescription%>s, but you don't have admininistrative permissions to all the <%=childrenDescription%>s.</td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td class="normal">
        <a href="manageFolders.view"><img border=0 src="<%=PageFlowUtil.buttonSrc("Back")%>"></a>
        </td></tr>
        </table><%

        return;
    }

    boolean recurse = form.getRecurse();
    boolean showFinalConfirmation = recurse || !c.hasChildren();

    if (showFinalConfirmation)
    {
        // Simplify the confirmation message in this case
        boolean singleEmptyContainer = !c.hasChildren() && ModuleLoader.getInstance().getModuleSummaries(c).isEmpty();

        if (!singleEmptyContainer)
        {
            List<Container> containers = Arrays.asList(ContainerManager.getAllChildren(c));
            %>
            <tr><td>You are about to delete the following <%="project".equals(containerType) ? "project and its subfolders" : ("folder" + (recurse ? "s" : ""))%>:</td></tr>
            <tr><td>&nbsp;</td></tr>
            <tr><td><ul><%

            for (Container container : containers)
            {
                Collection<String> messages = ModuleLoader.getInstance().getModuleSummaries(container);

                %><li><%=h(container.getPath().substring(1))%><%

                if (null != messages && messages.size() > 0)
                {
                    %>, containing the following objects:<ul class=star><%

                    for (String m : messages)
                    {
                        %><li><%=h(m)%></li><%
                    } %>
                    </ul><%
                } %>
                </li><%
            } %>
            </ul></td></tr>
            <tr><td><%=recurse ? "They" : "It"%> may contain objects that are not listed.</td></tr>
            <tr><td>&nbsp;</td></tr><%
        } %>
            <tr><td>Are you <u>sure</u> you want to permanently delete <%=containerType%> <b><%=name%></b><%=recurse ? ", all its subfolders," : ""%> and all the objects <%=recurse ? "they contain" : "it contains"%>?</td></tr>
            <tr><td>&nbsp;</td></tr>
        </table>

        <table border=0 cellspacing=2 cellpadding=0><tr>
            <td><form action="deleteFolder.post<%=recurse ? "?recurse=1" : ""%>" method="post"><input type=image border=0 src='<%=PageFlowUtil.buttonSrc("Delete")%>'></form></td>
            <td><a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
        </tr></table><%
    }
    else
    {
        %>
    <tr><td>This <%=h(containerType)%> has <%=childrenDescription%>s.  If you continue you will <b>permanently delete</b> the <%=h(containerType)%>, its <%=childrenDescription%>s, and all the objects they contain.
        The next page will summarize some of the objects in these folders and give you another chance to cancel.</td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>Cancel now to preserve these folders and objects.</td></tr>
    <tr><td>&nbsp;</td></tr>
</table>

<table border=0 cellspacing=2 cellpadding=0>
    <tr>
        <td><a href="deleteFolder.view?recurse=1"><img border=0 src="<%=PageFlowUtil.buttonSrc("Delete All Folders")%>"></a></td>
        <td><a href="manageFolders.view"><img border=0 src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></a></td>
    </tr>
</table><%
    }  %>
