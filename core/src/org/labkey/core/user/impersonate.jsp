<%
/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.user.UserController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<UserController.ImpersonateUserBean> me = (HttpView<UserController.ImpersonateUserBean>) HttpView.currentView();
    UserController.ImpersonateUserBean bean = me.getModelBean();

    User user = getUser();
    Container c = getContainer();
    ActionURL returnURL = getActionURL();

    if (bean.emails.isEmpty())
        return;
%>
    <form method="get" action="<%=new UserController.UserUrlsImpl().getImpersonateUserURL(c)%>"><labkey:csrf/>
        <table>
            <%

            if (bean.isAdminConsole)
            { %>
            <tr><td><%=bean.title%></td></tr><%
            }

            if (user.isImpersonated())
            {
                User impersonatingUser = user.getImpersonatingUser();
                String changeBackMessage = user.equals(impersonatingUser) ? "stop impersonating" : "change back to " + impersonatingUser.getDisplayName(user);
        %>
            <tr><td>Already impersonating; click <a href="<%=h(urlProvider(LoginUrls.class).getStopImpersonatingURL(c, user.getImpersonationContext().getReturnURL()))%>">here</a> to <%=h(changeBackMessage)%>.</td></tr><%
            }
            else
            {
                if (null != bean.message)
                { %>
            <tr><td><%=bean.message%></td></tr><%
                }
            %>
            <tr><td>
                <select id="email" name="email" style="width:200px;"><%
                    for (String email : bean.emails)
                    {%>
                    <option value="<%=h(email)%>"><%=h(email)%></option><%
                    }
                %>
                </select>
            <%=generateReturnUrlFormField(returnURL)%>
            <%=generateSubmitButton("Impersonate")%>
            </td></tr><%
            }
            %>
        </table>
    </form>
