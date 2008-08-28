<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<UserController.ImpersonateBean> me = (HttpView<UserController.ImpersonateBean>) HttpView.currentView();
    UserController.ImpersonateBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    User user = context.getUser();
    Container c = context.getContainer();

    if (bean.emails.isEmpty())
        return;
%>
    <form method="get" action="<%=new UserController.UserUrlsImpl().getImpersonateURL(c)%>">
        <table>
        <tr><td><b><%=h(bean.message)%></b></td></tr>
        <tr><td><%
            if (user.isImpersonated())
            {
        %>
        Already impersonating; click <a href="<%=h(urlProvider(LoginUrls.class).getLogoutURL(c))%>">here</a> to change back to <%=h(user.getImpersonatingUser().getDisplayName(context))%>.<%
            }
            else
            {
        %>
            <select id="email" name="email" style="width:200"><%
                for (String email : bean.emails)
                {%>
                <option value="<%=h(email)%>" <%=(email.equals(user.getEmail())) ? "selected" : ""%>><%=h(email)%></option ><%
                }
            %>
            </select><br>
            <%=PageFlowUtil.generateSubmitButton("Impersonate")%><%
            }
            %>
        </td></tr>
        </table>
    </form>
