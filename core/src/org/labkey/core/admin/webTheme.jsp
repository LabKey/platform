<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("color_functions.js");
        dependencies.add("js_color_picker_v2.js");
        dependencies.add("js_color_picker_v2.css");
    }
%>
<%
    String contextPath = request.getContextPath();

    HttpView<AdminController.WebThemesBean> me = (HttpView<AdminController.WebThemesBean>) HttpView.currentView();
    AdminController.WebThemesBean bean = me.getModelBean();
    WebTheme selectedTheme = bean.selectedTheme;
%>
<labkey:form name="themeForm" action="<%=h(buildURL(AdminController.SaveWebThemeAction.class))%>" enctype="multipart/form-data" method="post">
<input type="hidden" name="upgradeInProgress" value="<%=bean.form.isUpgradeInProgress()%>" />
<table width="100%">
<%
String webThemeErrors = formatMissedErrorsStr("form");
if (null != webThemeErrors) { %>
    <tr><td colspan=3><%=text(webThemeErrors)%></td></tr>
<% } %>
<tr>
<td valign="top">

<!-- web theme definition -->

<table>
    <% if (null == webThemeErrors) { %>
    <tr><td colspan=2>&nbsp;</td></tr>
<%  }

    boolean isBuiltInTheme = (selectedTheme != null && !selectedTheme.isEditable());

    String disabled = isBuiltInTheme ? "disabled" : "";
%>
<tr>
    <td colspan=2>Choose an existing web theme or define a new one. (<%=helpLink("customizeTheme", "examples...")%>)</td>
</tr>
<tr><td colspan=3 class="labkey-title-area-line"></td></tr>
<tr>
    <td class="labkey-form-label">Web site theme (color scheme)</td>
    <td>
        <select name="themeName" onchange="changeTheme(this)">
            <option value="">&lt;New Theme&gt;</option>
            <%
              boolean themeFound = false;
              for (WebTheme theme : bean.themes)
                {
                    if (theme == selectedTheme)
                        themeFound = true;
                    %>
                    <option value="<%=h(theme.toString())%>"<%=selected(theme == selectedTheme)%>><%=h(theme.getFriendlyName())%></option>
                <%}
            %>
        </select>
    </td>
</tr>
<%if (!themeFound) { %>
<tr>
    <td class="labkey-form-label">Theme Name</td>
    <td><input type="text" name="friendlyName" size="16" maxlength="16" value="<%=h(((null != selectedTheme) ? selectedTheme.getFriendlyName() : StringUtils.trimToEmpty(bean.form.getFriendlyName())))%>"></td>
</tr>
<%}%>
<tr>
    <td class="labkey-form-label">Text Color</td>
    <td>
        <input type="text" name="textColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getTextColor() : StringUtils.trimToEmpty(bean.form.getTextColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('textColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Link Color</td>
    <td>
        <input type="text" name="linkColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getLinkColor() : StringUtils.trimToEmpty(bean.form.getLinkColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('linkColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Grid Color</td>
    <td>
        <input type="text" name="gridColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getGridColor() : StringUtils.trimToEmpty(bean.form.getGridColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('gridColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Primary Background Color</td>
    <td>
        <input type="text" name="primaryBackgroundColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getPrimaryBackgroundColor() : StringUtils.trimToEmpty(bean.form.getPrimaryBackgroundColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('primaryBackgroundColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Secondary Background Color</td>
    <td>
        <input type="text" name="secondaryBackgroundColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getSecondaryBackgroundColor() : StringUtils.trimToEmpty(bean.form.getSecondaryBackgroundColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('secondaryBackgroundColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">Border & Title Color</td>
    <td>
        <input type="text" name="borderTitleColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getBorderTitleColor() : StringUtils.trimToEmpty(bean.form.getBorderTitleColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('borderTitleColor')[0])"<%}%>>
        <% } %>
    </td>
</tr>
<tr>
    <td class="labkey-form-label">WebPart Color</td>
    <td>
        <input type="text" name="webpartColor" size="6" maxlength="6" value="<%=h(((null != selectedTheme) ? selectedTheme.getWebPartColor() : StringUtils.trimToEmpty(bean.form.getWebpartColor())))%>" <%=text(disabled)%>>
        <% if (!isBuiltInTheme) { %>
        <img src="<%=text(contextPath)%>/_images/select_arrow.gif"<% if ("".equals(disabled)) {%> onmouseover="this.src='<%=text(contextPath)%>/_images/select_arrow_over.gif'" onmouseout="this.src='<%=text(contextPath)%>/_images/select_arrow.gif'" onclick="showColorPicker(this,document.getElementsByName('webpartColor')[0])"<%}%>>
        <% } %>
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
        <%= button("Save").submit(true).attributes("id=\"saveButton\" name=\"Define\"") %>
            <%
            if (selectedTheme != null && bean.themes.size() > 1)
            {%>
                <%= button("Delete").submit(true).onClick("var sure = confirm('Are you sure you want to delete the theme named " + request.getParameter("themeName") + "?'); if (sure) document.themeForm.action = " + qh(buildURL(AdminController.DeleteWebThemeAction.class)) + "; return sure;").attributes("name=\"Delete\"") %>&nbsp;
            <%}
        }
        else
            {%>
            <%= button("Done").href(urlProvider(AdminUrls.class).getProjectSettingsURL(getContainer())) %>
           <%}%>
    </td>
</tr>
</table>

</td>

<td>&nbsp;&nbsp;</td>

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

</labkey:form>
<script type="text/javascript">
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