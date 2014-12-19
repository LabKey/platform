<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.roles.RoleManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    // TODO: This doesn't currently load when used in a TabStripView.java -- need to bootstrap these view dependencies
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext4"));
        resources.add(ClientDependency.fromPath("/admin/FolderManagementPanel.js"));
        return resources;
    }
%>
<%
    Container c = getContainer();
%>
<div id="fmp"></div>
<script type="text/javascript">
    LABKEY.requiresExt4ClientAPI();
    LABKEY.requiresScript("admin/FolderManagementPanel.js");
</script>
<script type="text/javascript">

    Ext4.onReady(function() {

        var folderPanel = Ext4.create('LABKEY.ext4.panel.FolderManagement', {
            renderTo : 'fmp',
            height   : 700,
            minWidth : 600,
            selected : <%= c.getRowId() %>,
            requiredPermission : <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>,
            showContainerTabs : true
        });

        var _resize = function(w, h) {
            if (!folderPanel.rendered)
                return;

            LABKEY.ext4.Util.resizeToViewport(folderPanel, w, h);
        };

        Ext4.EventManager.onWindowResize(_resize);
    });
</script>