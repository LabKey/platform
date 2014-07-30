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
<%@ page import="org.labkey.api.di.ScheduledPipelineJobDescriptor" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.di.pipeline.TransformConfiguration" %>
<%@ page import="org.labkey.di.pipeline.TransformManager" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
List<TransformConfiguration> configurationsList = TransformManager.get().getTransformConfigurations(getContainer());
Map<String,TransformConfiguration> configurationsMap = new HashMap<>(configurationsList.size()*2);
for (TransformConfiguration c : configurationsList)
    configurationsMap.put(c.getTransformId(), c);

// It's possible to have configurations for transforms whose modules are in active, so make sure we get those
Collection<ScheduledPipelineJobDescriptor> descriptorsList = TransformManager.get().getDescriptors(getContainer());
TreeMap<String,ScheduledPipelineJobDescriptor> descriptorsMap = new TreeMap<>();
for (ScheduledPipelineJobDescriptor d : descriptorsList)
    descriptorsMap.put(d.getId(), d);
for (TransformConfiguration c : configurationsList)
{
    if (!descriptorsMap.containsKey(c.getTransformId()))
    {
        ScheduledPipelineJobDescriptor d = TransformManager.get().getDescriptor(c.getTransformId());
        if (null != d)
            descriptorsMap.put(d.getId(), d);
    }
}

boolean isAdmin = getViewContext().hasPermission(AdminPermission.class);
%>
<script>
    function onFailedConfigurationUpdate(response,config)
    {
        Ext4.MessageBox.show({
            modal:true,
            title:response.statusText,
            msg:"There was an error updating the configuration",
            icon: Ext4.MessageBox.ERROR,
            buttons: Ext4.MessageBox.OK,
            fn: function(){window.location.reload(true);}
        });
    }
    function Transform_setProperty(transformId, property, value)
    {
        var params = {'transformId':transformId};
        params[property] = value;

        Ext4.Ajax.request({
            url : <%=q(buildURL(DataIntegrationController.UpdateTransformConfigurationAction.class))%>,
            params : params,
            method : "POST"
            ,failure : onFailedConfigurationUpdate
        });
    }
    function Transform_setEnabled(transformId, enabled)
    {
        Transform_setProperty(transformId, "enabled", enabled);
    }
    function Transform_setVerboseLogging(transformId, verbose)
    {
        Transform_setProperty(transformId, "verboseLogging", verbose);
    }

    function Transform_runNow(transformId)
    {
        var params = {'transformId':transformId};
        Ext4.Ajax.request({
            url : <%=q(buildURL(DataIntegrationController.RunTransformAction.class))%>,
            params : params,
            method : "POST",
            success : function(response)
            {
                var json = {};
                try
                {
                    var json = response.responseJSON || LABKEY.Utils.decode(response.responseText);
                } catch (x) {}
                if ("pipelineURL" in json && json.pipelineURL)
                    window.location = json.pipelineURL;
                else
                    Ext4.MessageBox.alert("Success", json.status || "No work to do.");
            },
            failure : LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure({transformId:transformId}), window, true)
        });
    }

    function Transform_resetState(transformId)
    {
        var params = {'transformId':transformId};
        Ext4.Ajax.request({
            url : <%=q(buildURL(DataIntegrationController.ResetTransformStateAction.class))%>,
            params : params,
            method : "POST",
            success : function(response)
            {
            },
            failure : LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure({transformId:transformId}), window, true)
        });
    }


// UI GLUE
function getTransformId(srcEl)
{
    var transformId = null;
    var e = srcEl;
    while (e && e.tagName != "TR")
        e = e.parentElement;
    if (e && e.tagName == "TR")
        transformId = e.getAttribute("transformid");
    return transformId;
}
function onEnabledChanged(el,id)
{
    var checked = el.checked;
    Transform_setEnabled(id, checked);
}
function onVerboseLoggingChanged(el,id)
{
    var checked = el.checked;
    Transform_setVerboseLogging(id, checked);
}
function onRunNowClicked(el,id)
{
    Transform_runNow(id);
}

function onResetStateClicked(el, id)
{
    Transform_resetState(id);
}

    Ext4.onReady(function() {
        Ext4.create('Ext.Button',  {
            text : 'View Processed Jobs',
            renderTo : 'jobsButton',
            handler : function() {
                window.location = LABKEY.ActionURL.buildURL("DataIntegration", "viewJobs", LABKEY.ActionURL.getContainer(), null);
            }
        });
    });
</script>


<%--
 TODO: consider ext rendering for table (grid, or dataview)
--%>

<div class="labkey-data-region-wrap">
<table class="labkey-data-region labkey-show-borders">
    <tr><td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Source Module</td>
        <td class="labkey-column-header">Schedule</td>
        <td class="labkey-column-header">Enabled</td>
        <td class="labkey-column-header">Verbose Logging</td>
        <td class="labkey-column-header">&nbsp;</td>
        <td class="labkey-column-header">&nbsp;</td>
    </tr><%

int row = 0;
for (ScheduledPipelineJobDescriptor descriptor : descriptorsMap.values())
{
    row++;
    String id = descriptor.getId();
    TransformConfiguration configuration = configurationsMap.get(descriptor.getId());
    if (null == configuration)
    {
        configuration = new TransformConfiguration();
        configuration.setContainer(getContainer().getId());
        configuration.setTransformId(id);
    }

    if (isAdmin)
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=getShadeRowClass(1 == row % 2)%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox onchange="onEnabledChanged(this,<%=q(descriptor.getId())%>)" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox onchange="onVerboseLoggingChanged(this,<%=q(descriptor.getId())%>)" <%=checked(configuration.isVerboseLogging())%>></td>
        <td><%= button("run now").href("#").onClick("onRunNowClicked(this," + q(descriptor.getId()) + "); return false;") %></td><%
        if (configuration.getTransformState().contentEquals("{}"))
        {
            %><td><%=PageFlowUtil.generateDisabledButton("reset state")%></td><%
        }
        else
        {
            %><td><%= button("reset state").href("#").onClick("onResetStateClicked(this," + q(descriptor.getId()) + "); return false;") %></td><%
        }
        %></tr><%
    }
    else
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=getShadeRowClass(1 == row % 2)%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox disabled="true" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox disabled="true" onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>
        <td>&nbsp;</td>
        <td>&nbsp;</td>
        </tr><%
    }
}
%></table>
</div>

<br>
<div id='jobsButton'></div>
