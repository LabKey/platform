<%
/*
 * Copyright (c) 2016-2018 LabKey Corporation
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
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.util.JavaScriptFragment" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("notification/Notification.css");
        dependencies.add("notification/Notification.js");
    }
%>
<%
    String notificationCountId = "labkey-notifications-count" + UniqueID.getServerSessionScopedUID();
    String notificationPanelId = "labkey-notifications-panel" + UniqueID.getServerSessionScopedUID();
    HttpView.currentPageConfig().addDocumentLoadHandler(JavaScriptFragment.unsafe("LABKEY.internal.UserNotificationPanel.init(jQuery, " + q(notificationCountId) + ", " + q(notificationPanelId) + ");"));
%>
<li>
    <%
        var linkId = "notification" + this.getRequestScopedUID();
        addHandler(linkId, "click", "LABKEY.Notification.showPanel(); return false;");
    %>
    <a id="<%=h(linkId)%>" href="#">
        <i class="fa fa-inbox labkey-notification-inbox"></i>
        <span id=<%=q(notificationCountId)%>>&nbsp;</span>
    </a>
</li>











