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
%>
<%!
    private void toHTML(NavTree tree, Writer out) throws Exception
    {
        if (tree == null)
            return;

        for (NavTree child : tree.getChildren())
        {
            if ("-".equals(child.getText()))
                out.write("<li class=\"divider\"></li>");
            else
            {
                out.write("<li>");
                out.write("<a href=\"" + child.getHref() + "\">");
                out.write(PageFlowUtil.filter(child.getText()));
                out.write("</a>");
                out.write("</li>");
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
                <img src="<%=h(PageFlowUtil.staticResourceUrl("/_images/lk_logo_white_m.png"))%>" alt="<%=h(laf.getShortName())%>" height="30">
            </a>
        </div>
        <ul class="navbar-nav-lk">
<% if (showSearch) { %>
            <li class="navbar-search">
                <a href="#" class="fa fa-search" id="global-search-trigger"></a>
                <div id="global-search" class="global-search">
                    <labkey:form id="global-search-form" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null))%>" method="GET">
                        <input type="text" class="search-box" name="q" placeholder="Search LabKey Server" value="">
                        <input type="submit" hidden>
                        <a href="#" onclick="document.getElementById('global-search-form').submit(); return false;" class="btn-search fa fa-search"></a>
                    </labkey:form>
                </div>
            </li>
<% } %>
<% if (isRealUser) { %>
            <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown" data-toggleremote="lk-user-drop">
                    <i class="fa fa-user"></i>
                </a>
                <ul class="hidden-xs hidden-sm dropdown-menu dropdown-menu-right">
                    <% toHTML(PopupUserView.createNavTree(context), out); %>
                </ul>
            </li>
<% } %>
<% if (PopupAdminView.hasPermission(context)) { %>
            <li class="dropdown">
                <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                    <i class="fa fa-cog"></i>
                </a>
                <ul class="dropdown-menu dropdown-menu-right">
                    <% toHTML(PopupAdminView.createNavTree(context), out); %>
                </ul>
            </li>
<% } %>
<% if (!isRealUser && model.pageConfig.shouldIncludeLoginLink()) { %>
            <li>
                <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign In</a>
            </li>
<% } %>
        </ul>
    </div>
    <% if (isRealUser) { %>
    <div class="visible-xs visible-sm lk-user-drop">
        <ul><% toHTML(PopupUserView.createNavTree(context), out); %></ul>
    </div>
    <% } %>
</div>
<script type="application/javascript">
    (function($) {
        $('#global-search-trigger').click(function() {
            $(this).parent().toggleClass('active');
            var input = $('input.search-box');
            input.is(':focus') ? input.blur() : input.focus();
        });

        $('[data-toggleremote]').parent().click(function() {
            var cls = $(this).find('[data-toggleremote]').data('toggleremote');
            if (cls) { $('.' + cls).toggleClass('open'); }
        });
    })(jQuery);
</script>