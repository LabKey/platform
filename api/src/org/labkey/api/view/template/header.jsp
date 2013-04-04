<%
/*
 * Copyright (c) 2005-2013 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.AdminReadPermission" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.PopupAdminView" %>
<%@ page import="org.labkey.api.view.PopupDeveloperView" %>
<%@ page import="org.labkey.api.view.PopupHelpView" %>
<%@ page import="org.labkey.api.view.PopupUserView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.TemplateHeaderView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("Ext4"));
      return resources;
  }
%>
<%
    TemplateHeaderView me = ((TemplateHeaderView) HttpView.currentView());
    TemplateHeaderView.TemplateHeaderBean bean = me.getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    User user = (User) request.getUserPrincipal();
    Container c = currentContext.getContainer();
    String contextPath = currentContext.getContextPath();
    ActionURL currentURL = currentContext.getActionURL();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(currentContext.getContainer());

    boolean hasWarnings = me.getWarningMessages().size() > 0;
    boolean showSearchForm = bean.pageConfig.getTemplate() == PageConfig.Template.Home || bean.pageConfig.getTemplate() == PageConfig.Template.None;
    if ("search".equalsIgnoreCase(currentURL.getController()) && "search".equalsIgnoreCase(currentURL.getAction()))
        showSearchForm = false;
if ("true".equals(request.getParameter("testFont"))) {
%>
<script type="text/javascript">
    function changeFontEl(el, themeFontClass)
    {
        var _el = LABKEY.ExtAdapter.get(el);
        if (!_el) return;
        <%for (ThemeFont tf : ThemeFont.getThemeFonts()){%>
            _el.removeClass ? _el.removeClass(<%=PageFlowUtil.jsString(tf.getClassName())%>) : _el.removeCls(<%=PageFlowUtil.jsString(tf.getClassName())%>);
        <%}%>
        _el.addClass ? _el.addClass(themeFontClass) : _el.addCls(themeFontClass);
    }
    function changeFont(themeFontName)
    {
        var clsName = event.target.className;
        var body = document.getElementByTagName('body');
        if (body && body.length > 0) {
            changeFontEl(body,clsName);
            changeFontEl('bodyTableElement',clsName);
        }
    }
</script>
<%}%>
<style type="text/css">

    .lk-input input.hdr-search-input {
        padding-left: 22px;
        background: #FFFFFF url(<%=contextPath%>/_images/search.png) 2% center no-repeat;
    }

</style>
<div id="headerDiv"><table id="headerNav" cellpadding="0" cellspacing="0" border=0 width="auto">
  <tr>
      <td style="padding-right: 1em;">
          <form id="headerSearchForm" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null).toHString())%>" method="GET" style="margin:0; <%=showSearchForm?"":"display:none;"%>">
              <div id="hdr-search" class="lk-input">
                  <input placeholder="<%=h("Search " + laf.getShortName())%>" id="search-input" type="text" name="q" class="hdr-search-input" value="">
                  <input type="submit" style="position: absolute; left: -9999px; width: 1px; height: 1px;">
              </div>
          </form>
      </td>
      <td valign="top" align="right" class="labkey-main-nav">
      <%
          boolean needSeparator = false;

          if (currentContext.hasPermission("header.jsp popupadminview", AdminPermission.class) || ContainerManager.getRoot().hasPermission("header.jsp popupadminview", user, AdminReadPermission.class))
          {
              include(new PopupAdminView(currentContext), out);
              needSeparator = true;
          }
          else if (currentContext.getUser().isDeveloper())
          {
              include(new PopupDeveloperView(currentContext), out);
              needSeparator = true;
          }

          PopupHelpView helpMenu = new PopupHelpView(c, user, bean.pageConfig.getHelpTopic());
          if (helpMenu.hasChildren())
          {
              if (needSeparator)
                  out.write(" | ");
              include(helpMenu, out);
              needSeparator = true;
          }

          if (null != user && !user.isGuest())
          {
              if (needSeparator)
                  out.write(" | ");
              include(new PopupUserView(currentContext), out);
          }
          else if (bean.pageConfig.shouldIncludeLoginLink())
          {
              if (needSeparator)
                  out.write(" | ");

              String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

              if (null != authLogoHtml)
                  out.print(authLogoHtml + "&nbsp;");

              %><a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign&nbsp;In</a><%
          }
          if ("true".equals(request.getParameter("testFont"))) {
        %><span onclick="changeFont()"></span><span class="labkey-theme-font-smallest" onclick="changeFont('Smallest')">A</span><span class="labkey-theme-font-small" onclick="changeFont('Small')">A</span><span class="labkey-theme-font-medium" onclick="changeFont('Medium')">A</span><span class="labkey-theme-font-large" onclick="changeFont('Large')">A</span></span><%}%>
    </td>
  </tr>
</table></div>

<table id="header">
    <tr>
      <td class="labkey-main-icon">
          <a href="<%=h(laf.getLogoHref())%>"><img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>"></a>
      </td>
      <td class="labkey-main-title-area">
          <span>
              <a id="labkey-main-title" class="labkey-main-title" href="<%= AppProps.getInstance().getHomePageUrl() %>"><%=h(laf.getShortName())%></a>
          </span>
      </td>

<% if (hasWarnings) { %>

        <td width="16" valign="bottom">
            <span id="labkey-warning-message-icon" <%=me.isUserHidingWarningMessages() ? "" : "style=display:none;"%>>
                <img src="<%=getViewContext().getContextPath()%>/_images/warning-small.png" alt="!" title="Click to view warning messages." style="cursor: pointer;" onclick="labkeyShowWarningMessages(true);"/>
            </span>
        </td>
    </tr>
    <tr id="labkey-warning-messages-area" <%=me.isUserHidingWarningMessages() ? "style=display:none;" : ""%>>
        <td colspan="4" style="padding: 2px;">
            <div class="labkey-warning-messages">
                <img src="<%=getViewContext().getContextPath()%>/_images/partdelete.gif" alt="x"
                     style="float: right;cursor:pointer;" onclick="labkeyShowWarningMessages(false);">
                <ul>
                    <% for(String warningMessage : me.getWarningMessages()) { %>
                    <li><%=warningMessage%></li>
                    <% } //for each warning message %>
                </ul>
            </div>
        </td>
    </tr>
    </table>
    <script type="text/javascript">
    function labkeyShowWarningMessages(show)
    {
        if (undefined === show) {
            show = true;
        }

        LABKEY.ExtAdapter.onReady(function() {
            var elem = LABKEY.ExtAdapter.get("labkey-warning-messages-area");
            if (elem)
                elem.setDisplayed(show, true);
            elem = LABKEY.ExtAdapter.get("labkey-warning-message-icon");
            if (elem)
                elem.setDisplayed(!show, true);
            LABKEY.ExtAdapter.Ajax.request({
                url    : LABKEY.ActionURL.buildURL('user', 'setShowWarningMessages.api'),
                params : {showMessages: show}
            });
        });
    }
    </script>

<%  } else { %>
    </tr>
</table>
<%  } %>