<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AdminController.WebThemesBean> me = (HttpView<AdminController.WebThemesBean>) HttpView.currentView();
    AdminController.WebThemesBean bean = me.getModelBean();
    WebTheme selectedTheme = bean.selectedTheme;
%>
<link href="<%= request.getContextPath() %>/js_color_picker_v2.css" type="text/css" media="screen" rel="stylesheet">
<script type="text/javascript">
    LABKEY.requiresScript('color_functions.js');
    LABKEY.requiresScript('js_color_picker_v2.js');
</script>
<script type="text/javascript">
function isValidColor(color)
{
    if ("" == color) return false;
    if (6 != color.length) return false;
    //todo: check hex
    return true;
}

function getCssRules()
{
    // MSIE is from js_color_picker_v2.js
    var i;
    for (i = 0; i < document.styleSheets.length; i++)
    {
        if (document.styleSheets[i].href != null && document.styleSheets[i].href.indexOf("themeStylesheet.view") != -1)
        {
            if (MSIE)
                return document.styleSheets[i].rules;
            else
                return document.styleSheets[i].cssRules;
        }
    }
}

function updateNavigationColor ()
{
  var color=document.getElementsByName("navBarColor")[0].value;
  if (!isValidColor(color)) return;

  var i;
  var cssRules=getCssRules();
  for ( i = 0; i < cssRules.length; i++ )
  {
    // theme.getNavBarColor()
    var cssName=cssRules[i].selectorText.toLowerCase();
    if ((cssName.indexOf('labkey-frame')!=-1)
      || (cssName.indexOf('labkey-site-nav-panel')!=-1)
      || (cssName.indexOf('labkey-tab-shaded')!=-1)
      || (cssName.indexOf('labkey-nav-tree-row:hover')!=-1)
      )
    {
      cssRules[i].style.backgroundColor="#"+color;
    }
  }
}

function updateHeaderLineColor ()
{
  var color=document.getElementsByName("headerLineColor")[0].value;
  if (!isValidColor(color)) return;

  var cssRules=getCssRules();
  for (var i = 0; i < cssRules.length; i++ )
  {
    // headerline
    var cssName=cssRules[i].selectorText.toLowerCase();
    if (cssName.indexOf('labkey-title-area-line')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    } else if ((cssName.indexOf('labkey-nav-bordered')!=-1)
      || (cssName.indexOf('labkey-tab')!=-1 && cssName!='labkey-tab-selected')
      || (cssName.indexOf('labkey-tab-inactive')!=-1)){
      cssRules[i].style.border="1px solid #"+color;
    } else if (cssName.indexOf('labkey-site-nav-panel')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
    } else if (cssName.indexOf('labkey-expandable-nav')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
      cssRules[i].style.borderBottom="1px solid #"+color;
    } else if (cssName.indexOf('labkey-expandable-nav-body')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
    } else if (cssName.indexOf('labkey-tab-selected')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
      cssRules[i].style.borderRight="1px solid #"+color;
      cssRules[i].style.borderLeft="1px solid #"+color;
    } else if (cssName.indexOf('labkey-header-line')!=-1){
      cssRules[i].style.borderTop="1px solid #"+color;
    }

  }

  var panel=document.getElementById("leftmenupanel");

  //updateSrc("saveButton", "border", "%23" + color); just for testing
  updateSrc("navPortal", "border", "%23" + color)
  updateSrc("navAdmin", "border", "%23" + color)
}

function updateSrc(id, key, value)
{
    var el = document.getElementById(id);
    if (el)
    {
      var parts = el.src.split("?");
      var path = parts[0];
      var query = parts.length > 1 ? parts[1] : "";
      var queryObj = parseQuery(query);
      queryObj[key] = value;
      query = formatQuery(queryObj);
      el.src=path + "?" + query;
    }
}

function parseQuery(query)
{
    var o = {};
    var params=query.split("&");
    for (var i=0 ; i<params.length ; i++)
    {
        var s = params[i];
        var kv = s.split("=");
        var k = kv[0];
        var v = kv.length > 1 ? kv[1] : "";
        o[k] = v;
    }
    return o;
}

function formatQuery(o)
{
    var query = "";
    var and = "";
    for (var k in o)
    {
        if (o[k])
            query += and + k + "=" + o[k];
        else
            query += k;
        and = "&";
    }
    return query;
}


function updateFormFieldNameColor ()
{
  var color=document.getElementsByName("editFormColor")[0].value;
  if (!isValidColor(color)) return;

  var i;
  var cssRules=getCssRules();
  for ( i = 0; i < cssRules.length; i++ )
  {
    // theme.getEditFormColor()
    var cssName=cssRules[i].selectorText.toLowerCase();
    if (cssName.indexOf('.labkey-form-label')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    }
  }
}

