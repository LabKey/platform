<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page import="org.labkey.biotrue.objectmodel.BtServer" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>

<%
    ServerForm form = (ServerForm) __form;
    Server server = form.getServer()._server;
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<span class="labkey-error"><%=errors%></span>


<form action="updateScheduledSync.view" method="post">
    <table class="normal">
        <input type="hidden" name="serverId" value="<%=server.getRowId()%>">
        <tr><td class=ms-vb align=center><div class="ms-searchform"><b>Scheduled Synchronization</b></div></td></tr>
        <tr><td>Server:&nbsp;<%=server.getName()%></td></tr>
        <tr><td>Synchronize with the BioTrue server:&nbsp;
            <select name="serverSyncInterval">
                <option value="0" <%=getSelected("0", server.getSyncInterval())%>>manually</option>
                <option value="1" <%=getSelected("1", server.getSyncInterval())%>>every 1 hour</option>
                <option value="2" <%=getSelected("2", server.getSyncInterval())%>>every 2 hours</option>
                <option value="4" <%=getSelected("4", server.getSyncInterval())%>>every 4 hours</option>
                <option value="6" <%=getSelected("6", server.getSyncInterval())%>>every 6 hours</option>
                <option value="12" <%=getSelected("12", server.getSyncInterval())%>>every 12 hours</option>
                <option value="24" <%=getSelected("24", server.getSyncInterval())%>>every 24 hours</option>
            </select>
        <tr><td></td></tr>
        <tr><td>
            <%=PageFlowUtil.buttonLink("Cancel", form.getContext().cloneActionURL().setAction("admin.view"))%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Update")%>">&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>

<%!
    public String getSelected(String value, int option) {
        if (StringUtils.equals(value, String.valueOf(option)))
            return "selected";
        return "";
    }
%>

