<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.security.ACL"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>) HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    int permissions = context.getContainer().getAcl().getPermissions(context.getUser());
    List<ColumnInfo> cols = dataset.getTableInfo(context.getUser(), true, false).getColumns();

// UNDONE: clean way to get count of system fields???
    int systemCount = 6;
%>
<table class="normal">
    <tr>
<!--    <th>ID</th> -->
        <th>Name</th>
        <th>Label</th>
        <th>Type</th>
        <th>Format</th>
        <th>Required</th>
        <th>Description</th>
    </tr><%

    for (int i=0 ; i<systemCount ; i++)
    {
        ColumnInfo col = cols.get(i);
        %><tr>
            <td><%=h(col.getName())%></td>
            <td><%=h(col.getCaption())%></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormatString())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td><%=h(col.getDescription())%></td>
          </tr><%
    }

%><tr><td colspan=6><hr height=1></td></tr><%

    for (int i = systemCount; i < cols.size(); i++)
    {
        ColumnInfo col = cols.get(i);
        boolean isKeyColumn = (StringUtils.equalsIgnoreCase(col.getName(), dataset.getKeyPropertyName()));
%>
        <tr>
            <td><%=isKeyColumn?"<b>":""%><%= col.getName()%><%=isKeyColumn?"</b>":""%></td>
            <td><%= col.getCaption() %></td>
            <td><%=h(col.getFriendlyTypeName())%></td>
            <td><%=h(col.getFormatString())%></td>
            <td align="center"><input type=checkbox disabled <%=col.isNullable() ? "" : "checked"%>></td>
            <td><%=h(col.getDescription())%></td>
        </tr>
        <%
    }
%>
</table>

<%
    if (0 != (permissions & ACL.PERM_ADMIN))
    {
        if (dataset.getTypeURI() == null)
        {
            %>[<a href="bulkImportDataTypes.view?">Bulk import dataset schemas</a>]<%
        }
    }
%>