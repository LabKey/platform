<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps"%>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ThemeFont" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%=formatMissedErrors("form")%>
<%
    AdminController.LookAndFeelBean bean = ((JspView<AdminController.LookAndFeelBean>)HttpView.currentView()).getModelBean();
    AppProps appProps = AppProps.getInstance();
%>

<form name="preferences" enctype="multipart/form-data" method="post">

<table>
<tr>
    <td><input type="image" src="<%=PageFlowUtil.buttonSrc("Save")%>"/></td>
</tr>
</table>

<table cellpadding=0>
<tr>
    <td class="normal" colspan=2>&nbsp;</td>
</tr>

<tr>
    <td class="normal" colspan=2>Customize the look and feel of your LabKey Server installation (<%=bean.helpLink%>)</td>
</tr>
<tr style="height:1;"><td colspan=3 class=ms-titlearealine><img height=1 width=1 src="<%=request.getContextPath()%>/_.gif"></td></tr>
<tr>
    <td class="ms-searchform">Web site description (appears in every page header)</td>
    <td class="normal"><input type="text" name="systemDescription" size="50" value="<%= h(appProps.getSystemDescription()) %>"></td>
</tr>
<tr>
    <td class="ms-searchform">Web site short name (appears in every page header and in emails)</td>
    <td class="normal"><input type="text" name="systemShortName" size="50" value="<%= h(appProps.getSystemShortName()) %>"></td>
</tr>
<tr>
    <td class="ms-searchform">Web site theme (color scheme)</td>
    <td class="normal">
        <select name="themeName">
            <% for (WebTheme theme : bean.themes)
                {
                    String selected;

                    //if a new theme has just been defined
                    if (bean.newTheme != null)
                        selected = theme == bean.newTheme ? "selected" : "";
                    else
                        selected = theme == bean.currentTheme ? "selected" : "";
                    %>
                    <option value="<%=h(theme.toString())%>" <%=selected%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select>

        [<a href="<%=AdminController.getDefineWebThemesURL(false)%>">Define Web Themes</a>]
    </td>
</tr>
<tr>
    <td class="ms-searchform">Default font size</td>
    <td class="normal">
        <select name="themeFont">
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                out.print("<option value=\"" + themeFont.toString() + "\"" + (themeFont == bean.currentThemeFont ? " selected>" : ">") + themeFont.getFriendlyName() + "</option>\n");
                }
            %>
        </select>
        Font Size Samples:
            <% for (ThemeFont themeFont : bean.themeFonts)
                {
                out.print("<span class=\"" + themeFont.getId() + "\">&nbsp;&nbsp;" + themeFont.toString() + "</span>");
                }
            %>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Show left navigation bar</td>
    <td class="normal">
        <%
            FolderDisplayMode currentMode = appProps.getFolderDisplayMode();  
            for (FolderDisplayMode mode : FolderDisplayMode.values())
        {%>
            <input type="radio" name="folderDisplayMode" value="<%=mode.toString()%>" <%=mode == currentMode ? "CHECKED" : "" %> >
            <%=mode.getDisplayString()%><br>
     <% } %>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Left-navigation bar width (pixels)</td>
    <td class="normal">
        <input name="navigationBarWidth" value="<%=appProps.getNavigationBarWidth() %>" type="text" size="4">
    </td>
</tr>
<tr>
    <td class="ms-searchform">Web site logo (appears in every page header; 147 x 56 pixels)</td>
    <td><input type="file" name="logoImage" size="50"></td>
</tr>
<tr>
    <td></td>
    <td class="normal">
        <% if (null != bean.customLogo)
        { %>
            Currently using a custom logo. [<a href="resetLogo.view">Reset logo to default</a>]
        <% } else { %>
            Currently using the default logo.
        <% } %>
    </td>
</tr>
<tr>
    <td class="ms-searchform">Logo link (specifies page that logo links to)</td>
    <td class="normal"><input type="text" name="logoHref" size="50" value="<%= h(appProps.getLogoHref()) %>"></td>
</tr>
<tr>
    <td class="ms-searchform">Web site favorite icon (page icon displayed in user's favorites, .ico file only)</td>
    <td class="normal"><input type="file" name="iconImage" size="50"></td>
</tr>
<tr>
    <td></td>
    <td class="normal">
        <% if (null != bean.customFavIcon)
        { %>
            Currently using a custom favorite icon. [<a href="resetFavicon.view">Reset favorite logo to default</a>]
        <% } else { %>
            Currently using the default favorite icon.
        <% } %>
    </td>
</tr>
<tr>
    <td class="normal">&nbsp;</td>
</tr>

<tr>
    <td><input type="image" src='<%=PageFlowUtil.buttonSrc("Save")%>' /></td>
</tr>
</table>
</form>
