<%
/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.user.UserController.ChangeEmailAction" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<UserController.UserForm> me = (JspView<UserController.UserForm>) HttpView.currentView();
    UserController.UserForm form = me.getModelBean();
    ActionURL cancelURL = new ActionURL(UserController.DetailsAction.class, getContainer()).addParameter("userId", form.getUserId());
    String currentEmail = UserManager.getEmailForId(form.getUserId());
%>
<% if(form.getIsVerifyRedirect()) {%>
<p>We have sent a verification email to <%=h(currentEmail)%>. Please follow the link in the email to continue the email update process.</p>
<% } else if (form.getIsVerified()) { %>
<p>Email change from <%=h(form.getCurrentEmail())%> to <%=h(form.getRequestedEmail())%> was successful. An email has been sent to the old
    address for security purposes.</p>
<% } else if (form.getIsFromVerifiedLink()) { %>
<p>For security purposes, please enter your password.</p>
<form <%=formAction(ChangeEmailAction.class, Method.Post)%>><labkey:csrf/>
<%=formatMissedErrors("form")%>
<div class="auth-form-body">
    <label for="username">Email</label>
    <input id="username" name="username" value="<%=h(form.getUsername())%>" type="text" class="input-block" tabindex="1"%>
    <label for="password">
        Password
        <a href="<%=h(buildURL(LoginController.ResetPasswordAction.class))%>">(forgot password)</a>
    </label>

    <input id="password" name="password" type="password" class="input-block" tabindex="2" autofocus>
    <p><%= button("Submit").submit(true) %>
       <%= button("Cancel").href(cancelURL) %></p>
    <input type="hidden" name="userId" value="<%=form.getUserId()%>">
    <input type="hidden" name="currentEmail" value="<%=h(form.getCurrentEmail())%>">
    <input type="hidden" name="verificationToken" value="<%=h(form.getVerificationToken())%>">
    <input type="hidden" name="isFromVerifiedLink" value="<%=form.getIsFromVerifiedLink()%>">
</div>
</form>
<% } else {%>
<form <%=formAction(ChangeEmailAction.class, Method.Post)%>><labkey:csrf/>
<table><%=formatMissedErrorsInTable("form", 2)%>
    <tr>
        <td>Current Email:</td>
        <td><%=h(currentEmail)%></td>
    </tr>
    <tr>
        <td>New Email:</td>
        <td><input type="text" name="requestedEmail" id="requestedEmail" value="<%=h(form.getRequestedEmail())%>"></td>
    </tr>
    <tr>
        <td>New Email (verify):</td>
        <td><input type="text" name="requestedEmailConfirmation" id="requestedEmailConfirmation" value="<%=h(form.getRequestedEmailConfirmation())%>"></td>
    </tr>
    <tr>
        <td><%= button("Submit").submit(true) %>
            <%= button("Cancel").href(cancelURL) %></td>
    </tr>
</table>
<input type="hidden" name="userId" value="<%=form.getUserId()%>">
</form>
<% } %>