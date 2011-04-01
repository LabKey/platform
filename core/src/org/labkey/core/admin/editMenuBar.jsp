<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView me = HttpView.currentView();
    boolean menuEnabled = LookAndFeelProperties.getInstance(me.getViewContext().getContainer()).isMenuUIEnabled();
    ActionURL refreshURL = urlProvider(AdminUrls.class).getProjectSettingsMenuURL(me.getViewContext().getContainer());
%>
<style type="text/css">
    div.section
    {
        padding: 8px 0;
    }
</style>
<div>
    <form action="" method="POST">
        <div class="section">
            <span>The menu bar is a beta feature of labkey server that can be customized to provide quick access to LabKey features.</span>
            <br/><br/>
            <span>The menu bar is currently <%=menuEnabled ? "on" : "off"%>.</span>
        </div>
        <div class="section">
            <%=generateSubmitButton(menuEnabled ? "Turn Off Custom Menus" : "Turn On Custom Menus")%>
        </div>
        <div class="section">
            <span>The menu bar is populated by web parts. You can add and remove webparts here.</span>
        </div>
        <input type="hidden" value="<%=menuEnabled ? 0 : 1%>"  name="enableMenuBar">
    </form>
    <div class="section">
        <% if (menuEnabled){ %><%= generateButton("Refresh Menu Bar", refreshURL) %><% } %>
    </div>
    <div class="section"><% include(me.getView("menubar"), out); %></div>
</div>
