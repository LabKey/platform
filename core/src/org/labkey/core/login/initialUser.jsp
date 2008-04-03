<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.springframework.validation.Errors" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
String email = ((JspView<String>) HttpView.currentView()).getModelBean();

Errors errors = getErrors("form");

if (errors.hasErrors())
    out.print(formatMissedErrors("form") + "<br><br>");

// Don't display the form if we have a fatal error    
if (errors.hasGlobalErrors())
    return;
%>
You are the first user of this LabKey Server.  Enter a full, valid email address to use as your id for logging
into the system.  This email address will be added to the global Administrators group, which will give you
permission to add users to the system, add users to groups, assign permissions, create projects and
folders, etc.<br><br>

<form name="initialUser" method="post" action="initialUser.post">
<table border="0">
    <tr><td>Email:</td><td><input id="email" type="text" name="email" value="<%=h(email)%>" style="width:200px;"></td></tr>
    <tr><td>&nbsp;</td><td style="height:50px"><input type="image" src="<%=PageFlowUtil.buttonSrc("Register") %>" name="register"></td></tr>
</table>
</form>