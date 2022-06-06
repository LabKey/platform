<%
/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.collections.NamedObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.login.DbLoginManager" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.LoginController.SetPasswordBean" %>
<%@ page import="org.labkey.core.portal.ProjectController.HomeAction" %>
<%@ page import="org.labkey.core.portal.ProjectController.StartAction" %>
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
    SetPasswordBean bean = ((JspView<SetPasswordBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrorsStr("form");
%>
<style>
    .labkey-button {
        width: 100px;
    }
</style>
<labkey:form method="POST" id="setPasswordForm" action="<%=urlFor(bean.action)%>" layout="horizontal" className="auth-form">
    <% if (bean.title != null) { %>
        <div class="auth-header"><%=h(bean.title)%></div>
    <% } %>

    <% if (errors.length() > 0) { %>
        <%=text(errors)%>
    <% } %>

    <div class="auth-form-body">
    <% if (!bean.unrecoverableError) { %>
        <p><%=h(bean.message)%></p>

        <% for (NamedObject input : bean.nonPasswordInputs) { %>
            <label for="<%=h(input.getObject().toString())%>">
                <%=h(input.getName())%>
            </label>
            <input
                type="text"
                id="<%=h(input.getObject().toString())%>"
                name="<%=h(input.getObject().toString())%>"
                value="<%=h(input.getDefaultValue())%>"
                class="input-block"
            />
        <% } %>

        <% for (NamedObject input : bean.passwordInputs) {
            HtmlString contextContent = LoginController.PASSWORD1_TEXT_FIELD_NAME.equals(input.getObject())
                    ? DbLoginManager.getPasswordRule().getSummaryRuleHTML() : null;
        %>
            <p>
                <%=h(contextContent)%>
            </p>
            <label for="<%=h(input.getObject().toString())%>">
                <%=h(input.getName())%>
            </label>
            <input
                type="password"
                id="<%=h(input.getObject().toString())%>"
                name="<%=h(input.getObject().toString())%>"
                class="input-block"
                autocomplete="off"
            />
        <% } %>

        <div>
        <% if (null != bean.email) { %>
            <labkey:input type="hidden" name="email" value="<%=bean.email%>"/>
        <% }

        if (null != bean.form.getVerification()) { %>
            <labkey:input type="hidden" name="verification" value="<%=bean.form.getVerification()%>"/>
        <% }

        if (null != bean.form.getMessage()) { %>
            <labkey:input type="hidden" name="message" value="<%=bean.form.getMessage()%>"/>
        <% }

        if (bean.form.getSkipProfile()) { %>
            <labkey:input type="hidden" name="skipProfile" value="1"/>
        <% }

        if (null != bean.form.getReturnURLHelper()) { %>
            <%=generateReturnUrlFormField(bean.form)%>
        <% } %>
        </div>

        <div class="auth-item">
            <%= button(bean.buttonText).submit(true).name("set") %>
            <%=unsafe(bean.cancellable ? button("Cancel").href(bean.form.getReturnURLHelper() != null ? bean.form.getReturnURLHelper() : new ActionURL(HomeAction.class, getContainer())).toString() : "")%>
        </div>
    <% }
       else
       {
           Container c = getContainer().isRoot() ? ContainerManager.getHomeContainer() : getContainer();
           URLHelper homeURL = bean.form.getReturnURLHelper() != null ? bean.form.getReturnURLHelper() : new ActionURL(StartAction.class, c);
    %>
            <div class="auth-item">
                <%= unsafe(button("Home").href(homeURL).toString()) %>
            </div>
    <% } %>
    </div>
</labkey:form>