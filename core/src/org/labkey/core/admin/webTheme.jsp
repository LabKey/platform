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
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
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
    if (MSIE)
        return document.styleSheets[0].rules;
    else
        return document.styleSheets[0].cssRules;
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
    if ((cssName.indexOf('.ms-nav td')!=-1)
      || (cssName.indexOf('.ms-nav th')!=-1)
      || (cssName.indexOf('.ms-navframe')!=-1)
      || (cssName.indexOf('.labkey-completion-highlight')!=-1)
      || (cssName.indexOf('ms-navheader')!=-1)
      //|| (cssName.indexOf('header')!=-1 && cssName!=".navpageheader")
      || (cssName.indexOf('tr.wpHeader')!=-1)
      ) {
      cssRules[i].style.backgroundColor="#"+color;
    } else if (cssName.indexOf('.ms-tabinactive')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
      cssRules[i].style.borderColor="#"+color; // TODO: check
    }
  }
}

function descendLeftNav(element, color)
{
    if (element.className == "leftNavBox")
    {
        element.style.borderTopColor = color;
        element.style.borderBottomColor = color;
        element.style.borderRightColor = color;
    }
    else if (element.className == "leftNavBoxBody")
    {
        element.style.borderTopColor = color;
    }

    var childList = element.childNodes;
    for (var i = 0; i < childList.length; i++)
        descendLeftNav(childList[i], color);
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
    if (cssName.indexOf('.ms-titlearealine')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    } else if (cssName.indexOf('.navtab')!=-1 && cssName!='.navtab-selected') {
      cssRules[i].style.borderBottom="1px solid #"+color;
    } else if (cssName.indexOf('.navtab-inactive')!=-1) {
      cssRules[i].style.borderBottom="1px solid #"+color;
    }
  }

  var panel=document.getElementById("leftmenupanel");
  panel.style.borderTop="1px solid #"+color;
  panel.style.borderRight="1px solid #"+color;

  descendLeftNav(panel, color);

  //updateSrc("saveButton", "border", "%23" + color); just for testing
  updateSrc("navPortal", "border", "%23" + color)
  updateSrc("navAdmin", "border", "%23" + color)

  var navBar=document.getElementById("navBar");
  navBar.style.borderTopColor = color;
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
    if (cssName.indexOf('.ms-searchform')!=-1) {
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
    if (cssName.indexOf('fullscreentable')!=-1) {
      cssRules[i].style.backgroundColor="#"+color;
    }
  }
}

function updateGradientColor ()
{
    var lightColor=document.getElementsByName("gradientLightColor")[0].value;
    if (!isValidColor(lightColor)) return;
    var darkColor=document.getElementsByName("gradientDarkColor")[0].value;
    if (!isValidColor(darkColor)) return;

    var i;
    var cssRules=getCssRules();
    for ( i = 0; i < cssRules.length; i++ )
    {
        var cssName=cssRules[i].selectorText.toLowerCase();
        if (cssName.indexOf('wpheader') != -1)
        {
            cssRules[i].style.backgroundColor="#"+darkColor;
            var imageLink="url(\"gradient.image?lightColor="+lightColor+"&darkColor="+darkColor+"\")";
            cssRules[i].style.backgroundColor = "#" + lightColor;
            cssRules[i].style.border = "1px solid #" + darkColor;
        }
    }
}

