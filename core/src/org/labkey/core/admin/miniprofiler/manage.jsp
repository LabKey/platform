<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.miniprofiler.MiniProfiler" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.miniprofiler.MiniProfilerController" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<MiniProfiler.Settings> me = (JspView<MiniProfiler.Settings>) HttpView.currentView();
    MiniProfiler.Settings settings = me.getModelBean();

    Map<String, String> renderOptions = new LinkedHashMap<>();
    renderOptions.put(MiniProfiler.RenderPosition.TopLeft.name(), MiniProfiler.RenderPosition.TopLeft.toString());
    renderOptions.put(MiniProfiler.RenderPosition.TopRight.name(), MiniProfiler.RenderPosition.TopRight.toString());
    renderOptions.put(MiniProfiler.RenderPosition.BottomLeft.name(), MiniProfiler.RenderPosition.BottomLeft.toString());
    renderOptions.put(MiniProfiler.RenderPosition.BottomRight.name(), MiniProfiler.RenderPosition.BottomRight.toString());
%>

LabKey Server includes some simple built-in profiling tools.

Some of them incur overhead to track or take space in the UI, and are thus configurable here.

<labkey:errors/>
<labkey:form action="<%=h(buildURL(MiniProfilerController.ManageAction.class))%>" method="POST">
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
            <td>&nbsp;</td>
        </tr>

        <tr>
            <td colspan="2">
                The MiniProfiler is a simple profiler utility that shows at a glance how long actions and queries take
                to execute. It's shown in the corner of each LabKey web page. The profiler is enabled when the server is
                running in dev mode or if the current user has the <%=helpLink("devRoles#platformDeveloper", "Platform Developer")%> role.
                <%=MiniProfiler.getHelpTopic().getLinkHtml("MiniProfiler Help")%>
            </td>
        </tr>

        <tr>
            <td class="labkey-form-label"><label for="enabled">Enabled<%=helpPopup("Enabled", "Enable the MiniProfiler widget, causing it to appear for all Site Administrators and Site Developers")%></label></td>
            <td>
                <labkey:checkbox name="enabled" id="enabled" value="true" checked="<%=settings.isEnabled()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="renderPosition">Render position<%=helpPopup("Render Position", "Specifies the corner in which to render the MiniProfiler widget")%></label></td>
            <td>
                <select name="renderPosition" id="renderPosition">
                    <labkey:options map="<%=renderOptions%>" value="<%=settings.getRenderPosition()%>"/>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="toggleShortcut">Toggle shortcut<%=helpPopup("Toggle shortcut", "Show/hide the MiniProfiler widget using a keyboard shortcut (e.g., 'alt+p' or 'alt+shift+p', or 'none' to disable toggling.)")%></label></td>
            <td>
                <input name="toggleShortcut" id="toggleShortcut" value="<%=h(settings.getToggleShortcut())%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="startHidden">Start hidden<%=helpPopup("Start Hidden", "MiniProfiler widget will initially be hidden, requiring keyboard activation via the 'Toggle shortcut'")%></label></td>
            <td>
                <labkey:checkbox name="startHidden" id="startHidden" value="true" checked="<%=settings.isStartHidden()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="showControls">Show controls<%=helpPopup("Show Controls", "Show the minimize and clear controls in the MiniProfiler widget")%></label></td>
            <td>
                <labkey:checkbox name="showControls" id="showControls" value="true" checked="<%=settings.isShowControls()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="showTrivial">Show trivial timings by default<%=helpPopup("Show Trivial", "Specify whether trivial timings are displayed by default")%></label></td>
            <td>
                <labkey:checkbox name="showTrivial" id="showTrivial" value="true" checked="<%=settings.isShowTrivial()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="trivialMillis">Trivial milliseconds<%=helpPopup("Trivial Milliseconds", "Timings that are less than this value are considered trivial and will be greyed out.")%></label></td>
            <td>
                <input id="trivialMillis" name="trivialMillis" type="text" value="<%=settings.getTrivialMillis()%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label"><label for="showChildrenTime">Show children timings by default<%=helpPopup("Show Children Time", "Specify whether inclusive timings are displayed by default")%></label></td>
            <td>
                <labkey:checkbox name="showChildrenTime" id="showChildrenTime" value="true" checked="<%=settings.isShowChildrenTime()%>"/>
            </td>
        </tr>
        <tr>
            <td/>
            <td>
                    <%--<%= button("Reset to Default"). %>&nbsp;--%>
                <%= button("Save").submit(true) %>
                <%= button("Reset").href(urlFor(MiniProfilerController.ResetAction.class)).usePost() %>
                <%= button("Cancel").href(urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
            </td>
        </tr>
    </table>
</labkey:form>

