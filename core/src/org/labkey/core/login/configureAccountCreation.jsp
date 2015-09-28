<%
    /*
     * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>


<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    boolean isExternalProviderEnabled = AuthenticationManager.isExternalProviderEnabled();
    LoginUrls urls = urlProvider(LoginUrls.class);
%>
<table>

    <tr>
        <td>&nbsp;&nbsp;</td>
        <td>Self sign-up</td>
        <% if (AuthenticationManager.isRegistrationEnabled()) { %>
        <td><%=PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY))%></td>
        <% } else { %>
        <td><%=PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY))%></td>
        <% } %>
        <td>Users are able to register for accounts when using database authentication</td>
    </tr>
    <tr>
        <td>&nbsp;&nbsp;</td>
        <td>Auto-create authenticated users</td>
        <% if (AuthenticationManager.isAutoCreateAccountsEnabled()) { %>
        <td><%=isExternalProviderEnabled ? PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)) : "<span class=\"labkey-disabled-text-link\">Disable</a>"%></td>
        <% } else { %>
        <td><%=isExternalProviderEnabled ? PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)) : "<span class=\"labkey-disabled-text-link\">Enable</a>" %></td>
        <% } %>
        <td>Accounts are created automatically for users authenticated via LDAP, SSO, etc.</td>
    </tr>

    <tr><td colspan="2">&nbsp;&nbsp;</td></tr>
    <tr><td colspan="2"><%=PageFlowUtil.textLink("Return to Authentication Management", urls.getConfigureURL())%></td></tr>
</table>