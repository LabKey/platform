<%
/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.VerifyBean bean = ((JspView<LoginController.VerifyBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrors("form");
%>
<form method="POST" action="setPassword.post">
<table><%
    if (errors.length() > 0)
    { %>
    <tr><td colspan=2><%=errors%></td></tr>
    <tr><td colspan=2>&nbsp;</td></tr><%
    }

    if (!bean.unrecoverableError)
    { %>
    <tr><td colspan=2><%=h(bean.email)%>:</td></tr>
    <tr><td colspan=2>Choose a password you'll use to access this server.</td></tr>
    <tr><td colspan=2>&nbsp;</td></tr>
    <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:150;"></td></tr>
    <tr><td>Retype Password:</td><td><input type="password" name="password2" style="width:150;"></td></tr>
    <tr>
        <td>
            <input type=hidden name=email value="<%=h(bean.email)%>"><%

            if (bean.form.getSkipProfile())
            { %>
            <input type=hidden name=skipProfile value="1"><%
            }
            if (null != bean.form.getReturnUrl())
            { %>
            <input type=hidden name=<%=ReturnUrlForm.Params.returnUrl%> value="<%=h(bean.form.getReturnUrl())%>"><%
            }

            %>
        </td>
        <td><input type=hidden name=verification value="<%=h(bean.form.getVerification())%>"></td></tr>
    <tr><td></td><td height="50"><%=PageFlowUtil.generateSubmitButton("Set Password", "", "name=\"set\"")%></td></tr><%
    } %>
</table>
</form>