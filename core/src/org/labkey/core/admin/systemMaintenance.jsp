<%
/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.SystemMaintenance" %>
<%@ page import="org.labkey.api.util.SystemMaintenance.MaintenanceTask" %>
<%@ page import="org.labkey.api.util.SystemMaintenance.SystemMaintenanceProperties" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
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
    SystemMaintenanceProperties props = (SystemMaintenanceProperties)getModelBean();
    List<MaintenanceTask> tasks = SystemMaintenance.getTasks();
    String initalTime = SystemMaintenance.formatSystemMaintenanceTime(props.getSystemMaintenanceTime());
    Set<String> disabled = props.getDisabledTasks();
%>
<labkey:form name="systemMaintenanceSettings" method="post">
    <table width="1000">
        <tr>
            <td colspan="2">The follow tasks are (typically) run every night to clear unused data, update database statistics, perform nightly data refreshes,
                and generally keep this server running smoothly and quickly. We recommend leaving all system maintenance tasks enabled, but some
                of the tasks can be disabled if absolutely necessary. See the LabKey documentation for <%=helpLink("systemMaint", "more info...")%></td>
        </tr>

        <tr>
            <td style="padding-top: 10px;" colspan="2">You can run all enabled maintenance tasks now: <%=textLink("Run all tasks", "javascript:submitSystemMaintenance()")%></td>
        </tr>

        <tr>
            <td style="padding-top: 10px;" colspan="2">You can also run individual tasks, disable tasks, and change the maintenance time.
                Click a task description to invoke that task. Uncheck a checkbox (and save) to disable a task.</td>
        </tr>
        <tr>
            <td style="padding-top: 10px; font-weight: bold;">Tasks That Can Be Disabled</td>
            <td style="padding-top: 10px; font-weight: bold;">Tasks That Can't Be Disabled</td>
        </tr>

        <tr><td style="vertical-align: top;">
            <table>
                <%
                    for (MaintenanceTask task : tasks)
                    {
                        if (task.canDisable() && !task.hideFromAdminPage())
                        {
                %><tr><td><input name="enable" value="<%=h(task.getName())%>" type="checkbox"<%=checked(!disabled.contains(task.getName()))%>/><%=textLink(task.getDescription(), "javascript:submitSystemMaintenance(" + q(task.getName()) + ")")%></td></tr><%
                    }
                }
            %>
            </table>
        </td>
            <td style="vertical-align: top;">
                <table>
                    <%
                        for (MaintenanceTask task : tasks)
                        {
                            if (!task.canDisable() && !task.hideFromAdminPage())
                            {
                    %><tr><td><input type="checkbox" disabled checked/><%=textLink(task.getDescription(), "javascript:submitSystemMaintenance(" + q(task.getName()) + ")")%></td></tr><%
                        }
                    }
                %>
                </table>
            </td>
        </tr>

        <tr>
            <td style="padding-top: 7px;" colspan="2"><div style="" id='timePicker'></div></td>
        </tr>
        <%
            if (AppProps.getInstance().isDevMode())
            {
        %><tr><td colspan="2"><table><tr><td><input name="enableSystemMaintenance" type="checkbox"<%=checked(!SystemMaintenance.isTimerDisabled())%>/>Enable daily system maintenance (dev mode only; system maintenance is re-enabled after every server restart)</td></tr></table></td></tr><%
        }

    %>
        <tr>
            <td style="padding-top: 10px;"><%= button("Save").submit(true).onClick("return validateForm();") %><%= button("Cancel").href(new AdminController.AdminUrlsImpl().getAdminConsoleURL()) %></td>
        </tr>
    </table>
</labkey:form>
<labkey:form name="systemMaintenance" action="<%=h(buildURL(AdminController.SystemMaintenanceAction.class))%>" method="post" target="systemMaintenance"><input type="hidden" name="taskName"/></labkey:form>
<script type="text/javascript">

    // global functions for script calls from this Form
    var validateForm, submitSystemMaintenance;

    Ext4.onReady(function(){
        var timeField = Ext4.create('Ext.form.field.Time', {
            renderTo: 'timePicker',
            width: 400,
            fieldLabel: 'Perform system maintenance every day at',
            labelWidth: 280,
            name: 'maintenanceTime',
            submitFormat: 'H:i',
            value: <%=q(initalTime)%>,
            increment: 15,
            anchor: '100%'
        });

        submitSystemMaintenance = function(taskName)
        {
            var form = document.forms['systemMaintenance'];
            form.taskName.value = (taskName ? taskName : null);
            form.method = 'post';
            document.forms['systemMaintenance'].submit();
        };

        validateForm = function() { return !timeField.hasActiveError(); };
    });
</script>
