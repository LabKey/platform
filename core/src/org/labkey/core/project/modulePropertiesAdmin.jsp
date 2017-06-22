<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    //int webPartId = me.getModelBean().getRowId();
    String renderTarget = "project-";// + me.getModelBean().getIndex();

    List<String> modules = new ArrayList<>();
    for (Module m : getContainer().getActiveModules(getUser()))
    {
        if(m.getModuleProperties().size() > 0)
        {
            modules.add(m.getName());
        }
    }

%>
<script type="text/javascript">

Ext4.onReady(function(){

    LABKEY.requiresScript("ModulePropertiesAdminPanel.js", function() {

        Ext4.create('LABKEY.ext.ModulePropertiesAdminPanel', {
            modules: ['<%=StringUtils.join(modules, "','")%>']
        }).render('<%=renderTarget%>');

    });

});

</script>
<div id='<%=renderTarget%>'></div>