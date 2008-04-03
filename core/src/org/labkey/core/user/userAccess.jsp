<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.core.user.UserController"%>
<%@ page import="org.labkey.api.security.Group"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.ContainerManager"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<UserController.AccessDetail> me =
            (JspView<UserController.AccessDetail>) HttpView.currentView();
    UserController.AccessDetail bean = me.getModelBean();
    List<UserController.AccessDetailRow> rows = bean.getRows();

    String shadeColor = "#EEEEEE";
    String borderColor = "#808080";
    String styleTH = "border-right:solid 1px " + borderColor + "; border-top:solid 2px " + borderColor + ";";
    String styleTD = "border-right:solid 1px " + borderColor + ";";
    int cellPadding = 3;
%>
<table class="normal" cellspacing="0" cellpadding="<%= cellPadding %>" style="border-bottom:solid 2px <%=borderColor%>;">
    <tr>
        <th style="border-left:solid 1px <%= borderColor %>;<%=styleTH%>">Container</th>
        <th style="<%=styleTH%>">Current Access</th>
<%
    if (bean.showGroups())
    {
%>
        <th style="<%=styleTH%>">Relevant Group(s)</th>
<%
    }
%>
    </tr>
<%
    int rowNumber = 0;
    for (UserController.AccessDetailRow row : rows)
    {
        boolean inherited = row.isInheritedAcl() && !row.getContainer().isProject();
        ActionURL containerPermissionsLink = new ActionURL("Security", "project", row.getContainer());
%>
    <tr<%= rowNumber++ % 2 == 0 ?  " bgcolor=\"" + shadeColor + "\"" : ""%>>
        <td style="border-left:solid 1px <%= borderColor %>;padding-left:<%= cellPadding + (10 * row.getDepth()) %>;<%= styleTD %>">
            <a href="<%= containerPermissionsLink.getLocalURIString() %>"><%= row.getContainer().getName() %></a>
        </td>
        <td style="<%= styleTD %>"><%= row.getAccess() %><%= inherited ? "*" : "" %></td>
    <%
        if (bean.showGroups())
        {
            out.print("<td style=\"" +  styleTD + "\">");
            boolean first = true;
            for (Group group : row.getGroups())
            {
                Container groupContainer = group.isAdministrators() ? ContainerManager.getRoot() : row.getContainer().getProject();
                String displayName = (group.isProjectGroup() ? groupContainer.getName() + "/" : "Site ") + group.getName();
                if (group.isAdministrators() || group.isProjectGroup())
                {
                    ActionURL groupURL = new ActionURL("Security", "group", groupContainer);
                    String groupName = group.isProjectGroup() ? groupContainer.getPath() + "/" + group.getName() : group.getName();
                    groupURL.addParameter("group", groupName);
                    %><%= !first ? ", " : "" %><a href="<%= groupURL.getLocalURIString() %>"><%= displayName %></a><%
                }
                else
                {
                    %><%= !first ? ", " : "" %><%= displayName %><%
                }
                first = false;
            }
            out.print("&nbsp;</td>");
        }
    %>
    </tr>
<%
    }
%>
</table><br>
*Indicates that this group's permissions are inherited from the parent folder