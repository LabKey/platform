<%
/*
 * Copyright (c) 2005-2010 Fred Hutchinson Cancer Research Center
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.DbLoginManager" %>
<%@ page import="org.labkey.api.collections.NamedObject" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.SetPasswordBean bean = ((JspView<LoginController.SetPasswordBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrors("form");
%>
<form method="POST" action="<%=bean.actionName%>.post"><labkey:csrf />
<table><%
    if (errors.length() > 0)
    { %>
    <tr><td colspan=2><%=errors%></td></tr>
    <tr><td colspan=2>&nbsp;</td></tr><%
    }

    if (!bean.unrecoverableError)
    { %>
    <tr><td colspan=2><%=h(bean.email)%>:</td></tr>
    <tr><td colspan=2><%=h(bean.message)%></td></tr>
    <tr><td colspan=2>&nbsp;</td></tr>
    <tr><td colspan=2><%=DbLoginManager.getPasswordRule().getRuleHTML()%></td></tr>
    <tr><td colspan=2>&nbsp;</td></tr><%

    for (NamedObject passwordInput : bean.passwordInputs)
    { %>
    <tr><td width=150px><%=h(passwordInput.getName())%></td><td><input id="<%=passwordInput.getObject()%>" type="password" name="<%=passwordInput.getObject()%>" style="width:150px;"></td></tr><%
    }
    %>
    <tr>
        <td>
            <input type="hidden" name="email" value="<%=h(bean.email)%>"><%

            if (null != bean.form.getVerification())
            { %>
            <input type="hidden" name="verification" value="<%=h(bean.form.getVerification())%>"><%
            }

            if (null != bean.form.getMessage())
            { %>
            <input type="hidden" name="message" value="<%=h(bean.form.getMessage())%>"><%
            }

            if (bean.form.getSkipProfile())
            { %>
            <input type="hidden" name="skipProfile" value="1"><%
            }

            if (null != bean.form.getReturnURLHelper())
            { %>
            <%=generateReturnUrlFormField(bean.form)%><%
            }
        %>
        </td>
    </tr>
    <tr><td></td><td height="50"><%=PageFlowUtil.generateSubmitButton("Set Password", "", "name=\"set\"")%><%=bean.cancellable ? generateButton("Cancel", bean.form.getReturnURLHelper()) : ""%></td></tr><%
    } %>
</table>
</form>