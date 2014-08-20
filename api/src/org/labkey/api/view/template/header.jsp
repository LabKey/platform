<%
/*
 * Copyright (c) 2005-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.search.SearchUrls" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.menu.HeaderMenu" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.template.TemplateHeaderView" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      resources.add(ClientDependency.fromFilePath("Ext4"));
      return resources;
  }
%>
<!-- HEADER -->
<%
    TemplateHeaderView me = ((TemplateHeaderView) HttpView.currentView());
    TemplateHeaderView.TemplateHeaderBean bean = me.getModelBean();
    Container c = getContainer();
    ActionURL currentURL = getActionURL();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);

    boolean hasWarnings = me.getWarningMessages().size() > 0;
    boolean showSearchForm = bean.pageConfig.getTemplate() == PageConfig.Template.Home || bean.pageConfig.getTemplate() == PageConfig.Template.None;
    boolean showHeaderMenu = !showSearchForm;
    if ("search".equalsIgnoreCase(currentURL.getController()) && "search".equalsIgnoreCase(currentURL.getAction()))
    {
        showSearchForm = false;
        showHeaderMenu = false;
    }

    if ("true".equals(request.getParameter("testFont")))
    {
%>
<script type="text/javascript">
    function changeFontEl(el, themeFontClass)
    {
        var _el = Ext4.get(el);
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
<%
    }

    if (showSearchForm)
    {
%>
<div id="headerDiv">
    <labkey:form id="headerSearchForm" action="<%=h(urlProvider(SearchUrls.class).getSearchURL(c, null).toHString())%>" method="GET" style="margin:0;">
        <div id="hdr-search" class="lk-input">
            <input placeholder="<%=h("Search " + laf.getShortName())%>" id="search-input" type="text" name="q" class="hdr-search-input" value="">
            <input type="submit" tabindex="-1" style="position: absolute; left: -9999px; width: 1px; height: 1px;"/>
        </div>
    </labkey:form>
</div>
<%
    }

    if (showHeaderMenu)
    {
        include(new HeaderMenu(bean.pageConfig), out);
    }
%>

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
                <img src="<%=getWebappURL("/_images/partdelete.gif")%>" alt="x"
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

        Ext4.onReady(function() {
            var elem = Ext4.get("labkey-warning-messages-area");
            if (elem)
                elem.setDisplayed(show, true);
            elem = Ext4.get("labkey-warning-message-icon");
            if (elem)
                elem.setDisplayed(!show, true);
            Ext4.Ajax.request({
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
<!-- /HEADER -->