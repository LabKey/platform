<%
/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.ldap.LdapController" %>
<%@ page import="org.labkey.authentication.ldap.LdapController.Config" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
%>
<labkey:form action="configure.post" method="post">
<table>
    <%=formatMissedErrorsInTable("form", 2)%>
    <tr>
        <td class="labkey-form-label">LDAP servers<%= PageFlowUtil.helpPopup("LDAP servers", "Specifies the addresses of your organization's LDAP server or servers. You can provide a list of multiple servers separated by semicolons. The general form for the LDAP server address is ldap://servername.domain.org:389")%></td>
        <td><input type="text" name="servers" size="50" value="<%=h(bean.getServers()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">LDAP domain<%= PageFlowUtil.helpPopup("LDAP domain", "For all users signing in with an email address from this domain, LabKey will attempt authentication against the LDAP server. Use '*' to attempt LDAP authentication on all email addresses entered, regardless of domain.")%></td>
        <td><input type="text" name="domain" size="50" value="<%=h(bean.getDomain()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">LDAP principal template<%=PageFlowUtil.helpPopup("LDAP principal template", "Enter an LDAP principal template that matches the requirements of the configured LDAP server(s). The template supports substitution syntax: include ${email} to substitute the user's full email address and ${uid} to substitute the left part of the user's email address.")%></td>
        <td><input type="text" name="principalTemplate" size="50" value="<%=h(bean.getPrincipalTemplate()) %>"></td>
    </tr>
    <tr>
        <td class="labkey-form-label">Use SASL authentication</td>
        <td><input type="checkbox" name="SASL"<%=checked(bean.getSASL())%>></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2>
            <%= button("Save").submit(true) %>
            <%= button(bean.reshow ? "Done" : "Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
        </td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td colspan=2><%=helpLink("configLdap", "More information about LDAP authentication")%></td>
    </tr>
    <tr>
        <td colspan=2><%=textLink("Test LDAP settings", urlFor(LdapController.TestLdapAction.class).addReturnURL(getActionURL()))%></td>
    </tr>
</table>
</labkey:form>

