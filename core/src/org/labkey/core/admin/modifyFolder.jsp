<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    //This file was naively translated from modifyFolder.gm. Could probably use a rewrite & separation into different pages
    JspView<AdminController.ManageFoldersForm> me = (JspView<AdminController.ManageFoldersForm>) HttpView.currentView();
    AdminController.ManageFoldersForm form = me.getModelBean();
    Container c = HttpView.currentContext().getContainer();
    User user = HttpView.currentContext().getUser();

    String action = form.getAction();
    String containerDescription;
    String name = form.getName();
    if ("create".equals(action))
        containerDescription = (c.isRoot() ? "Project" : "Folder");
    else
    {
        containerDescription = (c.isProject() ? "Project" : "Folder");
        if (null == name)
            name = c.getName();
    }
    String childrenDescription = c.isProject() ? "folder" : "subfolder";

    String containerType = containerDescription.toLowerCase();
%>
<%
if (action.equals("rename"))
    {
    %><form action=renameFolder.view>
    <table border=0 cellspacing=2 cellpadding=0 class="dataRegion">
        <%=PageFlowUtil.getStrutsError(request, "main")%>
    <tr><td>Rename <%=containerType%> <b><%=h(name)%></b> to:&nbsp;<input id="name" name="name" value="<%=h(name)%>"/></td></tr>
    <tr><td><input type="checkbox" name="addAlias" checked> Add a folder alias for the folder's current name. This will make links that still target the old folder name continue to work.</td></tr>
    </table><table border=0 cellspacing=2 cellpadding=0><tr>
    <td><input type="image" src='<%=PageFlowUtil.buttonSrc("Rename")%>'></td>
    <td><a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
    </tr></table>

    </form>
    <script for=window event=onload>
    try {document.getElementById("name").focus();} catch(x){}
    </script><%

    return;
    }

if (action.equals("move"))
{
    %><table class="dataRegion">
    <%=PageFlowUtil.getStrutsError(request, "main")%>
    <tr><td><form name="moveAddAlias">
        <input type="checkbox" onchange="document.forms.moveAddAlias.submit()" name="addAlias" <% if (form.isAddAlias()) { %>checked<% } %>> Add a folder alias for the folder's current location. This will make links that still target the old folder location continue to work.
        <input type="hidden" name="action" value="move" />
        <% if (form.isShowAll()) { %>
            <input type="hidden" name="showAll" value="1" />
        <% } %>
    </form></td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td>Move <%=containerType%> <b><%=h(name)%></b> to:</td></tr>
    <tr><td>&nbsp;</td></tr>
    <%
        boolean showAll = form.isShowAll() || c.isProject();
        ActionURL currentUrl = HttpView.currentContext().cloneActionURL();
        currentUrl.setAction("moveFolder");
        currentUrl.addParameter("folder", c.getPath());
        currentUrl.deleteParameter("addAlias");
        if (form.isAddAlias())
        {
            currentUrl.addParameter("addAlias", "on");
        }
        AdminController.MoveContainerTree ct = new AdminController.MoveContainerTree(showAll ? "/" : form.getProjectName(), HttpView.currentContext().getUser(), ACL.PERM_ADMIN, currentUrl);
        ct.setIgnore(c);        // Can't set a folder's parent to itself or its children
        ct.setInitialLevel(1);  // Use as left margin
    %>
    <%=ct.render()%>
    <tr><td>&nbsp;</td></tr>
    </table>

    <table border=0 cellspacing=2 cellpadding=0><tr>
    <td><a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td><%
    if (form.isShowAll())
    {
        if (containerDescription.equals("Folder"))
        {
            %><td><a href="modifyFolder.view?action=move<% if (form.isAddAlias()) { %>&addAlias=on<% } %>"><img border=0 src='<%=PageFlowUtil.buttonSrc("Show Current Project Only")%>'></a></td><%
        }
    }
    else
    {
        %><td><a href="modifyFolder.view?action=move&showAll=1<% if (form.isAddAlias()) { %>&addAlias=on<% } %>"><img border=0 src='<%=PageFlowUtil.buttonSrc("Show All Projects")%>'></a></td><%
    }
    %></tr></table><%
    return;
}

if (action.equals("create"))
{
    %><form name="createForm" action=createFolder.view>
    <%=PageFlowUtil.getStrutsError(request, "main")%>
    <table border=0 cellspacing=2 cellpadding=0 class="dataRegion">
    <tr><td colspan=2><%
    if (!c.isRoot())
    {
        %>New <%=h(containerType)%> under <b><%=h(c.getName())%></b><%
    }
    else
    {
        %>New <%=h(containerType)%><%
    } %>
    </tr>
        <tr>
    <td>Name:&nbsp;</td>
    <td><input id="name" name="name" value="<%=h(name)%>"/></td></tr>
    <tr><td>
    Folder Type
        </td><td >
        <table>
<%
    String folderTypeName = form.getFolderType();
    if (null == folderTypeName) //Try to avoid "None"
        folderTypeName = FolderType.NONE.equals(c.getFolderType()) ? "Collaboration" : c.getFolderType().getName();
    int radioIndex = 0;
for (FolderType ft : ModuleLoader.getInstance().getFolderTypes())
{
%>
        <tr>
            <td valign="top">
                <input type="radio" name="folderType" value="<%=h(ft.getName())%>" <%=folderTypeName.equals(ft.getName()) ? "checked" : ""%> >
             </td>
            <td valign="top">
                <span style="cursor:pointer;font-weight:bold" onclick="document.createForm.folderType[<%=radioIndex%>].checked = true;"><%=h(ft.getLabel())%></span><br>
                <%=h(ft.getDescription())%>
            </td>
        </tr>
<%
    radioIndex++;
}
%>
        </table>
        </td></tr>
    </table>

    <table border=0 cellspacing=2 cellpadding=0><tr>
    <td><input type="image" src='<%=PageFlowUtil.buttonSrc("Next")%>'></td>
    <td><%
    if (!c.isRoot())
    {
        %><a href="manageFolders.view"><%
    }
    else
    {
        %><a href="" onclick="window.history.back(); return false;"><%
    } %><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
    </tr></table>

    </form>
    <script for=window event=onload type="text/javascript">
    try {document.getElementById("name").focus();} catch(x){}
    </script><%

    return;
}

if (action.equals("delete"))
{
    %><table class="dataRegion"><%
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
        <td><form action="deleteFolder.post<%=recurse ? "?recurse=1" : ""%>" method="POST"><input type=image border=0 src='<%=PageFlowUtil.buttonSrc("Delete")%>'></form></td>
        <td><a href="manageFolders.view"><img border=0 src='<%=PageFlowUtil.buttonSrc("Cancel")%>'></a></td>
        </tr></table><%
    }
    else
    {
        %><tr><td>This <%=h(containerType)%> has <%=childrenDescription%>s.  If you continue you will <b>permanently delete</b> the <%=h(containerType)%>, its <%=childrenDescription%>s, and all the objects they contain.
        The next page will summarize some of the objects in these folders and give you another chance to cancel.</td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td>Cancel now to preserve these folders and objects.</td></tr>
        <tr><td>&nbsp;</td></tr>
        <tr><td class="normal">
        <a href="modifyFolder.view?action=delete&amp;recurse=1"><img border=0 src="<%=PageFlowUtil.buttonSrc("Delete All Folders")%>"></a>
        <a href="manageFolders.view"><img border=0 src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></a>
        </td></tr>
        </table><%
    }
}
%>