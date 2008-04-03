<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.experiment.controllers.list.ListController" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<% Map<String, ListDefinition> map = ListService.get().getLists(getContainer());
%>

<% if (map.isEmpty()) { %>
<p>There are no user-defined lists in this folder.</p>
<% } else {%>
    <table class="normal">
        <tr>
            <th colspan="4">User defined lists in this folder.</th>
        </tr>
   <% for (ListDefinition def : map.values()) { %>
        <tr>
        <td>
            <%=h(def.getName())%>
        </td>
        <td>
            <labkey:link href="<%=def.urlShowData()%>" text="view data" />
        </td>
<% if (hasPermission(ACL.PERM_UPDATE)) { %>
        <td>
            <labkey:link href="<%=def.urlShowDefinition()%>" text="view design" />
        </td>
<% } %>
<% if (AuditLogService.get().isViewable()) { %>
        <td>
            <labkey:link href="<%=def.urlShowHistory()%>" text="view history" />
        </td>
<% } %>            
        <td>
            <%=h(def.getDescription())%>
        </td>
        </tr>
<% }%>
    </table>
    <%} %>
<% if (hasPermission(ACL.PERM_ADMIN)) { %>
    <labkey:button text="Create New List" href="<%=urlFor(ListController.Action.newListDefinition)%>" />
<% } %>