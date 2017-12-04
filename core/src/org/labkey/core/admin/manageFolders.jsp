<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
        dependencies.add("admin/FolderManagementPanel.js");
    }
%>
<%
    Container c = getContainer();
%>
<div id="fmp"></div>
<script type="text/javascript">
    Ext4.onReady(function() {
        Ext4.create('LABKEY.ext4.panel.FolderManagement', {
            renderTo: 'fmp',
            height: 700,
            minWidth: 600,
            selected: <%= c.getRowId() %>,
            requiredPermission: <%=PageFlowUtil.jsString(RoleManager.getPermission(AdminPermission.class).getUniqueName())%>,
            showContainerTabs: true
        });
    });
</script>