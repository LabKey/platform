<%
/*
 * Copyright (c) 2014-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.ConnectionUsage" %>
<%@ page import="org.labkey.api.miniprofiler.MiniProfiler" %>
<%@ page import="org.labkey.core.admin.miniprofiler.MiniProfilerController.ManageAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

LabKey Server includes some simple built-in profiling tools.

Some of them incur overhead to track or take space in the UI, and are thus configurable here.

<labkey:errors/>
<labkey:form action="<%=urlFor(ManageAction.class)%>" method="POST">
    <table class="labkey-manage-display">
        <tr>
            <td class="labkey-form-label" style="width: 200px;"><label for="collectTroubleshootingStackTraces">Capture stack traces until server shutdown<%=helpPopup("Capture stack traces",
                    "The server can automatically capture stack traces for key operations, including the creation of certain objects, " +
                            "the execution of database, queries, and more. While they can be very useful for troubleshooting, " +
                            "they can add 10% or more overhead, so this setting will automatically reset when the server is restarted.")%></label></td>
            <td>
                <labkey:checkbox name="collectTroubleshootingStackTraces" id="collectTroubleshootingStackTraces" value="true" checked="<%=MiniProfiler.isCollectTroubleshootingStackTraces()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="trackConnectionUsage">Track connection usage until server shutdown<%=helpPopup("Track connection usage",
                    "Records database connection borrows, hold time, and acquire time for each action, shown on the action statistics page. " +
                            "It adds a small cost to every connection borrow, so this setting resets to its default when the server is restarted. " +
                            "Turning it off discards the connection statistics collected so far.")%></label></td>
            <td>
                <labkey:checkbox name="trackConnectionUsage" id="trackConnectionUsage" value="true" checked="<%=ConnectionUsage.isEnabled()%>"/>
            </td>
        </tr>

        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td/>
            <td>
                    <%--<%= button("Reset to Default"). %>&nbsp;--%>
                <%= button("Save").submit(true) %>
                <%= button("Cancel").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
            </td>
        </tr>
    </table>
</labkey:form>

