<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.LoginForm form = ((JspView<LoginController.LoginForm>)HttpView.currentView()).getModelBean();
    ActionURL doneURL = AppProps.getInstance().getHomePageActionURL();

    String errors = formatMissedErrorsStr("form");
    if (errors.length() > 0)
    {
        %><%=text(errors)%><%
    }
%>
<p>To reset your password, type in your email address and click the Submit button.</p>
<labkey:form method="POST" action="<%=h(buildURL(LoginController.ResetPasswordAction.class))%>" layout="horizontal">
    <labkey:input id="EmailInput" type="text" name="email" value="<%=h(form.getEmail())%>"/>
    <%= button("Submit").submit(true).attributes("name=\"reset\"")%>
    <%= button("Cancel").href(urlProvider(LoginUrls.class).getLoginURL(doneURL)) %>
</labkey:form>