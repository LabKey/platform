<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>
<%
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();
    User user = getUser();
    Container c = getContainer();
    List<Container> containers = ContainerManager.containersToRootList(c);
    int size = containers.size();

    ActionURL createFolderURL = new ActionURL(AdminController.CreateFolderAction.class, c);
    createFolderURL.addParameter(ActionURL.Param.returnUrl, c.getStartURL(user).toString());
%>
<%!
    public _HtmlString getTrailSeparator()
    {
        return _hs("&nbsp;<img src=\"" + getWebappURL("/_images/arrow_breadcrumb.png") + "\" alt=\"\">&nbsp;");
    }

    public _HtmlString getTrailLink(Container c, User u)
    {
        if (c.hasPermission(u, ReadPermission.class))
        {
            return _hs("<a href=\"" + h(c.getStartURL(u)) +"\">" + h(c.getName()) + "</a>" + getTrailSeparator());
        }
        return _hs("<span>" + h(c.getName()) + "</span>" + getTrailSeparator());
    }
%>
<div>
<%
    // Only show the nav trail if subfolders exist
    if (size > 1)
    {
%>
    <div class="folder-trail">
        <%
            if (size < 5)
            {
                for (int p=0; p < size-1; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                %><span style="color: black;"><%=h(containers.get(size - 1).getName())%></span><%
            }
            else
            {
                for (int p=0; p < 2; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                %>...<%=getTrailSeparator()%><%
                for (int p=(size-2); p < size-1 ; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                %><span style="color: black;"><%=h(containers.get(size - 1).getName())%></span><%
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

        var expandCls = 'expand-folder';
        var collapseCls = 'collapse-folder';

        var toggle = function(selector) {
            var p = selector.parent();

            if (p) {
                var collapse = p.hasCls(expandCls);

                collapse ? p.replaceCls(expandCls, collapseCls) : p.replaceCls(collapseCls, expandCls);

                var a = p.child('a');
                if (a) {
                    var url = a.getAttribute('expandurl');
                    if (url) {
                        url += (collapse ? '&collapse=true' : '');
                        Ext4.Ajax.request({ url : url });
                    }
                }
            }
        };

        // nodes - the set of +/- icons
        var nodes = Ext4.DomQuery.select('.folder-nav .clbl span.marked');
        for (var n=0; n < nodes.length; n++) {
            Ext4.get(nodes[n]).on('click', function(x,node) { toggle(Ext4.get(node)); });
        }

        // scrollIntoView
        var siv = function(t, ct) {
            ct = Ext4.getDom(ct) || Ext4.getBody().dom;
            var el = t.dom,
                    offsets = t.getOffsetsTo(ct),
                    // el's box
                    top = offsets[1] + ct.scrollTop,
                    bottom = top + el.offsetHeight,
                    // ct's box
                    ctClientHeight = ct.clientHeight,
                    ctScrollTop = parseInt(ct.scrollTop, 10),
                    ctBottom = ctScrollTop + ctClientHeight,
                    ctHalf = (ctBottom / 2);

            if (bottom > ctBottom) { // outside the visible area
                ct.scrollTop = bottom - (ctClientHeight / 2);
            }
            else if (bottom > ctHalf) { // centering
                ct.scrollTop = bottom - ctHalf;
            }

            // corrects IE, other browsers will ignore
            ct.scrollTop = ct.scrollTop;

            return this;
        };

        // Folder Scrolling
        var t = Ext4.get('folder-target');
        if (t) { siv(t, Ext4.get('folder-tree-wrap')); }
    });
</script>
<div class="folder-menu-buttons">
<%
    if (c.hasPermission(user, AdminPermission.class))
    {
%>
    <span class="button-icon"><a href="<%=createFolderURL%>" title="New Subfolder"><img src="<%=getWebappURL("_images/icon_folders_add.png")%>" alt="New Subfolder" /></a></span>
<%
    }
%>
    <span class="button-icon"><a id="permalink_vis" href="#" title="Permalink Page"><img src="<%=getWebappURL("_images/icon_permalink.png")%>" alt="Permalink Page" /></a></span>
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