function updateFullScreenBorderColor ()
{
  var color=document.getElementsByName("fullScreenBorderColor")[0].value;
  if (!isValidColor(color)) return;

  var i;
  var cssRules=getCssRules();
  for ( i = 0; i < cssRules.length; i++ )
  {
    //theme.getFullScreenBorderColor()
    var cssName=cssRules[i].selectorText.toLowerCase();
    if (cssName.indexOf('labkey-full-screen-background')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    }
  }
}

function updateTitleBarColor()
{
    var backgroundColor=document.getElementsByName("titleBarBackgroundColor")[0].value;
    if (!isValidColor(backgroundColor)) return;
    var borderColor=document.getElementsByName("titleBarBorderColor")[0].value;
    if (!isValidColor(borderColor)) return;

    var i;
    var cssRules=getCssRules();
    for ( i = 0; i < cssRules.length; i++ )
    {
        var cssName=cssRules[i].selectorText.toLowerCase();
        if (cssName.indexOf('labkey-wp-header') != -1)
        {
            cssRules[i].style.backgroundColor = "#" + backgroundColor;
        } else if (cssName.indexOf('labkey-wp-title-left') != -1)
        {
            cssRules[i].style.borderTop = "1px solid #" + borderColor;
            cssRules[i].style.borderBottom = "1px solid #" + borderColor;
            cssRules[i].style.borderLeft = "1px solid #" + borderColor;
        } else if (cssName.indexOf('labkey-wp-title-right') != -1)
        {
            cssRules[i].style.borderTop = "1px solid #" + borderColor;
            cssRules[i].style.borderBottom = "1px solid #" + borderColor;
            cssRules[i].style.borderRight = "1px solid #" + borderColor;
        }
    }
}

function updateAll()
{
  updateNavigationColor();
  updateHeaderLineColor();
  updateFormFieldNameColor();
  updateFullScreenBorderColor();
  updateTitleBarColor();
}
</script>

<form action="defineWebThemes.view" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean.form.isUpgradeInProgress()%>" />
<table width="100%">
<%
String webThemeErrors = formatMissedErrors("form");
if (null != webThemeErrors)
{
%>
<tr><td colspan=3><%=webThemeErrors%></td></tr>
<%
}
%>

<tr>
<td valign="top">

<!-- web theme definition -->

<table>
<%
if (null == webThemeErrors)
{
%><tr><td colspan=2>&nbsp;</td></tr><%
    }
    boolean isBuiltInTheme;
    if (bean.selectedTheme != null)
    {
        isBuiltInTheme = (bean.selectedTheme.getFriendlyName().compareToIgnoreCase("Blue") == 0
                || bean.selectedTheme.getFriendlyName().compareToIgnoreCase("Brown") == 0);
    }
    else
        isBuiltInTheme = false;

    String disabled = isBuiltInTheme ? "disabled" : "";

    String helpLink = (new HelpTopic("customizeTheme", HelpTopic.Area.SERVER)).getHelpTopicLink();
%>
<tr>
    <td colspan=2>Choose an existing web theme or define a new one. (<a href="<%=helpLink%>" target="_new">examples...</a>)</td>
</tr>
<tr><td colspan=3 class="labkey-title-area-line"><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="labkey-form-label">Web site theme (color scheme)</td>
    <td>
        <select name="themeName" onchange="changeTheme(this)">
            <option value="">&lt;New Theme&gt;</option>
            <%
              boolean themeFound = false;
              for (WebTheme theme : bean.themes)
                {
                    if (theme == bean.selectedTheme)
                        themeFound = true;
                    String selected = (theme == bean.selectedTheme ? "selected" : "");
                    %>
                    <option value="<%=h(theme.toString())%>" <%=selected%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select>
    </td>
</tr>
<%if (!themeFound)
{%>
<tr>
    <td class="labkey-form-label">Theme Name</td>
    <td><input type="text" name="friendlyName" size="16" maxlength="16" value="<%=((null != selectedTheme) ? selectedTheme.getFriendlyName() : StringUtils.trimToEmpty(bean.form.getFriendlyName()))%>"></td>
</tr>
<%}%>
<tr>
    <td class="labkey-form-label">Navigation Bar Color (left panel menu)</td>
    <td>
        <input type="text" name="navBarColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getNavBarColor() : StringUtils.trimToEmpty(bean.form.getNavBarColor()))%>" <%=disabled%> onfocus="updateNavigationColor()" onblur="updateNavigationColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].navBarColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Left Navigation Border Color</td>
    <td>
        <input type="text" name="headerLineColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getHeaderLineColor() : StringUtils.trimToEmpty(bean.form.getHeaderLineColor()))%>" <%=disabled%> onfocus="updateHeaderLineColor()" onblur="updateHeaderLineColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].headerLineColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Form Field Name Color</td>
    <td>
        <input type="text" name="editFormColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getEditFormColor() : StringUtils.trimToEmpty(bean.form.getEditFormColor()))%>" <%=disabled%> onfocus="updateFormFieldNameColor()" onblur="updateFormFieldNameColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].editFormColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Full Screen Border Color</td>
    <td>
        <input type="text" name="fullScreenBorderColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getFullScreenBorderColor() : StringUtils.trimToEmpty(bean.form.getFullScreenBorderColor()))%>" <%=disabled%> onfocus="updateFullScreenBorderColor()" onblur="updateFullScreenBorderColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].fullScreenBorderColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Title Bar Background Color</td>
    <td>
        <input type="text" name="titleBarBackgroundColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getTitleBarBackgroundString() : StringUtils.trimToEmpty(bean.form.getTitleBarBackgroundColor()))%>" <%=disabled%> onfocus="updateTitleBarColor()" onblur="updateTitleBarColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].titleBarBackgroundColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Title Bar Border Color</td>
    <td>
        <input type="text" name="titleBarBorderColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getTitleBarBorderString () : StringUtils.trimToEmpty(bean.form.getTitleBarBorderColor()))%>" <%=disabled%> onfocus="updateTitleBarColor()" onblur="updateTitleBarColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].titleBarBorderColor)"<%}%>>
    </td>