function updateAll()
{
  updateNavigationColor();
  updateHeaderLineColor();
  updateFormFieldNameColor();
  updateFullScreenBorderColor();
  updateGradientColor();
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

<table cellpadding=0>
<%
if (null == webThemeErrors)
{
%><tr><td class="normal" colspan=2>&nbsp;</td></tr><%
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
    <td class="normal" colspan=2>Choose an existing web theme or define a new one. (<a href="<%=helpLink%>" target="_new">examples...</a>)</td>
</tr>
<tr style="height:1;"><td colspan=3 class=ms-titlearealine><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="ms-searchform">Web site theme (color scheme)</td>
    <td class="normal">
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
    <td class="ms-searchform">Theme Name</td>
    <td><input type="text" name="friendlyName" size="16" maxlength="16" value="<%=((null != selectedTheme) ? selectedTheme.getFriendlyName() : StringUtils.trimToEmpty(bean.form.getFriendlyName()))%>"></td>
</tr>
<%}%>
<tr>
    <td class="ms-searchform">Navigation Bar Color (left panel menu)</td>
    <td>
        <input type="text" name="navBarColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getNavBarColor() : StringUtils.trimToEmpty(bean.form.getNavBarColor()))%>" <%=disabled%> onfocus="updateNavigationColor()" onblur="updateNavigationColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].navBarColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Left Navigation Border Color</td>
    <td>
        <input type="text" name="headerLineColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getHeaderLineColor() : StringUtils.trimToEmpty(bean.form.getHeaderLineColor()))%>" <%=disabled%> onfocus="updateHeaderLineColor()" onblur="updateHeaderLineColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].headerLineColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Form Field Name Color</td>
    <td>
        <input type="text" name="editFormColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getEditFormColor() : StringUtils.trimToEmpty(bean.form.getEditFormColor()))%>" <%=disabled%> onfocus="updateFormFieldNameColor()" onblur="updateFormFieldNameColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].editFormColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Full Screen Border Color</td>
    <td>
        <input type="text" name="fullScreenBorderColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getFullScreenBorderColor() : StringUtils.trimToEmpty(bean.form.getFullScreenBorderColor()))%>" <%=disabled%> onfocus="updateFullScreenBorderColor()" onblur="updateFullScreenBorderColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].fullScreenBorderColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Title Bar Background Color</td>
    <td>
        <input type="text" name="gradientLightColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getGradientLightString() : StringUtils.trimToEmpty(bean.form.getGradientLightColor()))%>" <%=disabled%> onfocus="updateGradientColor()" onblur="updateGradientColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].gradientLightColor)"<%}%>>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Title Bar Border Color</td>
    <td>
        <input type="text" name="gradientDarkColor" size="6" maxlength="6" value="<%=((null != selectedTheme) ? selectedTheme.getGradientDarkString () : StringUtils.trimToEmpty(bean.form.getGradientDarkColor()))%>" <%=disabled%> onfocus="updateGradientColor()" onblur="updateGradientColor()">
        <img src="<%=request.getContextPath()%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=request.getContextPath()%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=request.getContextPath()%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.forms[0].gradientDarkColor)"<%}%>>
    </td>
</tr>
<tr>
    <td colspan="2" class="normal">&nbsp;</td>
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

<table class="wp" cellpadding="0" cellspacing="0" width="100%">
<tr class="wpHeader">
  <th class="wpTitle" style="border-right:0px" title="Full Screen Border Preview">Full Screen Border Preview</th>
  <th class="wpTitle" style="text-align:right;border-left:0px"><a href="javascript:;"><img border=0 src="<%=request.getContextPath()%>/_images/partedit.gif" title="Customize Web Part"></a>&nbsp;<img border=0 src="<%=request.getContextPath()%>/_images/partupg.gif" title="">&nbsp;<img border=0 src="<%=request.getContextPath()%>/_images/partdowng.gif" title="">&nbsp;<a href="javascript:;"><img border=0 src="<%=request.getContextPath()%>/_images/partdelete.gif" title="Remove From Page"></a></th>
</tr>

<tr>
  <td colspan="3">&nbsp;</td>
</tr>

<form name="login" method="post" onsubmit="javascript:return false;">
<tr>
  <td colspan="3" class="fullScreenTable">
    <table style="height: 100%; width: 100%; background-color: rgb(255, 255, 255);" cellpadding="0" cellspacing="0">
      <tr><td style="background-color: rgb(229, 229, 204);" height="20"><img src="login_files/_.gif" height="1" width="100%"></td></tr>
      <tr><td class="wpBody" style="padding: 10px;" align="left" height="100%" valign="top"><div class="normal">
        <table border="0">
          <tr><td colspan="2">Changes to full-screen color preferences will be displayed here.</td></tr>
          <tr><td colspan="2">&nbsp;</td></tr>
        </table>
      </div></td></tr>
      <tr><td style="background-color: rgb(229, 229, 204);" height="20"><img src="login_files/_.gif" height="1" width="100%"></td></tr>
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
New themes will not be visible to other users until you save changes to the Customize Site page.
<%}%>
</td>
</tr>

</table>

</form>
<script>
function changeTheme(sel)
    {
    var search = document.location.search;
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
        searchNew += "&themeName=" + opt.text;
    document.location.search = searchNew;
    }

</script>
<script for=window event=onload>
try {document.getElementByName("themeName").focus();} catch(x){}
updateAll();
</script>
