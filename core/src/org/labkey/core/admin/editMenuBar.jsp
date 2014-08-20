<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView me = HttpView.currentView();
    ActionURL refreshURL = urlProvider(AdminUrls.class).getProjectSettingsMenuURL(getContainer());
%>
<style type="text/css">
    div.section
    {
        padding: 8px 0;
    }
</style>
<div>
    <labkey:form action="" method="POST">
        <div class="section">
            <span>The menu bar can be customized to provide quick access to LabKey features.</span>
        </div>
        <div class="section">
            <span>The menu bar is populated by web parts. You can add and remove webparts here.</span>
        </div>
    </labkey:form>
    <div class="section">
        <%= button("Refresh Menu Bar").href(refreshURL) %>
    </div>
    <div class="section"><% include(me.getView("menubar"), out); %></div>
</div>
