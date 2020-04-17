<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls"%>
<%@ page import="org.labkey.api.admin.CoreUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.core.admin.AdminController.DeleteCustomStylesheetAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetFaviconAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetLogoAction" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetMobileLogoAction" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AdminController.LookAndFeelResourcesBean bean = ((JspView<AdminController.LookAndFeelResourcesBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();
    boolean canUpdate = !c.isRoot() || c.hasPermission(getUser(), ApplicationAdminPermission.class);
    HtmlString rowSpan = HtmlString.of(!canUpdate ? "1" : "2");
%>
<%=formatMissedErrors("form")%>
<labkey:form name="preferences" enctype="multipart/form-data" method="post" id="form-preferences">

<table cellpadding=0 class="lk-fields-table">
<%=getTroubleshooterWarning(canUpdate, HtmlString.unsafe("<tr><td colspan=2>"), HtmlString.unsafe("</td></tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=2>Customize the logo, icon, and stylesheets used <%=text(c.isRoot() ? "throughout the site" : "in this project")%> (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td class="labkey-form-label" rowspan="<%=rowSpan%>">Header logo<%=helpPopup("Header Logo", "Appears in the header on every page when the page width is greater than 767px.<br><br>Recommend size: 100px x 30px", true, 300)%></td>
    <td>
        <% if (null != bean.customLogo)
        { %>
            Currently using a custom header logo. <%=link("view logo", TemplateResourceHandler.LOGO.getURL(c))%> <%=canUpdate ? link("reset header logo to default", ResetLogoAction.class).usePost() : HtmlString.EMPTY_STRING%>
        <% } else { %>
            Currently using the default header logo.
        <% } %>
    </td>
</tr>
<%
    if (canUpdate)
    {
%>
<tr>
    <td>Replace with: <input type="file" name="logoImage" size="25" style="border: none;"></td>
</tr>
<%
    }
%>
<tr>
    <td class="labkey-form-label" rowspan="<%=rowSpan%>">Responsive logo<%=helpPopup("Responsive Logo", "Appears in the header on every page when the page width is less than 768px.<br><br>Recommend size: 30px x 30px", true, 300)%></td>
    <td>
        <% if (null != bean.customLogoMobile)
        { %>
        Currently using a custom responsive logo. <%=link("view logo", TemplateResourceHandler.LOGO_MOBILE.getURL(c))%> <%=canUpdate ? link("reset responsive logo to default", ResetMobileLogoAction.class).usePost() : HtmlString.EMPTY_STRING%>
        <% } else { %>
        Currently using the default responsive logo.
        <% } %>
    </td>
</tr>
<%
    if (canUpdate)
    {
%>
<tr>
    <td>Replace with: <input type="file" name="logoMobileImage" size="25" style="border: none;"></td>
</tr>
<%
    }
%>
<tr>
    <td class="labkey-form-label" rowspan="<%=rowSpan%>">Favorite icon (displayed in user's favorites or bookmarks, .ico file only)</td>
    <td>
        <% if (null != bean.customFavIcon)
        { %>
            Currently using a custom favorite icon. <%=link("view icon", TemplateResourceHandler.FAVICON.getURL(c))%> <%=canUpdate ? link("reset favorite icon to default", ResetFaviconAction.class).usePost() : HtmlString.EMPTY_STRING%>
        <% } else { %>
            Currently using the default favorite icon.
        <% } %>
    </td>
</tr>
<%
    if (canUpdate)
    {
%>
<tr>
    <td>Replace with: <input type="file" name="iconImage" size="25" style="border: none;"></td>
</tr>
<%
    }
%>

<tr>
    <td class="labkey-form-label" rowspan="<%=rowSpan%>">Custom stylesheet</td>
    <td>
        <% if (null != bean.customStylesheet)
        { %>
            Currently using a custom stylesheet. <%=link("view CSS", PageFlowUtil.urlProvider(CoreUrls.class).getCustomStylesheetURL(getContainer()))%> <%=canUpdate ? link("delete custom stylesheet", DeleteCustomStylesheetAction.class).usePost() : HtmlString.EMPTY_STRING%>
        <% } else { %>
            No custom stylesheet.
        <% } %>
    </td>
</tr>
<%
    if (canUpdate)
    {
%>
<tr>
    <td>Replace with: <input type="file" name="customStylesheet" size="25" style="border: none;"></td>
</tr>
<tr>
    <td><br/><%=button("Save").submit(true).onClick("_form.setClean();")%></td>
</tr>
<%
    }
    else
    {
%>
    <tr>
        <td><br/><%=button("Done").href(urlProvider(AdminUrls.class).getAdminConsoleURL())%></td>
    </tr>
<%
    }
%>
<tr>
    <td>&nbsp;</td>
</tr>

</table>
</labkey:form>
<script type="text/javascript">
    var _form = new LABKEY.Form({ formElement: 'form-preferences' });
</script>
