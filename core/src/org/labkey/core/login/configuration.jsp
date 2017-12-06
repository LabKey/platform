<%
/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.SecondaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Collection" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Collection<PrimaryAuthenticationProvider> primary = AuthenticationManager.getAllPrimaryProviders();
    Collection<SecondaryAuthenticationProvider> secondary = AuthenticationManager.getAllSecondaryProviders();
    boolean isExternalProviderEnabled = AuthenticationManager.isExternalProviderEnabled();
    boolean canEdit = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

    LoginUrls urls = urlProvider(LoginUrls.class);
%>
<style>
    .labkey-enabled-option
    {
        color: black;
    }
</style>

<labkey:panel title="Installed primary authentication providers">
    <% appendProviders(out, primary, urls, canEdit); %>
</labkey:panel>

<%
    if (!secondary.isEmpty())
    {
%>
        <labkey:panel title="Installed secondary authentication providers">
            <% appendProviders(out, secondary, urls, canEdit); %>
        </labkey:panel>
<%
    }
%>

<labkey:panel title="Other options">
<table class="labkey-data-region-legacy labkey-show-borders">
    <tr>
        <td class="labkey-column-header">Name</td>
        <td class="labkey-column-header">Status</td>
        <td class="labkey-column-header">Description</td>
    </tr>
    <tr class="labkey-alternate-row">
        <td>Self sign-up</td>
        <td>
        <% if (AuthenticationManager.isRegistrationEnabled())
        {
            if (canEdit)
            {
                out.write(PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY)));
            }
            else
            {
        %>
            <div class="labkey-disabled-text-link labkey-enabled-option">Enabled</div>
        <%
            }
        }
        else
        {
                if (canEdit)
                {
                    out.write(PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.SELF_REGISTRATION_KEY)));
                }
                else
                {
        %>
            <div class="labkey-disabled-text-link">Disabled</div>
        <%
                }
        }
        %>
        </td>
        <td>Users are able to register for accounts when using database authentication. Use caution when enabling this if you have enabled sending email to non-users.</td>
    </tr>
    <tr class="labkey-row">
        <td>Auto-create authenticated users</td>
        <td>
            <% if (!isExternalProviderEnabled)
            {
                out.write("&nbsp;");
            }
            else if (AuthenticationManager.isAutoCreateAccountsEnabled())
            {
                if (canEdit)
                {
                    out.write(PageFlowUtil.textLink("Disable", urls.getDisableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)));
                }
                else
                {
            %>
            <div class="labkey-disabled-text-link labkey-enabled-option">Enabled</div>
            <%
                }
            }
            else
            {
                if (canEdit)
                {
                    out.write(PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.AUTO_CREATE_ACCOUNTS_KEY)));
                }
                else
                {
            %>
            <div class="labkey-disabled-text-link">Disabled</div>
            <%
                }
            }
            %>
        </td>
        <td>Accounts are created automatically when new users authenticate via LDAP or SSO.<%=h(isExternalProviderEnabled ? "" : " This option is available only when an LDAP or SSO provider is enabled.")%></td>
    </tr>
    <tr class="labkey-alternate-row">
        <td>Self-service email changes</td>
        <td>
            <%
                if (AuthenticationManager.isSelfServiceEmailChangesEnabled())
                {
                    if (canEdit)
                    {
                        out.write(PageFlowUtil.textLink("Disable",  urls.getDisableConfigParameterURL(AuthenticationManager.SELF_SERVICE_EMAIL_CHANGES_KEY)));
                    }
                    else
                    {
            %>
            <div class="labkey-disabled-text-link labkey-enabled-option">Enabled</div>
            <%
                }
            }
            else
            {
                if (canEdit)
                {
                    out.write(PageFlowUtil.textLink("Enable", urls.getEnableConfigParameterURL(AuthenticationManager.SELF_SERVICE_EMAIL_CHANGES_KEY)));
                }
                else
                {
            %>
            <div class="labkey-disabled-text-link">Disabled</div>
            <%
                    }
                }
            %>
        </td>
        <td>Users can change their own email address if their password is managed by LabKey Server.</td>
    </tr>
</table>
</labkey:panel>

<%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>

<%!
    private static void appendProviders(JspWriter out, Collection<? extends AuthenticationProvider> providers, LoginUrls urls, Boolean canEdit) throws IOException
    {
        out.write("<table class=\"labkey-data-region-legacy labkey-show-borders\">");

        out.write("<tr>\n" +
                "    <td class=\"labkey-column-header\">Name</td>\n" +
                "    <td class=\"labkey-column-header\">Status</td>\n" +
                "    <td class=\"labkey-column-header\">Configuration</td>\n" +
                "    <td class=\"labkey-column-header\">Logos</td>\n" +
                "    <td class=\"labkey-column-header\">Description</td>\n" +
                "</tr>");

        int rowIndex = 0;
        for (AuthenticationProvider authProvider : providers)
        {
            out.write("<tr class=\"" + (rowIndex % 2 == 1 ? "labkey-row" : "labkey-alternate-row") + "\"><td>");
            out.write(PageFlowUtil.filter(authProvider.getName()));
            out.write("</td>");

            if (AuthenticationManager.isAcceptOnlyFicamProviders() && !authProvider.isFicamApproved())
                out.write("<td style='opacity:0.5;'>");
            else
                out.write("<td>");

            if (authProvider.isPermanent())
            {
                out.write("&nbsp;");
            }
            else
            {
                if (AuthenticationManager.isActive(authProvider))
                {
                    if (canEdit)
                        out.write(PageFlowUtil.textLink("disable", urls.getDisableProviderURL(authProvider)));
                    else
                        out.write("<div class=\"labkey-disabled-text-link labkey-enabled-option\">Enabled</div>");
                }
                else if (AuthenticationManager.isAcceptOnlyFicamProviders() && !authProvider.isFicamApproved())
                {

                      out.write("Not Available");
                      out.write(PageFlowUtil.helpPopup("Not Available",
                              authProvider.getName() + " cannot be enabled because it is not FICAM approved. Please go to the <a target=\"_blank\" href=\"https://www.labkey.org/home/Documentation/wiki-page.view?name=complianceSettings#Accounts\">Compliance Settings</a> page to disable this control.",
                              true, 500));
                }
                else
                {
                    if (canEdit)
                        out.write(PageFlowUtil.textLink("enable", urls.getEnableProviderURL(authProvider)));
                    else
                        out.write("<div class=\"labkey-disabled-text-link\">Disabled</div>");
                }
            }
            out.write("</td>");

            ActionURL url = authProvider.getConfigurationLink();

            out.write("<td>");
            if (null == url || !canEdit)
                out.write("&nbsp;");
            else
                out.write(PageFlowUtil.textLink("configure", url));
            out.write("</td>");

            out.write("<td>");
            if (canEdit && authProvider instanceof SSOAuthenticationProvider)
            {
                ActionURL pickLogoURL = urls.getPickLogosURL(authProvider);
                out.write(PageFlowUtil.textLink("pick logos", pickLogoURL));
            };
            out.write("</td>");

            out.write("<td>");
            out.write(authProvider.getDescription());
            out.write("</td>");

            out.write("</tr>\n");

            rowIndex++;
        }

        out.write("</table>");
    }
%>
