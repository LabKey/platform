<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.di.ScheduledPipelineJobDescriptor" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.di.pipeline.TransformManager" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3");
        dependencies.add("Ext4");
    }
%>
<%
    JspView<DataIntegrationController.TransformViewForm> me =
            (JspView<DataIntegrationController.TransformViewForm>) HttpView.currentView();
    DataIntegrationController.TransformViewForm f = me.getModelBean();
    String transformId = f.getTransformId();
    Integer transformRunId = f.getTransformRunId();
    ScheduledPipelineJobDescriptor descriptor = TransformManager.get().getDescriptor(transformId);
%>
<script type="text/javascript">
    Ext4.onReady(function()
    {
        var qwp = new LABKEY.QueryWebPart({
            renderTo: 'transformDetailDiv',
            title: '<%=h(transformId)%>',
            schemaName: 'dataintegration',
            queryName: 'transformrun',
            filters: [
                LABKEY.Filter.create('TransformRunId', '<%=h(transformRunId)%>')
            ]
        });

        qwp.render();
    });
</script>
<span id="transformHeader" style="font-size: medium" >
    <i>The Transform Details View is under construction.</i><br>
    <p/>
    <%
    if (null != descriptor)
    {
    %>
       Description:  <%=h(descriptor.getDescription())%><br>
       Module:  <%=h(descriptor.getModuleName())%><br>
       Schedule:  <%=h(descriptor.getScheduleDescription())%><br>
    <%
    }
    %>
</span>
<p/>
<div id="transformDetailDiv"/>
