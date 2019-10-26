<%
/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.authentication.ldap.LdapController.TestLdapForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TestLdapForm> me = (JspView<TestLdapForm>)HttpView.currentView();
    TestLdapForm form = me.getModelBean();

    // Message is HTML, which is why we don't use Spring error handling
    if (null != form.getMessage())
    {
        %><%=form.getMessage()%><br><br><%
    }
%>
Use this page to test your LDAP authentication settings. If you're unfamiliar with LDAP or your organization's directory services
configuration you should consult with your network administrator. You may also want to download an LDAP client browser.
<ul>
<li>Server URL is typically of the form ldap://servername.domain.org</li>
<li>Security Principal varies widely by vendor and configuration. A couple starting points:
    <ul>
    <li>Microsoft Active Directory requires just the email address.</li>
    <li>Other LDAP servers typically require a more detailed DN (distinguished name) such as: uid=myuserid,ou=people,dc=mydomain,dc=org</li>
    </ul>
</ul>
<br>

<labkey:form name="testLdap" method="post" action="testLdap.post">
<table class="lk-fields-table">
    <tr><td>LDAP Server URL:</td><td><input id="server" type="text" name="server" style="width:500px;" value="<%=h(form.getServer())%>"></td></tr>
    <tr><td>Security Principal:</td><td><input id="principal" type="text" name="principal" style="width:500px;" value="<%=h(form.getPrincipal())%>"></td></tr>
    <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:500px;" value="<%=h(form.getPassword())%>"></td></tr>
    <tr><td>Use SASL Authentication:</td><td><input id="sasl" type="checkbox" name="sasl"<%=checked(form.getSasl())%>></td></tr>
    <tr><td colspan=2>
        <br/>
        <%= button("Test").submit(true) %>
    </td></tr>
</table>
</labkey:form>

