<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.di.filters.FilterStrategy" %>
<%@ page import="org.labkey.di.pipeline.TransformConfiguration" %>
<%@ page import="org.labkey.di.pipeline.TransformDescriptor" %>
<%@ page import="org.labkey.di.pipeline.TransformManager" %>
<%@ page import="org.labkey.di.pipeline.TransformRun" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
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
{
    if (d.isStandalone())
        descriptorsMap.put(d.getId(), d);
}
for (TransformConfiguration c : configurationsList)
{
    if (!descriptorsMap.containsKey(c.getTransformId()))
    {
        ScheduledPipelineJobDescriptor d = TransformManager.get().getDescriptor(c.getTransformId());
        if (null != d && d.isStandalone())
            descriptorsMap.put(d.getId(), d);
    }
}
final String PENDING = TransformRun.TransformRunStatus.PENDING.getDisplayName();
boolean isAdmin = getViewContext().hasPermission(AdminPermission.class);
%>
<style type="text/css">
    .etl-table {
        width: 100%;
    }

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
    function transform_setProperty(transformId, property, value)
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
    function transform_setEnabled(transformId, enabled)
    {
        transform_setProperty(transformId, "enabled", enabled);
    }
    function transform_setVerboseLogging(transformId, verbose)
    {
        transform_setProperty(transformId, "verboseLogging", verbose);
    }

    function transform_runNow(transformId)
    {
        transform_runWithParams({'transformId': transformId});
    }

    function transform_runWithParams(params)
    {
        Ext4.Ajax.request({
            url : <%=q(buildURL(DataIntegrationController.RunTransformAction.class))%>,
            params : params,
            method : "POST",
            success : function(response)
            {
                var json = {};
                try
                {
                    json = response.responseJSON || LABKEY.Utils.decode(response.responseText);
                } catch (x) {}
                if ("pipelineURL" in json && json.pipelineURL)
                    window.location = json.pipelineURL;
                else
                    Ext4.Msg.alert("Success", json.status || "No work to do.");
            },
            failure : LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure({
                failure: function(response) {
                    Ext4.Msg.alert('Error', Ext4.htmlEncode(response.exception));
                }
            }), window, true)
        });
    }

    function transform_resetState(transformId)
    {
        var params = {'transformId':transformId};
        Ext4.Ajax.request({
            url : <%=q(buildURL(DataIntegrationController.ResetTransformStateAction.class))%>,
            params : params,
            method : "POST",
            success : function(response)
            {
            },
            failure : LABKEY.Utils.getCallbackWrapper(LABKEY.Utils.getOnFailure({
                failure: function(response) {
                    Ext4.Msg.alert('Error', Ext4.htmlEncode(response.exception));
                }
            }), window, true)
        });
    }

    function transform_truncateResetState(transformId)
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
                            if(data.success === true && data.deletedRows)
                                Ext4.Msg.alert("Success", data.deletedRows + " rows deleted");
                            else if(data.error)
                                Ext4.Msg.alert("Error", data.error.replace(/(?:\r\n|\r|\n)/g, '<br>'));
                            else
                                Ext4.Msg.alert("Error", "Unable to complete operation.");
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

    function transform_runDialog(transformId, filterType)
    {
        var params = {'transformId': transformId,
                        'type' : filterType};

        var useModified = false;
        var intFormTitle = '';
        var intFieldLabel = 'Transfer Run Id'
        if (filterType == 'ModifiedSince')
        {
            intFormTitle = 'Rowversion Range';
            intFieldLabel = 'Rowversion';
            useModified = true;
        }

        var cancelDialogButton = {
            text: 'Cancel',
            scope: this,
            handler: function ()
            {
                win.close();
            }
        };
        var helpMsg = {xtype: 'component', html: '<b>For testing purposes only.</b><br/>Overrides initial or persisted incremental filter values.<br/><br/>'};
        var panelWidth = 325;

        var dateItems = [
            {xtype: 'fieldcontainer',
            fieldLabel: 'Min Date/Time',
            labelWidth: 120,
            layout: 'hbox',
            items: [{
                xtype: 'datefield',
                name: 'minDate',
                hideLabel: true,
                width: 100,
                value: new Date(),
                minValue: '1/1/1753'
            },
            {
                xtype: 'timefield',
                name: 'minTime',
                hideLabel: true,
                width: 100,
                format: 'H:i',
                increment: 30,
                minValue: '1/1/1753'
            }]
        },
            {
                xtype: 'fieldcontainer',
                fieldLabel: 'Max Date/Time',
                labelWidth: 120,
                layout: 'hbox',
                items: [{
                    xtype: 'datefield',
                    name: 'maxDate',
                    hideLabel: true,
                    width: 100,
                    value: new Date()
                },
                {
                    xtype: 'timefield',
                    name: 'maxTime',
                    hideLabel: true,
                    width: 100,
                    format: 'H:i',
                    increment: 30
                }]
            }
        ];

        var intItems = [
            {xtype: 'fieldcontainer',
            fieldLabel: 'Min ' + intFieldLabel,
            labelWidth: 150,
            layout: 'hbox',
            items: [{
                xtype: 'numberfield',
                name: 'minInt',
                hideLabel: true,
                allowDecimals: false,
                width: 150
            }]
        },
        {
            xtype: 'fieldcontainer',
            fieldLabel: 'Max ' + intFieldLabel,
            labelWidth: 150,
            layout: 'hbox',
            items: [{
                xtype: 'numberfield',
                name: 'maxInt',
                hideLabel: true,
                allowDecimals: false,
                width: 150
            }]
        }
        ];



        var dateForm = Ext4.create('Ext.form.Panel', {
            border: false,
            title: 'Date Range',
            items: dateItems,
            width: panelWidth,
            buttons: [{
                text: 'Run',
                scope: this,
                handler: function()
                {
                    var values = dateForm.getValues();
                    if (values.minDate != '')
                    {
                        var dateWindowMin = values.minDate;
                        if (values.minTime != undefined)
                        {
                            dateWindowMin += ' ' + values.minTime;
                        }
                        params.dateWindowMin = dateWindowMin;
                    }
                    if (values.maxDate != '')
                    {
                        var dateWindowMax = values.maxDate;
                        if (values.maxTime != undefined)
                        {
                            dateWindowMax += ' ' + values.maxTime;
                        }
                        params.dateWindowMax = dateWindowMax;
                    }
                    transform_runWithParams(params);
                    win.close();
                }
            }, cancelDialogButton]
        });

        var integerForm = Ext4.create('Ext.form.Panel', {
                    border: false,
                    title: intFormTitle,
                    items: intItems,
                    width: panelWidth,
                    buttons: [{
                        text: 'Run',
                        scope: this,
                        handler: function(){
                            var values = integerForm.getValues();
                            if (values.minInt != undefined)
                            {
                                params.intWindowMin = values.minInt;
                            }
                            if (values.maxInt != undefined)
                            {
                                params.intWindowMax = values.maxInt;
                            }
                            transform_runWithParams(params);
                            win.close();
                        }
                    }, cancelDialogButton]
                }
        );
        
        var modifiedForm = Ext4.create('Ext.tab.Panel', {
            border: false,
            width: panelWidth,
            items: [dateForm, integerForm]
        });


        var win = Ext4.create('Ext.window.Window', {
            title: 'Incremental Run Range',
            autoShow: true,
            modal: true,
            resizable: false,
            bodyStyle: 'padding: 10px;',
            items: [helpMsg, useModified ? modifiedForm : integerForm]
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
    transform_setEnabled(id, checked);
}
function onVerboseLoggingChanged(el,id)
{
    var checked = el.checked;
    transform_setVerboseLogging(id, checked);
}
function onRunNowClicked(el,id)
{
    transform_runNow(id);
}

function onRunDialogClicked(id, filterType)
{
    transform_runDialog(id, filterType);
}

function onResetStateClicked(el, id)
{
    transform_resetState(id);
}

function onTruncateAndReset(el, id)
{
    transform_truncateResetState(id);
}
</script>


<%--
 TODO: consider ext rendering for table (grid, or dataview)
--%>

<div class="labkey-data-region-wrap">
<table class="labkey-data-region-legacy labkey-show-borders etl-table">
    <tr class="etl-table-header" align="center">
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Source Module</td>
        <td class="labkey-column-header">Schedule</td>
        <td class="labkey-column-header">Enabled</td>
        <%--<td class="labkey-column-header">Verbose Logging</td>--%>
        <td class="labkey-column-header">Last Status</td>
        <td class="labkey-column-header">Last Successful Run</td>
        <td class="labkey-column-header">Last Checked</td>
    <%if(isAdmin) {%>
        <td class="labkey-column-header">Run</td>
        <% if (AppProps.getInstance().isDevMode()) { %>
            <td class="labkey-column-header">Set Range</td>
        <% } %>
        <td class="labkey-column-header">Reset</td>
        <td class="labkey-column-header">Last Transform Run Log Error</td>
    <%}%>
    </tr><%

int row = 0;
List<ScheduledPipelineJobDescriptor> sortedDescriptors = new ArrayList<>(descriptorsMap.values());
sortedDescriptors.sort(Comparator.comparing(ScheduledPipelineJobDescriptor::getName, String.CASE_INSENSITIVE_ORDER));
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
        <%--<td><input type=checkbox--%>
                   <%--onchange="onVerboseLoggingChanged(this,<%=q(descriptor.getId())%>)" <%=checked(configuration.isVerboseLogging())%>>--%>
        <%--</td>--%>
        <td><%=text(configuration.getLastStatusUrl())%></td>
        <td><%=text(configuration.getLastCompletionUrl())%></td>
        <td><%=text(configuration.getLastCheckedString())%></td>
        <td class="etl-action-col">
            <%= button("run now").href("#").onClick("onRunNowClicked(this," + q(descriptor.getId()) + "); return false;").enabled(enableControls) %>
        </td>
        <% if (AppProps.getInstance().isDevMode()) { %>
        <td class="etl-action-col">
            <%  FilterStrategy.Type filterType = ((TransformDescriptor)descriptor).getDefaultFilterFactory().getType();
                if (FilterStrategy.Type.ModifiedSince == filterType || FilterStrategy.Type.Run == filterType) { %>
                <%= button("run...").href("#").onClick("onRunDialogClicked(" + q(descriptor.getId()) + ", " + q(filterType.toString()) + "); return false;").enabled(enableControls) %>
            <%--<% } else { %>--%>
                <%--<%= button("run ...").enabled(false)%>--%>
            <% } %>
        </td>
        <% } %>
        <td class="etl-action-col"><%
            MenuButton reset = new MenuButton("Reset State...");
            reset.addMenuItem("Reset", "#", "onResetStateClicked(this," + q(descriptor.getId()) + "); return false;");
            reset.addMenuItem("Truncate and Reset", "#", "onTruncateAndReset(this," + q(descriptor.getId()) + "); return false;");
            reset.render(new RenderContext(getViewContext()), out);
        %></td>
        <td><%=h(configuration.getLastTransformRunLog())%></td>
        </tr><%
    }
    else
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=getShadeRowClass(1 == row % 2)%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox disabled="true" <%=checked(configuration.isEnabled())%>></td>
        <%--<td><input type=checkbox disabled="true" onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>--%>
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
