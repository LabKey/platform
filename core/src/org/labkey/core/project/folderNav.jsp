<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="java.util.LinkedHashSet" %>
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
    Path path = ctx.getContainer().getParsedPath();
    int size = path.size();
%>
<style type="text/css">

    #folderBar_menu {
        padding-right: 0px;
        padding-left: 0px;
    }

    .folder-nav {
        max-height: 350px;
        min-height: 75px;
        min-width: 280px;
        max-width: 800px;
        white-space: nowrap;
        display: inline-block;
    }

    .folder-trail {
        padding: 3px 20px 7px 7px;
        color: #a9a9a9;
        white-space: nowrap;
        border-bottom: 1px solid lightgray;
    }

    .folder-tree {
        padding-right: 50px;
        max-height: 325px;
        overflow-x: hidden;
        overflow-y: auto;
        padding-left: 7px;
    }

    ul {
        list-style: none;
        padding-left: 20px;
    }

    .folder-nav ul.folder-nav-top {
        padding-left: 0 !important;
        margin: 0;
    }

    .folder-nav span:hover {
        cursor: pointer;
    }

    .folder-nav ul li {
        padding-top: 8px;
    }

    .folder-nav ul li span {
        padding-left: 20px;
    }

    .folder-nav .clbl span.marked {
        padding-left: 20px;
    }

    .folder-nav ul li a {
        white-space: nowrap;
    }

    .collapse-folder {
        background: url(<%=contextPath%>/_images/expand-collapse.gif) no-repeat 0 2px;
        padding-left: 20px;
    }

    .expand-folder {
        background: url(<%=contextPath%>/_images/expand-collapse.gif) no-repeat 0 -130px;
        padding-left: 20px;
    }
</style>
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
                    %><a href="#"><%=path.get(p)%></a>&nbsp;&gt;&nbsp;<%
                }
                if (size > 0)
                    %><span style="color: black;"><%=path.get(size-1)%></span><%
            }
            else
            {
                for (int p=0; p < 2; p++)
                {
                    %><a href="#"><%=path.get(p)%></a>&nbsp;&gt;&nbsp;<%
                }
                %><%="...&nbsp;&gt;&nbsp;"%><%
                for (int p=(size-2); p < size-1 ; p++)
                {
                    %><a href="#"><%=path.get(p)%></a>&nbsp;&gt;&nbsp;<%
                }
                if (size > 0)
                    %><span style="color: black;"><%=path.get(size-1)%></span><%
            }
        %>
    </div>
<%
    }
%>
    <div class="folder-tree">
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
    });
</script>