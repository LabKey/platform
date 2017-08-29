<%
/*
 * Copyright (c) 2005-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.CoreController" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteCustomStylesheetAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetFaviconAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetLogoAction" %>
<%@ page import="org.labkey.core.admin.ProjectSettingsAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ProjectSettingsAction.LookAndFeelResourcesBean bean = ((JspView<ProjectSettingsAction.LookAndFeelResourcesBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();
%>
<%=formatMissedErrors("form")%>
<labkey:form name="preferences" enctype="multipart/form-data" method="post" id="form-preferences">

<table cellpadding=0 class="lk-fields-table">
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize the logo, icon, and stylesheets used <%=text(c.isRoot() ? "throughout the site" : "in this project")%> (<%=text(bean.helpLink)%>)</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td class="labkey-form-label" rowspan="2">Header logo (appears in every page header in the upper left)</td>
    <td>
        <% if (null != bean.customLogo)
        { %>
            Currently using a custom logo. <%=textLink("view logo", TemplateResourceHandler.LOGO.getURL(c)) %> <%=textLink("reset logo to default", ResetLogoAction.class)%>
        <% } else { %>
            Currently using the default logo.
        <% } %>
    </td>
</tr>
<tr>
    <td>Replace with: <input type="file" name="logoImage" size="25" style="border: none;"></td>
</tr>

<tr>
    <td rowspan="2" class="labkey-form-label">Favorite icon (displayed in user's favorites or bookmarks, .ico file only)</td>
    <td>
        <% if (null != bean.customFavIcon)
        { %>
            Currently using a custom favorite icon. <%=textLink("view icon", TemplateResourceHandler.FAVICON.getURL(c)) %> <%=textLink("reset favorite icon to default", ResetFaviconAction.class)%>
        <% } else { %>
            Currently using the default favorite icon.
        <% } %>
    </td>
</tr>
<tr>
    <td>Replace with: <input type="file" name="iconImage" size="25" style="border: none;"></td>
</tr>

<tr>
    <td rowspan="2" class="labkey-form-label">Custom stylesheet</td>
    <td>
        <% if (null != bean.customStylesheet)
        { %>
            Currently using a custom stylesheet. <%=textLink("view CSS", CoreController.CustomStylesheetAction.class) %> <%=textLink("delete custom stylesheet", DeleteCustomStylesheetAction.class)%>
        <% } else { %>
            No custom stylesheet.
        <% } %>
    </td>
</tr>
<tr>
    <td>Replace with: <input type="file" name="customStylesheet" size="25" style="border: none;"></td>
</tr>
<tr>
    <td><br/><%= button("Save").submit(true).onClick("_form.setClean();") %></td>
</tr>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</labkey:form>
<script type="text/javascript">
    var _form = new LABKEY.Form({ formElement: 'form-preferences' });
</script>
