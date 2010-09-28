<%
/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.ldap.LdapController" %>
<%@ page import="org.labkey.authentication.ldap.LdapController.Config" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
%>
<form action="configure.post" method="post"><labkey:csrf/>
<table>
    <tr>
        <td class="labkey-form-label">LDAP servers</td>
        <td><input type="text" name="servers" size="50" value="<%=h(bean.getServers()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">LDAP domain</td>
        <td><input type="text" name="domain" size="50" value="<%=h(bean.getDomain()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">LDAP principal template</td>
        <td><input type="text" name="principalTemplate" size="50" value="<%=h(bean.getPrincipalTemplate()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Use SASL authentication</td>
        <td><input type="checkbox" name="SASL" <%=bean.getSASL() ? "checked" : ""%>></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2>
            <%=generateSubmitButton("Save")%>
            <%=generateButton(bean.reshow ? "Done" : "Cancel", urlProvider(LoginUrls.class).getConfigureURL())%>
        </td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2>[<%=bean.helpLink%>]</td>
    </tr>
    <tr>
        <td colspan=2><%=textLink("Test LDAP settings", urlFor(LdapController.TestLdapAction.class).addReturnURL(me.getViewContext().getActionURL()))%></td>
    </tr>
</table>
</form>

