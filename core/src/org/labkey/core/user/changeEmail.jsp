<%
/*
 * Copyright (c) 2005-2016 Fred Hutchinson Cancer Research Center
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
    String currentEmail;
    boolean isSiteAdmin = getUser().isSiteAdmin();
    if(!isSiteAdmin)
    {
        currentEmail = getUser().getEmail();
    }
    else
    {
        currentEmail = UserManager.getUser(form.getUserId()).getEmail();
    }
%>
<% if (form.getIsChangeEmailRequest()) {%>
<form <%=formAction(ChangeEmailAction.class, Method.Post)%>><labkey:csrf/>
    <% if (!isSiteAdmin) {%>
        <p>NOTE: You will need to know your account password and have access to your current email address to change your email address!</p>
    <% } %>
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
    <input type="hidden" name="isChangeEmailRequest" value="true">
</form>
<% } else if (form.getIsPasswordPrompt()) { %>
<p>For security purposes, please enter your password.</p>
<form <%=formAction(ChangeEmailAction.class, Method.Post)%>><labkey:csrf/>
    <table><%=formatMissedErrorsInTable("form", 2)%>
        <tr>
            <td>Email:</td>
            <td><%=h(currentEmail)%></td>
        </tr>
        <tr>
            <td><label for="password">Password <a href="<%=h(buildURL(LoginController.ResetPasswordAction.class))%>">(forgot password)</a>:</label></td>
            <td><input id="password" name="password" type="password" tabindex="2" autofocus></td>
        </tr>
        <tr>
            <td><%= button("Submit").submit(true) %>
                <%= button("Cancel").href(cancelURL) %></td>
        </tr>
    </table>
    <input type="hidden" name="userId" value="<%=form.getUserId()%>">
    <input type="hidden" name="requestedEmail" value="<%=h(form.getRequestedEmail())%>">
    <input type="hidden" name="verificationToken" value="<%=h(form.getVerificationToken())%>">
    <input type="hidden" name="isPasswordPrompt" value="true">
</form>
<% } else if (form.getIsVerifyRedirect()) {%>
<p>We have sent a verification email to <%=h(currentEmail)%>. Please follow the link in the email to continue the email update process.
    If you do not see the email in your inbox, please check your spam folder, and add the message sender to your address book if the email has been marked as spam.</p>
<% } else if (form.getIsFromVerifiedLink())  { // only still set here when there are errors in verification link processing %>
<p><%=formatMissedErrors("form")%></p>
<% } else if (form.getIsVerified()) { %>
<p>Email change from <%=h(form.getOldEmail())%> to <%=h(form.getRequestedEmail())%> was successful. An email has been sent to the old
    address for security purposes.</p>
<% } %>