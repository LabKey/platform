<%--
/*
 * Copyright (c) 2016 LabKey Corporation
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
--%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView me = HttpView.currentView();
    boolean showRight = me.getView(WebPartFactory.LOCATION_RIGHT) instanceof HttpView && ((HttpView) me.getView(WebPartFactory.LOCATION_RIGHT)).isVisible();
    ActionURL url = new ActionURL(AdminController.ExperimentalFeaturesAction.class, ContainerManager.getRoot());
%>
<div class="container">
    <div class="alert alert-warning" role="alert" style="margin-top: 20px;">
        <strong>Under construction!</strong>
        This layout is under development. <a href="<%=h(url.getLocalURIString())%>" class="alert-link">Turn it off here</a> by disabling the "Core UI Migration" feature.
    </div>
    <div>
        <div class="<%= h(showRight ? "col-md-9" : "col-md-*" ) %>">
            <!-- BODY -->
            <% me.include(me.getBody(), out); %>
            <!-- /BODY -->
        </div>

        <% if (showRight) { %>
        <div class="col-md-3">
            <!-- RIGHT -->
            <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT), out); %>
            <!-- /RIGHT -->
        </div>
        <% } %>
    </div>
</div>