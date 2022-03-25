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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.ApplicationAdminPermission" %>
<%@ page import="org.labkey.api.settings.LookAndFeelPropertiesManager.ResourceType" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.LookAndFeelBean" %>
<%@ page import="org.labkey.core.admin.AdminController.ResetResourceAction" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LookAndFeelBean bean = ((JspView<LookAndFeelBean>)HttpView.currentView()).getModelBean();
    Container c = getContainer();
    boolean canUpdate = !c.isRoot() || c.hasPermission(getUser(), ApplicationAdminPermission.class);
    HtmlString rowSpan = HtmlString.of(!canUpdate ? "1" : "2");
%>
<%=formatMissedErrors("form")%>
<labkey:form name="preferences" enctype="multipart/form-data" method="post" id="form-preferences">

<table cellpadding=0 class="lk-fields-table">
<%=getTroubleshooterWarning(canUpdate, HtmlString.unsafe("<tr><td colspan=4>"), HtmlString.unsafe("</td></tr>"))%>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>

<tr>
    <td colspan=4>Customize the header logo, responsive logo, favicon, and stylesheet used <%=h(c.isRoot() ? "throughout the site" : "in this project")%> (<%=bean.helpLink%>)</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<%
    for (ResourceType type : ResourceType.values())
    {
%>
    <tr>
        <td class="labkey-form-label" style="width: 150px;" rowspan="<%=rowSpan%>"><%=h(type.getLongLabel())%><%=type.getHelpPopup()%></td>
        <td>
            <% if (type.isSet(getContainer()))
            { %>
            Currently using a custom <%=h(type.getShortLabel().toLowerCase())%>.</td><td><%=type.getViewLink(getContainer()).target("_view")%></td><td><%=canUpdate ? link(type.getDeleteText(), urlFor(ResetResourceAction.class).addParameter("resource", type.name())).usePost() : HtmlString.NBSP%>
            <% } else { %>
            <%=h(type.getDefaultText())%>.
            <% } %>
        </td>
    </tr>
<%
        if (canUpdate)
        {
%>
    <tr>
        <td>Replace with: <input type="file" name="<%=h(type.getFieldName())%>" size="25" style="border: none;"></td>
    </tr>
<%
        }
    }
%>

<%
    if (canUpdate)
    {
%>
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
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    var _form = new LABKEY.Form({ formElement: 'form-preferences' });
</script>
