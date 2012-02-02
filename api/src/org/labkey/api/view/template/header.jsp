<%
/*
 * Copyright (c) 2005-2011 Fred Hutchinson Cancer Research Center
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    TemplateHeaderView me = ((TemplateHeaderView) HttpView.currentView());
    TemplateHeaderView.TemplateHeaderBean bean = me.getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    User user = (User) request.getUserPrincipal();
    Container c = currentContext.getContainer();
    String contextPath = currentContext.getContextPath();
    ActionURL currentURL = currentContext.getActionURL();
    AppProps app = AppProps.getInstance();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(currentContext.getContainer());

    boolean hasWarnings = me.getWarningMessages().size() > 0;
    boolean showSearchForm = bean.pageConfig.getTemplate() == PageConfig.Template.Home || bean.pageConfig.getTemplate() == PageConfig.Template.None;
    if ("search".equalsIgnoreCase(currentURL.getPageFlow()) && "search".equalsIgnoreCase(currentURL.getAction()))
        showSearchForm = false;
if ("true".equals(request.getParameter("testFont"))) {
%><script>
    function changeFontEl(el, themeFontClass)
    {
        el = Ext.get(el);
        if (!el) return;
        <%for (ThemeFont tf : ThemeFont.getThemeFonts()){%>
            el.removeClass(<%=PageFlowUtil.jsString(tf.getClassName())%>);
        <%}%>
        el.addClass(themeFontClass);
    }
    function changeFont(themeFontName)
    {
        var className = event.target.className;
        var body = Ext.getBody();
        changeFontEl(Ext.getBody(),className);
        changeFontEl('bodyTableElement',className);
    }
</script><%}%>
<div id="headerDiv"><table id="headerNav" cellpadding="0" cellspacing="0" border=0 width="auto">
  <tr>
      <td style="padding-right: 1em;">
          <form id="headerSearchForm" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null).toHString())%>" method="GET" style="margin:0; <%=showSearchForm?"":"display:none;"%>">
            <table cellspacing=0 cellpadding=0 class="labkey-main-search">
              <tr>
                <td><input id="headerSearchContainer" name="container" type="hidden" value=""><input id="headerSearchInput" name="q" type="text"></td>
                <td><img src="<%=contextPath%>/_images/search.png" onclick="return submit_onClick();"></td>
              </tr>
            </table>
          </form>
      </td>
      <td valign="top" align="right" class="labkey-main-nav">
      <%
          if (currentContext.hasPermission(AdminPermission.class) || ContainerManager.getRoot().hasPermission(user, AdminReadPermission.class))
          {
              include(new PopupAdminView(currentContext), out);
              out.write(" | ");
          }
          else if (currentContext.getUser().isDeveloper())
          {
              include(new PopupDeveloperView(currentContext), out);
              out.write(" | ");
          }

          include(new PopupHelpView(c, user, bean.pageConfig.getHelpTopic()), out);

          if (null != user && !user.isGuest())
          {
              out.write(" | ");
              include(new PopupUserView(currentContext), out);
          }
          else if (bean.pageConfig.shouldIncludeLoginLink())
          {
              String authLogoHtml = AuthenticationManager.getHeaderLogoHtml(currentURL);

              if (null != authLogoHtml)
                  out.print(authLogoHtml + "&nbsp;");

              %> | <a href="<%=h(urlProvider(LoginUrls.class).getLoginURL())%>">Sign&nbsp;In</a><%
          }
          if ("true".equals(request.getParameter("testFont"))) {
        %><span onclick="changeFont()"></span><span class="labkey-theme-font-smallest" onclick="changeFont('Smallest')">A</span><span class="labkey-theme-font-small" onclick="changeFont('Small')">A</span><span class="labkey-theme-font-medium" onclick="changeFont('Medium')">A</span><span class="labkey-theme-font-large" onclick="changeFont('Large')">A</span></span><%}%>
    </td>
  </tr>
</table></div>

<table id="header">
<tr>
  <td class="labkey-main-icon"><a href="<%=h(laf.getLogoHref())%>"><img src="<%=h(TemplateResourceHandler.LOGO.getURL(c))%>" alt="<%=h(laf.getShortName())%>"></a></td>
  <td class="labkey-main-title-area"><span><a id="labkey-main-title" class="labkey-main-title" href="<%= app.getHomePageUrl() %>"><%=h(laf.getShortName())%></a></span></td>
  <%if (hasWarnings) {%>
  <td width="16" valign="bottom"><span id="labkey-warning-message-icon" <%=me.isUserHidingWarningMessages() ? "" : "style=display:none;"%>><img src="<%=getViewContext().getContextPath()%>/_images/warning-small.png" alt="!" title="Click to view warning messages." style="cursor: pointer;" onclick="labkeyShowWarningMessages(true);"/></span></td>
  <%}%>
</tr>
<%
    if (hasWarnings)
    {
%>
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
<%  } //if warning messages %>
</table>
<script type="text/javascript">
<% if (hasWarnings) { %>
function labkeyShowWarningMessages(show)
{
    if (undefined === show)
        show = true;
    var elem = Ext.get("labkey-warning-messages-area");
    if (elem)
        elem.setDisplayed(show, true);
    elem = Ext.get("labkey-warning-message-icon");
    if (elem)
        elem.setDisplayed(!show, true);
    Ext.Ajax.request({
        url    : LABKEY.ActionURL.buildURL('user', 'setShowWarningMessages.api'),
        method : 'GET',
        params : {showMessages: show}
    });
}
<% } %>
var hdrSearch; // Ext.form.TextField
function submit_onClick()
{
    if (hdrSearch && hdrSearch.el.hasClass(hdrSearch.emptyClass) && hdrSearch.el.dom.value == hdrSearch.emptyText)
        hdrSearch.setRawValue('');
    document.forms["headerSearchForm"].submit();
    return true;
}
Ext.onReady(function()
{
    if (Ext.isSafari || Ext.isWebKit)
        Ext.get("headerNav").applyStyles({height:Ext.get("header").getSize().height});

    var serverDescription = <%=PageFlowUtil.jsString(LookAndFeelProperties.getInstance(c).getShortName())%> || LABKEY.serverName;
    var inputEl = Ext.get('headerSearchInput');
    var parentEl = inputEl.parent();
    inputEl.remove();
    hdrSearch = new Ext.form.TextField({id:'headerSearchInput',name:'q',emptyText:'Search',cls:'labkey-main-search', focusClass:'labkey-main-search'});
    hdrSearch.render(parentEl);
    var handler = function(item)
    {
        var form = Ext.get('headerSearchForm');
        form.dom.action = item.action;
        form.dom.target = item.target || '_self';
        Ext.get('headerSearchContainer').dom.value = item.containerId;
        if (hdrSearch.el.hasClass(hdrSearch.emptyClass) && hdrSearch.el.dom.value == hdrSearch.emptyText)
            hdrSearch.setRawValue(item.emptyText);
        else
            form.dom.submit();
        hdrSearch.emptyText = item.emptyText;
    };
    var items = [];
    items.push({text:serverDescription, emptyText:'Search ' + serverDescription, containerId:'', action:LABKEY.ActionURL.buildURL("search", "search"), target:'', handler:handler});
    if (LABKEY.project)
        items.push({text:LABKEY.project.name, emptyText:('Search ' + LABKEY.project.name), containerId:LABKEY.project.id, action:LABKEY.ActionURL.buildURL("search", "search", LABKEY.project.path), target:'', handler:handler});
    if (window.location.hostname.toLowerCase() != 'labkey.org')
        items.push({text:'labkey.org', emptyText:'Search labkey.org', containerId:'', action:'http://www.labkey.org/search/home/search.view', target:'_blank', handler:handler});
    items.push({text:'Google', emptyText:'Search google.com', containerId:'', action:'http://www.google.com/search', target:'_blank', handler:handler});
    new Ext.menu.Menu({cls:'extContainer',id:'headerSearchMenu',items:items});
    handler(items[0]);
});
</script>
