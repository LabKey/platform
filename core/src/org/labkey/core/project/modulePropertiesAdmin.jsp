<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.gwt.client.util.StringUtils" %>
<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%
    JspView<FolderManagementAction.FolderManagementForm> me = (JspView) HttpView.currentView();
    //int webPartId = me.getModelBean().getRowId();
    String renderTarget = "project-";// + me.getModelBean().getIndex();
    ViewContext ctx = me.getViewContext();
    boolean hasPermission;

    Container target = ctx.getContainerNoTab();
    hasPermission = target.hasPermission(ctx.getUser(), ReadPermission.class);

    List<String> modules = new ArrayList<String>();
    for (Module m : target.getActiveModules())
    {
        if(m.getModuleProperties().size() > 0)
        {
            modules.add(m.getName());
        }
    }

%>
<script type="text/javascript">

    LABKEY.requiresExt4ClientAPI(true);
    LABKEY.requiresScript("ModulePropertiesAdminPanel.js");

</script>
<script type="text/javascript">

Ext4.onReady(function(){
    Ext4.onReady(function(){
        Ext4.create('LABKEY.ext.ModulePropertiesAdminPanel', {
            modules: ['<%=StringUtils.join(modules, "','")%>']
        }).render('<%=renderTarget%>');
    });
});

</script>
<div id='<%=renderTarget%>'></div>