<%
/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.MenuButton" %>
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.security.AuthenticationConfiguration" %>
<%@ page import="org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration" %>
<%@ page import="org.labkey.api.security.AuthenticationConfigurationCache" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider" %>
<%@ page import="org.labkey.api.security.AuthenticationProvider.PrimaryAuthenticationProvider" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Comparator" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Collection<PrimaryAuthenticationProvider> primary = AuthenticationManager.getAllPrimaryProviders();
    boolean isExternalProviderEnabled = AuthenticationManager.isExternalConfigurationEnabled();
    boolean canEdit = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

    LoginUrls urls = urlProvider(LoginUrls.class);
%>
<style>
    .labkey-enabled-option
    {
        color: black;
    }
</style>

<labkey:panel title="Primary authentication configurations">
    <%
        if (canEdit)
        {
            MenuButton btn = new MenuButton("Add...");
            primary.stream()
                .filter(ap->null != ap.getConfigurationLink())
                .filter(ap->!ap.isPermanent())
                .sorted(Comparator.comparing(AuthenticationProvider::getName))
                .forEach(ap->btn.addMenuItem(ap.getName() + " - " + ap.getDescription(), ap.getConfigurationLink()));

            if (btn.getNavTree().hasChildren())
                btn.render(new RenderContext(getViewContext()), out);
        }
        appendConfigurations(out, PrimaryAuthenticationConfiguration.class, canEdit, false);
    %>
</labkey:panel>

<%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%>

<%!
    private <AC extends AuthenticationConfiguration<?>> void appendConfigurations(JspWriter out, Class<AC> clazz, boolean canEdit, boolean alwaysIncludeDelete) throws IOException
    {
        Collection<AC> configurations = AuthenticationConfigurationCache.getConfigurations(clazz);
        boolean includeDeleteColumn = canEdit && configurations.size() > 1;  // Don't show "delete" column if database auth is the only configuration
        out.print("<table class=\"labkey-data-region-legacy labkey-show-borders\">");

        out.print("<tr>\n" +
                "    <td class=\"labkey-column-header\">Description</td>\n" +
                "    <td class=\"labkey-column-header\">Provider</td>\n" +
                "    <td class=\"labkey-column-header\">Enabled</td>\n" +
                "    <td " + (includeDeleteColumn ? "colspan=\"2\" " : "") + "class=\"labkey-column-header\">Configuration</td>\n" +
                "</tr>");

        int rowIndex = 0;
        for (AC configuration : configurations)
        {
            ActionURL url = configuration.getAuthenticationProvider().getConfigurationLink(configuration.getRowId());
            if (null == url)
                continue;

            out.print("<tr class=\"" + (rowIndex % 2 == 1 ? "labkey-row" : "labkey-alternate-row") + "\"><td>");
            out.print(h(configuration.getDescription()));
            out.print("</td>");

            out.print("<td>");
            out.print(h(configuration.getAuthenticationProvider().getName()));
            out.print("</td>");

            out.print("<td style=\"text-align:center;\">");
            out.print("<span class=\"" + (configuration.isEnabled() ? "fa fa-check-square" : "fa fa-square-o") + "\"></span>");
            out.print("</td>");

            out.print("<td>");
            out.print(link(canEdit ? "edit" : "view", url));
            out.print("</td>");

            if (includeDeleteColumn)
            {
                out.print("<td>");
                if (configuration.getAuthenticationProvider().isPermanent())
                    out.print("&nbsp;");
                else
                    out.print(link("delete", new ActionURL(LoginController.OldDeleteConfigurationAction.class, ContainerManager.getRoot()).addParameter("configuration", configuration.getRowId()))
                        .usePost("Are you sure you want to delete the \"" + configuration.getDescription() + "\" authentication configuration?"));
                out.print("</td>");
            }

            out.print("</tr>\n");

            rowIndex++;
        }

        out.print("</table>");
    }
%>
