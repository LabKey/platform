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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.SystemMaintenance" %>
<%@ page import="org.labkey.api.util.SystemMaintenance.MaintenanceTask" %>
<%@ page import="org.labkey.api.util.SystemMaintenance.SystemMaintenanceProperties" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    SystemMaintenanceProperties props = (SystemMaintenanceProperties)getModelBean();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
    List<MaintenanceTask> tasks = new ArrayList<>(SystemMaintenance.getTasks());
    tasks.sort(Comparator.comparing(MaintenanceTask::getDescription, String.CASE_INSENSITIVE_ORDER));
    String initialTime = SystemMaintenance.formatSystemMaintenanceTime(props.getSystemMaintenanceTime());
    Set<String> disabled = props.getDisabledTasks();
%>
<style>
    .labkey-enabled-option
    {
        color: black;
    }
</style>

<labkey:form name="systemMaintenanceSettings" method="post">
    <table width="1000">
        <tr>
            <td colspan="2">The following tasks are (typically) run every night to clear unused data, update database statistics, perform nightly data refreshes,
                and generally keep this server running smoothly and quickly. We recommend leaving all system maintenance tasks enabled, but some
                of the tasks can be disabled if absolutely necessary. See the LabKey documentation for <%=helpLink("systemMaint", "more information.")%></td>
        </tr>

        <%
            if (hasAdminOpsPerms)
            {
        %>
        <tr>
            <td style="padding-top: 10px;" colspan="2">You can run all enabled maintenance tasks now: <%=textLink("Run all tasks", "javascript:submitSystemMaintenance()")%></td>
        </tr>

        <tr>
            <td style="padding-top: 10px;" colspan="2">You can also run individual tasks, disable tasks, and change the maintenance time.
                Click a task description to invoke that task. Uncheck a checkbox (and save) to disable a task.</td>
        </tr>
        <%
            }
        %>
        <tr>
            <td style="padding-top: 10px; font-weight: bold;">Tasks </td>
        </tr>

        <tr><td style="vertical-align: top;">
            <table>
                <%
                    for (MaintenanceTask task : tasks)
                    {
                        if (!task.hideFromAdminPage())
                        {
                            String description;
                            if (hasAdminOpsPerms)
                                description = textLink(task.getDescription(), "javascript:submitSystemMaintenance(" + q(task.getName()) + ")");
                            else
                                description = "<span class=\"labkey-disabled-text-link labkey-enabled-option\">" + h(task.getDescription()) + "</span>";
                            if (!task.canDisable())
                            {
                %><tr><td><input type="checkbox" disabled checked/><%=text(description)%></td></tr><%
                            }
                            else
                            {
                                String checkboxDisabled = hasAdminOpsPerms ? "" : "disabled";
                %><tr><td><input name="enable" <%=text(checkboxDisabled)%> value="<%=h(task.getName())%>" type="checkbox"<%=checked(!disabled.contains(task.getName()))%>/><%=text(description)%></td></tr><%
                            }
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
            if (AppProps.getInstance().isDevMode() && hasAdminOpsPerms)
            {
        %><tr><td colspan="2"><table><tr><td><labkey:checkbox id="enableSystemMaintenance" name="enableSystemMaintenance" value="true" checked="<%=!SystemMaintenance.isTimerDisabled()%>"/>Enable daily system maintenance (dev mode only; system maintenance is re-enabled after every server restart)</td></tr></table></td></tr><%
            }
        %>
        <tr>
            <td style="padding-top: 10px;">
                <%= hasAdminOpsPerms ? button("Save").submit(true).onClick("return validateForm();") : "" %>
                <%= button(!hasAdminOpsPerms ? "Done" : "Cancel").href(new AdminController.AdminUrlsImpl().getAdminConsoleURL()) %>
            </td>
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
            disabled: <%=!hasAdminOpsPerms%>,
            value: <%=q(initialTime)%>,
            increment: 15,
            anchor: '100%'
        });

        submitSystemMaintenance = function(taskName)
        {
            if (<%=hasAdminOpsPerms%>) {
                var form = document.forms['systemMaintenance'];
                form.taskName.value = (taskName ? taskName : null);
                form.method = 'post';
                document.forms['systemMaintenance'].submit();
            }
        };

        validateForm = function() { return !timeField.hasActiveError(); };
    });
</script>
