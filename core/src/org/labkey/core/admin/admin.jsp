<%
/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either extpress or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.apache.commons.lang3.ObjectUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.admin.AdminBean" %>
<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="org.labkey.api.module.DefaultModule" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.settings.AdminConsole" %>
<%@ page import="org.labkey.api.settings.AdminConsole.AdminLink" %>
<%@ page import="org.labkey.api.settings.AdminConsole.SettingsLinkType" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.core.admin.AdminController"%>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    HttpView<AdminBean> me = (HttpView<AdminBean>) HttpView.currentView();
    AdminBean bean = me.getModelBean();
    boolean devMode = AppProps.getInstance().isDevMode();

    String location = null;
    try
    {
        Class cls = CoreSchema.getInstance().getSchema().getScope().getDelegateClass();
        location = cls.getProtectionDomain().getCodeSource().getLocation().toString();
    }
    catch (Exception ignored)
    {}

    int row = 0;
%>
<style type="text/css">
    body { overflow-y: scroll; }
    .lk-admin-section { display: none; }
    .header-title { margin-bottom: 5px; }
    .header-link { margin: 0 5px 20px 0; }
</style>
<div class="row">
    <div class="col-sm-12 col-md-3">
        <div id="lk-admin-nav" class="list-group">
            <a href="#links" class="list-group-item">Settings</a>
            <a href="#info" class="list-group-item">Server Information</a>
            <a href="#modules" class="list-group-item">Module Information</a>
            <a href="#users" class="list-group-item">Active Users</a>
        </div>
    </div>
    <div class="col-sm-12 col-md-9">
        <labkey:panel id="info" className="lk-admin-section">
            <h3 class="header-title labkey-page-section-header">LabKey Server <%=h(ObjectUtils.defaultIfNull(bean.releaseVersion, "Information"))%></h3>
            <% for (NavTree link : bean.getLinks(getViewContext())) { %>
            <div class="header-link">
                <a href="<%=h(link.getHref())%>"><%=h(link.getText())%></a>
            </div>
            <% } %>
            <h4>Core Database Configuration</h4>
            <table class="labkey-data-region-legacy labkey-show-borders">
                <tr><td class="labkey-column-header">Property</td><td class="labkey-column-header">Value</td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Database Server URL</td><td id="databaseServerURL"><%=h(bean.scope.getURL())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Database Product Name</td><td id="databaseProductName"><%=h(bean.scope.getDatabaseProductName())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Database Product Version</td><td id="databaseProductVersion"><%=h(bean.scope.getDatabaseProductVersion())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>JDBC Driver Name</td><td id="databaseDriverName"><%=h(bean.scope.getDriverName())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>JDBC Driver Version</td><td id="databaseDriverVersion"><%=h(bean.scope.getDriverVersion())%></td></tr><%
                if (null != location)
                { %>
                <tr class="<%=getShadeRowClass(row++)%>"><td>JDBC Driver Location</td><td id="databaseDriverLocation"><%=h(location)%></td></tr><%
                } %>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Connection Pool Max Size</td><td id="connectionPoolSize"><%=h(bean.scope.getDataSourceProperties().getMaxTotal())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Connection Pool Active</td><td id="connectionPoolActive"><%=h(bean.scope.getDataSourceProperties().getNumActive())%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Connection Pool Idle</td><td id="connectionPoolIdle"><%=h(bean.scope.getDataSourceProperties().getNumIdle())%></td></tr>
            </table>
            <br/>
<%
    row = 0;
