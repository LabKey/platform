<%
/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.mothership.MothershipController" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Container c = getContainer();
%>
<div>
    <%= link("View Exceptions", new ActionURL(MothershipController.ShowExceptionsAction.class, c).addParameter(DataRegion.LAST_FILTER_PARAM, "true")) %>
    <%= link("View All Installations", new ActionURL(MothershipController.ShowInstallationsAction.class, c)) %>
    <%= link("Configure Mothership", new ActionURL(MothershipController.EditUpgradeMessageAction.class, c)) %>
    <%= link("List of Releases", new ActionURL(MothershipController.ShowReleasesAction.class, c)) %>
    <% if (getContainer().hasPermission(getUser(), AdminPermission.class)) { %>
    <%= link("Manual Metric Import", new ActionURL(MothershipController.ManualMetricImportAction.class, c)) %>
    <%}%>
    <% if (getUser() != null && !getUser().isGuest()) {
            ActionURL myExceptions = new ActionURL(MothershipController.ShowExceptionsAction.class, c);
            myExceptions.addFilter("ExceptionSummary", FieldKey.fromParts("BugNumber"), CompareType.ISBLANK, null);
            myExceptions.addFilter("ExceptionSummary", FieldKey.fromParts("AssignedTo", "DisplayName"), CompareType.EQUAL, getUser().getDisplayName(getUser()));
        %>
        <%= link("My Exceptions", myExceptions)%>
    <%}%>
    <labkey:form name="jumpToErrorCode" action="<%= new ActionURL(MothershipController.JumpToErrorCodeAction.class, c) %>" layout="inline" style="display:inline-block;margin-left:20px;margin-bottom:10px;">
        <div class="input-group">
            <labkey:input name="errorCode" formGroup="false" placeholder="Find Error Code"/>
            <div class="input-group-btn">
                <%= button("Search").addClass("btn btn-default").iconCls("search").submit(true) %>
            </div>
        </div>
    </labkey:form>
</div>