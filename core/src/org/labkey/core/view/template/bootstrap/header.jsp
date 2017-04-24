<%--
/*
 * Copyright (c) 2016 LabKey Corporation
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
--%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.view.template.bootstrap.BootstrapHeader" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="java.io.Writer" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }

    private void renderTree(NavTree tree, Writer out) throws Exception
    {
        if (tree == null)
            return;

        for (NavTree child : tree.getChildren())
        {
            if (child.hasChildren())
            {
                String text = PageFlowUtil.filter(child.getText());

                out.write("<li class=\"dropdown-submenu\">");
                out.write("<a class=\"subexpand\">" + text + "<i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\"><i class=\"fa fa-chevron-circle-left\"></i>" + text + "</a></li>");
                renderTree(child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else
            {
                if ("-".equals(child.getText()))
                    out.write("<li class=\"divider\"></li>");
                else
                {
                    out.write("<li>");
                    out.write("<a");
                    if (null != child.getScript())
                        out.write(" onclick=\"" + PageFlowUtil.filter(child.getScript()) +"\" ");
                    if (null != child.getHref())
                        out.write(" href=\"" + child.getHref() + "\" ");
                    if (null != child.getTarget())
                        out.write(" target=\"" + child.getTarget() + "\" ");
                    out.write(">" + PageFlowUtil.filter(child.getText()) + "</a>");
                    out.write("</li>");
                }
            }
        }
    }
%>
<%
    BootstrapHeader me = (BootstrapHeader) HttpView.currentView();
    BootstrapHeader.BootstrapHeaderBean model = me.getModelBean();
    Container c = getContainer();
    User user = getUser();
    boolean isRealUser = null != user && !user.isGuest();
    ViewContext context = getViewContext();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    boolean showSearch = hasUrlProvider(SearchUrls.class);
%>
<div class="labkey-page-header">
    <div class="container clearfix">
        <div class="navbar-header">
            <a class="hidden-xs brand-logo" href="<%=h(laf.getLogoHref())%>">
                <img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
            </a>
            <a class="hidden-sm hidden-md hidden-lg brand-logo" href="<%=h(laf.getLogoHref())%>">
                <img src="<%=h(TemplateResourceHandler.LOGO_MOBILE.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
            </a>
            <% if (laf.getShortName() != null && laf.getShortName().length() > 0) { %>
            <h4 class="brand-link"><a href="<%=h(laf.getLogoHref())%>"><%=h(laf.getShortName())%></a></h4>
            <% } %>
        </div>
        <ul class="navbar-nav-lk">
<% if (showSearch) { %>
            <li class="navbar-search hidden-xs">
                <a href="#" class="fa fa-search" id="global-search-trigger"></a>
                <div id="global-search" class="global-search">
                    <labkey:form id="global-search-form" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="GET">
                        <input type="text" class="search-box" name="q" placeholder="Search LabKey Server" value="">
                        <input type="submit" hidden>
                        <a href="#" onclick="document.getElementById('global-search-form').submit(); return false;" class="btn-search fa fa-search"></a>
                    </labkey:form>
                </div>
            </li>
            <li id="global-search-xs" class="dropdown visible-xs">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-search"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <li style="padding: 3px 20px;">
                        <labkey:form action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="GET">
                            <div class="input-group" style="width: 100%;">
                                <input type="text" class="search-box" name="q" placeholder="Search LabKey Server" value="" style="width: 100%;">
                                <input type="submit" hidden>
                            </div>
                        </labkey:form>
                    </li>
                </ul>
            </li>
<% } %>
<% if (isRealUser) { %>
            <li class="dropdown dropdown-rollup">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-user"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% renderTree(PopupUserView.createNavTree(context, model.pageConfig), out); %>
                </ul>
            </li>
<% } %>
<% if (PopupAdminView.hasPermission(context)) { %>
            <li class="dropdown dropdown-rollup">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-cog"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% renderTree(PopupAdminView.createNavTree(context), out); %>
                </ul>
            </li>
<% } %>
<% if (user != null && user.isImpersonated()) { %>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL()))%>" class="btn btn-primary">Stop impersonating</a>
            </li>
<% } %>
<% if (!isRealUser && model.pageConfig.shouldIncludeLoginLink()) { %>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign In</a>
            </li>
<% } %>
        </ul>
    </div>
    <script type="application/javascript">
        (function($) {
            $('#global-search-trigger').click(function() {
                $(this).parent().toggleClass('active');
                var input = $('input.search-box');
                input.is(':focus') ? input.blur() : input.focus();
            });

            // delay for mobile focus on search
            $('#global-search-xs').on('show.bs.dropdown', function() {
                setTimeout(function() {
                    jQuery(this).find('input.search-box').focus();
                }.bind(this), 500);
            });

            $('.dropdown-submenu a.subexpand').click(function(e) {
                var el = $(this);

                // hide this link and its direct parent sibling list elements
                el.css('display', 'none');
                el.parent().siblings('li').css('display', 'none');

                // toggle the sibling ul element to show the nested list
                el.next('ul').toggleClass('open');
                e.stopPropagation();
                e.preventDefault();
            });

            $('.dropdown-layer-menu a.subcollapse').click(function(e) {
                var el = $(this);
                var menu = el.parent().parent();

                // toggle the element class
                menu.toggleClass('open');

                // show the parent link and its direct parent sibling list elements
                var menuLink = menu.prev('a.subexpand');
                menuLink.css('display', '');
                menuLink.parent().siblings('li').css('display', '');

                e.stopPropagation();
                e.preventDefault();
            });

            // unfurl menus
            $('.dropdown-rollup').on('hide.bs.dropdown', function() {
                var menu =  $(this).children('ul.dropdown-menu');
                menu.find('li').css('display', '');
                menu.find('.subexpand').css('display', '');
                menu.find('.dropdown-layer-menu.open').toggleClass('open');
            });
        })(jQuery);
    </script>
</div>