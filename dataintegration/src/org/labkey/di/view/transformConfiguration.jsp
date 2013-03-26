<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.di.pipeline.ETLManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.di.pipeline.TransformConfiguration" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.di.api.ScheduledPipelineJobDescriptor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
ViewContext context = HttpView.currentContext();
List<ScheduledPipelineJobDescriptor> descriptors = ETLManager.get().getETLs();
List<TransformConfiguration> configurationsList = ETLManager.get().getTransformConfigurations(context.getContainer());
Map<String,TransformConfiguration> configurationsMap = new HashMap<String, TransformConfiguration>(configurationsList.size()*2);
for (TransformConfiguration c : configurationsList)
    configurationsMap.put(c.getTransformId(), c);

boolean isAdmin = context.hasPermission(AdminPermission.class);

%>
<script>
var X = Ext4 || Ext;

function onFailedConfigurationUpdate(response,config)
{
    X.MessageBox.show({
        modal:true,
        title:response.statusText,
        msg:"There was an error updating the configuration",
        icon:Ext.MessageBox.ERROR,
        buttons: Ext.MessageBox.OK,
        fn: function(){window.location.reload(true);}
    });
}
function Transform_setProperty(transformId, property, value)
{
    var params = {'transformId':transformId};
    params[property] = value;

    X.Ajax.request({
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
    X.Ajax.request({
        url : <%=q(buildURL(DataIntegrationController.RunTransformAction.class))%>,
        params : params,
        method : "POST"
//        ,success : onSuccess
//        ,failure : onFailure
    });
}



// UI GLUE
function getTransformId()
{
    var transformId = null;
    var e = event.srcElement;
    while (e && e.tagName != "TR")
        e = e.parentElement;
    if (e && e.tagName == "TR")
        transformId = e.getAttribute("transformid");
    return transformId;
}
function onEnabledChanged()
{
    var checked = event.srcElement.checked;
    Transform_setEnabled(getTransformId(), checked);
}
function onVerboseLoggingChanged()
{
    var checked = event.srcElement.checked;
    Transform_setVerboseLogging(getTransformId(), checked);
}
function onRunNowClicked()
{
    Transform_runNow(getTransformId());
}
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
    </tr><%

int row = 0;
for (ScheduledPipelineJobDescriptor descriptor : descriptors)
{
    row++;
    String id = descriptor.getId();
    TransformConfiguration configuration = configurationsMap.get(descriptor.getId());
    if (null == configuration)
    {
        configuration = new TransformConfiguration();
        configuration.setContainer(context.getContainer().getId());
        configuration.setTransformId(id);
    }

    if (isAdmin)
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=text(1==row%2?"labkey-alternate-row":"labkey-row")%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox onchange="onEnabledChanged()" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>
        <td><%=generateButton("run now", "#", "onRunNowClicked(); return false;")%></td>
        </tr><%
    }
    else
    {
        %><tr transformId="<%=h(descriptor.getId())%>" class="<%=text(1==row%2?"labkey-alternate-row":"labkey-row")%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox disabled="true" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox disabled="true" onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>
        <td>&nbsp;</td>
        </tr><%
    }
}
%></table>
</div>