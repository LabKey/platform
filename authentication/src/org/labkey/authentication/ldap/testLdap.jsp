<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.authentication.ldap.LdapController.*" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<TestLdapForm> me = (JspView<TestLdapForm>)HttpView.currentView();
    TestLdapForm form = me.getModelBean();

    // TODO: Use Spring error handling?
    if (null != form.getMessage() && 0 != form.getMessage().length())
        out.print(form.getMessage() + "<br><br>");
%>
Use this page to test your LDAP authentication settings.  If you're unfamiliar with LDAP or your organization's directory
services configuration you should consult with your network administrator.  You may also want to download an LDAP
client browser.
<ul>
<li>Server URL is typically of the form ldap://servername.domain.org:389</li>
<li>Security Principal varies widely by vendor and configuration.  A couple starting points:
    <ul>
    <li>Microsoft Active Directory requires just the email address.</li>
    <li>Sun Directory Server requires a more detailed DN (distinguished name) such as: uid=myuserid,ou=people,dc=mydomain,dc=org</li>
    </ul>
</ul>
<br>

<form name="testLdap" method="post" action="testLdap.post">
<table border="0">
    <tr><td>LDAP Server URL:</td><td><input id="server" type="text" name="server" style="width:400;" value="<%=h(form.getServer())%>"></td></tr>
    <tr><td>Security Principal:</td><td><input id="principal" type="text" name="principal" style="width:400;" value="<%=h(form.getPrincipal())%>"></td></tr>
    <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:400;" value="<%=h(form.getPassword())%>"></td></tr>
    <tr><td>Use SASL Authentication:</td><td><input id="SASL" type="checkbox" name="SASL" <%=form.getSASL() ? "checked" : ""%>></td></tr>
    <tr><td colspan=2 align=center style="height:50">
        <input type="hidden" name="returnUrl" value="<%=form.getReturnUrl()%>">
        <input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Configure">
        <%=PageFlowUtil.buttonLink("Cancel", form.getReturnUrl())%>
    </td></tr>
</table>
</form>

