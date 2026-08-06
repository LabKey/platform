<%
/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.CoreUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="org.labkey.api.search.SearchUtils" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.settings.HeaderProperties" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.DOM" %>
<%@ page import="org.labkey.api.util.DOM.Renderable" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HtmlView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.core.view.template.bootstrap.Header" %>
<%@ page import="static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY" %>
<%@ page import="static org.labkey.api.util.DOM.IMG" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.src" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.alt" %>
<%@ page import="static org.labkey.api.util.DOM.A" %>
<%@ page import="static org.labkey.api.util.DOM.Attribute.href" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("core/ProductNavigationHeader.js");
    }

    Renderable getLogo(Container c, TemplateResourceHandler handler, String shortName, String logoHref, String className, boolean isStartupComplete)
    {
        Renderable logo = IMG(
            DOM.at(src, handler.getURL(c), alt, shortName)
        );

        if (isStartupComplete)
            logo = A(DOM.at(href, logoHref).cl(className), logo);

        return logo;
    }
%>
<%
    Header me = HttpView.currentView();
    PageConfig pageConfig = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    boolean isRealUser = null != user && !user.isGuest();
    ViewContext context = getViewContext();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    ModuleLoader moduleLoader = ModuleLoader.getInstance();
    boolean isStartupComplete = moduleLoader.isStartupComplete();
    boolean showSearch = isStartupComplete && PageFlowUtil.urlProviderOptional(SearchUrls.class) != null && SearchService.get().isSearchIconVisible();

    HtmlView headerHtml = new HeaderProperties(getContainer()).getView();
    String siteShortName = (laf.getShortName() != null && !laf.getShortName().isEmpty()) ? laf.getShortName() : null;

    final NavTree optionsMenu = isStartupComplete ? PopupAdminView.createNavTree(context) : null;
    boolean hasPremiumModule = moduleLoader.hasModule("Premium");
    boolean isSMHostedOnly = !hasPremiumModule && moduleLoader.hasModule("SampleManagement");
    boolean showProductMenu =
        // don't show product menu when starting up
        isRealUser && isStartupComplete &&
        // .. or if user does not have read permission
        c.hasPermission(user, ReadPermission.class)
        // show only for premium distributions or SM distributions
                && (hasPremiumModule || isSMHostedOnly)
        // show only if configured to always be shown or shown to admins and this is an admin user
                && (laf.getApplicationMenuDisplayMode() == FolderDisplayMode.ALWAYS || c.hasPermission(user, AdminPermission.class));
%>
<div class="labkey-page-header">
    <div class="container clearfix">
        <div class="hidden-xs navbar-header">
            <%=getLogo(c, TemplateResourceHandler.LOGO, laf.getShortName(), laf.getLogoHref(), "brand-logo", isStartupComplete)%>
            <%-- _header.html overrides the server short name--%>
<%
    if (headerHtml == null && siteShortName != null)
    {
        String displayedShortName = "LabKey Server".equals(siteShortName) ? "" : siteShortName;
%>
            <h4 class="brand-link"><%=isStartupComplete ? simpleLink(displayedShortName, laf.getLogoHref()) : h(displayedShortName)%></h4>
<%
    }
%>
        </div>
        <div class="hidden-sm hidden-md hidden-lg navbar-header">
            <%=getLogo(c, TemplateResourceHandler.LOGO_MOBILE, laf.getShortName(), laf.getLogoHref(), "brand-logo-mobile", isStartupComplete)%>
<%
    if (headerHtml == null && siteShortName != null)
    {
%>
            <h4 class="brand-link"><%=isStartupComplete ? link(siteShortName, laf.getLogoHref()) : h(siteShortName)%></h4>
<%
    }
%>
        </div>
        <%--if a _header.html file is defined put it into dom without html encoding. It will need to define divs
        with appropriate bootstrap classes--%>
<%
    if (headerHtml != null)
    {
%>
        <%=headerHtml.getHtml()%>
<%
    }
%>
        <ul class="navbar-nav-lk">
