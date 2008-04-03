<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.datamodel.Server" %>
<%@ page import="org.labkey.biotrue.datamodel.BtManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.biotrue.objectmodel.BtServer" %>
<%@ page import="org.labkey.biotrue.controllers.BtController" %>
<%@ page import="org.labkey.biotrue.task.BtTaskManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentContext();
    Server[] servers = BtManager.get().getServers(context.getContainer());
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<span class="labkey-error"><%=errors%></span>

<form action="updateAdmin.view" method="post">
    <table class="normal">
<%
    for (Server server : servers) {
        BtServer btServer = BtServer.fromId(server.getRowId());

        if (btServer == null)
            continue;

        String status = "idle";
        boolean anyTasks = BtTaskManager.get().anyTasks(btServer);
        if (anyTasks)
            status = "synchronizing";
%>
        <tr>
            <td><input type="checkbox" name="deleteServer" value="<%=server.getRowId()%>"></td>
            <td>Server:&nbsp;<%=server.getName()%></td>
            <td>Status:&nbsp;<%=status%></td>
            <td>[<a href="<%=btServer.urlFor(BtController.Action.scheduledSync)%>">configure synchronization</a>]
<%
        if (anyTasks) {
%>
            <td>[<a href="<%=btServer.urlFor(BtController.Action.cancelSynchronization)%>">cancel synchronization</a>]
<%
        } else {
%>
            <td>[<a href="<%=btServer.urlFor(BtController.Action.synchronizeServer)%>">synchronize now</a>]
<%
        }
%>
            <td>[<a href="<%=btServer.urlFor(BtController.Action.configurePassword)%>">configure password</a>]</td>
        </tr>
<%
    }
%>
    </table>
    <table class="normal">
        <tr><td>
            <%=PageFlowUtil.buttonLink("Back to BioTrue", context.cloneActionURL().setAction("begin.view"))%>&nbsp;
            <input type="image" src="<%=PageFlowUtil.buttonSrc("Delete Selected Servers")%>">&nbsp;
        </td></tr>
        <tr><td>&nbsp;</td></tr>
    </table>
</form>
