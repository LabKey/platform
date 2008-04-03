<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>

<%
    ServerForm form = (ServerForm) __form;
    Server server = form.getServer()._server;
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<span class="labkey-error"><%=errors%></span>


<form action="updatePassword.view" method="post">
    <table class="normal">
        <input type="hidden" name="serverId" value="<%=server.getRowId()%>">
        <tr><td class=ms-vb align=center><div class="ms-searchform"><b>Set Server Password</b></div></td></tr>
        <tr><td>Server:&nbsp;<%=server.getName()%></td><td><input name="password" value="<%=server.getPassword()%>"></td></tr>
        <tr><td colspan="2">
            <%=PageFlowUtil.buttonLink("Cancel", form.getContext().cloneActionURL().setAction("admin.view"))%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>">&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>

