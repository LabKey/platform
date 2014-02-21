<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    Portal.WebPart webPart = (Portal.WebPart)HttpView.currentModel();
    ViewContext context = HttpView.currentContext();
    String selected = StringUtils.defaultString(webPart.getPropertyMap().get("style"), "full");
%>

<form name="frmCustomize" method="post" action="<%=h(webPart.getCustomizePostURL(context))%>">
    <input type="radio" name="style" value="full" <%=text("full".equals(selected)?"checked":"")%>>&nbsp;full<br>
    <input type="radio" name="style"value="simple" <%=text("simple".equals(selected)?"checked":"")%>>&nbsp;simple<br>
    <input type="submit">
</form>
