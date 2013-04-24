<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    String contextPath = ctx.getContextPath();
    User user = ctx.getUser();
    List<Container> containers = ContainerManager.containersToRootList(ctx.getContainer());
    int size = containers.size();

    ActionURL createFolderURL = new ActionURL(AdminController.CreateFolderAction.class, ctx.getContainer());
%>
<%!
    public String getTrailSeparator(String ctxPath)
    {
        return "&nbsp;<img src=\"" + ctxPath + "/_images/arrow_breadcrumb.png\" alt=\"\">&nbsp;";
    }

    public String getTrailLink(Container c, User u, String ctxPath)
    {
        return "<a href=\"" + c.getStartURL(u) +"\">" + c.getName() + "</a>" + getTrailSeparator(ctxPath);
    }
%>
<div>
<%
    if (size > 1)
    {
%>
    <div class="folder-trail">
        <%
            if (size < 5)
            {
                for (int p=0; p < size-1; p++)
                {
                    %><%=getTrailLink(containers.get(p), user, contextPath)%><%
                }
                if (size > 0)
                    %><span style="color: black;"><%=containers.get(size-1).getName()%></span><%
            }
            else
            {
                for (int p=0; p < 2; p++)
                {
                    %><%=getTrailLink(containers.get(p), user, contextPath)%><%
                }
                %>...<%=getTrailSeparator(contextPath)%><%
                for (int p=(size-2); p < size-1 ; p++)
                {
                    %><%=getTrailLink(containers.get(p), user, contextPath)%><%
                }
                if (size > 0)
                    %><span style="color: black;"><%=containers.get(size-1).getName()%></span><%
            }
        %>
    </div>
<%
    }
%>
    <div id="folder-tree-wrap" class="folder-tree">
        <% me.include(form.getFolderMenu(), out); %>
    </div>
</div>
<script type="text/javascript">
    Ext4.onReady(function() {

        var toggle = function(node) {
            var p = node.parent();
            var el = p.child('ul');
            el.setVisibilityMode(Ext4.Element.DISPLAY);

            if (node.hasCls('expand-folder')) {
                node.replaceCls('expand-folder', 'collapse-folder');
                el.hide();
            }
            else {
                node.replaceCls('collapse-folder', 'expand-folder');
                el.show();
            }
        };

        var applyCollapse = function(nodes) {
            for (var n=0; n < nodes.length; n++) {
                Ext4.get(nodes[n]).on('click', function(x,node,z) {
                    toggle(Ext4.get(node));
                });
            }
        };

        var selNodes = Ext4.DomQuery.select('.folder-nav .clbl span.marked');
        applyCollapse(selNodes);

        // Folder Scrolling
        var target = Ext4.get('folder-target');
        if (target) {
            target.scrollIntoView(Ext4.get('folder-tree-wrap'));
        }
    });
</script>
<div class="folder-menu-buttons">
<%
    if (ctx.getContainer().hasPermission(ctx.getUser(), AdminPermission.class))
    {
%>
    <span class="button-icon"><a href="<%=createFolderURL%>" title="New Subfolder"><img src="/labkey/_images/icon_projects_add.png" alt="New Sub-Folder" /></a></span>
<%
    }
%>
    <span class="button-icon"><a id="permalink_vis" href="#" title="Permalink Page"><img src="/labkey/_images/icon_permalink.png" alt="Permalink Page" /></a></span>
    <script type="text/javascript">
        (function(){
            var p = document.getElementById('permalink');
            var pvis = document.getElementById('permalink_vis');
            if (p && pvis) {
                pvis.href = p.href;
            }
        })();
    </script>
</div>