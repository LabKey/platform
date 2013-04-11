<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="org.labkey.api.view.menu.FolderMenu" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    String contextPath = ctx.getContextPath();
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
        padding: 3px 20px;
        color: lightgray;
        text-align: center;
    }

    .folder-tree {
        padding-right: 50px;
        max-height: 325px;
        overflow-x: hidden;
        overflow-y: auto;
        border-top: 1px solid lightgray;
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
    <div class="folder-trail">---&nbsp;CRUMB TRAIL&nbsp;---</div>
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