</tr>
<tr>
    <td colspan="2">&nbsp;</td>
</tr>

<tr>
    <td colspan="2">
        <%
        if (!isBuiltInTheme)
        {%>
             <input type="image" id="saveButton" src='<%=PageFlowUtil.buttonSrc("Save")%>' name="Define" />&nbsp;
            <%
            if (selectedTheme != null && bean.themes.size() > 1)
            {%>
                <input type="image" src='<%=PageFlowUtil.buttonSrc("Delete")%>' name="Delete" onClick="return confirm('Are you sure you want to delete the theme named <%=request.getParameter("themeName")%>?');" />&nbsp;
            <%}
        }
        else
            {%>
            <%=PageFlowUtil.buttonLink("Done", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeSiteURL(bean.form.isUpgradeInProgress()))%>
           <%}%>
    </td>
</tr>
</table>

</td>

<td>&nbsp;&nbsp;</td>

<td>
<!-- start of dialog preview -->

<table class="labkey-wp">
<tr class="labkey-wp-header">
  <th class="labkey-wp-title-left" title="Full Screen Border Preview">Full Screen Border Preview</th>
  <th class="labkey-wp-title-right"><a href="javascript:;"><img src="<%=request.getContextPath()%>/_images/partedit.gif" title="Customize Web Part"></a>&nbsp;<img src="<%=request.getContextPath()%>/_images/partupg.gif" title="">&nbsp;<img src="<%=request.getContextPath()%>/_images/partdowng.gif" title="">&nbsp;<a href="javascript:;"><img src="<%=request.getContextPath()%>/_images/partdelete.gif" title="Remove From Page"></a></th>
</tr>

<tr>
  <td colspan="3">&nbsp;</td>
</tr>

<form name="login" method="post" onsubmit="javascript:return false;">
<tr>
  <td colspan="3" class="labkey-full-screen-background">
    <table class="labkey-full-screen-table">
      <tr class="labkey-full-screen-table-panel"><td height="20"><img src="login_files/_.gif" height="1" width="100%"></td></tr>
      <tr><td class="labkey-wp-body" style="padding: 10px;" align="left" height="100%" valign="top"><div>
        <table>
          <tr><td colspan="2">Changes to full-screen color preferences will be displayed here.</td></tr>
          <tr><td colspan="2">&nbsp;</td></tr>
        </table>
      </div></td></tr>
      <tr class="labkey-full-screen-table-panel"><td height="20"><img src="login_files/_.gif" height="1" width="100%"></td></tr>
    </table>
   </td>
</tr>
</form>
</table>
<!-- end of dialog preview -->
</td>

</tr>

<tr>
<td colspan=3>
<%
if (!themeFound)
{%>
New themes will not be visible to other users until you save changes on the Look and Feel Settings page.
<%}%>
</td>
</tr>

</table>

</form>
<script>
function changeTheme(sel)
{
    var search = document.location.search;
    if (search.indexOf("?") == 0)
    {
        search = search.substring(1);
    }
    var params = search.split('&');
    var searchNew = "";
    for (var i = 0; i < params.length; i++)
    {
        if (params[i].indexOf("themeName=") != 0)
        {
            if (searchNew != "")
                searchNew += "&";
            searchNew += params[i];
        }
    }
    var opt = sel.options[sel.selectedIndex];
    if (opt.text.indexOf("<") != 0)
        if (searchNew.length == 0)
        {
            searchNew = "themeName=" + escape(opt.text);
        }
        else
        {
            searchNew += "&themeName=" + escape(opt.text);
        }
    document.location.search = searchNew;
}

</script>
<script for=window event=onload>
try {document.getElementByName("themeName").focus();} catch(x){}
updateAll();
</script>
