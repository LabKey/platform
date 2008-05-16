<%@ page import="org.labkey.api.security.AuthenticationManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.login.LoginController.LoginBean" %>
<%@ page import="org.labkey.core.login.LoginController.LoginForm" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<LoginBean> me = (HttpView<LoginBean>) HttpView.currentView();
    LoginBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    User user = context.getUser();
    LoginForm form = bean.form;
    String returnURI = form.getReturnUrl();
    if (returnURI == null)
        returnURI = form.getReturnActionURL().toString();
    boolean agreeOnly = bean.agreeOnly;

    if (agreeOnly)
    { %>
<form name="login" method="POST" action="agreeToTerms.post"><%
    }
    else
    { %>
<form name="login" method="POST" action="login.post"><%
    } %>
    <table border="0"><%
    if (null != form.getErrorHtml() && form.getErrorHtml().length() > 0)
    { %>
        <tr><td colspan=2><b><%=form.getErrorHtml()%></b></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
    }
    if (!user.isGuest())
    { %>
        <tr><td colspan=2>You are currently logged in as <%=h(user.getName())%>.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
    }
    if (!agreeOnly)
    {
        String logoHtml = AuthenticationManager.getLoginPageLogoHtml(context.getActionURL());
        if (null != logoHtml)
        { %>
        <tr><td colspan="2"><%=logoHtml%></td></tr>
        <tr><td colspan=2>&nbsp;</td></tr><%
        } %>
        <tr><td colspan=2>Type in your email address and password and click the Sign In button.</td></tr>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td>Email:</td><td><input id="email" type="text" name="email" value="<%=h(form.getEmail())%>" style="width:200px;"></td></tr>
        <tr><td>Password:</td><td><input id="password" type="password" name="password" style="width:200px;"></td></tr>
        <tr><td></td><td><input type=checkbox name="remember" id="remember" <%=bean.remember ? "checked" : ""%>><label for="remember">Remember my email address</label></td></tr>
        <tr><td></td><td><a href="resetPassword.view">Forgot your password?</a></td></tr><%
    }
    else
    { %>
        <tr><td colspan=2>You must agree to terms of use to view data in this project.<br></td></tr><%
    }

    if (null != bean.termsOfUseHtml)
    { %>
        <tr><td colspan=2>&nbsp;</td></tr>
        <tr><td></td><td><b>Terms of Use</b></td></tr>
        <tr><td></td><td><%=h(bean.termsOfUseHtml)%></td></tr>
        <tr><td></td><td><input type=checkbox name="approvedTermsOfUse" id="approvedTermsOfUse"<%=bean.termsOfUseChecked ? " checked" : ""%>><label for="approvedTermsOfUse">I agree to these terms</label></td></tr><%
    } %>
        <tr><td></td><td style="height:50px">
            <input type="hidden" name="URI" value="<%=h(returnURI)%>"><%

            if (bean.form.getSkipProfile())
            { %>
            <input type=hidden name=skipProfile value="1"><%
            }
            %>
            <input type="image" src="<%=PageFlowUtil.buttonSrc(bean.agreeOnly ? "Agree" : "Sign In")%>" value="Sign in" name="SUBMIT">
        </td></tr>
    </table>
</form>