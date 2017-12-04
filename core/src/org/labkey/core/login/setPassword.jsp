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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.DbLoginManager" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.portal.ProjectController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.SetPasswordBean bean = ((JspView<LoginController.SetPasswordBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrorsStr("form");
%>
<labkey:form method="POST" id="setPasswordForm" action="<%=h(buildURL(bean.action))%>" layout="horizontal">
<%
    if (errors.length() > 0)
    {
        %><%=text(errors)%><%
    }

    if (!bean.unrecoverableError)
    {
        if (null != bean.email) { %>
            <div class="lk-body-title"><h3><%=h(bean.email)%></h3></div>
        <% } %>
        <p><%=h(bean.message)%></p>
    <%

        for (NamedObject input : bean.nonPasswordInputs)
        {
        %>
                <labkey:input
                    type="text"
                    id="<%=h(input.getObject().toString())%>"
                    name="<%=h(input.getObject().toString())%>"
                    label="<%=h(input.getName())%>"
                    value="<%=h(input.getDefaultValue())%>"
                />
        <%
        }

        for (NamedObject input : bean.passwordInputs)
        {
            String contextContent = LoginController.PASSWORD1_TEXT_FIELD_NAME.equals(input.getObject())
                    ? DbLoginManager.getPasswordRule().getSummaryRuleHTML() : "";
        %>
                <labkey:input
                    type="password"
                    id="<%=h(input.getObject().toString())%>"
                    name="<%=h(input.getObject().toString())%>"
                    label="<%=h(input.getName())%>"
                    contextContent="<%=text(contextContent)%>"
                />
        <%
        }
        %>
        <div>
        <%
        if (null != bean.email)
        { %>
            <labkey:input type="hidden" name="email" value="<%=h(bean.email)%>"/>
        <% }

        if (null != bean.form.getVerification())
        { %>
            <labkey:input type="hidden" name="verification" value="<%=h(bean.form.getVerification())%>"/>
        <% }

        if (null != bean.form.getMessage())
        { %>
            <labkey:input type="hidden" name="message" value="<%=h(bean.form.getMessage())%>"/>
        <% }

        if (bean.form.getSkipProfile())
        { %>
            <labkey:input type="hidden" name="skipProfile" value="1"/>
        <% }

        if (null != bean.form.getReturnURLHelper())
        { %>
            <%=generateReturnUrlFormField(bean.form)%>
        <% } %>
        </div>
        <div style="padding-top: 1em;">
            <%= button(bean.buttonText).submit(true).attributes("name=\"set\"") %>
            <%=text(bean.cancellable ? button("Cancel").href(bean.form.getReturnURLHelper() != null ? bean.form.getReturnURLHelper() : new ActionURL(ProjectController.HomeAction.class, getContainer())).toString() : "")%>
        </div>
    <% } %>
</labkey:form>