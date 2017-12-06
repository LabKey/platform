<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.PasswordExpiration" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.LoginController.Config" %>
<%@ page import="org.labkey.core.login.PasswordRule" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
%>
<labkey:form action="<%=h(buildURL(LoginController.ConfigureDbLoginAction.class))%>" method="post">
<table class="lk-fields-table">
    <tr>
        <td class="labkey-form-label">Password Strength</td>
        <td><table class="labkey-data-region-legacy labkey-show-borders"><%
            for (PasswordRule rule : PasswordRule.values())
            { %>
            <tr valign="center">
                <td style="padding: 5px;">
                    <input type="radio" name="strength" value="<%=h(rule.name())%>"<%=checked(rule.equals(bean.currentRule))%>><b><%=h(rule.name())%></b>
                </td>
                <td style="padding: 5px;">
                    <%=text(rule.getFullRuleHTML())%>
                </td>
            </tr>
                <%
            }
        %></table></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Password Expiration</td>
        <td><select name="expiration"><%
            for (PasswordExpiration expiration : PasswordExpiration.displayValues())
            { %>
            <option value="<%=h(expiration.name())%>"<%=selected(expiration.equals(bean.currentExpiration))%>><%=h(expiration.getDescription())%></option>
                <%
            }
        %></select></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2>
            <%= hasAdminOpsPerms ? button("Save").submit(true) : "" %>
            <%= button(!hasAdminOpsPerms || bean.reshow ? "Done" : "Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
        </td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2><%=helpLink("configDbLogin", "More information about database authentication")%></td>
    </tr>
</table>
</labkey:form>