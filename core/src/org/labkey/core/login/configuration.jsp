<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    List<AuthenticationProvider> primary = AuthenticationManager.getAllPrimaryProviders();
    List<SecondaryAuthenticationProvider> secondary = AuthenticationManager.getAllSecondaryProviders();

    LoginUrls urls = urlProvider(LoginUrls.class);
%>

<table>
    <tr><td colspan="5">Most web browsers save and automatically enter passwords on login pages, but, for security reasons, administrators may want to disable this ability on the LabKey Server login page.<br><br></td></tr>
    <tr><td>&nbsp;&nbsp;</td>

    <labkey:form action="<%=h(buildURL(LoginController.SaveSettingsAction.class))%>" method="post" name="settings">
        <td>Allow browser caching of credentials</td><td><input type="checkbox" name="allowBrowserCaching" onclick="document.forms.settings.submit();" <%=checked(AuthenticationManager.isBrowserCachingAllowed())%>/></td>
    </labkey:form>

    </tr>
    <tr><td colspan="5">&nbsp;</td></tr>

    <tr><td colspan="5">These are the installed primary authentication providers:<br><br></td></tr>

    <% appendProviders(out, primary, urls); %>

<%
    if (!secondary.isEmpty())
    {
%>
        <tr><td colspan="5">&nbsp;</td></tr>
        <tr><td colspan="5">These are the installed secondary authentication providers:<br><br></td></tr>

        <% appendProviders(out, secondary, urls); %>
<%
    }
%>
    <tr><td colspan="5">&nbsp;</td></tr>
    <tr><td colspan="5">
    <%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>
    </td></tr>
</table>

<%!
    private static void appendProviders(JspWriter out, List<? extends AuthenticationProvider> providers, LoginUrls urls) throws IOException
    {
        for (AuthenticationProvider authProvider : providers)
        {
            out.write("<tr><td>&nbsp;&nbsp;</td><td>");
            out.write(PageFlowUtil.filter(authProvider.getName()));
            out.write("</td>");

            if (authProvider.isPermanent())
            {
                out.write("<td>&nbsp;</td>");
            }
            else
            {
                if (AuthenticationManager.isActive(authProvider))
                {
                    out.write("<td>");
                    out.write(PageFlowUtil.textLink("disable", urls.getDisableProviderURL(authProvider).getEncodedLocalURIString()));
                    out.write("</td>");
                }
                else
                {
                    out.write("<td>");
                    out.write(PageFlowUtil.textLink("enable", urls.getEnableProviderURL(authProvider).getEncodedLocalURIString()));
                    out.write("</td>");
                }
            }

            ActionURL url = authProvider.getConfigurationLink();

            if (null == url)
            {
                out.write("<td>&nbsp;</td>");
            }
            else
            {
                out.write("<td>");
                out.write(PageFlowUtil.textLink("configure", url.getEncodedLocalURIString()));
                out.write("</td>");
            }

            out.write("<td>");
            out.write(authProvider.getDescription());
            out.write("</td>");

            out.write("</tr>\n");
        }
    }
%>
