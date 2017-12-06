<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.login.LoginController.AuthLogoBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AuthLogoBean> me = (HttpView<AuthLogoBean>) HttpView.currentView();
    AuthLogoBean bean = me.getModelBean();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%><labkey:form enctype="multipart/form-data" method="post">
<table class="lk-fields-table">
<%=formatMissedErrorsInTable("form", 3)%>
<tr>
    <td colspan="3"><input type="hidden" name="name" value="<%=h(bean.provider.getName())%>"></td>
</tr>
<tr id="auth_header_logo_row">
    <td class="labkey-form-label" nowrap>Page header logo</td>
    <%=text(bean.headerLogo)%>
</tr>
<tr id="auth_login_page_logo_row">
    <td class="labkey-form-label" nowrap>Login page logo</td>
    <%=text(bean.loginPageLogo)%>
</tr>
<tr>
    <td colspan="3">&nbsp;</td>
</tr>
<tr>
    <td colspan="3">
        <%= hasAdminOpsPerms ? button("Save").submit(true) : "" %>
        <%= button(!hasAdminOpsPerms || bean.reshow ? "Done" : "Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
    </td>
</tr>
</table>
</labkey:form>
<script type="text/javascript">
    function deleteLogo(prefix)
    {
        var td1 = document.getElementById(prefix + 'td1');
        var td2 = document.getElementById(prefix + 'td2');
        var tr = document.getElementById(prefix + 'row');
        tr.removeChild(td1);
        tr.removeChild(td2);

        var newTd = document.createElement('td');
        newTd.setAttribute('colspan', '2');

        var fb = document.createElement('input');
        fb.setAttribute('name', prefix + 'file');
        fb.setAttribute('type', 'file');
        fb.setAttribute('size', '60');

        var hidden = document.createElement('input');
        hidden.setAttribute('type', 'hidden');
        hidden.setAttribute("name", "deletedLogos");
        hidden.setAttribute("value", prefix + '<%=bean.provider.getName()%>');

        newTd.appendChild(fb);
        newTd.appendChild(hidden);

        tr.appendChild(newTd);        
    }
</script>