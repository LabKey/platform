<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.user.UserController.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ChangeEmailBean> me = (JspView<ChangeEmailBean>) HttpView.currentView();
    ChangeEmailBean bean = me.getModelBean();
%>
<form method="post" action="showChangeEmail.post">
<input type="hidden" name="userId" value="<%=bean.userId%>">
<table><%=formatMissedErrorsInTable("form", 2)%>
    <tr>
        <td>Current Email:</td>
        <td><%=h(bean.currentEmail)%></td>
    </tr>
    <tr>
        <td>New Email:</td>
        <td><input type="text" name="newEmail" id="newEmail" value=""></td>
    </tr>
    <tr>
        <td colspan=2><input type="image" src="<%=PageFlowUtil.submitSrc()%>" value="Submit"></td>
    </tr>
</table>
</form>