%>
            <h4>Runtime Information</h4>
            <table class="labkey-data-region-legacy labkey-show-borders">
                <tr><td class="labkey-column-header">Property</td><td class="labkey-column-header">Value</td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Mode</td><td><%=h(bean.mode)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Asserts</td><td><%=h(bean.asserts)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Servlet Container</td><td><%=h(bean.servletContainer)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Java Runtime Vendor</td><td><%=h(bean.javaVendor)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Java Runtime Name</td><td><%=h(bean.javaRuntimeName)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Java Runtime Version</td><td><%=h(bean.javaVersion)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Java Home</td><td><%=h(bean.javaHome)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Username</td><td><%=h(bean.userName)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>User Home Dir</td><td><%=h(bean.userHomeDir)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Webapp Dir</td><td><%=h(bean.webappDir)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>OS</td><td><%=h(bean.osName)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Working Dir</td><td><%=h(bean.workingDir)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Server GUID</td><td><%=h(bean.serverGuid)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Server Session GUID</td><td><%=h(bean.serverSessionGuid)%></td></tr>
                <tr class="<%=getShadeRowClass(row++)%>"><td>Server Time</td><td><%=formatDateTime(new Date())%></td></tr>
            </table>
        </labkey:panel>
        <labkey:panel id="links" className="lk-admin-section">
            <h3 class="labkey-page-section-header">Settings</h3>
            <%
                for (SettingsLinkType type : SettingsLinkType.values())
                {
                    Collection<AdminLink> links = AdminConsole.getLinks(type, getUser());

                    if (!links.isEmpty())
                    { %>
            <div style="display: inline-block; vertical-align: top; padding-right: 25px;">
                <h4><%=h(type.getCaption())%></h4><%

                for (AdminLink link : links)
                { %>
                <div><%=link(link.getText(), link.getUrl())%></div><%
                }%>
            </div><%
                }
            } %>
        </labkey:panel>
        <labkey:panel id="modules" className="lk-admin-section">
            <h3 class="labkey-page-section-header">Module Information</h3>
            <%=link("Module Details", AdminController.ModulesAction.class)%>
            <br/><br/>
            <table><%

                for (Module module : bean.modules)
                {
                    String guid = GUID.makeGUID();
            %>
                <tr class="labkey-header">
                    <td valign="middle" width="15">
                        <a id="<%= h(guid) %>" onclick="return LABKEY.Utils.toggleLink(this, false);">
                            <img src="<%=getWebappURL("_images/plus.gif")%>">
                        </a>
                    </td>
                    <td style="padding: 3px;">
                    <span onclick="return LABKEY.Utils.toggleLink(document.getElementById('<%= h(guid) %>'), false);">
                        <%=h(module.getName())%>
                    </span>
                        <span style="color: #333;">
                        <%=h(module.getReleaseVersion())%> <%=h(StringUtils.isEmpty(module.getLabel()) ? "" : "- " + module.getLabel())%>
                    </span>
                    </td>
                </tr>
                <tr style="display:none">
                    <td></td>
                    <td style="padding-bottom: 10px;"><%
                        if (!StringUtils.isEmpty(module.getDescription()))
                        {
                    %><p><%=h(module.getDescription())%></p><%
                        } %>
                        <table class="labkey-data-region-legacy labkey-show-borders">
                            <tr><td class="labkey-column-header">Property</td><td class="labkey-column-header">Value</td></tr><%
                            boolean sourcePathMatched = module instanceof DefaultModule && ((DefaultModule)module).isSourcePathMatched();
                            boolean enlistmentIdMatched = module instanceof DefaultModule && ((DefaultModule)module).isSourceEnlistmentIdMatched();

                            Map<String, String> properties = module.getProperties();
                            int count = 0;
                            for (Map.Entry<String, String> entry : new TreeMap<>(properties).entrySet())
                            {
                                if (StringUtils.equals("Source Path", entry.getKey()))
                                {%>
                            <tr class="<%=getShadeRowClass(count)%>">
                                <td nowrap="true"><%=h(entry.getKey())%><%=(devMode && (!sourcePathMatched || !enlistmentIdMatched)) ? helpPopup(!sourcePathMatched ? "source path not found" : "enlistmentId not found/matched") : HtmlString.EMPTY_STRING%></td>
                                <td nowrap="true" style="color:<%=h(!devMode?"":enlistmentIdMatched?"green":sourcePathMatched?"yellow":"red")%>;"><%=h(entry.getValue())%></td>
                            </tr><%
                        }
                        else if (StringUtils.equals("Enlistment ID", entry.getKey()))
                        {%>
                            <tr class="<%=getShadeRowClass(count)%>">
                                <td nowrap="true"><%=h(entry.getKey())%><%=(devMode && sourcePathMatched && !enlistmentIdMatched) ? helpPopup("enlistment id does not match") : HtmlString.EMPTY_STRING%></td>
                                <td nowrap="true" style="color:<%=h( (!devMode||!sourcePathMatched)?"":enlistmentIdMatched?"green":"red")%>;"><%=h(entry.getValue())%></td>
                            </tr><%
                        }
                        else if (StringUtils.equals("OrganizationURL", entry.getKey()) || StringUtils.equals("LicenseURL", entry.getKey()))
                        {
                            continue;
                        }
                        else if (StringUtils.equals("Organization", entry.getKey()) || StringUtils.equals("License", entry.getKey()))
                        {
                            String url = properties.get(entry.getKey() + "URL"); %>
                            <tr class="<%=getShadeRowClass(count)%>">
                                <td nowrap="true"><%=h(entry.getKey())%></td>
                                <% if (url != null) { %>
                                <td nowrap="true"><%=link(entry.getValue()).href(url)%></td>
                                <% } else { %>
                                <td nowrap="true"><%=h(entry.getValue())%></td>
                                <% } %>
                            </tr><%
                        }
                        else
                        {%>
                            <tr class="<%=getShadeRowClass(count)%>">
                                <td nowrap="true"><%=h(entry.getKey())%></td>
                                <td nowrap="true"><%=h(entry.getValue())%></td>
                            </tr><%
                                }

                                count++;
                            } %>
                        </table>
                    </td>
                </tr><%
                    }%>
            </table>
        </labkey:panel>
        <labkey:panel id="users" className="lk-admin-section">
            <h3 class="labkey-page-section-header">Active Users in the Last Hour</h3>
            <table class="labkey-data-region-legacy labkey-show-borders">
                <tr><td class="labkey-column-header">User</td><td class="labkey-column-header">Last Activity</td></tr>
                <%
                    int count = 0;
                    for (var activeUser : bean.active)
                    {
                %>
                <tr class="<%=getShadeRowClass(count)%>"><td><%=h(activeUser.email)%></td><td><%=activeUser.minutes%> minutes ago</td></tr>
                <%
                        count++;
                    } %>
            </table>
        </labkey:panel>
    </div>
</div>
<script type="text/javascript">
    +function($) {

        var defaultRoute = "links";

        function loadRoute(hash) {
            if (!hash || hash === '#') {
                hash = '#' + defaultRoute;
            }

            $('#lk-admin-nav').find('a').removeClass('active');
            $('#lk-admin-nav').find('a[href=\'' + hash + '\']').addClass('active');
            $('.lk-admin-section').hide();
            $('.lk-admin-section[id=\'' + hash.replace('#', '') + '\']').show();
        }

        $(window).on('hashchange', function() {
            loadRoute(window.location.hash);
        });

        $(function() {
            loadRoute(window.location.hash);
        });
    }(jQuery);
</script>