<%
    if (showSearch && pageConfig.shouldIncludeSearch())
    {
%>
            <li class="navbar-search hidden-xs">
                <a class="fa fa-search" id="global-search-trigger" aria-label="<%=h(SearchUtils.getPlaceholder(c))%>" role="button"></a>
                <div id="global-search" class="global-search">
                    <labkey:form id="global-search-form" action="<%=urlProvider(SearchUrls.class).getSearchURL(c, null)%>" method="GET">
                        <input type="text" class="search-box" name="q" placeholder="<%=h(SearchUtils.getPlaceholder(c))%>" value="">
                        <input type="submit" hidden>
                        <a id="a_header_search" href="#" class="btn-search fa fa-search" aria-label="Search" role="button"></a>
                    </labkey:form>
                    <% pageConfig.addHandler("a_header_search","click","document.getElementById('global-search-form').submit(); return false;"); %>
                </div>
            </li>
            <li id="global-search-xs" class="dropdown visible-xs">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-label="<%=h(SearchUtils.getPlaceholder(c))%>" aria-haspopup="true" role="button">
                    <i class="fa fa-search"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <li>
                        <labkey:form action="<%=urlProvider(SearchUrls.class).getSearchURL(c, null)%>" method="GET">
                            <div class="input-group">
                                <input type="text" class="search-box" name="q" placeholder="<%=h(SearchUtils.getPlaceholder(c))%>" value="">
                                <input type="submit" hidden>
                            </div>
                        </labkey:form>
                    </li>
                </ul>
            </li>
<%
    }

    HttpSession httpSession = getViewContext().getRequest().getSession();
    boolean showWarningIconInitially = (httpSession.getAttribute(SESSION_WARNINGS_BANNER_KEY) != null && !(boolean) httpSession.getAttribute(SESSION_WARNINGS_BANNER_KEY));
%>
            <li <%=unsafe(showWarningIconInitially ? "" : "style=\"display:none\" ")%>class="dropdown dropdown-rollup" id="headerWarningIcon">
                <a href="#" class="" id="headerWarningLink" data-tt="tooltip" data-placement="bottom" title data-original-title="Click to show important notifications.">
                    <i class="fa fa-exclamation-circle warning"></i>
                </a>
            </li>
<%
    CoreUrls coreUrls = urlProvider(CoreUrls.class);

    String displayUrl = coreUrls.getDisplayWarningsActionURL(getViewContext()).toString();
%>
            <script type="text/javascript" nonce="<%=getScriptNonce()%>">
                +function($){
                    $('#headerWarningLink').on('click', function () {
                        LABKEY.Ajax.request({
                            url: <%=q(displayUrl)%>,
                            method: 'POST',
                            success: function(xhr) {
                                var resp = JSON.parse(xhr.responseText);
                                $('.lk-dismissable-alert-ct').html(resp['warningsHtml']);
                                $('#headerWarningIcon').hide();
                            },
                            failure: LABKEY.Utils.displayAjaxErrorResponse
                        });
                    })
                }(jQuery)
            </script>
<%
    if (showProductMenu)
    {
%>
            <li class="dropdown dropdown-rollup" id="headerProductDropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-label="Product navigation" aria-haspopup="menu" role="button">
                    <i class="fa fa-th-large" style="font-size: 18px; padding-top: 2px;"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <div id="headerProductDropdown-content"></div>
                </ul>
            </li>
<%
    }

    if (optionsMenu != null && optionsMenu.hasChildren())
    {
%>
            <li class="dropdown dropdown-rollup" id="headerAdminDropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-label="Admin menu" aria-haspopup="menu" role="button">
                    <i class="fa fa-cog"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% PopupMenuView.renderTree(optionsMenu, out); %>
                </ul>
            </li>
<%
    }

    if (isStartupComplete && me.getView("notifications") != null)
    {
        include(me.getView("notifications"), out);
    }

    if (!isRealUser && pageConfig.shouldIncludeLoginLink())
    {
        final HtmlString authLogoHtml = AuthenticationManager.getHeaderLogoHtml(getActionURL());
        if (null != authLogoHtml)
        {
%>
            <%= authLogoHtml /* TODO: currently expected to generate <li> tags, could expose set of links instead  */ %>
<%
        }
%>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>" class="header-link">
                    <span>Sign In</span>
                </a>
            </li>
<%
    }

    if (isRealUser)
    {
%>
            <li class="dropdown dropdown-rollup" id="headerUserDropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" aria-label="User menu" aria-haspopup="menu" role="button">
                    <i class="fa fa-user"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right" >
                    <% PopupMenuView.renderTree(PopupUserView.createNavTree(context, pageConfig), out); %>
                </ul>
            </li>
            <li class="dropdown dropdown-rollup">
                <a class="hidden-xs dropdown-toggle" href="#" data-toggle="dropdown" data-target="#headerUserDropdown" style="padding-left: 8px;"><%=h(user.getDisplayName(user))%></a>
            </li>
<%
    }

    if (user != null && user.isImpersonated())
    {
        ActionURL stopUrl = urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getPermissionsContext().getReturnUrl());
%>
            <li>
                <%=simpleLink("Stop impersonating", stopUrl).addClass("btn btn-primary").usePost()%>
            </li>
<%
    }

    if (isStartupComplete && PageFlowUtil.isPageAdminMode(getViewContext()))
    {
        ActionURL exitUrl = urlProvider(ProjectUrls.class).getTogglePageAdminModeURL(c, getActionURL());
%>
            <li>&nbsp;</li> <!--spacer, for the case of both impersonating and page admin mode-->
            <li>
                <%=simpleLink("Exit Admin Mode", exitUrl).addClass("btn btn-primary").usePost()%>
            </li>
<%
    }
%>
        </ul>
    </div>
</div>
