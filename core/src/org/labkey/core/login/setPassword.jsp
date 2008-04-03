<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.VerifyBean bean = ((JspView<LoginController.VerifyBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrors("form");
%>
<form method="POST" action="setPassword.post">
<table><%
    if (errors.length() > 0)
    { %>
    <tr><td colspan=2><%=errors%></td></tr>
    <tr><td colspan=2>&nbsp;</td></tr><%
    } %>
    <tr><td colspan=2><%=h(bean.email)%>:</td></tr>
    <tr><td colspan=2>Type in a new password twice.  After setting your password you'll be asked to sign in.</td></tr>
    <tr><td colspan=2>&nbsp;</td></tr>
    <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:150;"></td></tr>
    <tr><td>Retype Password:</td><td><input type="password" name="password2" style="width:150;"></td></tr>
    <tr><td><input type=hidden name=email value="<%=h(bean.email)%>"></td>
    <td><input type=hidden name=verification value="<%=h(bean.verification)%>"></td></tr>
    <tr><td></td><td style="height:50"><input type="image" src="<%=PageFlowUtil.buttonSrc("Set Password")%>" name="set"></td></tr>
</table>
</form>