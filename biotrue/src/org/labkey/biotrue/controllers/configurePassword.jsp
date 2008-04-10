<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    ServerForm form = (ServerForm) __form;
    Server server = form.getServer()._server;
%>
<labkey:errors />


<form action="" method="post">
    <table class="normal">
        <input type="hidden" name="serverId" value="<%=server.getRowId()%>">
        <tr><td class=ms-vb align=center><div class="ms-searchform"><b>Set Server Password</b></div></td></tr>
        <tr><td>Server:&nbsp;<%=server.getName()%></td><td><input name="password" value="<%=StringUtils.trimToEmpty(server.getPassword())%>"></td></tr>
        <tr><td colspan="2">
            <%=PageFlowUtil.buttonLink("Cancel", form.getContext().cloneActionURL().setAction("admin.view"))%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>">&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>

