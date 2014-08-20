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
<%@ page import="org.labkey.api.security.LoginUrls"%>
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.LoginForm form = ((JspView<LoginController.LoginForm>)HttpView.currentView()).getModelBean();
    ActionURL doneURL = AppProps.getInstance().getHomePageActionURL();

    String errors = formatMissedErrorsStr("form");
%>
<labkey:form method="POST" action="<%=h(buildURL(LoginController.ResetPasswordAction.class))%>">
    <table><%
        if (errors.length() > 0)
        { %>
        <tr><td colspan=2><%=text(errors)%></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
        }
        %>
        <tr><td colspan=2>To reset your password, type in your email address and click the Submit button.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Email:</td><td><input id="EmailInput" type="text" name="email" value="<%=h(form.getEmail())%>" style="width:200;"></td></tr>
        <tr>
            <td></td>
            <td style="height:50">
            <%= button("Submit").submit(true).attributes("name=\"reset\"")%>
            <%= button("Cancel").href(urlProvider(LoginUrls.class).getLoginURL(doneURL)) %>
            </td>
        </tr>
    </table>
</labkey:form>