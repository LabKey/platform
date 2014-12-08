<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.LoginController.LoginBean" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<LoginBean> me = (HttpView<LoginBean>) HttpView.currentView();
    LoginBean bean = me.getModelBean();

    User user = getUser();
    URLHelper returnURL = bean.form.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL());
    LookAndFeelProperties props = LookAndFeelProperties.getInstance(getContainer());
    boolean agreeOnly = bean.agreeOnly;
    String formURL = agreeOnly ? buildURL(LoginController.AgreeToTermsAction.class) : buildURL(LoginController.LoginAction.class);
    String email = bean.form.getEmail();
    boolean focusEmail = StringUtils.isBlank(email);
%>
<style type="text/css">
    .labkey-error {
        margin-top: 15px;
    }
</style>
<div class="auth-form">
    <% if (!user.isGuest()) { %>
    <div class="auth-item">You are currently logging in as <%=h(user.getName())%>.</div>
    <% } %>
    <% if (!agreeOnly) { %>
    <div class="auth-header">Sign In</div>
    <% } %>
    <labkey:errors />
    <form name="login" method="POST" action="<%=h(formURL)%>" accept-charset="UTF-8"><labkey:csrf/>
        <div class="auth-form-body">
            <% if (!agreeOnly) { %>
            <label for="email">Email</label>
            <input id="email" name="email" value="<%=h(email)%>" type="text" class="input-block" tabindex="1" <%=h(focusEmail ? "autofocus" : "")%>>
            <label for="password">
                Password
                <a href="<%=h(buildURL(LoginController.ResetPasswordAction.class))%>">(forgot password)</a>
            </label>
            <input id="password" name="password" type="password" class="input-block" tabindex="2" <%=h(!focusEmail ? "autofocus" : "")%>>
            <input type=checkbox name="remember" id="remember" <%=checked(bean.remember)%>>
            <label for="remember">Remember my email address</label>
            <% } // !agreeOnly %>

            <% if (null != bean.termsOfUseHTML) { %>
            <div class="auth-header auth-item">Terms of Use</div>
            <div class="toucontent auth-item" ><%=text(bean.termsOfUseHTML)%></div>
            <div class="auth-item">
                <input type="checkbox" name="approvedTermsOfUse" id="approvedTermsOfUse" class="auth-item" <%=checked(bean.termsOfUseChecked)%>>
                <label for="approvedTermsOfUse">I agree to these terms</label>
            </div>
            <% } %>

            <div class="auth-item">
                <%= button((agreeOnly ? "Agree" : "Sign In")).submit(true) %>
                <% if (!StringUtils.isBlank(props.getSupportEmail()) && !agreeOnly) { %>
                or <a href="mailto:<%= h(props.getSupportEmail()) %>?subject=Account request<%=h(StringUtils.isBlank(props.getShortName()) ? "" : " for " + props.getShortName())%>">Request an account</a>
                <% } %>
            </div>
        </div>

        <%=generateReturnUrlFormField(returnURL)%>

        <% if (bean.form.getSkipProfile()) { %>
        <input type="hidden" name="skipProfile" value="1">
        <% } %>
        <input type="hidden" id="urlhash" name="urlhash">
    </form>
    <%-- this should be controlled by the authentication provider --%>
    <%
        if (AppProps.getInstance().isExperimentalFeatureEnabled("experimental-openid-google"))
        {
            boolean hasGoogle = false;
            for (AuthenticationProvider ap : AuthenticationManager.getActiveProviders())
                if (ap.getName().equals("Google"))
                    hasGoogle = true;
            if (hasGoogle)
            {
                ActionURL toGoogle = new ActionURL("openid","redirect",ContainerManager.getRoot()).addParameter("provider","Google").addParameter("returnUrl",returnURL.getLocalURIString());
    %><a href="<%=h(toGoogle)%>"><img class="auth-item" src="<%=getContextPath()%>/authentication/openid_google.png"></a><%
        }
    }
%>
</div>
<script type="text/javascript">
    <% // Provide support for persisting the url hash through a login redirect %>
    (function() { if (window && window.location && window.location.hash) { var h = document.getElementById('urlhash'); if (h) { h.value = window.location.hash; } } })();

    <%-- Issue 22094: Clear password on login page after session timeout has been exceeded --%>
    (function() {
        var timeout = <%= request.getSession(false) == null ? 30 * 60 * 1000 : request.getSession(false).getMaxInactiveInterval() * 1000 %>;
        if (timeout > 0) {
            var passwordField = document.getElementById('password');
            <%-- The function to do the clearing --%>
            var clearPasswordField = function ()
            {
                passwordField.value = '';
            };
            <%-- Start the clock when the page loads --%>
            var timer = setInterval(clearPasswordField, timeout);
            <%-- Any time the value changes reset the clock --%>
            var changeListener = function ()
            {
                if (timer)
                {
                    clearInterval(timer);
                }
                timer = setInterval(clearPasswordField, timeout);
            };
            <%-- Wire up the listener for changes to the password field --%>
            passwordField.onchange = changeListener;
            passwordField.onkeypress = changeListener;
        }
    })();
</script>
