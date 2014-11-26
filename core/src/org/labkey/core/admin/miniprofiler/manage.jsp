<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
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

The MiniProfiler is a simple profiler utility that shows at a glance how long actions and queries take to execute. The
profiler is enabled when the server is running in dev mode or if the current user is in the <%=helpLink("globalGroups", "site developer group")%>.

<p>
<%=text(MiniProfiler.getHelpTopic().getLinkHtml("Profiler Help"))%>
</p>

<labkey:errors/>
<labkey:form action="<%=h(buildURL(MiniProfilerController.ManageAction.class))%>" method="POST">
    <table class="labkey-manage-display">
        <tr>
            <td class="labkey-form-label">Enabled<%=helpPopup("Enabled", "Enable the MiniProfiler widget, causing it to appear for all Site Administrators and Site Developers")%></td>
            <td>
                <labkey:checkbox name="enabled" id="enabled" value="true" checked="<%=settings.isEnabled()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Render position<%=helpPopup("Render Position", "Specifies the corner in which to render the MiniProfiler widget")%></td>
            <td>
                <select name="renderPosition" id="renderPosition">
                    <labkey:options map="<%=renderOptions%>" value="<%=h(settings.getRenderPosition())%>"/>
                </select>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Toggle shortcut<%=helpPopup("Toggle shortcut", "Show/hide the MiniProfiler widget using a keyboard shortcut (e.g., 'alt+p' or 'alt+shift+p', or 'none' to disable toggling.)")%></td>
            <td>
                <input name="toggleShortcut" id="toggleShortcut" value="<%=h(settings.getToggleShortcut())%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Start hidden<%=helpPopup("Start Hidden", "MiniProfiler widget will initially be hidden, requiring keyboard activation via the 'Toggle shortcut'")%></td>
            <td>
                <labkey:checkbox name="startHidden" id="startHidden" value="true" checked="<%=settings.isStartHidden()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show controls<%=helpPopup("Show Controls", "Show the minimize and clear controls in the MiniProfiler widget")%></td>
            <td>
                <labkey:checkbox name="showControls" id="showControls" value="true" checked="<%=settings.isShowControls()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show trivial timings by default<%=helpPopup("Show Trivial", "Specify whether trivial timings are displayed by default")%></td>
            <td>
                <labkey:checkbox name="showTrivial" id="showTrivial" value="true" checked="<%=settings.isShowTrivial()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Trivial milliseconds<%=helpPopup("Trivial Milliseconds", "Timings that are less than this value are considered trivial and will be greyed out.")%></td>
            <td>
                <input id="trivialMillis" name="trivialMillis" type="text" value="<%=settings.getTrivialMillis()%>">
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Show children timings by default<%=helpPopup("Show Children Time", "Specify whether inclusive timings are displayed by default")%></td>
            <td>
                <labkey:checkbox name="showChildrenTime" id="showChildrenTime" value="true" checked="<%=settings.isShowChildrenTime()%>"/>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Custom timing stacktrace<%=helpPopup("Custom timing stacktrace", "Capture stacktrace when recording custom timings")%></td>
            <td>
                <labkey:checkbox name="captureCustomTimingStacktrace" id="captureCustomTimingStacktrace" value="true" checked="<%=settings.isCaptureCustomTimingStacktrace()%>"/>
            </td>
        </tr>
    </table>
    <p>
        <%--<%= button("Reset to Default"). %>&nbsp;--%>
        <%= button("Save").submit(true) %>
        <%= button("Reset").href(buildURL(MiniProfilerController.ResetAction.class)) %>
        <%= button("Cancel").href(PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL()) %>
    </p>
</labkey:form>

