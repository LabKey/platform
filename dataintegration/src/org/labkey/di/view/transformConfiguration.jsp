<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.di.pipeline.ETLDescriptor" %>
<%@ page import="org.labkey.di.pipeline.TransformConfiguration" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.di.view.DataIntegrationController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
ViewContext context = HttpView.currentContext();
List<ETLDescriptor> descriptors = ETLManager.get().getETLs();
List<TransformConfiguration> configurationsList = ETLManager.get().getTransformConfigutaions(context.getContainer());
Map<String,TransformConfiguration> configurationsMap = new HashMap<String, TransformConfiguration>(configurationsList.size()*2);
for (TransformConfiguration c : configurationsList)
    configurationsMap.put(c.getTransformId(), c);

%>
<script>
var X = Ext4 || Ext;
function Transform_setProperty(transformId, property, value)
{
//    alert(transformId + " " + property + "=" + value);
    var params = {'transformId':transformId};
    params[property] = value;

    X.Ajax.request({
        url : <%=q(buildURL(DataIntegrationController.UpdateTransformConfigurationAction.class))%>,
        params : params,
        method : "POST"
//        ,success : onSuccess
//        ,failure : onFailure
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

<table><tr><th>Name</th><th>Source Module</th><th>Schedule</th><th>Enabled</th><th>Verbose Logging</th><th>&nbsp;</th></tr><%

for (ETLDescriptor descriptor : descriptors)
{
    String id = descriptor.getId();
    TransformConfiguration configuration = configurationsMap.get(descriptor.getId());
    boolean isNewConfiguration = false;
    if (null == configuration)
    {
        isNewConfiguration = true;
        configuration = new TransformConfiguration();
        configuration.setContainer(context.getContainer().getId());
        configuration.setTransformId(id);
    }
    %><tr transformId="<%=h(descriptor.getId())%>">
        <td><%=h(descriptor.getName())%></td>
        <td><%=h(descriptor.getModuleName())%></td>
        <td><%=h(descriptor.getScheduleDescription())%></td>
        <td><input type=checkbox onchange="onEnabledChanged()" <%=checked(configuration.isEnabled())%>></td>
        <td><input type=checkbox onchange="onVerboseLoggingChanged()" <%=checked(configuration.isVerboseLogging())%>></td>
        <td><%=generateButton("run now", "#", "onRunNowClicked()")%></td>
    </tr><%
}
%></table>