<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.authentication.ldap.LdapController" %>
<%@ page import="org.labkey.authentication.ldap.LdapController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Config> me = (JspView<Config>)HttpView.currentView();
    Config bean = me.getModelBean();
%>
<form action="configure.post" method="post">
<table>
    <tr>
        <td class="ms-searchform">LDAP servers</td>
        <td class="normal"><input type="text" name="servers" size="50" value="<%=h(bean.getServers()) %>"></td>
    </tr>
    <tr>
        <td class="ms-searchform">LDAP domain</td>
        <td class="normal"><input type="text" name="domain" size="50" value="<%=h(bean.getDomain()) %>"></td>
    </tr>
    <tr>
        <td class="ms-searchform">LDAP principal template</td>
        <td class="normal"><input type="text" name="principalTemplate" size="50" value="<%=h(bean.getPrincipalTemplate()) %>"></td>
    </tr>
    <tr>
        <td class="ms-searchform">Use SASL authentication</td>
        <td class="normal"><input type="checkbox" name="SASL" <%=bean.getSASL() ? "checked" : ""%>></td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td class="normal" colspan=2>
            <input type=image src="<%=PageFlowUtil.submitSrc()%>" value="Configure">
            <%=PageFlowUtil.buttonLink("Done", PageFlowUtil.urlProvider(LoginUrls.class).getConfigureURL())%>
        </td>
    </tr>
    <tr><td colspan="2">&nbsp;</td></tr>
    <tr>
        <td class="normal" colspan=2>[<%=bean.helpLink%>]</td>
    </tr>
    <tr>
        <td class="normal" colspan=2>[<a href="<%=urlFor(LdapController.TestLdapAction.class).addReturnURL(me.getViewContext().getActionURL())%>">Test LDAP settings</a>]</td>
    </tr>
</table>
</form>

