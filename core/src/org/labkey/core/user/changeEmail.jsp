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
<%@ page import="org.labkey.api.security.permissions.UserManagementPermission"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.core.user.UserController.ChangeEmailAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String errors = formatMissedErrorsStr("form");
    if (errors.length() > 0)
    {
        %><%=text(errors)%><%
        return;
    }

    JspView<UserController.UserForm> me = (JspView<UserController.UserForm>) HttpView.currentView();
    UserController.UserForm form = me.getModelBean();
    ActionURL cancelURL = new ActionURL(UserController.DetailsAction.class, getContainer()).addParameter("userId", form.getUserId());
    boolean isUserManager = getUser().hasRootPermission(UserManagementPermission.class);
    String currentEmail = form.getUser().getEmail();

    if (form.getIsChangeEmailRequest())
    {
%>
<labkey:form action="<%=buildURL(ChangeEmailAction.class)%>" method="POST" layout="horizontal">
<%
        if (!isUserManager) { %>
            <p>NOTE: You will need to know your account password and have access to your new email address to change your email address!</p>
        <% } %>
    <labkey:input type="text" size="50" name="currentEmail" id="currentEmail" label="Current Email" value="<%=currentEmail%>" isReadOnly="true"/>
    <labkey:input type="text" size="50" name="requestedEmail" id="requestedEmail" label="New Email" value="<%=form.getRequestedEmail()%>" />
    <labkey:input type="text" size="50" name="requestedEmailConfirmation" id="requestedEmailConfirmation" label="Retype New Email" value="<%=form.getRequestedEmailConfirmation()%>" />
    <labkey:input type="hidden" name="userId" value="<%=form.getUserId()%>"/>
    <labkey:input type="hidden" name="isChangeEmailRequest" value="true"/>
    <labkey:csrf/>
    <%= button("Submit").submit(true) %>
    <%= button("Cancel").href(cancelURL) %>
</labkey:form>
<%
    }
    else if (form.getIsPasswordPrompt())
    {
        String resetPasswordLink = "<a href=" + buildURL(LoginController.ResetPasswordAction.class) + ">forgot password</a>";
%>
        <p>For security purposes, please enter your password.</p>
        <labkey:form action="<%=buildURL(ChangeEmailAction.class)%>" method="POST" layout="horizontal">
            <labkey:input size="50" type="text" name="currentEmail" id="currentEmail" label="Email" value="<%=currentEmail%>" isReadOnly="true"/>
            <labkey:input size="50" id="password" name="password" type="password" label="Password" contextContent="<%=resetPasswordLink%>"/>
            <labkey:input type="hidden" name="userId" value="<%=form.getUserId()%>"/>
            <labkey:input type="hidden" name="requestedEmail" value="<%=form.getRequestedEmail()%>"/>
            <labkey:input type="hidden" name="verificationToken" value="<%=form.getVerificationToken()%>"/>
            <labkey:input type="hidden" name="isPasswordPrompt" value="true"/>
            <labkey:csrf/>
            <%= button("Submit").submit(true) %>
            <%= button("Cancel").href(cancelURL) %>
        </labkey:form>
<%
    }
    else if (form.getIsVerifyRedirect())
    {
%>
        <p>We have sent a verification email to <%=h(form.getRequestedEmail())%>. Please follow the link in the email to continue the email update process.</p>
        <p>If you do not see the email in your inbox, please check your spam folder, and add the message sender to your address book if the email has been marked as spam.</p>
        <%= button("Done").href(cancelURL) %>
<%
    }
    else if (form.getIsVerified())
    {
%>
        <p>Email change from <%=h(form.getOldEmail())%> to <%=h(form.getRequestedEmail())%> was successful. An email has been sent to the old address for security purposes.</p>
        <%= button("Done").href(cancelURL) %>
<%
    }
%>