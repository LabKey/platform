<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.data.MenuButton" %>
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.di.ScheduledPipelineJobDescriptor" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.di.pipeline.TransformConfiguration" %>
<%@ page import="org.labkey.di.pipeline.TransformManager" %>
<%@ page import="org.labkey.di.pipeline.TransformRun" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
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
List<TransformConfiguration> configurationsList = TransformManager.get().getTransformConfigurations(getContainer());
Map<String,TransformConfiguration> configurationsMap = new HashMap<>(configurationsList.size()*2);
for (TransformConfiguration c : configurationsList)
    configurationsMap.put(c.getTransformId(), c);

// It's possible to have configurations for transforms whose modules are inactive, so make sure we get those
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
final String PENDING = TransformRun.TransformRunStatus.PENDING.getDisplayName();
boolean isAdmin = getViewContext().hasPermission(AdminPermission.class);
%>
<style type="text/css">
    .etl-table td {
        height: 35px;
        padding: 3px 5px;
    }

    .etl-table-header td {
        height: 20px;
    }

    .etl-action-col {
        text-align: center;
    }
</style>
<script type="text/javascript">
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

    function Transform_truncateResetState(transformId)
    {
        var params = {'transformId':transformId};
        Ext4.MessageBox.confirm(
            'Confirm',
            'You are about to permanently delete the records associated with this ETL.  It cannot be undone.  Are you sure you want to do this?',
            function(val){
                if(val=='yes'){
                    var waitMask = Ext4.Msg.wait('Deleting Rows...', 'Truncating tables');
                    Ext4.Ajax.request({
                        url : <%=q(buildURL(DataIntegrationController.TruncateTransformStateAction.class))%>,
                        params : params,
                        method : "POST",
                        success : function(response)
                        {
                            waitMask.close();
                            var data = Ext4.JSON.decode(response.responseText);
                            Ext4.Msg.alert("Success", data.deletedRows + " rows deleted");
                        },
                        failure : function(response, opts)
                        {
                            waitMask.close();
                            LABKEY.Utils.displayAjaxErrorResponse(response, opts);
                        }
                    });
                }
            },
        this);

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

function onTruncateAndReset(el, id)
{
    Transform_truncateResetState(id);
}
</script>


<%--
 TODO: consider ext rendering for table (grid, or dataview)
--%>

<div class="labkey-data-region-wrap">
<table class="labkey-data-region labkey-show-borders etl-table">
    <tr class="etl-table-header">
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Source Module</td>
        <td class="labkey-column-header">Schedule</td>
        <td class="labkey-column-header">Enabled</td>
        <td class="labkey-column-header">Verbose Logging</td>
        <td class="labkey-column-header">Last Status</td>
        <td class="labkey-column-header">Last Successful Run</td>
        <td class="labkey-column-header">Last Checked</td>
        <td class="labkey-column-header"></td>
        <td class="labkey-column-header"></td>
    </tr><%

int row = 0;
List<ScheduledPipelineJobDescriptor> sortedDescriptors = new ArrayList<>(descriptorsMap.values());
Collections.sort(sortedDescriptors, new Comparator<ScheduledPipelineJobDescriptor>()
{
    @Override
    public int compare(ScheduledPipelineJobDescriptor o1, ScheduledPipelineJobDescriptor o2)
    {
        return o1.getName().compareToIgnoreCase(o2.getName());
    }
});
for (ScheduledPipelineJobDescriptor descriptor : sortedDescriptors)
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
        boolean enableControls = !PENDING.equalsIgnoreCase(configuration.getLastStatus());
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=getShadeRowClass(1 == row % 2)%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox onchange="onEnabledChanged(this,<%=q(descriptor.getId())%>)" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox
                   onchange="onVerboseLoggingChanged(this,<%=q(descriptor.getId())%>)" <%=checked(configuration.isVerboseLogging())%>>
        </td>
        <td><%=text(configuration.getLastStatusUrl())%></td>
        <td><%=text(configuration.getLastCompletionUrl())%></td>
        <td><%=text(configuration.getLastCheckedString())%></td>
        <td class="etl-action-col"><%= button("run now").href("#").onClick("onRunNowClicked(this," + q(descriptor.getId()) + "); return false;").enabled(enableControls) %></td>
        <td class="etl-action-col"><%
            MenuButton reset = new MenuButton("ETL state...");
            reset.addMenuItem("Reset", "#", "onResetStateClicked(this," + q(descriptor.getId()) + "); return false;");
            reset.addMenuItem("Truncate and Reset", "#", "onTruncateAndReset(this," + q(descriptor.getId()) + "); return false;");
            reset.render(new RenderContext(getViewContext()), out);
        %></td>
        </tr><%
    }
    else
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=getShadeRowClass(1 == row % 2)%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox disabled="true" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox disabled="true" onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>
        <td></td>
        <td></td>
        <td></td>
        </tr><%
    }
}
%></table>
</div>

<br>
<div><%= button("View Processed Jobs").href(DataIntegrationController.viewJobsAction.class, getContainer()) %></div>
