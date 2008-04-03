<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ButtonServlet"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    LoginController.LoginForm form = ((JspView<LoginController.LoginForm>)HttpView.currentView()).getModelBean();

    String errors = formatMissedErrors("form");
%>
<form style="margin:0" method="POST" action="resetPassword.post">
    <table border="0"><%
        if (errors.length() > 0)
        { %>
        <tr><td colspan=2><%=errors%></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
        }
        %>
        <tr><td colspan=2>To reset your password, type in your email address and click the Submit button.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Email:</td><td><input id="EmailInput" type="text" name="email" value="<%=h(form.getEmail())%>" style="width:200;"></td></tr>
        <tr>
            <td></td>
            <td style="height:50"><input name="reset" type="image" value="reset" src="<%=ButtonServlet.buttonSrc("Submit")%>">
            <%=PageFlowUtil.buttonLink("Cancel", LoginController.getLoginURL())%>
            </td>
        </tr>
    </table>
</form>