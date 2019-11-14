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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.action.SpringActionController" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.authentication.ldap.LdapConfigureForm" %>
<%@ page import="org.labkey.core.authentication.ldap.LdapController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<LdapConfigureForm> me = (JspView<LdapConfigureForm>)HttpView.currentView();
    LdapConfigureForm bean = me.getModelBean();
    boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

    ActionURL testLdapURL = urlFor(LdapController.TestLdapAction.class);
    if (null != bean.getRowId())
        testLdapURL.addParameter("configuration", bean.getRowId());
    testLdapURL.addReturnURL(getActionURL());
%>
<%=formatMissedErrorsInTable("form", 2)%>
<labkey:form action="configure.post" method="post" layout="horizontal">
    <input type="hidden" name="configuration" value="<%=h(bean.getRowId())%>">
    <labkey:input type="text" name="description" size="50" value="<%=h(bean.getDescription())%>"
        label="Description"
        contextContent="A description that appears on the authentication configuration page to help you distinguish this configuration from others"
    />
    <labkey:input type="text" name="servers" size="50" value="<%=h(bean.getServers())%>"
        label="LDAP server URLs"
        contextContent="Specifies the addresses of your organization's LDAP server or servers. You can provide a list of multiple servers separated by semicolons. The general form for an LDAP server address is ldap://servername.domain.org"
    />
    <labkey:input type="text" name="domain" size="50" value="<%=h(bean.getDomain()) %>"
        label="LDAP domain"
        contextContent="For all users signing in with an email address from this domain, LabKey will attempt authentication against the LDAP server. Set this to an email domain such as 'labkey.org' or specify '*' to attempt LDAP authentication on all email addresses entered, regardless of domain."
    />
    <labkey:input type="text" name="principalTemplate" size="50" value="<%=h(bean.getPrincipalTemplate()) %>"
        label="LDAP principal template"
        contextContent="Enter an LDAP principal template that matches the requirements of the configured LDAP server(s). The template supports substitution syntax: include \${email} to substitute the user's full email address and \${uid} to substitute the left part of the user's email address."
    />
    <labkey:input type="checkbox" name="SASL" checked="<%=bean.getSASL()%>"
        label="Use SASL authentication"
    />
    <labkey:input type="checkbox" name="enabled" label="Enabled" checked="<%=bean.isEnabled()%>" />
    <input type="hidden" name="<%=SpringActionController.FIELD_MARKER%>enabled">
    <br/>
    <%= hasAdminOpsPerms ? button("Save").submit(true) : "" %>
    <%= button(!hasAdminOpsPerms || bean.reshow ? "Done" : "Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
    <br/><br/>
    <%=helpLink("configLdap", "More information about LDAP authentication")%>
    <br/>
    <%=link("Test LDAP settings").onClick("test()")%>
</labkey:form>
<script type="text/javascript">
    function test()
    {
        var servers = document.getElementsByName("servers")[0].value;
        var principal = document.getElementsByName("principalTemplate")[0].value;
        var sasl = document.getElementsByName('SASL')[0].checked;
        var url = LABKEY.ActionURL.buildURL('ldap', 'testLdap', null, {server:servers, principal:principal, sasl:sasl});
        var win = window.open(url, '_testLdap');
        win.focus();
    }
</script>

