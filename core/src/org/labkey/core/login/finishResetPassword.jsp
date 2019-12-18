<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("login.css");
    }
%>
<%
    String message = ((HttpView<String>) HttpView.currentView()).getModelBean();
    ActionURL homeURL = AppProps.getInstance().getHomePageActionURL();
%>
<div class="auth-form">
    <div class="auth-header">Reset Password</div>
    <div class="auth-form-body">
        <p><%=h(message)%></p>
        <div class="auth-item">
            <%= button("Home").primary(true).href(homeURL) %>
        </div>
    </div>
</div>


