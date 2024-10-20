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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.security.LoginUrls"%>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.login.LoginController.LoginForm" %>
<%@ page import="org.labkey.core.login.LoginController.ResetPasswordAction" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("login.css");
    }
%>
<%
    LoginForm form = ((JspView<LoginForm>)HttpView.currentView()).getModelBean();
    ActionURL doneURL = AppProps.getInstance().getHomePageActionURL();
    HtmlString errors = formatMissedErrors("form");
%>
<labkey:form method="POST" action="<%=urlFor(ResetPasswordAction.class)%>" className="auth-form">
    <div class="auth-header">Reset Password</div>
    <% if (!errors.isEmpty()) { %>
        <%=errors%>
    <% } %>
    <div class="auth-form-body">
        <p>To reset your password, type in your email address and click the Reset button.</p>
        <input id="email" name="email" type="text" class="input-block" tabindex="1" autocomplete="off" value="<%=h(form.getEmail())%>">
        <div class="auth-item">
            <%= button("Reset").submit(true).name("reset")%>
            <%= button("Cancel").href(urlProvider(LoginUrls.class).getLoginURL(doneURL)) %>
        </div>
    </div>
</labkey:form>