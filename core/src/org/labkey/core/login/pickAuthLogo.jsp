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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.core.login.LoginController.AuthLogoBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<AuthLogoBean> me = (HttpView<AuthLogoBean>) HttpView.currentView();
    AuthLogoBean bean = me.getModelBean();

    if (bean.formatInTable)
    {
%>
<tr><td class="labkey-form-label-nowrap">Page header logo</td><td><%=text(bean.headerLogo)%></td></tr>
<tr><td class="labkey-form-label-nowrap">Login page logo</td><td><%=text(bean.loginPageLogo)%></td></tr>
<%
    }
    else
    {
%>
<div class="form-group"><label class="control-label col-sm-3 col-lg-2">Page header logo</label><div class="col-sm-9 col-lg-10"><%=text(bean.headerLogo)%></div></div>
<div class="form-group"><label class="control-label col-sm-3 col-lg-2">Login page logo</label><div class="col-sm-9 col-lg-10"><%=text(bean.loginPageLogo)%></div></div>
<%
    }
%>
<script type="text/javascript">
    function deleteLogo(name)
    {
        var d1 = document.getElementById(name + 'd1');
        var d2 = document.getElementById(name + 'd2');
        d1.innerHTML = "";
        d2.innerHTML = "";

        var fb = document.createElement('input');
        fb.setAttribute('name', name);
        fb.setAttribute('type', 'file');
        fb.setAttribute('size', '60');

        var hidden = document.createElement('input');
        hidden.setAttribute('type', 'hidden');
        hidden.setAttribute("name", "deletedLogos");
        hidden.setAttribute("value", name);

        d1.appendChild(fb);
        d1.appendChild(hidden);
    }
</script>